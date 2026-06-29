package io.github.darkstarworks.ancientCityPro.gui

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.gui.framework.BaseHolder
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGui
import io.github.darkstarworks.ancientCityPro.managers.ContainerLootManager
import io.github.darkstarworks.ancientCityPro.models.City
import net.kyori.adventure.text.Component
import org.bukkit.Material

/**
 * Per-city container browser: every materialized container template, shown as its
 * own block icon with a loot summary. Click one to inspect its canonical loot.
 * Templates are prefetched by [MenuService.openContainerList].
 */
class ContainerListView(
    private val plugin: AncientCityPro,
    private val menu: MenuService,
    private val city: City,
    private val templates: List<ContainerLootManager.TemplateRow>,
    private val page: Int = 0,
) : VcGui(6, Component.text("§5City #${city.id} — Containers"), Holder(), "acp.admin") {

    class Holder : BaseHolder()

    private companion object { const val PER_PAGE = 45 }

    init { layout() }

    private fun layout() {
        clear()
        val pages = maxOf(1, (templates.size + PER_PAGE - 1) / PER_PAGE)
        val p = page.coerceIn(0, pages - 1)
        val slice = templates.drop(p * PER_PAGE).take(PER_PAGE)

        if (templates.isEmpty()) {
            set(22, guiItem(Material.BARRIER, "<gray>No container templates yet",
                listOf("<gray>A container's template is created the first", "<gray>time any player opens it in this city.")))
        }

        slice.forEachIndexed { i, t ->
            val count = t.contents.count { it != null && !it.type.isAir }
            set(i, guiItem(
                t.material,
                "<aqua>Container <white>${t.pos.x}, ${t.pos.y}, ${t.pos.z}",
                listOf("<gray>Loot items: <white>$count", "", "<yellow>Click to view loot")
            ) { ctx ->
                ContainerContentsView(
                    plugin,
                    Component.text("§5Loot @ ${t.pos.x},${t.pos.y},${t.pos.z}"),
                    t.contents,
                    back = { pl -> menu.openContainerList(pl, city) },
                ).open(ctx.player)
            })
        }

        if (p > 0) set(48, guiItem(Material.ARROW, "<white>◀ Previous") { ctx ->
            ContainerListView(plugin, menu, city, templates, p - 1).open(ctx.player)
        })
        set(49, guiItem(Material.BOOK, "<gray>Page <white>${p + 1}<gray>/<white>$pages",
            listOf("<gray>${templates.size} containers")))
        if (p < pages - 1) set(50, guiItem(Material.ARROW, "<white>Next ▶") { ctx ->
            ContainerListView(plugin, menu, city, templates, p + 1).open(ctx.player)
        })
        set(45, guiItem(Material.ARROW, "<white>◀ Back") { ctx -> menu.openCityDetail(ctx.player, city) })
        set(53, guiItem(Material.BARRIER, "<red>Close") { ctx -> ctx.player.closeInventory() })
    }
}
