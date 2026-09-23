package com.esmpfun.betterancientcities.managers

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.models.City
import com.esmpfun.betterancientcities.models.IntBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Location
import java.sql.Statement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The city cache and all `cities` / `city_pieces` SQL. A server has a handful of
 * cities at most, so everything is preloaded and lookups are a linear scan.
 */
class CityManager(private val plugin: BetterAncientCities) {

    private val cache = ConcurrentHashMap<Int, City>()

    /**
     * Per-city loot-cycle start in epoch ms (0 = none). Only checked when a chest is
     * opened, so cities refresh staggered by when each was first looted.
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
        plugin.logger.info("Loaded ${cache.size} Ancient ${if (cache.size == 1) "City" else "Cities"}")
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

    /** Whether a city with this origin is already registered. */
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
     * Saves a newly found city and its pieces and returns it, or null when it could
     * not be saved (usually because another chunk of the same city got there first).
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
            plugin.logger.warning("Could not save the Ancient City found in $world at ${origin.first}, ${origin.second}, ${origin.third}: ${e.message}")
            null
        }
    }

    /**
     * True for exactly one caller when [cityId] has no loot cycle or its cycle is
     * older than [refreshMs]. That caller clears the copies and calls
     * [persistCycleStart]. [refreshMs] of 0 or less turns refreshing off.
     */
    fun beginCycleIfDue(cityId: Int, refreshMs: Long): Boolean {
        if (refreshMs <= 0L) return false
        val al = cycleStarts.getOrPut(cityId) { AtomicLong(0L) }
        while (true) {
            val cur = al.get()
            val now = System.currentTimeMillis()
            if (cur != 0L && now - cur < refreshMs) return false // active cycle, not due
            if (al.compareAndSet(cur, now)) return true
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
            plugin.logger.warning("Could not save the loot refresh time of city #$cityId: ${e.message}")
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
            plugin.logger.warning("Could not record the snapshot of city #$id: ${e.message}")
        }
    }

    /** The approved city whose area contains [loc]. Pending cities are inactive. */
    fun getCachedCityAt(loc: Location): City? =
        cache.values.firstOrNull { it.approved && it.containsInRegion(loc) }

    /** The approved city whose area, grown by [pad], contains [loc]. */
    fun getCachedCityInPaddedRegion(loc: Location, pad: Int): City? =
        cache.values.firstOrNull { it.approved && it.containsInPaddedRegion(loc, pad) }

    /** Any city, approved or pending, containing [loc]. For staff tools, not gameplay. */
    fun getAnyCityAt(loc: Location): City? =
        cache.values.firstOrNull { it.containsInRegion(loc) }

    fun byId(id: Int): City? = cache[id]

    fun all(): Collection<City> = cache.values.toList()

    /** Cities with an approval running, so a spammed [approve] link starts it once. */
    private val approvingInFlight = ConcurrentHashMap.newKeySet<Int>()

    fun tryBeginApproval(id: Int): Boolean = approvingInFlight.add(id)

    fun endApproval(id: Int) { approvingInFlight.remove(id) }

    /** Turns on loot and protection for a pending city. False if unknown or not saved. */
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
            plugin.logger.warning("Could not approve city #$id: ${e.message}")
            false
        }
    }

    /**
     * Removes a city with its saved chests, stats, bans and snapshot. Discovery
     * registers it again the next time one of its chunks loads, unless its world
     * is excluded or discovery is off.
     */
    suspend fun deleteCity(id: Int): Boolean = withContext(Dispatchers.IO) {
        val city = cache[id]
        try {
            val removed = plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM cities WHERE id = ?").use { stmt ->
                    stmt.setInt(1, id)
                    stmt.executeUpdate() > 0
                }
            }
            if (removed) {
                cache.remove(id)
                cycleStarts.remove(id)
                plugin.containerLootManager.forgetCity(id)
                plugin.banManager.forgetCity(id)
                plugin.snapshotManager.deleteSnapshot(id)
                city?.let { plugin.discoveryManager.forget(it) }
            }
            removed
        } catch (e: Exception) {
            plugin.logger.warning("Could not delete city #$id: ${e.message}")
            false
        }
    }
}
