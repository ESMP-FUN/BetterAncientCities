package com.esmpfun.betterancientcities.gui.framework

import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * The item in one menu slot and what a click on it does.
 *
 * @property acceptsBottomShiftClick a shift-click on any item in the player's own
 *   inventory is routed to this slot's [onClick]; the item stays where it is.
 * @property acceptsDrag a drag onto this slot fires [VcGui.handleDrag]; the cursor is kept.
 */
data class VcGuiItem(
    val stack: ItemStack,
    val onClick: ((ClickContext) -> Unit)? = null,
    val acceptsBottomShiftClick: Boolean = false,
    val acceptsDrag: Boolean = false,
) {
    companion object {
        private val mm: MiniMessage = MiniMessage.miniMessage()

        /** An item with a non-italic MiniMessage name and lore. */
        fun of(
            material: Material,
            name: String,
            lore: List<String> = emptyList(),
            onClick: ((ClickContext) -> Unit)? = null,
            acceptsBottomShiftClick: Boolean = false,
            acceptsDrag: Boolean = false,
        ): VcGuiItem {
            val stack = ItemStack(material, 1)
            stack.editMeta { meta ->
                meta.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false))
                if (lore.isNotEmpty()) {
                    meta.lore(lore.map { mm.deserialize(it).decoration(TextDecoration.ITALIC, false) })
                }
            }
            return VcGuiItem(stack, onClick, acceptsBottomShiftClick, acceptsDrag)
        }

        /** Wraps a ready-made stack. [onClick] is last so a trailing lambda binds to it. */
        fun wrap(
            stack: ItemStack,
            acceptsBottomShiftClick: Boolean = false,
            acceptsDrag: Boolean = false,
            onClick: ((ClickContext) -> Unit)? = null,
        ): VcGuiItem = VcGuiItem(stack, onClick, acceptsBottomShiftClick, acceptsDrag)
    }
}
