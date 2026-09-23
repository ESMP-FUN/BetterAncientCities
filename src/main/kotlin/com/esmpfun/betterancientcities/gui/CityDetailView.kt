package com.esmpfun.betterancientcities.gui

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.gui.framework.BaseHolder
import com.esmpfun.betterancientcities.gui.framework.VcGui
import com.esmpfun.betterancientcities.managers.SnapshotManager
import com.esmpfun.betterancientcities.models.City
import com.esmpfun.betterancientcities.utils.CityTeleport
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player

/** One city's screen: teleport, approve, player data, chests, loot refresh, snapshots, delete. */
class CityDetailView(
    private val plugin: BetterAncientCities,
    private val menu: MenuService,
    private val city: City,
) : VcGui(3, Component.text("City #${city.id}", NamedTextColor.DARK_PURPLE), Holder(), "bac.admin") {

    class Holder : BaseHolder()

    private val mm = MiniMessage.miniMessage()

    init { layout() }

    private fun say(player: Player, text: String) = player.sendMessage(mm.deserialize(text))

    private fun layout() {
        clear()
        val r = city.region
        val occupants = plugin.presenceListener.occupants(city.id).size
        val hasSnapshot = plugin.snapshotManager.hasSnapshot(city.id)

        set(0, guiItem(
            Material.LODESTONE,
            "<light_purple>Ancient City <white>#${city.id}",
            listOf(
                "<gray>World: <white>${city.world}",
                "<gray>From <white>${r.minX}, ${r.minY}, ${r.minZ}",
                "<gray>to <white>${r.maxX}, ${r.maxY}, ${r.maxZ}",
                "<gray>Buildings: <white>${city.pieces.size}",
                "<gray>Status: ${if (city.approved) "<green>active" else "<yellow>waiting for approval"}",
                "<gray>Players inside now: <white>$occupants",
                "<gray>Snapshot: ${if (hasSnapshot) "<green>saved" else "<red>none"}",
            )
        ))

        set(11, guiItem(Material.ENDER_PEARL, "<aqua>Teleport here",
            listOf("<gray>Takes you to a safe spot", "<gray>in the middle of the city.")) { ctx ->
            ctx.player.closeInventory()
            CityTeleport.teleport(plugin, ctx.player, city)
        })

        if (!city.approved) {
            set(12, guiItem(Material.LIME_DYE, "<green>Approve city",
                listOf("<gray>Turns on per-player loot and", "<gray>protection for this city.")) { ctx ->
                ctx.player.closeInventory()
                menu.beginApproval(ctx.player, city, reopenDetail = true)
            })
        }

        set(13, guiItem(Material.PLAYER_HEAD, "<yellow>Player data",
            listOf("<gray>What each player looted, how long", "<gray>they spent here, deaths, loot bans.")) { ctx ->
            menu.openPlayerList(ctx.player, city)
        })

        set(14, guiItem(Material.CHEST, "<yellow>Chests",
            listOf("<gray>See the loot in each of", "<gray>this city's chests.")) { ctx ->
            menu.openContainerList(ctx.player, city)
        })

        set(15, guiItem(Material.CLOCK, "<gold>Refresh loot now",
            listOf("<gray>Every player finds full chests", "<gray>again the next time they open one.")) { ctx ->
            val player = ctx.player
            plugin.launchAsync {
                val n = plugin.containerLootManager.clearCity(city.id)
                say(player, "<green>Loot in city #${city.id} refreshed. <gray>($n saved ${if (n == 1) "chest" else "chests"} cleared)")
            }
        })

        set(16, guiItem(Material.SPYGLASS, "<aqua>Save snapshot",
            listOf("<gray>Saves the city's blocks as they are", "<gray>now, replacing any older snapshot.")) { ctx ->
            val player = ctx.player
            say(player, "<gray>Saving the blocks of city #${city.id}. This can take a few seconds.")
            plugin.launchAsync {
                val n = plugin.snapshotManager.capture(city)
                plugin.scheduler.runAtEntity(player, Runnable {
                    say(player, when {
                        n == SnapshotManager.BUSY -> "<yellow>City #${city.id} is already being saved or restored."
                        n >= 0 -> "<green>City #${city.id} saved ($n blocks)."
                        else -> "<red>City #${city.id} could not be saved. The server console says why."
                    })
                    if (player.openInventory.topInventory.holder is Holder) {
                        plugin.cityManager.byId(city.id)?.let { menu.openCityDetail(player, it) }
                    }
                })
            }
        })

        if (hasSnapshot) {
            set(17, guiItem(Material.RECOVERY_COMPASS, "<gold>Restore from snapshot",
                listOf("<gray>Puts the city's blocks back as saved,", "<gray>undoing griefing and sculk spread.",
                    "<red>Players standing in changed spots", "<red>can end up inside blocks.")) { ctx ->
                val player = ctx.player
                say(player, "<gray>Restoring city #${city.id}. This can take a few seconds.")
                plugin.launchAsync {
                    val n = plugin.snapshotManager.restore(city)
                    say(player, when {
                        n == SnapshotManager.BUSY -> "<yellow>City #${city.id} is already being saved or restored."
                        n >= 0 -> "<green>City #${city.id} restored ($n blocks)."
                        else -> "<red>City #${city.id} could not be restored. The server console says why."
                    })
                }
            })
        }

        set(26, guiItem(Material.RED_CONCRETE, "<red>Delete city",
            listOf("<gray>Forgets this city with its saved chests,", "<gray>stats, loot bans and snapshot.",
                "", "<red>Shift-click to delete. Cannot be undone.")) { ctx ->
            if (!ctx.click.isShiftClick) {
                say(ctx.player, "<yellow>Shift-click the button to delete city #${city.id}.")
                return@guiItem
            }
            val player = ctx.player
            plugin.launchAsync {
                val ok = plugin.cityManager.deleteCity(city.id)
                plugin.scheduler.runAtEntity(player, Runnable {
                    say(player, if (ok) "<green>City #${city.id} deleted." else "<red>City #${city.id} could not be deleted. The server console says why.")
                    if (ok) menu.openCityList(player)
                })
            }
        })

        set(18, guiItem(Material.ARROW, "<white>« Back",
            listOf("<gray>Back to the list of cities.")) { ctx -> menu.openCityList(ctx.player) })
    }
}
