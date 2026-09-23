package com.esmpfun.betterancientcities.gui.framework

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

/**
 * Base class for a chest menu. Subclasses fill slots with [set] and call [open];
 * clicks go to each slot's [VcGuiItem.onClick] through [VcGuiListener].
 *
 * @property requiredPermission checked again on every click; null means none.
 */
abstract class VcGui(
    val rows: Int,
    val title: Component,
    val holder: BaseHolder,
    val requiredPermission: String? = null,
) {
    init {
        require(rows in 1..6) { "VcGui rows must be 1..6, got $rows" }
        holder.gui = this
    }

    private val slots = arrayOfNulls<VcGuiItem>(rows * 9)

    internal fun items(): Array<VcGuiItem?> = slots

    fun set(slot: Int, item: VcGuiItem?) {
        require(slot in 0 until rows * 9) { "slot $slot out of range 0..${rows * 9 - 1}" }
        slots[slot] = item
    }

    fun set(row: Int, col: Int, item: VcGuiItem?) = set(row * 9 + col, item)

    fun clear() {
        for (i in slots.indices) slots[i] = null
    }

    open fun render(inv: Inventory) {
        for (i in slots.indices) inv.setItem(i, slots[i]?.stack)
    }

    /** Redraws the open inventory after slots changed. */
    fun update() {
        render(holder.inventory)
    }

    fun open(player: Player) {
        val inv = Bukkit.createInventory(holder, rows * 9, title)
        holder.attach(inv)
        render(inv)
        player.openInventory(inv)
    }

    /** When true, [VcGuiListener] lets every click and drag through; read the result in [handleClose]. */
    open val freelyEditable: Boolean = false

    open fun handleClose(player: Player) {}

    /** Called for a drag onto a slot with `acceptsDrag`. Cancels by default. */
    open fun handleDrag(ctx: DragContext) {
        ctx.event.isCancelled = true
    }
}
