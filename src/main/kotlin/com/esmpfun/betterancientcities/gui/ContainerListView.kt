package com.esmpfun.betterancientcities.gui

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.gui.framework.BaseHolder
import com.esmpfun.betterancientcities.gui.framework.VcGui
import com.esmpfun.betterancientcities.managers.ContainerLootManager
import com.esmpfun.betterancientcities.models.City
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material

/** Every chest in a city that has been opened at least once, with the loot everyone finds in it. */
class ContainerListView(
    private val plugin: BetterAncientCities,
    private val menu: MenuService,
    private val city: City,
    private val templates: List<ContainerLootManager.TemplateRow>,
    private val page: Int = 0,
) : VcGui(6, Component.text("City #${city.id} chests", NamedTextColor.DARK_PURPLE), Holder(), "bac.admin") {

    class Holder : BaseHolder()

    private companion object { const val PER_PAGE = 45 }

    init { layout() }

    private fun layout() {
        clear()
        val pages = maxOf(1, (templates.size + PER_PAGE - 1) / PER_PAGE)
        val p = page.coerceIn(0, pages - 1)
        val slice = templates.drop(p * PER_PAGE).take(PER_PAGE)

        if (templates.isEmpty()) {
            set(22, guiItem(Material.BARRIER, "<gray>No chests opened yet",
                listOf("<gray>A chest shows up here once any", "<gray>player has opened it.")))
        }

        slice.forEachIndexed { i, t ->
            val count = t.contents.count { it != null && !it.type.isAir }
            set(i, guiItem(
                t.material,
                "<aqua>Chest at <white>${t.pos.x}, ${t.pos.y}, ${t.pos.z}",
                listOf("<gray>Filled slots: <white>$count", "", "<yellow>Click to see the loot")
            ) { ctx ->
                ContainerContentsView(
                    plugin,
                    Component.text("Loot at ${t.pos.x}, ${t.pos.y}, ${t.pos.z}", NamedTextColor.DARK_PURPLE),
                    t.contents,
                    back = { pl -> menu.openContainerList(pl, city) },
                ).open(ctx.player)
            })
        }

        if (p > 0) set(48, guiItem(Material.ARROW, "<white>« Previous page") { ctx ->
            ContainerListView(plugin, menu, city, templates, p - 1).open(ctx.player)
        })
        set(49, guiItem(Material.BOOK, "<gray>Page <white>${p + 1}<gray>/<white>$pages",
            listOf("<gray>${templates.size} chests")))
        if (p < pages - 1) set(50, guiItem(Material.ARROW, "<white>Next page »") { ctx ->
            ContainerListView(plugin, menu, city, templates, p + 1).open(ctx.player)
        })
        set(45, guiItem(Material.ARROW, "<white>« Back") { ctx -> menu.openCityDetail(ctx.player, city) })
        set(53, guiItem(Material.BARRIER, "<red>Close") { ctx -> ctx.player.closeInventory() })
    }
}
