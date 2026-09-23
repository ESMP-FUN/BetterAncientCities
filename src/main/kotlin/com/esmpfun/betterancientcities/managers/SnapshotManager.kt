package com.esmpfun.betterancientcities.managers

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.models.City
import com.esmpfun.betterancientcities.models.IntBox
import com.esmpfun.betterancientcities.utils.CompressionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.data.BlockData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.io.Serializable
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Saves and restores the blocks of a city's structure pieces (padded by
 * `protection.piece-padding`, air included) to `snapshots/city_<id>.dat`.
 *
 * Block data only: loot lives in the database, so chests need no NBT. Work hops to
 * each chunk's own thread one chunk at a time, so a big city never stalls a tick.
 */
class SnapshotManager(private val plugin: BetterAncientCities) {

    /** The pre-2.2 file layout, still read. The name and fields must not change. */
    private data class SnapshotData(
        val worldName: String,
        val originX: Int,
        val originY: Int,
        val originZ: Int,
        val blocks: Map<Triple<Int, Int, Int>, String>,
    ) : Serializable {
        companion object { private const val serialVersionUID = 1L }
    }

    /** One chunk's worth of cells: positions relative to the origin and palette indexes. */
    private class Section(val chunkX: Int, val chunkZ: Int, val xs: ShortArray, val ys: ShortArray, val zs: ShortArray, val ids: IntArray)

    private class Snapshot(val world: String, val originX: Int, val originY: Int, val originZ: Int, val palette: List<String>, val sections: List<Section>) {
        val cells: Int get() = sections.sumOf { it.ids.size }
    }

    private val busy = ConcurrentHashMap.newKeySet<Int>()

    private fun fileFor(cityId: Int) = File(plugin.snapshotsDir, "city_$cityId.dat")

    private fun maxCells() = plugin.config.getInt("snapshot.max-cells", 3_000_000)

    /** Capture covers what protection covers, so edge decoration is restored too. */
    private fun capturePad() = plugin.config.getInt("protection.piece-padding", 3).coerceIn(0, 16)

    fun hasSnapshot(cityId: Int): Boolean = fileFor(cityId).exists()

    fun deleteSnapshot(cityId: Int): Boolean = fileFor(cityId).let { it.exists() && it.delete() }

    /** Every cell of the padded pieces, grouped by chunk, without overlaps counted twice. */
    private class ChunkCells(val chunkX: Int, val chunkZ: Int, val minY: Int, val height: Int, val bits: BitSet)

    private fun cellsByChunk(city: City, pad: Int): List<ChunkCells> {
        val boxes = city.pieces.map { it.expanded(pad) }
        if (boxes.isEmpty()) return emptyList()
        val all = IntBox.union(boxes)
        val height = all.maxY - all.minY + 1
        val byChunk = LinkedHashMap<Long, ChunkCells>()
        for (b in boxes) {
            for (cx in (b.minX shr 4)..(b.maxX shr 4)) for (cz in (b.minZ shr 4)..(b.maxZ shr 4)) {
                val cc = byChunk.getOrPut(chunkKey(cx, cz)) { ChunkCells(cx, cz, all.minY, height, BitSet(16 * 16 * height)) }
                val x0 = maxOf(b.minX, cx shl 4); val x1 = minOf(b.maxX, (cx shl 4) + 15)
                val z0 = maxOf(b.minZ, cz shl 4); val z1 = minOf(b.maxZ, (cz shl 4) + 15)
                for (x in x0..x1) for (z in z0..z1) {
                    val column = ((x and 15) * 16 + (z and 15)) * height
                    cc.bits.set(column + (b.minY - all.minY), column + (b.maxY - all.minY) + 1)
                }
            }
        }
        return byChunk.values.toList()
    }

