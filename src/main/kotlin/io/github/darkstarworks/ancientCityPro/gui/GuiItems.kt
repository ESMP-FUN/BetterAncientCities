package io.github.darkstarworks.ancientCityPro.gui

import io.github.darkstarworks.ancientCityPro.gui.framework.ClickContext
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGuiItem
import org.bukkit.Material

/**
 * Thin wrapper over [VcGuiItem.of] with [onClick] as the LAST parameter, so
 * Kotlin trailing-lambda syntax binds to the click handler:
 * `guiItem(MAT, "<name>", lore) { ctx -> ... }`. (On `VcGuiItem.of` itself the
 * last params are the drag flags, so a trailing lambda would mis-bind.)
 * Omit the lambda for a non-interactive display item.
 */
internal fun guiItem(
    material: Material,
    name: String,
    lore: List<String> = emptyList(),
    onClick: ((ClickContext) -> Unit)? = null,
): VcGuiItem = VcGuiItem.of(material, name, lore, onClick = onClick)
