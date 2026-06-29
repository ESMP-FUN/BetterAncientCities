package io.github.darkstarworks.ancientCityPro.listeners

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.managers.CityPalette
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent

/**
 * Palette-aware griefing protection for registered Ancient Cities.
 *
 * A block is protected iff it's inside a city's region envelope (expanded by
 * `protection.bounds-padding` to cover edge decoration the structure bbox
 * doesn't enclose) AND its type is in [CityPalette]. This protects the structure
 * — including outlying sculk/wool/bricks in the pad margin — while leaving the
 * natural deepslate, ores, and caves around it fully mineable.
 *
 * Players with `acp.bypass.protection` (default op) are exempt.
 */
class ProtectionListener(private val plugin: AncientCityPro) : Listener {

    private fun pad() = plugin.config.getInt("protection.bounds-padding", 8)
    private fun enabled() = plugin.config.getBoolean("protection.enabled", true)

    /** Whether [block] is a protected city-structure block (palette + padded region). */
    private fun isProtected(block: Block): Boolean {
        if (!CityPalette.contains(block.type)) return false
        return plugin.cityManager.getCachedCityInPaddedRegion(block.location, pad()) != null
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.player.hasPermission("acp.bypass.protection")) return
        if (isProtected(event.block)) {
            event.isCancelled = true
            notifyDenied(event.player)
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.player.hasPermission("acp.bypass.protection")) return
        if (!plugin.config.getBoolean("protection.block-place", true)) return
        // Only block placing INTO the structure footprint (a palette block placed,
        // or any block placed against the structure's interior); keep it simple —
        // deny placing palette-type blocks inside the padded region.
        if (CityPalette.contains(event.block.type) &&
            plugin.cityManager.getCachedCityInPaddedRegion(event.block.location, pad()) != null
        ) {
            event.isCancelled = true
            notifyDenied(event.player)
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (!enabled() || !plugin.isReady) return
        if (!plugin.config.getBoolean("protection.block-explosions", true)) return
        event.blockList().removeIf { isProtected(it) }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (!enabled() || !plugin.isReady) return
        if (!plugin.config.getBoolean("protection.block-explosions", true)) return
        event.blockList().removeIf { isProtected(it) }
    }

    private fun notifyDenied(player: Player) {
        if (!plugin.config.getBoolean("protection.notify-denied", true)) return
        player.sendActionBar(net.kyori.adventure.text.Component.text("§cThis Ancient City is protected."))
    }
}
