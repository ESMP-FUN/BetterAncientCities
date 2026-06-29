package io.github.darkstarworks.ancientCityPro.managers

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.models.City
import io.github.darkstarworks.ancientCityPro.utils.CompressionUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import java.io.File
import java.io.Serializable
import java.util.concurrent.atomic.AtomicInteger

/**
 * Captures and restores an Ancient City's structure to/from a gzip-compressed
 * file on disk (one per city id, in `snapshots/`).
 *
 * **Block-data only** — no tile-entity NBT. ACP's loot is virtual (served from DB
 * templates, never the physical chest), so a snapshot only needs the block
 * palette to restore the structure. This reverts sculk spread and any griefing
 * back to the captured state, and it sidesteps the reflection-heavy NBT capture
 * TCP needs for vault/spawner state.
 *
 * **Scope** — only the cells inside each `city_pieces` bounding box are captured
 * (air included, so a restore truly resets spread), NOT the full ~220³ city AABB.
 * Reads/writes hop per-chunk to the owning region thread (Folia-safe).
 */
class SnapshotManager(private val plugin: AncientCityPro) {

    private data class SnapshotData(
        val worldName: String,
        val originX: Int,
        val originY: Int,
        val originZ: Int,
        val blocks: Map<Triple<Int, Int, Int>, String>, // relative pos -> blockData string
    ) : Serializable {
        companion object { private const val serialVersionUID = 1L }
    }

    private fun fileFor(cityId: Int) = File(plugin.snapshotsDir, "city_$cityId.dat")

    private fun maxCells() = plugin.config.getInt("snapshot.max-cells", 3_000_000)

    /** Block positions of every piece cell, deduped and grouped by chunk key. */
    private fun cellsByChunk(city: City): Map<Long, MutableList<Triple<Int, Int, Int>>> {
        val seen = HashSet<Long>()
        val byChunk = HashMap<Long, MutableList<Triple<Int, Int, Int>>>()
        for (p in city.pieces) {
            for (x in p.minX..p.maxX) for (y in p.minY..p.maxY) for (z in p.minZ..p.maxZ) {
                if (!seen.add(blockKey(x, y, z))) continue
                val ck = (x shr 4).toLong() shl 32 or ((z shr 4).toLong() and 0xffffffffL)
                byChunk.getOrPut(ck) { mutableListOf() }.add(Triple(x, y, z))
            }
        }
        return byChunk
    }

    private fun blockKey(x: Int, y: Int, z: Int): Long =
        (x.toLong() and 0x3FFFFFF shl 38) or (z.toLong() and 0x3FFFFFF shl 12) or (y.toLong() and 0xFFF)

    /**
     * Captures [city]'s structure to disk. Returns the cell count, or -1 if the
     * city has no pieces / exceeds `snapshot.max-cells`. Persists the file path
     * onto the city row.
     */
    suspend fun capture(city: City): Int {
        val world = city.getWorld() ?: run {
            plugin.logger.warning("[Snapshot] World '${city.world}' not loaded; cannot capture city #${city.id}")
            return -1
        }
        val byChunk = cellsByChunk(city)
        val total = byChunk.values.sumOf { it.size }
        if (total == 0) { plugin.logger.warning("[Snapshot] City #${city.id} has no piece cells to capture"); return -1 }
        if (total > maxCells()) {
            plugin.logger.warning("[Snapshot] City #${city.id} capture aborted: $total cells exceeds snapshot.max-cells (${maxCells()})")
            return -1
        }

        val origin = Triple(city.region.minX, city.region.minY, city.region.minZ)
        val blocks = HashMap<Triple<Int, Int, Int>, String>(total)

        // Read each chunk's cells on its owning region thread.
        for ((_, cells) in byChunk) {
            val rep = cells.first()
            val loc = Location(world, rep.first.toDouble(), rep.second.toDouble(), rep.third.toDouble())
            suspendCancellableCoroutine<Unit> { cont ->
                plugin.scheduler.runAtLocation(loc, Runnable {
                    try {
                        for (c in cells) {
                            val data = world.getBlockAt(c.first, c.second, c.third).blockData.asString
                            blocks[Triple(c.first - origin.first, c.second - origin.second, c.third - origin.third)] = data
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("[Snapshot] capture chunk read failed: ${e.message}")
                    }
                    cont.resume(Unit) {}
                })
            }
        }

        return withContext(Dispatchers.IO) {
            val data = SnapshotData(city.world, origin.first, origin.second, origin.third, blocks)
            val file = fileFor(city.id)
            file.writeBytes(CompressionUtil.compressObject(data))
            plugin.cityManager.setSnapshotFile(city.id, file.name)
            plugin.logger.info("[Snapshot] Captured city #${city.id}: $total cells (${CompressionUtil.formatSize(file.length())})")
            total
        }
    }

    fun hasSnapshot(cityId: Int): Boolean = fileFor(cityId).exists()

    /**
     * Restores [city] from its snapshot, setting every captured cell back (physics
     * suppressed). Returns the cell count restored, or -1 if no snapshot / load
     * failure. Block writes hop per-chunk to the region thread and the call
     * suspends until all batches complete.
     */
    suspend fun restore(city: City): Int {
        val file = fileFor(city.id)
        if (!file.exists()) { plugin.logger.warning("[Snapshot] No snapshot for city #${city.id}"); return -1 }

        val data = withContext(Dispatchers.IO) {
            try { CompressionUtil.decompressObject<SnapshotData>(file.readBytes()) }
            catch (e: Exception) { plugin.logger.warning("[Snapshot] load failed for city #${city.id}: ${e.message}"); null }
        } ?: return -1

        val world = plugin.server.getWorld(data.worldName) ?: run {
            plugin.logger.warning("[Snapshot] World '${data.worldName}' not loaded; cannot restore city #${city.id}")
            return -1
        }

        // Group absolute positions + their block data by chunk.
        val byChunk = HashMap<Long, MutableList<Pair<Location, String>>>()
        for ((rel, str) in data.blocks) {
            val x = data.originX + rel.first; val y = data.originY + rel.second; val z = data.originZ + rel.third
            val ck = (x shr 4).toLong() shl 32 or ((z shr 4).toLong() and 0xffffffffL)
            byChunk.getOrPut(ck) { mutableListOf() }.add(Location(world, x.toDouble(), y.toDouble(), z.toDouble()) to str)
        }

        val remaining = AtomicInteger(byChunk.size)
        val done = CompletableDeferred<Unit>()
        var restored = 0
        for ((_, list) in byChunk) {
            val loc = list.first().first
            plugin.scheduler.runAtLocation(loc, Runnable {
                try {
                    for ((bloc, str) in list) {
                        val bd = runCatching { Bukkit.createBlockData(str) }.getOrNull() ?: continue
                        bloc.block.setBlockData(bd, false) // no physics — avoid cascade updates
                        restored++
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("[Snapshot] restore chunk write failed: ${e.message}")
                }
                if (remaining.decrementAndGet() == 0) done.complete(Unit)
            })
        }
        if (byChunk.isEmpty()) done.complete(Unit)
        done.await()
        plugin.logger.info("[Snapshot] Restored city #${city.id}: $restored cells")
        return restored
    }

    fun deleteSnapshot(cityId: Int): Boolean = fileFor(cityId).let { if (it.exists()) it.delete() else false }
}
