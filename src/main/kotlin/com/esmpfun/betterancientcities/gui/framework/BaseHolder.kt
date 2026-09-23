package com.esmpfun.betterancientcities.gui.framework

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/** Ties an open inventory back to the [VcGui] that made it, so [VcGuiListener] can find it. */
abstract class BaseHolder : InventoryHolder {
    private lateinit var inv: Inventory

    lateinit var gui: VcGui
        internal set

    fun attach(inventory: Inventory) {
        inv = inventory
    }

    override fun getInventory(): Inventory = inv
}