    private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xffffffffL)

    /**
     * Saves [city]'s blocks. Returns the number of cells, -1 on failure (reason in the
     * console) or [BUSY] when a capture or restore of this city is already running.
     */
    suspend fun capture(city: City): Int {
        if (!busy.add(city.id)) return BUSY
        try {
            return doCapture(city)
        } finally {
            busy.remove(city.id)
        }
    }

    private suspend fun doCapture(city: City): Int {
        val world = city.getWorld() ?: run {
            plugin.logger.warning("Could not save city #${city.id}: its world '${city.world}' is not loaded.")
            return -1
        }
        val chunks = withContext(Dispatchers.Default) { cellsByChunk(city, capturePad()) }
        val total = chunks.sumOf { it.bits.cardinality() }
        if (total == 0) {
            plugin.logger.warning("Could not save city #${city.id}: it has no structure pieces.")
            return -1
        }
        if (total > maxCells()) {
            plugin.logger.warning("Did not save city #${city.id}: it has $total blocks, more than snapshot.max-cells (${maxCells()}).")
            return -1
        }

        val originX = city.region.minX; val originY = city.region.minY; val originZ = city.region.minZ
        val palette = ArrayList<String>()
        val paletteIndex = HashMap<BlockData, Int>()
        val sections = ArrayList<Section>(chunks.size)
        var failed = 0

        for (cc in chunks) {
            val count = cc.bits.cardinality()
            val section = Section(cc.chunkX, cc.chunkZ, ShortArray(count), ShortArray(count), ShortArray(count), IntArray(count))
            val loc = Location(world, (cc.chunkX shl 4).toDouble(), cc.minY.toDouble(), (cc.chunkZ shl 4).toDouble())
            val ok = onRegion(loc) {
                val chunk = world.getChunkAt(cc.chunkX, cc.chunkZ)
                var n = 0
                var bit = cc.bits.nextSetBit(0)
                while (bit >= 0) {
                    val y = cc.minY + bit % cc.height
                    val column = bit / cc.height
                    val lx = column / 16; val lz = column % 16
                    val data = try {
                        chunk.getBlock(lx, y, lz).blockData
                    } catch (e: Exception) {
                        if (failed++ < 5) plugin.logger.warning("Could not read the block at ${(cc.chunkX shl 4) + lx}, $y, ${(cc.chunkZ shl 4) + lz}: ${e.message}")
                        null
                    }
                    section.xs[n] = ((cc.chunkX shl 4) + lx - originX).toShort()
                    section.ys[n] = (y - originY).toShort()
                    section.zs[n] = ((cc.chunkZ shl 4) + lz - originZ).toShort()
                    section.ids[n] = if (data == null) -1 else paletteIndex.getOrPut(data) {
                        palette.add(data.asString)
                        palette.size - 1
                    }
                    n++
                    bit = cc.bits.nextSetBit(bit + 1)
                }
            }
            if (!ok) {
                plugin.logger.warning("Could not save city #${city.id}: the server stopped before it was done.")
                return -1
            }
            sections.add(section)
        }

        return withContext(Dispatchers.IO) {
            val snapshot = Snapshot(city.world, originX, originY, originZ, palette, sections)
            val file = fileFor(city.id)
            val tmp = File(file.parentFile, file.name + ".tmp")
            try {
                tmp.writeBytes(CompressionUtil.compress(encode(snapshot)))
            } catch (e: Exception) {
                tmp.delete()
                plugin.logger.warning("Could not save city #${city.id}: the snapshot file could not be written (${e.message}).")
                return@withContext -1
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) {
                    plugin.logger.warning("Could not save city #${city.id}: the snapshot file could not be written.")
                    return@withContext -1
                }
            }
            plugin.cityManager.setSnapshotFile(city.id, file.name)
            plugin.logger.info(
                "Saved city #${city.id}: ${snapshot.cells} blocks (${CompressionUtil.formatSize(file.length())})" +
                    if (failed > 0) ", $failed could not be read" else ""
            )
            snapshot.cells
        }
    }

    /**
     * Puts [city]'s blocks back as saved. Returns the number of cells, -1 on failure
     * or [BUSY] when a capture or restore of this city is already running.
     */
    suspend fun restore(city: City): Int {
        if (!busy.add(city.id)) return BUSY
        try {
            return doRestore(city)
        } finally {
            busy.remove(city.id)
        }
    }

    private suspend fun doRestore(city: City): Int {
        val file = fileFor(city.id)
        if (!file.exists()) {
            plugin.logger.warning("City #${city.id} has no snapshot to restore.")
            return -1
        }
        val snapshot = withContext(Dispatchers.IO) {
            try {
                decode(CompressionUtil.decompress(file.readBytes()))
            } catch (e: Exception) {
                plugin.logger.warning("Could not read the snapshot of city #${city.id}: ${e.message}")
                null
            }
        } ?: return -1

        val world = plugin.server.getWorld(snapshot.world) ?: run {
            plugin.logger.warning("Could not restore city #${city.id}: its world '${snapshot.world}' is not loaded.")
            return -1
        }

        val parsed = arrayOfNulls<BlockData>(snapshot.palette.size)
        val unreadable = BooleanArray(snapshot.palette.size)
        var restored = 0
        var failed = 0

        for (section in snapshot.sections) {
            val loc = Location(world, (section.chunkX shl 4).toDouble(), snapshot.originY.toDouble(), (section.chunkZ shl 4).toDouble())
            val ok = onRegion(loc) {
                for (i in section.ids.indices) {
                    val id = section.ids[i]
                    if (id < 0 || unreadable[id]) { failed++; continue }
                    val data = parsed[id] ?: try {
                        Bukkit.createBlockData(snapshot.palette[id]).also { parsed[id] = it }
                    } catch (e: Exception) {
                        unreadable[id] = true
                        plugin.logger.warning("Skipped '${snapshot.palette[id]}' in city #${city.id}: this server does not know that block.")
                        failed++
                        continue
                    }
                    try {
                        val block = world.getBlockAt(
                            snapshot.originX + section.xs[i], snapshot.originY + section.ys[i], snapshot.originZ + section.zs[i]
                        )
                        if (block.blockData != data) block.setBlockData(data, false)
                        restored++
                    } catch (e: Exception) {
                        if (failed++ < 5) plugin.logger.warning("Could not restore a block in city #${city.id}: ${e.message}")
                    }
                }
            }
            if (!ok) {
                plugin.logger.warning("Restoring city #${city.id} stopped early because the server is shutting down.")
                return -1
            }
        }
        plugin.logger.info(
            "Restored city #${city.id}: $restored of ${snapshot.cells} blocks" +
                if (failed > 0) " ($failed skipped, see the warnings above)" else ""
        )
        return restored
    }

    /** Runs [block] on the thread that owns [loc] and waits. False if it never ran. */
    private suspend fun onRegion(loc: Location, block: () -> Unit): Boolean =
        suspendCancellableCoroutine { cont ->
            try {
                plugin.scheduler.runAtLocation(loc, Runnable {
                    try {
                        block()
                        cont.resume(true)
                    } catch (e: Exception) {
                        plugin.logger.warning("Snapshot step failed: ${e.message}")
                        cont.resume(false)
                    }
                })
            } catch (e: Exception) {
                cont.resume(false)
            }
        }

    // File layout, inside gzip: magic, version, world, origin, palette, then chunk sections.

    private fun encode(s: Snapshot): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(FORMAT_VERSION)
            out.writeUTF(s.world)
            out.writeInt(s.originX); out.writeInt(s.originY); out.writeInt(s.originZ)
            out.writeInt(s.palette.size)
            s.palette.forEach(out::writeUTF)
            out.writeInt(s.sections.size)
            for (sec in s.sections) {
                out.writeInt(sec.chunkX); out.writeInt(sec.chunkZ)
                out.writeInt(sec.ids.size)
                for (i in sec.ids.indices) {
                    out.writeShort(sec.xs[i].toInt()); out.writeShort(sec.ys[i].toInt()); out.writeShort(sec.zs[i].toInt())
                    out.writeInt(sec.ids[i])
                }
            }
        }
        return baos.toByteArray()
    }

    private fun decode(bytes: ByteArray): Snapshot {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (bytes.size < 4 || input.readInt() != MAGIC) return decodeLegacy(bytes)
            val version = input.readInt()
            require(version == FORMAT_VERSION) { "snapshot format $version is newer than this plugin" }
            val world = input.readUTF()
            val ox = input.readInt(); val oy = input.readInt(); val oz = input.readInt()
            val palette = List(input.readInt()) { input.readUTF() }
            val sections = List(input.readInt()) {
                val cx = input.readInt(); val cz = input.readInt()
                val n = input.readInt()
                val xs = ShortArray(n); val ys = ShortArray(n); val zs = ShortArray(n); val ids = IntArray(n)
                for (i in 0 until n) {
                    xs[i] = input.readShort(); ys[i] = input.readShort(); zs[i] = input.readShort()
                    ids[i] = input.readInt()
                }
                Section(cx, cz, xs, ys, zs, ids)
            }
            return Snapshot(world, ox, oy, oz, palette, sections)
        }
    }

    /** Reads the old Java-serialized layout, including files saved under the pre-2.0 name. */
    private fun decodeLegacy(bytes: ByteArray): Snapshot {
        val data = object : ObjectInputStream(ByteArrayInputStream(bytes)) {
            override fun resolveClass(desc: ObjectStreamClass): Class<*> = when (desc.name) {
                LEGACY_CLASS, CURRENT_LEGACY_CLASS -> SnapshotData::class.java
                in ALLOWED_CLASSES -> super.resolveClass(desc)
                else -> throw java.io.InvalidClassException(desc.name, "not allowed in a snapshot file")
            }
        }.use { it.readObject() as SnapshotData }

        val palette = ArrayList<String>()
        val paletteIndex = HashMap<String, Int>()
        val grouped = LinkedHashMap<Long, MutableList<Pair<Triple<Int, Int, Int>, Int>>>()
        for ((rel, str) in data.blocks) {
            val id = paletteIndex.getOrPut(str) { palette.add(str); palette.size - 1 }
            val cx = (data.originX + rel.first) shr 4
            val cz = (data.originZ + rel.third) shr 4
            grouped.getOrPut(chunkKey(cx, cz)) { mutableListOf() }.add(rel to id)
        }
        val sections = grouped.map { (key, cells) ->
            Section(
                (key shr 32).toInt(), key.toInt(),
                ShortArray(cells.size) { cells[it].first.first.toShort() },
                ShortArray(cells.size) { cells[it].first.second.toShort() },
                ShortArray(cells.size) { cells[it].first.third.toShort() },
                IntArray(cells.size) { cells[it].second },
            )
        }
        return Snapshot(data.worldName, data.originX, data.originY, data.originZ, palette, sections)
    }

    companion object {
        const val BUSY = -2
        private const val MAGIC = 0x42414353 // "BACS"
        private const val FORMAT_VERSION = 2
        private const val LEGACY_CLASS = "io.github.darkstarworks.ancientCityPro.managers.SnapshotManager\$SnapshotData"
        private const val CURRENT_LEGACY_CLASS = "com.esmpfun.betterancientcities.managers.SnapshotManager\$SnapshotData"
        private val ALLOWED_CLASSES = setOf(
            "kotlin.Triple", "java.util.HashMap", "java.util.LinkedHashMap",
            "java.lang.Integer", "java.lang.Number", "java.lang.String",
        )
    }
}
