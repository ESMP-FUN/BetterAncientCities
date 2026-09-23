package com.esmpfun.betterancientcities.gui

import com.esmpfun.betterancientcities.gui.framework.ClickContext
import com.esmpfun.betterancientcities.gui.framework.VcGuiItem
import org.bukkit.Material

/** [VcGuiItem.of] with the click handler last, so `guiItem(...) { ctx -> }` binds to it. */
internal fun guiItem(
    material: Material,
    name: String,
    lore: List<String> = emptyList(),
    onClick: ((ClickContext) -> Unit)? = null,
): VcGuiItem = VcGuiItem.of(material, name, lore, onClick = onClick)
