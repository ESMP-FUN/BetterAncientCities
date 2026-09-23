package com.esmpfun.betterancientcities.gui.framework

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent

/**
 * Routes clicks, drags and closes for every [VcGui].
 *
 * Anything that could move an item between the menu and the player's inventory
 * (shift-click, double-click collect, number-key swap on a menu slot) is always
 * cancelled. Clicks on menu slots are cancelled and handed to the slot's handler.
 * Ordinary clicks inside the player's own inventory go through untouched.
 */
class VcGuiListener : Listener {

    private val noPermission = Component.text("You no longer have permission to use this menu.", NamedTextColor.RED)

    @EventHandler(priority = EventPriority.HIGH)
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.getHolder(false) as? BaseHolder ?: return
        val gui = holder.gui
        val player = event.whoClicked as? Player ?: return

        if (gui.requiredPermission != null && !player.hasPermission(gui.requiredPermission)) {
            event.isCancelled = true
            player.closeInventory()
            player.sendMessage(noPermission)
            return
        }
        if (gui.freelyEditable) return

        when (event.action) {
            InventoryAction.MOVE_TO_OTHER_INVENTORY -> {
                val clickedBottom = event.clickedInventory != null && event.clickedInventory != event.inventory
                if (clickedBottom) {
                    routeBottomShiftClick(gui, player, event)
                    event.isCancelled = true
                    return
                }
                // A shift-click on a menu slot falls through so its handler can react.
            }
            // A double-click sweep pulls matching items out of both inventories,
            // so it is cancelled wherever it starts.
            InventoryAction.COLLECT_TO_CURSOR -> {
                event.isCancelled = true
                return
            }
            InventoryAction.HOTBAR_SWAP -> {
                if (event.clickedInventory == event.inventory) event.isCancelled = true
                return
            }
            else -> {}
        }

        if (event.clickedInventory == event.inventory) {
            event.isCancelled = true
            val slot = event.slot
            val item = gui.items().getOrNull(slot) ?: return
            val handler = item.onClick ?: return
            handler(ClickContext(
                player = player,
                click = event.click,
                action = event.action,
                slot = slot,
                isBottomInv = false,
                currentItem = event.currentItem?.clone(),
                cursor = event.cursor.clone(),
                event = event,
            ))
        }
    }

    private fun routeBottomShiftClick(gui: VcGui, player: Player, event: InventoryClickEvent) {
        val stamped = event.currentItem ?: return
        if (stamped.type.isAir) return
        val items = gui.items()
        val receiverSlot = items.indices.firstOrNull { items[it]?.acceptsBottomShiftClick == true } ?: return
        val handler = items[receiverSlot]?.onClick ?: return
        handler(ClickContext(
            player = player,
            click = event.click,
            action = event.action,
            slot = receiverSlot,
            isBottomInv = true,
            currentItem = stamped.clone(),
            cursor = event.cursor.clone(),
            event = event,
        ))
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDrag(event: InventoryDragEvent) {
        val holder = event.inventory.getHolder(false) as? BaseHolder ?: return
        val gui = holder.gui
        if (gui.freelyEditable) return
        val topSize = event.inventory.size
        val topSlotsTouched = event.rawSlots.filter { it < topSize }
        if (topSlotsTouched.isEmpty()) return

        if (topSlotsTouched.size == 1) {
            val targetSlot = topSlotsTouched.single()
            val deposited = event.newItems[targetSlot]
            val player = event.whoClicked as? Player
            if (gui.items().getOrNull(targetSlot)?.acceptsDrag == true && player != null && deposited != null && !deposited.type.isAir) {
                gui.handleDrag(DragContext(
                    player = player,
                    rawSlots = event.rawSlots,
                    newItems = event.newItems,
                    targetSlot = targetSlot,
                    depositedItem = deposited.clone(),
                    event = event,
                ))
                return
            }
        }
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.getHolder(false) as? BaseHolder ?: return
        val player = event.player as? Player ?: return
        holder.gui.handleClose(player)
    }
}
