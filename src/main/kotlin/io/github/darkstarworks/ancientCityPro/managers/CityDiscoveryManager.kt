package io.github.darkstarworks.ancientCityPro.managers

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.models.City
import io.github.darkstarworks.ancientCityPro.models.IntBox
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.World
import org.bukkit.generator.structure.GeneratedStructure
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a [GeneratedStructure] (an Ancient City the server already knows about)
 * into a registered [City]. Far simpler than fingerprint/flood-fill discovery:
 * the structure API hands us complete bounds + per-piece bounds from a single
 * chunk, so there's no palette scan, no multi-chunk AABB growth, no retry.
 *
 * Dedup: every loaded chunk of a city reports the same structure, so we key by
 * the structure bounding-box min corner (the city's [City.origin]) in an
 * in-memory seen-set — cheap rejection before any DB hit — backed by the DB
 * UNIQUE constraint for cross-restart and concurrent safety.
 */
class CityDiscoveryManager(private val plugin: AncientCityPro) {

    /** "world:ox:oy:oz" of cities already handled this session. */
    private val seen = ConcurrentHashMap.newKeySet<String>()

    private fun enabled() = plugin.config.getBoolean("discovery.enabled", true)

    /**
     * Considers one generated structure for registration. MUST be called on the
     * region thread owning the structure's chunk — it reads the structure's
     * bounding box and pieces synchronously here, then hands plain data to the
     * async DB path.
     */
    fun handle(world: World, gs: GeneratedStructure) {
        if (!enabled()) return

        val bb = gs.boundingBox
        val origin = Triple(
            Math.floor(bb.minX).toInt(),
            Math.floor(bb.minY).toInt(),
            Math.floor(bb.minZ).toInt(),
        )
        val key = "${world.name}:${origin.first}:${origin.second}:${origin.third}"
        if (!seen.add(key)) return // already handled this session

        val pieces = gs.pieces.map { IntBox.fromBukkit(it.boundingBox) }
        // Tighten the envelope to the actual pieces when configured (and we have
        // them); otherwise use the raw structure bounding box.
        val region = if (plugin.config.getBoolean("discovery.clamp-to-structure-y", true) && pieces.isNotEmpty())
            IntBox.union(pieces)
        else
            IntBox.fromBukkit(bb)

        val approved = !plugin.config.getBoolean("discovery.require-approval", true)

        plugin.launchAsync {
            if (plugin.cityManager.existsAt(world.name, origin)) return@launchAsync
            val city = plugin.cityManager.registerCity(world.name, region, origin, pieces, approved) ?: return@launchAsync
            notifyDiscovery(city)
        }
    }

    private fun notifyDiscovery(city: City) {
        val c = city.region
        val state = if (city.approved) "active" else "PENDING approval"
        plugin.logger.info(
            "Discovered Ancient City #${city.id} in ${city.world} " +
                "(${c.minX},${c.minY},${c.minZ})..(${c.maxX},${c.maxY},${c.maxZ}), ${city.pieces.size} pieces [$state]"
        )
        // Same formatting as a /acp list entry: clickable green coords (teleport)
        // + a yellow clickable action — [approve] while pending, [menu] once active.
        val tag = if (city.approved) "<green>active" else "<yellow>pending"
        val action = if (city.approved)
            "<click:run_command:'/acp open ${city.id}'><hover:show_text:'<gray>Open city <white>#${city.id}<gray> in the GUI'><yellow>[menu]</yellow></hover></click>"
        else
            "<click:run_command:'/acp approve ${city.id}'><hover:show_text:'<gray>Approve city <white>#${city.id}'><yellow>[approve]</yellow></hover></click>"
        val comp = MiniMessage.miniMessage().deserialize(
            "<light_purple>[AncientCityPro] <gray>Discovered Ancient City <gray>#<white>${city.id} <gray>[$tag<gray>] <white>${city.world} " +
                "<click:run_command:'/acp tp ${city.id}'><hover:show_text:'<gray>Teleport to city <white>#${city.id}'>" +
                "<green>[${c.minX} ${c.minY} ${c.minZ}]</green></hover></click> " +
                "<dark_gray>• ${city.pieces.size} pieces  $action"
        )
        plugin.scheduler.runTask(Runnable {
            plugin.server.onlinePlayers
                .filter { it.hasPermission("acp.discovery.notify") }
                .forEach { it.sendMessage(comp) }
        })
    }

    /**
     * One-time sweep over already-loaded chunks on enable, so cities resident at
     * startup are caught without waiting for a ChunkLoadEvent. Folia-safe: hops
     * to each chunk's region thread to read its structures.
     */
    fun startupSweep() {
        if (!enabled() || !plugin.config.getBoolean("discovery.startup-sweep", true)) return
        for (world in plugin.server.worlds) {
            if (world.environment != World.Environment.NORMAL) continue
            for (chunk in world.loadedChunks) {
                val loc = chunk.getBlock(0, world.minHeight, 0).location
                plugin.scheduler.runAtLocation(loc, Runnable {
                    for (gs in world.getStructures(chunk.x, chunk.z, org.bukkit.generator.structure.Structure.ANCIENT_CITY)) {
                        handle(world, gs)
                    }
                })
            }
        }
    }
}
