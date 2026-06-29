package io.github.darkstarworks.ancientCityPro.gui

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.models.City
import org.bukkit.entity.Player

/**
 * Central opener for AncientCityPro's admin GUI. Views are built fresh on each
 * open; data that requires the DB (stats, bans) is fetched on the IO dispatcher
 * and the view is opened back on the player's region thread (Folia-safe) — never
 * `runBlocking` on the region thread.
 */
class MenuService(private val plugin: AncientCityPro) {

    /** Top level: the list of discovered cities (approved + pending). In-memory cache, no DB. */
    fun openCityList(player: Player) {
        CityListView(plugin, this).open(player)
    }

    /** A single city's admin hub. */
    fun openCityDetail(player: Player, city: City) {
        CityDetailView(plugin, this, city).open(player)
    }

    /**
     * The per-city player grid (heads + stat tooltips + quick actions). Stats are
     * read async, then the view opens on the player's region thread.
     */
    fun openPlayerList(player: Player, city: City) {
        plugin.launchAsync {
            val stats = plugin.statsManager.listForCity(city.id)
            val bannedFlags = stats.associate { it.playerUuid to plugin.banManager.isBanned(city.id, it.playerUuid) }
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) PlayerListView(plugin, this@MenuService, city, stats, bannedFlags).open(player)
            })
        }
    }
}
