package com.esmpfun.betterancientcities.listeners

import com.esmpfun.betterancientcities.BetterAncientCities
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which approved city each player is in, for time-in-city stats and the
 * "players inside now" count. Time is held in memory and written when the player
 * leaves, switches city or logs out.
 */
class CityPresenceListener(private val plugin: BetterAncientCities) : Listener {

    private data class Presence(val cityId: Int, val since: Long)

    private val inside = ConcurrentHashMap<UUID, Presence>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (!plugin.isReady) return
        val from = event.from
        val to = event.to
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ && from.world == to.world) return
        update(event.player, to)
    }

    // PlayerTeleportEvent has its own handler list, so onMove never sees teleports.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        if (!plugin.isReady) return
        update(event.player, event.to)
    }

    private fun update(player: Player, to: Location) {
        val uuid = player.uniqueId
        val currentCity = plugin.cityManager.getCachedCityAt(to)?.id
        val prev = inside[uuid]
        if (prev?.cityId == currentCity) return

        val now = System.currentTimeMillis()
        if (prev != null) plugin.statsManager.addTime(prev.cityId, uuid, now - prev.since)
        if (currentCity != null) inside[uuid] = Presence(currentCity, now) else inside.remove(uuid)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val p = inside.remove(event.player.uniqueId) ?: return
        plugin.statsManager.addTime(p.cityId, event.player.uniqueId, System.currentTimeMillis() - p.since)
    }

    /** Writes everyone's time and waits. For plugin shutdown. */
    fun flushAllBlocking() {
        val now = System.currentTimeMillis()
        for (uuid in inside.keys.toList()) {
            val p = inside.remove(uuid) ?: continue
            plugin.statsManager.addTimeBlocking(p.cityId, uuid, now - p.since)
        }
    }

    fun occupants(cityId: Int): List<UUID> =
        inside.entries.filter { it.value.cityId == cityId }.map { it.key }
}
