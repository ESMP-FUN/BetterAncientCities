package io.github.darkstarworks.ancientCityPro.gui

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.gui.framework.BaseHolder
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGui
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGuiItem
import net.kyori.adventure.text.Component
import org.bukkit.Material

/**
 * Top-level admin view: a paginated list of discovered Ancient Cities. Approved
 * cities show as a green icon, pending ones as amber. Click a city to open its
 * detail hub.
 */
class CityListView(
    private val plugin: AncientCityPro,
    private val menu: MenuService,
    private val page: Int = 0,
) : VcGui(6, Component.text("§5Ancient Cities"), Holder(), "acp.admin") {

    class Holder : BaseHolder()

    private companion object {
        const val PER_PAGE = 45 // slots 0..44; bottom row reserved for nav
    }

    init { layout() }

    private fun layout() {
        clear()
        val cities = plugin.cityManager.all().sortedBy { it.id }
        val pages = maxOf(1, (cities.size + PER_PAGE - 1) / PER_PAGE)
        val p = page.coerceIn(0, pages - 1)
        val slice = cities.drop(p * PER_PAGE).take(PER_PAGE)

        if (cities.isEmpty()) {
            set(22, guiItem(Material.BARRIER, "<red>No Ancient Cities discovered yet",
                listOf("<gray>Explore the Deep Dark — cities register", "<gray>automatically as their chunks load.")))
        }

        slice.forEachIndexed { i, city ->
            val mat = if (city.approved) Material.LIME_CONCRETE else Material.ORANGE_CONCRETE
            val status = if (city.approved) "<green>active" else "<yellow>pending approval"
            val r = city.region
            set(i, guiItem(
                mat,
                "<light_purple>Ancient City <white>#${city.id}",
                listOf(
                    "<gray>World: <white>${city.world}",
                    "<gray>Center: <white>${(r.minX + r.maxX) / 2}, ${(r.minY + r.maxY) / 2}, ${(r.minZ + r.maxZ) / 2}",
                    "<gray>Pieces: <white>${city.pieces.size}",
                    "<gray>Status: $status",
                    "",
                    "<yellow>Click to manage",
                )
            ) { ctx -> menu.openCityDetail(ctx.player, city) })
        }

        // Nav row.
        if (p > 0) set(48, guiItem(Material.ARROW, "<white>◀ Previous") { ctx ->
            CityListView(plugin, menu, p - 1).open(ctx.player)
        })
        set(49, guiItem(Material.BOOK, "<gray>Page <white>${p + 1}<gray>/<white>$pages",
            listOf("<gray>${cities.size} cities total")))
        if (p < pages - 1) set(50, guiItem(Material.ARROW, "<white>Next ▶") { ctx ->
            CityListView(plugin, menu, p + 1).open(ctx.player)
        })
        set(53, guiItem(Material.BARRIER, "<red>Close") { ctx -> ctx.player.closeInventory() })
    }
}
