package io.github.darkstarworks.ancientCityPro.listeners

import io.github.darkstarworks.ancientCityPro.AncientCityPro
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
 * Griefing protection for registered Ancient Cities — bounds-based per structure
 * piece, NOT palette-based.
 *
 * Ancient city templates are built primarily from plain deepslate and basalt
 * (e.g. 928 plain Deepslate in a single entrance piece), so a material allow-list
 * would leave most of the structure unprotected. Instead a block is protected iff
 * it falls inside a `city_pieces` bounding box (expanded by
 * `protection.piece-padding` to cover edge decoration + a thin shell), regardless
 * of its type. Natural terrain *between* the scattered pieces — the bulk of the
 * 220×220 envelope — stays fully mineable.
 *
 * Players with `acp.bypass.protection` (default op) are exempt.
 */
class ProtectionListener(private val plugin: AncientCityPro) : Listener {

    private fun pad() = plugin.config.getInt("protection.piece-padding", 3)
    private fun enabled() = plugin.config.getBoolean("protection.enabled", true)

    /** Whether [block] sits inside a (padded) structure piece of some city. */
    private fun isProtected(block: Block): Boolean {
        // Fast reject via the padded region AABB, then the per-piece test.
        val city = plugin.cityManager.getCachedCityInPaddedRegion(block.location, pad()) ?: return false
        return city.inStructurePiece(block.location, pad())
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
        if (isProtected(event.block)) {
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
