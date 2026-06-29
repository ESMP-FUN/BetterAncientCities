package io.github.darkstarworks.ancientCityPro.managers

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.models.City
import io.github.darkstarworks.ancientCityPro.models.IntBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Location
import java.sql.Statement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the in-memory city cache and all `cities`/`city_pieces` SQL (mirrors how
 * TCP's ChamberManager owns its own persistence). A server has at most a handful
 * of registered cities, so the cache is fully preloaded with no eviction and
 * spatial lookups are a linear scan.
 */
class CityManager(private val plugin: AncientCityPro) {

    private val cache = ConcurrentHashMap<Int, City>()

    /**
     * Per-city loot-cycle start (epoch ms; 0 = no active cycle). A cycle is a
     * lazily-evaluated per-city refresh window: the first player to loot a city
     * with no active (or an expired) cycle starts a new one, which clears
     * everyone's per-player copies so the city's loot is fresh again. No
     * scheduler — the timer is only ever checked on a container open — so cities
     * refresh staggered by when each was first looted, never all at once.
     */
    private val cycleStarts = ConcurrentHashMap<Int, AtomicLong>()

    /** Loads every city (with its pieces) into the cache. Call once at startup. */
    suspend fun preload() = withContext(Dispatchers.IO) {
        cache.clear()
        cycleStarts.clear()
        plugin.databaseManager.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, world, min_x, min_y, min_z, max_x, max_y, max_z, " +
                    "origin_x, origin_y, origin_z, created_at, last_reset, snapshot_file, loot_cycle_start, approved FROM cities"
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val id = rs.getInt("id")
                        val region = IntBox(
                            rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                            rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"),
                        )
                        cache[id] = City(
                            id = id,
                            world = rs.getString("world"),
                            region = region,
                            origin = Triple(rs.getInt("origin_x"), rs.getInt("origin_y"), rs.getInt("origin_z")),
                            pieces = loadPieces(conn, id),
                            createdAt = rs.getLong("created_at"),
                            lastReset = rs.getLong("last_reset").takeIf { !rs.wasNull() },
                            snapshotFile = rs.getString("snapshot_file"),
                            approved = rs.getInt("approved") != 0,
                        )
                        val cycle = rs.getLong("loot_cycle_start")
                        if (!rs.wasNull() && cycle > 0L) cycleStarts[id] = AtomicLong(cycle)
                    }
                }
            }
        }
        plugin.logger.info("Loaded ${cache.size} Ancient ${if (cache.size == 1) "City" else "Cities"} into cache")
    }

    private fun loadPieces(conn: java.sql.Connection, cityId: Int): List<IntBox> {
        val out = mutableListOf<IntBox>()
        conn.prepareStatement(
            "SELECT min_x, min_y, min_z, max_x, max_y, max_z FROM city_pieces WHERE city_id = ?"
        ).use { stmt ->
            stmt.setInt(1, cityId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) out.add(
                    IntBox(
                        rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                        rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"),
                    )
                )
            }
        }
        return out
    }

    /** Whether a city with this origin already exists (dedup guard for discovery). */
    suspend fun existsAt(world: String, origin: Triple<Int, Int, Int>): Boolean =
        withContext(Dispatchers.IO) {
            cache.values.any { it.world == world && it.origin == origin } || run {
                plugin.databaseManager.connection.use { conn ->
                    conn.prepareStatement(
                        "SELECT 1 FROM cities WHERE world = ? AND origin_x = ? AND origin_y = ? AND origin_z = ?"
                    ).use { stmt ->
                        stmt.setString(1, world)
                        stmt.setInt(2, origin.first); stmt.setInt(3, origin.second); stmt.setInt(4, origin.third)
                        stmt.executeQuery().use { it.next() }
                    }
                }
            }
        }

    /**
     * Persists a newly-discovered city and its pieces, caches it, and returns it —
     * or null if a row with this origin already exists (UNIQUE collision, e.g. a
     * concurrent discovery). Idempotent against double-fire.
     */
    suspend fun registerCity(
        world: String,
        region: IntBox,
        origin: Triple<Int, Int, Int>,
        pieces: List<IntBox>,
        approved: Boolean,
    ): City? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val cityId = conn.prepareStatement(
                        "INSERT INTO cities (world, min_x, min_y, min_z, max_x, max_y, max_z, " +
                            "origin_x, origin_y, origin_z, created_at, approved) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS
                    ).use { stmt ->
                        stmt.setString(1, world)
                        stmt.setInt(2, region.minX); stmt.setInt(3, region.minY); stmt.setInt(4, region.minZ)
                        stmt.setInt(5, region.maxX); stmt.setInt(6, region.maxY); stmt.setInt(7, region.maxZ)
                        stmt.setInt(8, origin.first); stmt.setInt(9, origin.second); stmt.setInt(10, origin.third)
                        stmt.setLong(11, now)
                        stmt.setInt(12, if (approved) 1 else 0)
                        stmt.executeUpdate()
                        stmt.generatedKeys.use { if (it.next()) it.getInt(1) else error("no generated city id") }
                    }
                    conn.prepareStatement(
                        "INSERT INTO city_pieces (city_id, min_x, min_y, min_z, max_x, max_y, max_z) VALUES (?,?,?,?,?,?,?)"
                    ).use { stmt ->
                        for (p in pieces) {
                            stmt.setInt(1, cityId)
                            stmt.setInt(2, p.minX); stmt.setInt(3, p.minY); stmt.setInt(4, p.minZ)
                            stmt.setInt(5, p.maxX); stmt.setInt(6, p.maxY); stmt.setInt(7, p.maxZ)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                    conn.commit()
                    val city = City(cityId, world, region, origin, pieces, now, approved = approved)
                    cache[cityId] = city
                    city
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        } catch (e: Exception) {
            // Likely a UNIQUE collision from a concurrent discovery — treat as already-registered.
            plugin.logger.warning("[CityManager] registerCity failed for $world @ $origin: ${e.message}")
            null
        }
    }

    /**
     * Atomically decides whether THIS call should start a new loot cycle for
     * [cityId]: true when there's no active cycle or the current one is older than
     * [refreshMs]. Exactly one concurrent caller wins (CAS), so only one clears
     * the city's copies. The winner should call [ContainerLootManager.clearCity]
     * and [persistCycleStart]. Synchronous + thread-safe; [refreshMs] <= 0
     * disables refresh entirely (always false).
     */
    fun beginCycleIfDue(cityId: Int, refreshMs: Long): Boolean {
        if (refreshMs <= 0L) return false
        val al = cycleStarts.getOrPut(cityId) { AtomicLong(0L) }
        while (true) {
            val cur = al.get()
            val now = System.currentTimeMillis()
            if (cur != 0L && now - cur < refreshMs) return false // active cycle, not due
            if (al.compareAndSet(cur, now)) return true          // we started the new cycle
            // lost the race — another thread advanced it; re-read and re-check
        }
    }

    /** Persists the current in-memory cycle start for [cityId] to the DB. */
    suspend fun persistCycleStart(cityId: Int) = withContext(Dispatchers.IO) {
        val value = cycleStarts[cityId]?.get() ?: return@withContext
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET loot_cycle_start = ? WHERE id = ?").use { stmt ->
                    stmt.setLong(1, value)
                    stmt.setInt(2, cityId)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] persistCycleStart($cityId) failed: ${e.message}")
        }
    }

    /** Records the snapshot file name on a city (DB + cache). */
    suspend fun setSnapshotFile(id: Int, fileName: String?) = withContext(Dispatchers.IO) {
        val city = cache[id] ?: return@withContext
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET snapshot_file = ? WHERE id = ?").use { stmt ->
                    stmt.setString(1, fileName)
                    stmt.setInt(2, id)
                    stmt.executeUpdate()
                }
            }
            cache[id] = city.copy(snapshotFile = fileName)
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] setSnapshotFile($id) failed: ${e.message}")
        }
    }

    /** The APPROVED city whose region envelope contains [loc], or null. Used by
     *  loot + protection — pending cities are inactive. */
    fun getCachedCityAt(loc: Location): City? =
        cache.values.firstOrNull { it.approved && it.containsInRegion(loc) }

    /** The APPROVED city whose region envelope, expanded by [pad], contains [loc]. */
    fun getCachedCityInPaddedRegion(loc: Location, pad: Int): City? =
        cache.values.firstOrNull { it.approved && it.containsInPaddedRegion(loc, pad) }

    /** Any city (approved or pending) containing [loc] — for admin/info, not gameplay. */
    fun getAnyCityAt(loc: Location): City? =
        cache.values.firstOrNull { it.containsInRegion(loc) }

    fun byId(id: Int): City? = cache[id]

    /** All cached cities (read-only snapshot). */
    fun all(): Collection<City> = cache.values.toList()

    fun approved(): List<City> = cache.values.filter { it.approved }
    fun pending(): List<City> = cache.values.filter { !it.approved }

    /** Approves a pending city (activates loot + protection). Returns false if unknown. */
    suspend fun approveCity(id: Int): Boolean = withContext(Dispatchers.IO) {
        val city = cache[id] ?: return@withContext false
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE cities SET approved = 1 WHERE id = ?").use { stmt ->
                    stmt.setInt(1, id)
                    stmt.executeUpdate()
                }
            }
            cache[id] = city.copy(approved = true)
            true
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] approveCity($id) failed: ${e.message}")
            false
        }
    }

    suspend fun deleteCity(id: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM cities WHERE id = ?").use { stmt ->
                    stmt.setInt(1, id)
                    val removed = stmt.executeUpdate() > 0
                    if (removed) {
                        cache.remove(id)
                        cycleStarts.remove(id)
                    }
                    removed
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] deleteCity($id) failed: ${e.message}")
            false
        }
    }
}
