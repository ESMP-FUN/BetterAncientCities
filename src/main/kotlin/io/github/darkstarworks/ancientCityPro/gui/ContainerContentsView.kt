package io.github.darkstarworks.ancientCityPro.gui

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.gui.framework.BaseHolder
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGui
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGuiItem
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Read-only display of a container's contents (a template's canonical loot, or a
 * player's private copy). Items are shown but not interactive — the central
 * listener cancels clicks on wired slots. A back row returns to the caller.
 */
class ContainerContentsView(
    private val plugin: AncientCityPro,
    title: Component,
    private val contents: Array<ItemStack?>,
    private val back: (Player) -> Unit,
) : VcGui(rowsFor(contents.size), title, Holder()) {

    class Holder : BaseHolder()

    private companion object {
        /** Content rows (cap 5) + one nav row. */
        fun rowsFor(size: Int): Int = ((size + 8) / 9).coerceIn(1, 5) + 1
    }

    init { layout() }

    private fun layout() {
        clear()
        val navRow = rows - 1
        val capacity = navRow * 9
        for (i in 0 until minOf(contents.size, capacity)) {
            val stack = contents[i] ?: continue
            if (stack.type.isAir) continue
            set(i, VcGuiItem.wrap(stack.clone())) // display only; clicks cancelled centrally
        }
        set(navRow * 9, guiItem(Material.ARROW, "<white>◀ Back") { ctx -> back(ctx.player) })
        set(navRow * 9 + 8, guiItem(Material.BARRIER, "<red>Close") { ctx -> ctx.player.closeInventory() })
    }
}
