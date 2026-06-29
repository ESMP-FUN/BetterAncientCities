package io.github.darkstarworks.ancientCityPro.gui

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.gui.framework.BaseHolder
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGui
import io.github.darkstarworks.ancientCityPro.managers.ContainerLootManager
import io.github.darkstarworks.ancientCityPro.models.City
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import java.util.UUID

/**
 * The containers a specific player has a private copy of in a city — i.e. what
 * they've looted and what they left behind. Click a container to view that
 * player's exact copy. Copies are prefetched by [MenuService.openPlayerContainers].
 */
class PlayerContainersView(
    private val plugin: AncientCityPro,
    private val menu: MenuService,
    private val city: City,
    private val target: UUID,
    private val copies: List<ContainerLootManager.PlayerCopy>,
    private val templates: Map<ContainerLootManager.ContainerPos, Array<org.bukkit.inventory.ItemStack?>>,
    private val page: Int = 0,
) : VcGui(6, Component.text("§5City #${city.id} — Player Loot"), Holder(), "acp.admin") {

    class Holder : BaseHolder()

    private companion object { const val PER_PAGE = 45 }

    private val targetName = Bukkit.getOfflinePlayer(target).name ?: target.toString().take(8)

    init { layout() }

    private fun layout() {
        clear()
        val pages = maxOf(1, (copies.size + PER_PAGE - 1) / PER_PAGE)
        val p = page.coerceIn(0, pages - 1)
        val slice = copies.drop(p * PER_PAGE).take(PER_PAGE)

        if (copies.isEmpty()) {
            set(22, guiItem(Material.BARRIER, "<gray>$targetName has not looted any containers here",
                listOf("<gray>A copy is created the first time they", "<gray>open a container in this city.")))
        }

        slice.forEachIndexed { i, c ->
            val count = c.contents.count { it != null && !it.type.isAir }
            set(i, guiItem(
                Material.CHEST,
                "<aqua>Container <white>${c.pos.x}, ${c.pos.y}, ${c.pos.z}",
                listOf("<gray>Items remaining: <white>$count", "", "<yellow>Click to view their copy")
            ) { ctx ->
                ContainerContentsView(
                    plugin,
                    Component.text("§5$targetName @ ${c.pos.x},${c.pos.y},${c.pos.z}"),
                    c.contents,
                    template = templates[c.pos],
                    back = { pl -> menu.openPlayerContainers(pl, city, target) },
                ).open(ctx.player)
            })
        }

        if (p > 0) set(48, guiItem(Material.ARROW, "<white>◀ Previous") { ctx ->
            PlayerContainersView(plugin, menu, city, target, copies, templates, p - 1).open(ctx.player)
        })
        set(49, guiItem(Material.PLAYER_HEAD, "<gray>$targetName",
            listOf("<gray>${copies.size} looted containers", "<gray>Page <white>${p + 1}<gray>/<white>$pages")))
        if (p < pages - 1) set(50, guiItem(Material.ARROW, "<white>Next ▶") { ctx ->
            PlayerContainersView(plugin, menu, city, target, copies, templates, p + 1).open(ctx.player)
        })
        set(45, guiItem(Material.ARROW, "<white>◀ Back") { ctx -> menu.openPlayerList(ctx.player, city) })
        set(53, guiItem(Material.BARRIER, "<red>Close") { ctx -> ctx.player.closeInventory() })
    }
}
