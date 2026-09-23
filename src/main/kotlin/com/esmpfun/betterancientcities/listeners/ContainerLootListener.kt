package com.esmpfun.betterancientcities.listeners

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.managers.ContainerLootManager
import com.esmpfun.betterancientcities.managers.ContainerLootManager.Load
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.block.Chest
import org.bukkit.block.Container
import org.bukkit.block.DoubleChest
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootContext
import org.bukkit.loot.Lootable
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Serves every player their own copy of a city chest, barrel, dispenser or dropper.
 *
 * Only containers inside a real structure piece count, and containers players place
 * in a city are tagged and left vanilla. Staff with `bac.admin` sneak-open a
 * container to edit its shared template. A double chest is keyed by its left half.
 */
class ContainerLootListener(private val plugin: BetterAncientCities) : Listener {

    // The old namespace is kept so containers tagged before the rename stay vanilla.
    private val playerPlacedKey = NamespacedKey("ancientcitypro", "player_placed_container")

    private val openDebounce = ConcurrentHashMap<UUID, Long>()

    class CopyHolder(val cityId: Int, val pos: ContainerLootManager.ContainerPos) : InventoryHolder {
        lateinit var backing: Inventory
        override fun getInventory(): Inventory = backing
    }

    class TemplateHolder(val cityId: Int, val pos: ContainerLootManager.ContainerPos) : InventoryHolder {
        lateinit var backing: Inventory
        override fun getInventory(): Inventory = backing
    }

    private companion object {
        val ELIGIBLE = setOf(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.DISPENSER, Material.DROPPER
        )
        val COPY_TITLE: Component = Component.text("Ancient City Loot")
        val TEMPLATE_TITLE: Component = Component.text("Loot for everyone (editing)")
    }

    private fun enabled() = plugin.config.getBoolean("loot.enabled", true)

