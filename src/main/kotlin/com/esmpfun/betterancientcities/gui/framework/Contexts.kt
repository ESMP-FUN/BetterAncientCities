package com.esmpfun.betterancientcities.gui.framework

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack

/**
 * A click handed to [VcGuiItem.onClick]. The items are clones, safe to keep.
 * [isBottomInv] is true for a shift-click in the player's own inventory routed to
 * an item with `acceptsBottomShiftClick`.
 */
data class ClickContext(
    val player: Player,
    val click: ClickType,
    val action: InventoryAction,
    val slot: Int,
    val isBottomInv: Boolean,
    val currentItem: ItemStack?,
    val cursor: ItemStack?,
    val event: InventoryClickEvent,
)

/** A drag onto a single top slot whose item has `acceptsDrag`, handed to [VcGui.handleDrag]. */
data class DragContext(
    val player: Player,
    val rawSlots: Set<Int>,
    val newItems: Map<Int, ItemStack>,
    val targetSlot: Int,
    val depositedItem: ItemStack,
    val event: InventoryDragEvent,
)
