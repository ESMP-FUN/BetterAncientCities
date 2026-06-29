package io.github.darkstarworks.ancientCityPro.gui

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.gui.framework.BaseHolder
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGui
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Read-only display of a container's contents. Items are shown but not
 * interactive (the central listener cancels clicks on wired slots).
 *
 * When [template] is supplied (a player-copy view), each slot is diffed against
 * the original loot so you can see at a glance what the player took, without
 * remembering the original contents:
 *  - **Removed** (had loot, now empty) → red pane; hover shows the original item.
 *  - **Modified** (fewer / a different item than the original) → the current item
 *    with a yellow "modified" tag and the original in its lore.
 *  - **Unchanged** → shown plainly.
 * With [template] null (a template/original view) items are just displayed.
 */
class ContainerContentsView(
    private val plugin: AncientCityPro,
    title: Component,
    private val contents: Array<ItemStack?>,
    private val template: Array<ItemStack?>? = null,
    private val back: (Player) -> Unit,
) : VcGui(rowsFor(maxOf(contents.size, template?.size ?: 0)), title, Holder()) {

    class Holder : BaseHolder()

    private companion object {
        val mm: MiniMessage = MiniMessage.miniMessage()
        fun rowsFor(size: Int): Int = ((size + 8) / 9).coerceIn(1, 5) + 1
    }

    init { layout() }

    private fun layout() {
        clear()
        val navRow = rows - 1
        val capacity = navRow * 9

        if (template != null) {
            // Diff legend in the nav row.
            set(navRow * 9 + 4, guiItem(Material.PAPER, "<gray>Diff vs. original loot",
                listOf("<red>Red <gray>= taken (removed)", "<yellow>Yellow <gray>= partly taken / changed", "<white>Plain <gray>= untouched")))
        }

        for (i in 0 until capacity) {
            val cur = contents.getOrNull(i)?.takeIf { !it.type.isAir }
            val tmpl = template?.getOrNull(i)?.takeIf { !it.type.isAir }

            val rendered: ItemStack? = when {
                template == null -> cur
                tmpl != null && cur == null -> removedMarker(tmpl)
                tmpl != null && cur != null && isModified(cur, tmpl) -> modifiedItem(cur, tmpl)
                else -> cur // unchanged, or player-added (cur with no tmpl)
            }
            if (rendered != null) set(i, VcGuiItem.wrap(rendered))
        }

        set(navRow * 9, guiItem(Material.ARROW, "<white>◀ Back") { ctx -> back(ctx.player) })
        set(navRow * 9 + 8, guiItem(Material.BARRIER, "<red>Close") { ctx -> ctx.player.closeInventory() })
    }

    private fun isModified(cur: ItemStack, tmpl: ItemStack): Boolean =
        cur.type != tmpl.type || cur.amount < tmpl.amount

    /** A red pane standing in for a slot whose loot the player removed entirely. */
    private fun removedMarker(original: ItemStack): ItemStack {
        val pane = ItemStack(Material.RED_STAINED_GLASS_PANE, 1)
        pane.editMeta { meta ->
            meta.displayName(mm.deserialize("<red>Taken (removed)").decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(
                mm.deserialize("<gray>Originally: <white>${original.amount}x ${itemName(original)}")
                    .decoration(TextDecoration.ITALIC, false),
            ))
        }
        return pane
    }

    /** The player's current item, tagged yellow with the original in its lore. */
    private fun modifiedItem(cur: ItemStack, tmpl: ItemStack): ItemStack {
        val out = cur.clone()
        out.editMeta { meta ->
            val baseName = if (meta.hasDisplayName()) meta.displayName()!! else Component.text(itemName(cur))
            meta.displayName(
                mm.deserialize("<yellow>⚠ ").decoration(TextDecoration.ITALIC, false).append(baseName)
            )
            val lore = (meta.lore()?.toMutableList() ?: mutableListOf())
            lore.add(mm.deserialize("<gray>Original: <white>${tmpl.amount}x ${itemName(tmpl)}").decoration(TextDecoration.ITALIC, false))
            val delta = if (cur.type == tmpl.type) "<yellow>${tmpl.amount - cur.amount} taken<gray>, ${cur.amount} left"
                else "<yellow>replaced<gray> (was a different item)"
            lore.add(mm.deserialize(delta).decoration(TextDecoration.ITALIC, false))
            meta.lore(lore)
        }
        return out
    }

    private fun itemName(item: ItemStack): String =
        item.type.name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
