package io.github.darkstarworks.ancientCityPro.gui

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.models.City
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicInteger

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
     * Approve a pending city with immediate feedback. Shared by the chat
     * `[approve]` link / `/acp approve` and the GUI button.
     *
     * Guards against duplicate runs (the slow baseline-snapshot capture meant a
     * spammed [approve] link fired many times), sends an instant chat line, and —
     * for an in-game player — animates a busy indicator on the action bar while
     * the snapshot is captured, finishing with a result line.
     */
    fun beginApproval(sender: CommandSender, city: City, reopenDetail: Boolean) {
        if (city.approved) { sender.sendMessage("§7City #${city.id} is already active."); return }
        if (!plugin.cityManager.tryBeginApproval(city.id)) {
            sender.sendMessage("§7Already approving city #${city.id}… one moment.")
            return
        }
        sender.sendMessage("§e⏳ Approving city #${city.id} — capturing baseline snapshot, this can take a few seconds…")
        val player = sender as? Player
        val busy = player?.let { startBusyActionBar(it, "Approving city #${city.id}") }

        plugin.launchAsync {
            try {
                val ok = plugin.cityManager.approveCity(city.id)
                var cells = -1
                if (ok && plugin.config.getBoolean("snapshot.auto-capture-on-approve", true)) {
                    plugin.cityManager.byId(city.id)?.let { cells = plugin.snapshotManager.capture(it) }
                }
                busy?.cancel()
                if (player != null) plugin.scheduler.runAtEntity(player, Runnable {
                    player.sendActionBar(Component.empty())
                    if (ok && reopenDetail && player.isOnline) {
                        plugin.cityManager.byId(city.id)?.let { openCityDetail(player, it) }
                    }
                })
                sender.sendMessage(
                    if (ok) "§a✓ Approved city #${city.id}${if (cells >= 0) " §7(baseline snapshot: $cells cells)" else ""}."
                    else "§cApproval failed."
                )
            } finally {
                plugin.cityManager.endApproval(city.id)
            }
        }
    }

    /** Cycling-dots busy indicator on the player's action bar; cancel the returned task to stop. */
    private fun startBusyActionBar(player: Player, label: String) =
        plugin.scheduler.runTaskTimer(object : Runnable {
            private val frames = listOf("", " .", " ..", " ...")
            private val i = AtomicInteger(0)
            override fun run() {
                if (player.isOnline) {
                    val dots = frames[i.getAndIncrement() % frames.size]
                    player.sendActionBar(MiniMessage.miniMessage().deserialize("<yellow>$label<gray>$dots"))
                }
            }
        }, 0L, 8L)

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

    /** Per-city container browser (templates fetched async). */
    fun openContainerList(player: Player, city: City) {
        plugin.launchAsync {
            val templates = plugin.containerLootManager.listTemplates(city.id)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) ContainerListView(plugin, this@MenuService, city, templates).open(player)
            })
        }
    }

    /** A specific player's looted-container copies in a city (fetched async),
     *  paired with the original templates so the contents view can diff them. */
    fun openPlayerContainers(player: Player, city: City, target: java.util.UUID) {
        plugin.launchAsync {
            val copies = plugin.containerLootManager.listPlayerCopies(city.id, target)
            val templates = plugin.containerLootManager.listTemplates(city.id)
                .associate { it.pos to it.contents }
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) PlayerContainersView(plugin, this@MenuService, city, target, copies, templates).open(player)
            })
        }
    }
}
