package io.github.darkstarworks.ancientCityPro.listeners

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which approved city each player is currently inside, accumulating
 * time-in-city stats and powering live "who's here now" occupancy for the GUI.
 *
 * Time is accrued lazily: an entry timestamp is held in memory while a player is
 * inside a city, and flushed to [io.github.darkstarworks.ancientCityPro.managers.StatsManager]
 * when they leave it, switch cities, or disconnect — so there's no per-tick DB
 * traffic. Movement handling is gated on actual block changes.
 */
class CityPresenceListener(private val plugin: AncientCityPro) : Listener {

    private data class Presence(val cityId: Int, val since: Long)

    /** player -> the city they're currently inside (+ when they entered). */
    private val inside = ConcurrentHashMap<UUID, Presence>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (!plugin.isReady) return
        val from = event.from
        val to = event.to
        // Only react to block changes, not head movement.
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return

        val player = event.player
        val uuid = player.uniqueId
        val currentCity = plugin.cityManager.getCachedCityAt(to)?.id
        val prev = inside[uuid]

        if (prev?.cityId == currentCity) return // no city transition

        // Left the previous city (or switched) — flush the accrued time.
        if (prev != null) {
            plugin.statsManager.addTime(prev.cityId, uuid, System.currentTimeMillis() - prev.since)
        }
        if (currentCity != null) {
            inside[uuid] = Presence(currentCity, System.currentTimeMillis())
        } else {
            inside.remove(uuid)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        flush(event.player.uniqueId)
    }

    /** Flushes one player's in-flight time and clears their presence. */
    private fun flush(uuid: UUID) {
        val p = inside.remove(uuid) ?: return
        plugin.statsManager.addTime(p.cityId, uuid, System.currentTimeMillis() - p.since)
    }

    /** Flushes everyone (plugin disable). */
    fun flushAll() {
        for (uuid in inside.keys.toList()) flush(uuid)
    }

    /** UUIDs of players currently inside [cityId] — for the GUI occupancy view. */
    fun occupants(cityId: Int): List<UUID> =
        inside.entries.filter { it.value.cityId == cityId }.map { it.key }
}
