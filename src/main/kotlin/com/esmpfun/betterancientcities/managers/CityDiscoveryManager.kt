package com.esmpfun.betterancientcities.managers

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.models.City
import com.esmpfun.betterancientcities.models.IntBox
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.World
import org.bukkit.generator.structure.GeneratedStructure
import org.bukkit.generator.structure.Structure
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/**
 * Registers Ancient Cities the server already knows about. The structure API gives
 * full bounds and per-piece bounds from any one chunk of the city.
 *
 * Every chunk of a city reports the same structure, so it is keyed by the
 * structure's min corner: an in-memory set skips repeats cheaply, and the database's
 * unique constraint covers restarts and races.
 */
class CityDiscoveryManager(private val plugin: BetterAncientCities) {

    /** "world:x:y:z" of cities already handled this session. */
    private val seen = ConcurrentHashMap.newKeySet<String>()

    fun enabled() = plugin.config.getBoolean("discovery.enabled", true)

    fun excluded(world: World) =
        plugin.config.getStringList("discovery.excluded-worlds").any { it.equals(world.name, ignoreCase = true) }

    private fun key(world: String, origin: Triple<Int, Int, Int>) = "$world:${origin.first}:${origin.second}:${origin.third}"

    /** Lets a deleted city be registered again when its chunks next load. */
    fun forget(city: City) {
        seen.remove(key(city.world, city.origin))
    }

    /** Must run on the thread that owns the structure's chunk. */
    fun handle(world: World, gs: GeneratedStructure) {
        if (!enabled() || excluded(world)) return

        val bb = gs.boundingBox
        val origin = Triple(floor(bb.minX).toInt(), floor(bb.minY).toInt(), floor(bb.minZ).toInt())
        val key = key(world.name, origin)
        if (!seen.add(key)) return

        val pieces = gs.pieces.map { IntBox.fromBukkit(it.boundingBox) }
        val region = if (plugin.config.getBoolean("discovery.clamp-to-structure-y", true) && pieces.isNotEmpty())
            IntBox.union(pieces)
        else
            IntBox.fromBukkit(bb)

        val approved = !plugin.config.getBoolean("discovery.require-approval", true)

        plugin.launchAsync {
            if (plugin.cityManager.existsAt(world.name, origin)) return@launchAsync
            val city = plugin.cityManager.registerCity(world.name, region, origin, pieces, approved)
            if (city == null) {
                // Let a later chunk load try again, in case the database was only briefly away.
                if (!plugin.cityManager.existsAt(world.name, origin)) seen.remove(key)
                return@launchAsync
            }
            notifyDiscovery(city)
        }
    }

    private fun notifyDiscovery(city: City) {
        val c = city.region
        val state = if (city.approved) "active" else "waiting for approval"
        plugin.logger.info(
            "Found Ancient City #${city.id} in ${city.world} at ${c.minX}, ${c.minY}, ${c.minZ} " +
                "(${city.pieces.size} pieces, $state)"
        )
        val tag = if (city.approved) "<green>active" else "<yellow>pending"
        val action = if (city.approved)
            "<click:run_command:'/ancient open ${city.id}'><hover:show_text:'<gray>Open city <white>#${city.id}<gray> in the menu'><yellow>[menu]</yellow></hover></click>"
        else
            "<click:run_command:'/ancient approve ${city.id}'><hover:show_text:'<gray>Approve city <white>#${city.id}'><yellow>[approve]</yellow></hover></click>"
        val comp = MiniMessage.miniMessage().deserialize(
            "<light_purple>[BetterAncientCities] <gray>Discovered Ancient City <gray>#<white>${city.id} <gray>[$tag<gray>] <white>${city.world} " +
                "<click:run_command:'/ancient tp ${city.id}'><hover:show_text:'<gray>Teleport to city <white>#${city.id}'>" +
                "<green>[${c.minX} ${c.minY} ${c.minZ}]</green></hover></click> " +
                "<dark_gray>• ${city.pieces.size} pieces  $action"
        )
        plugin.scheduler.runTask(Runnable {
            plugin.server.onlinePlayers
                .filter { it.hasPermission("bac.discovery.notify") }
                .forEach { it.sendMessage(comp) }
        })
    }

    /** Checks chunks that were already loaded when the plugin started. */
    fun startupSweep() {
        if (!enabled() || !plugin.config.getBoolean("discovery.startup-sweep", true)) return
        for (world in plugin.server.worlds) {
            if (world.environment != World.Environment.NORMAL || excluded(world)) continue
            val chunks = try {
                world.loadedChunks
            } catch (e: Exception) {
                plugin.logger.warning("Could not list the loaded chunks of ${world.name}, so its cities are found as players explore instead.")
                continue
            }
            for (chunk in chunks) {
                val cx = chunk.x; val cz = chunk.z
                val loc = org.bukkit.Location(world, (cx shl 4).toDouble(), 0.0, (cz shl 4).toDouble())
                plugin.scheduler.runAtLocation(loc, Runnable {
                    if (!world.isChunkLoaded(cx, cz)) return@Runnable
                    for (gs in world.getStructures(cx, cz, Structure.ANCIENT_CITY)) handle(world, gs)
                })
            }
        }
    }
}
