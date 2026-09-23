package com.esmpfun.betterancientcities.listeners

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.models.City
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent

/**
 * Griefing protection by position: every block inside a structure piece, grown by
 * `protection.piece-padding`, is protected whatever it is made of. Ancient cities are
 * mostly plain deepslate, so a list of protected block types would miss most of it.
 * The natural terrain between the pieces stays mineable.
 */
class ProtectionListener(private val plugin: BetterAncientCities) : Listener {

    private fun pad() = plugin.config.getInt("protection.piece-padding", 3).coerceAtLeast(0)
    private fun active() = plugin.isReady && plugin.config.getBoolean("protection.enabled", true)
    private fun placeProtected() = plugin.config.getBoolean("protection.block-place", true)
    private fun bypass(player: Player) = player.hasPermission("bac.bypass.protection")

    private fun protectingCity(block: Block): City? {
        val pad = pad()
        val city = plugin.cityManager.getCachedCityInPaddedRegion(block.location, pad) ?: return null
        return if (city.inStructurePiece(block.location, pad)) city else null
    }

    private fun isProtected(block: Block): Boolean = protectingCity(block) != null

    private fun deny(player: Player, city: City) {
        plugin.statsManager.incrementGrief(city.id, player.uniqueId)
        if (plugin.config.getBoolean("protection.notify-denied", true)) {
            player.sendActionBar(Component.text("This Ancient City is protected.", NamedTextColor.RED))
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!active() || bypass(event.player)) return
        val city = protectingCity(event.block) ?: return
        event.isCancelled = true
        deny(event.player, city)
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!active() || !placeProtected() || bypass(event.player)) return
        val city = protectingCity(event.block) ?: return
        event.isCancelled = true
        deny(event.player, city)
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        if (!active() || !placeProtected() || bypass(event.player)) return
        val city = protectingCity(event.block) ?: return
        event.isCancelled = true
        deny(event.player, city)
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBucketFill(event: PlayerBucketFillEvent) {
        if (!active() || bypass(event.player)) return
        val city = protectingCity(event.block) ?: return
        event.isCancelled = true
        deny(event.player, city)
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (!active() || !plugin.config.getBoolean("protection.block-explosions", true)) return
        event.blockList().removeIf { isProtected(it) }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (!active() || !plugin.config.getBoolean("protection.block-explosions", true)) return
        event.blockList().removeIf { isProtected(it) }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBurn(event: BlockBurnEvent) {
        if (!active()) return
        if (isProtected(event.block)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onIgnite(event: BlockIgniteEvent) {
        if (!active()) return
        val player = event.player
        if (player != null && bypass(player)) return
        val city = protectingCity(event.block) ?: return
        event.isCancelled = true
        if (player != null) deny(player, city)
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (!active()) return
        val dir = event.direction
        if (event.blocks.any { isProtected(it) || isProtected(it.getRelative(dir)) }) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (!active()) return
        if (event.blocks.any { isProtected(it) }) event.isCancelled = true
    }

    /** Mobs and falling blocks changing the structure (endermen, withers, ravagers, sand). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (!active()) return
        val player = event.entity as? Player
        if (player != null && bypass(player)) return
        if (isProtected(event.block)) event.isCancelled = true
    }
}
