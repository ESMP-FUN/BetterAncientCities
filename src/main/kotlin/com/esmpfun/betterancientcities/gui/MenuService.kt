package com.esmpfun.betterancientcities.gui

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.managers.SnapshotManager
import com.esmpfun.betterancientcities.models.City
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicInteger

/**
 * Opens the menu screens. Screens that need the database load it in the
 * background first, then open on the player's own thread.
 */
class MenuService(private val plugin: BetterAncientCities) {

    private val mm = MiniMessage.miniMessage()

    fun openCityList(player: Player) {
        CityListView(plugin, this).open(player)
    }

    fun openCityDetail(player: Player, city: City) {
        CityDetailView(plugin, this, city).open(player)
    }

    /**
     * Approves a pending city and saves its first snapshot. Shared by the chat
     * [approve] link, `/ancient approve` and the menu button; a second click while
     * it runs is ignored.
     */
    fun beginApproval(sender: CommandSender, city: City, reopenDetail: Boolean) {
        if (city.approved) { sender.sendMessage(mm.deserialize("<gray>City #${city.id} is already active.")); return }
        if (!plugin.cityManager.tryBeginApproval(city.id)) {
            sender.sendMessage(mm.deserialize("<gray>City #${city.id} is already being approved. One moment."))
            return
        }
        sender.sendMessage(mm.deserialize("<yellow>Approving city #${city.id} and saving its snapshot. This can take a few seconds."))
        val player = sender as? Player
        val busy = player?.let { startBusyActionBar(it, "Approving city #${city.id}") }

        plugin.launchAsync {
            var ok = false
            var cells = -1
            try {
                ok = plugin.cityManager.approveCity(city.id)
                if (ok && plugin.config.getBoolean("snapshot.auto-capture-on-approve", true)) {
                    plugin.cityManager.byId(city.id)?.let { cells = plugin.snapshotManager.capture(it) }
                }
            } finally {
                busy?.cancel()
                plugin.cityManager.endApproval(city.id)
            }
            if (player != null) plugin.scheduler.runAtEntity(player, Runnable {
                player.sendActionBar(Component.empty())
                if (ok && reopenDetail && player.isOnline) {
                    plugin.cityManager.byId(city.id)?.let { openCityDetail(player, it) }
                }
            })
            sender.sendMessage(mm.deserialize(when {
                !ok -> "<red>City #${city.id} could not be approved. The server console says why."
                cells >= 0 -> "<green>City #${city.id} is now active. <gray>(snapshot saved, $cells blocks)"
                cells == SnapshotManager.BUSY -> "<green>City #${city.id} is now active. <gray>(a snapshot was already being saved)"
                plugin.config.getBoolean("snapshot.auto-capture-on-approve", true) ->
                    "<green>City #${city.id} is now active. <yellow>Its snapshot could not be saved, see the console."
                else -> "<green>City #${city.id} is now active."
            }))
        }
    }

    private fun startBusyActionBar(player: Player, label: String) =
        plugin.scheduler.runTaskTimer(object : Runnable {
            private val frames = listOf("", " .", " ..", " ...")
            private val i = AtomicInteger(0)
            override fun run() {
                if (player.isOnline) {
                    player.sendActionBar(mm.deserialize("<yellow>$label<gray>${frames[i.getAndIncrement() % frames.size]}"))
                }
            }
        }, 0L, 8L)

    fun openPlayerList(player: Player, city: City) {
        plugin.launchAsync {
            val stats = plugin.statsManager.listForCity(city.id)
            val bannedFlags = stats.associate { it.playerUuid to plugin.banManager.isBanned(city.id, it.playerUuid) }
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) PlayerListView(plugin, this@MenuService, city, stats, bannedFlags).open(player)
            })
        }
    }

    fun openContainerList(player: Player, city: City) {
        plugin.launchAsync {
            val templates = plugin.containerLootManager.listTemplates(city.id)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) ContainerListView(plugin, this@MenuService, city, templates).open(player)
            })
        }
    }

    /** One player's chests in a city, next to the original loot so the screen can compare them. */
    fun openPlayerContainers(player: Player, city: City, target: java.util.UUID) {
        plugin.launchAsync {
            val copies = plugin.containerLootManager.listPlayerCopies(city.id, target)
            val templates = plugin.containerLootManager.listTemplates(city.id).associate { it.pos to it.contents }
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) PlayerContainersView(plugin, this@MenuService, city, target, copies, templates).open(player)
            })
        }
    }
}
