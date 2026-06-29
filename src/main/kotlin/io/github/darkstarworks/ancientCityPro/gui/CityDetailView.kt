package io.github.darkstarworks.ancientCityPro.gui

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.gui.framework.BaseHolder
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGui
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGuiItem
import io.github.darkstarworks.ancientCityPro.models.City
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material

/**
 * A single city's admin hub: teleport, force-refresh loot now, approve (if
 * pending), view player data, live occupancy, and delete.
 */
class CityDetailView(
    private val plugin: AncientCityPro,
    private val menu: MenuService,
    private val city: City,
) : VcGui(3, Component.text("§5City #${city.id}"), Holder(), "acp.admin") {

    class Holder : BaseHolder()

    init { layout() }

    private fun layout() {
        clear()
        val r = city.region
        val occupants = plugin.presenceListener.occupants(city.id).size

        // Info.
        set(0, guiItem(
            Material.LODESTONE,
            "<light_purple>Ancient City <white>#${city.id}",
            listOf(
                "<gray>World: <white>${city.world}",
                "<gray>Bounds: <white>(${r.minX},${r.minY},${r.minZ}) → (${r.maxX},${r.maxY},${r.maxZ})",
                "<gray>Pieces: <white>${city.pieces.size}",
                "<gray>Status: ${if (city.approved) "<green>active" else "<yellow>pending approval"}",
                "<gray>Players inside now: <white>$occupants",
            )
        ))

        // Teleport.
        set(11, guiItem(Material.ENDER_PEARL, "<aqua>Teleport here",
            listOf("<gray>Warp to the city centre.")) { ctx ->
            val world = city.getWorld()
            if (world == null) { ctx.player.sendMessage("§cWorld not loaded."); return@guiItem }
            val dest = Location(world, (r.minX + r.maxX) / 2.0 + 0.5, r.maxY + 1.0, (r.minZ + r.maxZ) / 2.0 + 0.5)
            ctx.player.closeInventory()
            plugin.scheduler.runAtEntity(ctx.player, Runnable { ctx.player.teleport(dest) })
        })

        // Approve (only while pending).
        if (!city.approved) {
            set(12, guiItem(Material.LIME_DYE, "<green>Approve city",
                listOf("<gray>Activate loot + protection for this city.")) { ctx ->
                plugin.launchAsync {
                    val ok = plugin.cityManager.approveCity(city.id)
                    plugin.scheduler.runAtEntity(ctx.player, Runnable {
                        ctx.player.sendMessage(if (ok) "§aApproved city #${city.id}." else "§cApproval failed.")
                        if (ok) plugin.cityManager.byId(city.id)?.let { menu.openCityDetail(ctx.player, it) }
                    })
                }
            })
        }

        // Player data.
        set(13, guiItem(Material.PLAYER_HEAD, "<yellow>Player data",
            listOf("<gray>Per-player loot, time, deaths,", "<gray>griefing attempts, bans.")) { ctx ->
            menu.openPlayerList(ctx.player, city)
        })

        // Force refresh loot now.
        set(15, guiItem(Material.CLOCK, "<gold>Refresh loot now",
            listOf("<gray>Clears every player's loot copies so the", "<gray>whole city is fresh on next open.")) { ctx ->
            plugin.launchAsync {
                val n = plugin.containerLootManager.clearCity(city.id)
                ctx.player.sendMessage("§aRefreshed city #${city.id} — cleared $n loot copy/copies.")
            }
        })

        // Delete.
        set(26, guiItem(Material.RED_CONCRETE, "<red>Delete city",
            listOf("<gray>Unregister this city and all its data.", "<red>Cannot be undone.")) { ctx ->
            plugin.launchAsync {
                val ok = plugin.cityManager.deleteCity(city.id)
                plugin.scheduler.runAtEntity(ctx.player, Runnable {
                    ctx.player.sendMessage(if (ok) "§aDeleted city #${city.id}." else "§cDelete failed.")
                    if (ok) menu.openCityList(ctx.player)
                })
            }
        })

        // Back.
        set(18, guiItem(Material.ARROW, "<white>◀ Back",
            listOf("<gray>Return to the city list.")) { ctx -> menu.openCityList(ctx.player) })
    }
}
