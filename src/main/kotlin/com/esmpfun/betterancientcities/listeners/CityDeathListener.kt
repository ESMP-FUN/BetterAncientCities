package com.esmpfun.betterancientcities.listeners

import com.esmpfun.betterancientcities.BetterAncientCities
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

/** Counts player deaths that occur inside an approved Ancient City. */
class CityDeathListener(private val plugin: BetterAncientCities) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        if (!plugin.isReady) return
        val player = event.entity
        val city = plugin.cityManager.getCachedCityAt(player.location) ?: return
        plugin.statsManager.incrementDeaths(city.id, player.uniqueId)
    }
}