    /** `loot.refresh-hours` in ms; 0 or less turns refreshing off. */
    private fun refreshMs(): Long = plugin.config.getLong("loot.refresh-hours", 12L) * 3_600_000L

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onContainerOpen(event: PlayerInteractEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return
        val block = event.clickedBlock ?: return
        if (block.type !in ELIGIBLE) return

        val city = plugin.cityManager.getCachedCityAt(block.location) ?: return
        if (!city.inStructurePiece(block.location)) return

        val state = block.state
        if (state is TileState && state.persistentDataContainer.has(playerPlacedKey, PersistentDataType.BYTE)) return
        val container = state as? Container ?: return
        val player = event.player

        // Vanilla never rolls loot for a spectator, and a copy made here would count
        // as their first loot later on.
        if (player.gameMode == GameMode.SPECTATOR) {
            event.isCancelled = true
            return
        }

        val isAdminEdit = player.isSneaking && player.hasPermission("bac.admin")

        if (!isAdminEdit && plugin.banManager.isBanned(city.id, player.uniqueId)) {
            event.isCancelled = true
            player.sendMessage(Component.text("You are not allowed to loot this Ancient City.", NamedTextColor.RED))
            return
        }

        event.isCancelled = true

        val now = System.currentTimeMillis()
        val last = openDebounce[player.uniqueId]
        if (last != null && now - last < 700) return
        openDebounce[player.uniqueId] = now
        if (openDebounce.size > 100) openDebounce.entries.removeIf { now - it.value > 10_000 }

        // Resolve the key block and size here, on the block's own thread.
        val inv = container.inventory
        val holder = inv.holder
        val keyBlock = if (holder is DoubleChest) (holder.leftSide as? Chest)?.block ?: block else block
        val size = if (holder is DoubleChest) 54 else inv.size
        val pos = ContainerLootManager.ContainerPos(keyBlock.x, keyBlock.y, keyBlock.z)
        val keyLoc = keyBlock.location
        val keyMaterial = keyBlock.type
        val loot = plugin.containerLootManager

        plugin.launchAsync {
            val template = when (val stored = loot.loadTemplate(city.id, pos)) {
                is Load.Found -> stored.contents
                Load.Missing, Load.Damaged -> {
                    if (stored == Load.Damaged) {
                        plugin.logger.warning("The saved loot for the chest at ${pos.x}, ${pos.y}, ${pos.z} could not be read, so it was rolled again.")
                    }
                    val rolled = materializeOnRegion(keyLoc, size)
                    loot.insertTemplate(city.id, pos, rolled, keyMaterial, replace = stored == Load.Damaged)
                }
                Load.Failed -> null
            }
            if (template == null) {
                player.sendMessage(Component.text("This chest could not be opened right now. Please try again in a moment.", NamedTextColor.RED))
                return@launchAsync
            }

            if (isAdminEdit) {
                openVirtual(player, TemplateHolder(city.id, pos), size, TEMPLATE_TITLE, template)
                player.sendMessage(Component.text(
                    "You are editing what every player finds in this chest. Changes apply to players who have not opened it yet.",
                    NamedTextColor.GRAY
                ))
                return@launchAsync
            }

            // The first loot after the refresh window starts a new one and wipes everyone's copies.
            if (plugin.cityManager.beginCycleIfDue(city.id, refreshMs())) {
                loot.clearCity(city.id)
                plugin.cityManager.persistCycleStart(city.id)
                if (plugin.config.getBoolean("snapshot.auto-reset-on-refresh", false) &&
                    plugin.snapshotManager.hasSnapshot(city.id)
                ) {
                    plugin.launchAsync { plugin.snapshotManager.restore(city) }
                }
            }

            val contents = when (val copy = loot.loadCopy(city.id, pos, player.uniqueId)) {
                is Load.Found -> copy.contents
                Load.Missing -> {
                    plugin.statsManager.incrementLooted(city.id, player.uniqueId)
                    template
                }
                Load.Damaged -> {
                    plugin.logger.warning(
                        "${player.name}'s copy of the chest at ${pos.x}, ${pos.y}, ${pos.z} in city #${city.id} could not be read. " +
                            "Run /ancient resetloot ${city.id} ${player.name} to give them a fresh one."
                    )
                    null
                }
                Load.Failed -> null
            }
            if (contents == null) {
                player.sendMessage(Component.text("This chest could not be opened right now. Please try again in a moment.", NamedTextColor.RED))
                return@launchAsync
            }
            openVirtual(player, CopyHolder(city.id, pos), size, COPY_TITLE, contents)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onContainerClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        when (val holder = event.inventory.holder) {
            is CopyHolder -> plugin.containerLootManager.queueCopySave(
                holder.cityId, holder.pos, player.uniqueId, snapshot(event.inventory)
            )
            is TemplateHolder -> plugin.containerLootManager.queueTemplateSave(
                holder.cityId, holder.pos, snapshot(event.inventory)
            )
            else -> return
        }
    }

    /**
     * Saves and closes every city chest still open. Called on shutdown, because the
     * server disables plugins before it closes players' inventories.
     */
    fun closeOpenCopies() {
        for (player in Bukkit.getOnlinePlayers()) {
            try {
                val top = player.openInventory.topInventory
                when (val holder = top.holder) {
                    is CopyHolder -> plugin.containerLootManager.queueCopySave(holder.cityId, holder.pos, player.uniqueId, snapshot(top))
                    is TemplateHolder -> plugin.containerLootManager.queueTemplateSave(holder.cityId, holder.pos, snapshot(top))
                    else -> continue
                }
                player.closeInventory()
            } catch (e: Exception) {
                plugin.logger.warning("Could not close ${player.name}'s city chest on shutdown: ${e.message}")
            }
        }
    }

    private fun snapshot(inv: Inventory): Array<ItemStack?> = inv.contents.map { it?.clone() }.toTypedArray()

    /** Tag containers players place inside cities so they keep vanilla behaviour. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onContainerPlace(event: BlockPlaceEvent) {
        if (event.block.type !in ELIGIBLE) return
        if (plugin.cityManager.getCachedCityAt(event.block.location) == null) return
        val state = event.block.state as? TileState ?: return
        state.persistentDataContainer.set(playerPlacedKey, PersistentDataType.BYTE, 1)
        state.update()
    }

    /** Hoppers must not pull from or push into a city container. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHopperMove(event: InventoryMoveItemEvent) {
        if (!enabled()) return
        if (isCityContainer(event.source) || isCityContainer(event.destination)) {
            event.isCancelled = true
        }
    }

    private suspend fun materializeOnRegion(keyLoc: Location, size: Int): Array<ItemStack?> =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            plugin.scheduler.runAtLocation(keyLoc, Runnable {
                val result = try {
                    materializeTemplate(keyLoc.block, size)
                } catch (e: Exception) {
                    plugin.logger.warning("Could not roll the loot for the chest at ${keyLoc.blockX}, ${keyLoc.blockY}, ${keyLoc.blockZ}: ${e.message}")
                    arrayOfNulls<ItemStack?>(size)
                }
                cont.resume(result)
            })
        }

    /** Rolls the container's loot table into a scratch inventory; the block keeps its own table. */
    private fun materializeTemplate(block: Block, size: Int): Array<ItemStack?> {
        val container = block.state as? Container ?: return arrayOfNulls(size)
        val holder = container.inventory.holder
        return if (holder is DoubleChest) {
            val out = arrayOfNulls<ItemStack?>(54)
            rollHalf(holder.leftSide as? Chest, out, 0)
            rollHalf(holder.rightSide as? Chest, out, 27)
            out
        } else {
            rollSingle(container, size)
        }
    }

    private fun rollSingle(state: BlockState, size: Int): Array<ItemStack?> {
        val container = state as Container
        val table = (state as? Lootable)?.lootTable
        val source: Array<ItemStack?> = if (table != null) {
            val temp = Bukkit.createInventory(null, size)
            table.fillInventory(temp, java.util.Random(), LootContext.Builder(state.location).build())
            temp.contents
        } else {
            container.inventory.contents
        }
        return Array(size) { source.getOrNull(it)?.clone() }
    }

    private fun rollHalf(chest: Chest?, out: Array<ItemStack?>, offset: Int) {
        chest ?: return
        val table = chest.lootTable
        val half: Array<ItemStack?> = if (table != null) {
            val temp = Bukkit.createInventory(null, 27)
            table.fillInventory(temp, java.util.Random(), LootContext.Builder(chest.location).build())
            temp.contents
        } else {
            chest.blockInventory.contents
        }
        for (i in 0 until 27) out[offset + i] = half.getOrNull(i)?.clone()
    }

    private suspend fun openVirtual(
        player: Player,
        holder: InventoryHolder,
        size: Int,
        title: Component,
        contents: Array<ItemStack?>,
    ) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
        plugin.scheduler.runAtEntity(player, Runnable {
            try {
                if (player.isOnline) {
                    val virtual = plugin.server.createInventory(holder, size, title)
                    when (holder) {
                        is CopyHolder -> holder.backing = virtual
                        is TemplateHolder -> holder.backing = virtual
                    }
                    for (i in 0 until minOf(size, contents.size)) virtual.setItem(i, contents[i])
                    player.openInventory(virtual)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Could not open a city chest for ${player.name}: ${e.message}")
            }
            cont.resume(Unit)
        }, Runnable { cont.resume(Unit) })
    }

    /**
     * Whether [inv] belongs to a city container. Checks the cheap location first,
     * since this runs for every hopper transfer on the server.
     */
    private fun isCityContainer(inv: Inventory): Boolean {
        val loc = inv.location ?: return false
        val city = plugin.cityManager.getCachedCityAt(loc) ?: return false
        val block: Block = when (val h = inv.getHolder(false)) {
            is DoubleChest -> (h.leftSide as? Chest)?.block ?: return false
            is org.bukkit.inventory.BlockInventoryHolder -> h.block
            else -> return false
        }
        if (block.type !in ELIGIBLE) return false
        if (!city.inStructurePiece(block.location)) return false
        val state = block.getState(false) as? TileState ?: return false
        return !state.persistentDataContainer.has(playerPlacedKey, PersistentDataType.BYTE)
    }
}
