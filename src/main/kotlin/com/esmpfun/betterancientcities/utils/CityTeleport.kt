package com.esmpfun.betterancientcities.utils

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.models.City
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent

/**
 * Teleports staff to a standing spot near the middle of a city. The city sits deep
 * in solid deepslate, so the spot is searched for from the bottom up instead of
 * dropping the player above the structure.
 */
object CityTeleport {

    private const val SEARCH_RADIUS = 12

    fun teleport(plugin: BetterAncientCities, player: Player, city: City) {
        val world = city.getWorld() ?: run {
            player.sendMessage(Component.text("The world '${city.world}' is not loaded.", NamedTextColor.RED))
            return
        }
        val r = city.region
        val cx = (r.minX + r.maxX) shr 1
        val cz = (r.minZ + r.maxZ) shr 1
        world.getChunkAtAsync(cx shr 4, cz shr 4, true).thenAccept { chunk ->
            val spot = try {
                findSpot(chunk, cx, cz, r.minY, r.maxY)
            } catch (e: Exception) {
                null
            }
            plugin.scheduler.runAtEntity(player, Runnable {
                if (spot == null) {
                    player.sendMessage(Component.text(
                        "No safe spot found in the middle of city #${city.id}. Its corner is at ${r.minX}, ${r.minY}, ${r.minZ}.",
                        NamedTextColor.RED
                    ))
                    return@Runnable
                }
                player.teleportAsync(spot, PlayerTeleportEvent.TeleportCause.COMMAND)
                player.sendMessage(Component.text("Teleported to city #${city.id}.", NamedTextColor.GRAY))
            })
        }.exceptionally { e ->
            plugin.logger.warning("Could not load the middle of city #${city.id} to teleport there: ${e.message}")
            null
        }
    }

    /** The lowest spot in the chunk, nearest the centre column first, with ground and two free blocks. */
    private fun findSpot(chunk: Chunk, cx: Int, cz: Int, minY: Int, maxY: Int): Location? {
        val world = chunk.world
        val baseX = chunk.x shl 4
        val baseZ = chunk.z shl 4
        val columns = (0 until 16).flatMap { x -> (0 until 16).map { z -> x to z } }
            .filter { (x, z) -> Math.abs(baseX + x - cx) <= SEARCH_RADIUS && Math.abs(baseZ + z - cz) <= SEARCH_RADIUS }
            .sortedBy { (x, z) -> (baseX + x - cx) * (baseX + x - cx) + (baseZ + z - cz) * (baseZ + z - cz) }
        val low = maxOf(minY, world.minHeight + 1)
        val high = minOf(maxY + 1, world.maxHeight - 2)
        for ((x, z) in columns) {
            for (y in low..high) {
                val ground = chunk.getBlock(x, y - 1, z)
                val feet = chunk.getBlock(x, y, z)
                val head = chunk.getBlock(x, y + 1, z)
                if (!ground.type.isSolid || ground.type in UNSAFE_GROUND) continue
                if (!feet.isPassable || !head.isPassable || feet.isLiquid || head.isLiquid) continue
                if (feet.type in UNSAFE_SPACE || head.type in UNSAFE_SPACE) continue
                return Location(world, baseX + x + 0.5, y.toDouble(), baseZ + z + 0.5)
            }
        }
        return null
    }

    // Landing on a shrieker or sensor would call the warden.
    private val UNSAFE_GROUND = setOf(
        Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW,
        Material.SCULK_SHRIEKER, Material.SCULK_SENSOR, Material.CALIBRATED_SCULK_SENSOR,
    )
    private val UNSAFE_SPACE = setOf(
        Material.FIRE, Material.SOUL_FIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.COBWEB,
    )
}
