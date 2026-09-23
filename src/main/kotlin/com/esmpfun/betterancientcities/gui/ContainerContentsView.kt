package com.esmpfun.betterancientcities.gui

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.gui.framework.BaseHolder
import com.esmpfun.betterancientcities.gui.framework.VcGui
import com.esmpfun.betterancientcities.gui.framework.VcGuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Shows a chest's contents without letting anything be taken. With a [template]
 * (a player's copy) each slot is compared to the original loot: taken items become
 * a red pane, partly taken or swapped items glow with the original in their lore.
 */
class ContainerContentsView(
    private val plugin: BetterAncientCities,
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
            set(navRow * 9 + 4, guiItem(Material.PAPER, "<gray>Compared with the original loot",
                listOf("<red>Red pane<gray>: taken", "<aqua>Glowing<gray>: partly taken or swapped", "<white>Plain<gray>: not touched")))
        }

        for (i in 0 until capacity) {
            val cur = contents.getOrNull(i)?.takeIf { !it.type.isAir }
            val tmpl = template?.getOrNull(i)?.takeIf { !it.type.isAir }

            val rendered: ItemStack? = when {
                template == null -> cur
                tmpl != null && cur == null -> removedMarker(tmpl)
                tmpl != null && cur != null && isModified(cur, tmpl) -> modifiedItem(cur, tmpl)
                else -> cur
            }
            if (rendered != null) set(i, VcGuiItem.wrap(rendered))
        }

        set(navRow * 9, guiItem(Material.ARROW, "<white>« Back") { ctx -> back(ctx.player) })
        set(navRow * 9 + 8, guiItem(Material.BARRIER, "<red>Close") { ctx -> ctx.player.closeInventory() })
    }

    private fun isModified(cur: ItemStack, tmpl: ItemStack): Boolean =
        cur.type != tmpl.type || cur.amount < tmpl.amount

    /** A red pane in place of loot the player took completely. */
    private fun removedMarker(original: ItemStack): ItemStack {
        val pane = ItemStack(Material.RED_STAINED_GLASS_PANE, 1)
        pane.editMeta { meta ->
            meta.displayName(mm.deserialize("<red>Taken").decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(
                mm.deserialize("<gray>Originally: <white>${original.amount}x ${itemName(original)}")
                    .decoration(TextDecoration.ITALIC, false),
            ))
        }
        return pane
    }

    /** The player's item, glowing, with the original in its lore. */
    private fun modifiedItem(cur: ItemStack, tmpl: ItemStack): ItemStack {
        val out = cur.clone()
        out.editMeta { meta ->
            meta.setEnchantmentGlintOverride(true)
            val baseName = if (meta.hasDisplayName()) meta.displayName()!! else Component.text(itemName(cur))
            meta.displayName(
                mm.deserialize("<yellow>Changed: ").decoration(TextDecoration.ITALIC, false).append(baseName)
            )
            val lore = (meta.lore()?.toMutableList() ?: mutableListOf())
            lore.add(mm.deserialize("<gray>Original: <white>${tmpl.amount}x ${itemName(tmpl)}").decoration(TextDecoration.ITALIC, false))
            val delta = if (cur.type == tmpl.type) "<yellow>${tmpl.amount - cur.amount} taken<gray>, ${cur.amount} left"
                else "<yellow>Swapped<gray> for a different item"
            lore.add(mm.deserialize(delta).decoration(TextDecoration.ITALIC, false))
            meta.lore(lore)
        }
        return out
    }

    private fun itemName(item: ItemStack): String =
        item.type.name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
