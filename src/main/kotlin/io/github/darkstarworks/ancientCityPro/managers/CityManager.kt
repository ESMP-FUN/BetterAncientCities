package io.github.darkstarworks.ancientCityPro.managers

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.models.City
import io.github.darkstarworks.ancientCityPro.models.IntBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Location
import java.sql.Statement
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the in-memory city cache and all `cities`/`city_pieces` SQL (mirrors how
 * TCP's ChamberManager owns its own persistence). A server has at most a handful
 * of registered cities, so the cache is fully preloaded with no eviction and
 * spatial lookups are a linear scan.
 */
class CityManager(private val plugin: AncientCityPro) {

    private val cache = ConcurrentHashMap<Int, City>()

    /** Loads every city (with its pieces) into the cache. Call once at startup. */
    suspend fun preload() = withContext(Dispatchers.IO) {
        cache.clear()
        plugin.databaseManager.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, world, min_x, min_y, min_z, max_x, max_y, max_z, " +
                    "origin_x, origin_y, origin_z, created_at, last_reset, snapshot_file FROM cities"
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
                        )
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
    ): City? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val cityId = conn.prepareStatement(
                        "INSERT INTO cities (world, min_x, min_y, min_z, max_x, max_y, max_z, " +
                            "origin_x, origin_y, origin_z, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS
                    ).use { stmt ->
                        stmt.setString(1, world)
                        stmt.setInt(2, region.minX); stmt.setInt(3, region.minY); stmt.setInt(4, region.minZ)
                        stmt.setInt(5, region.maxX); stmt.setInt(6, region.maxY); stmt.setInt(7, region.maxZ)
                        stmt.setInt(8, origin.first); stmt.setInt(9, origin.second); stmt.setInt(10, origin.third)
                        stmt.setLong(11, now)
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
                    val city = City(cityId, world, region, origin, pieces, now)
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

    /** The city whose region envelope contains [loc], or null. */
    fun getCachedCityAt(loc: Location): City? =
        cache.values.firstOrNull { it.containsInRegion(loc) }

    /** All cached cities (read-only snapshot). */
    fun all(): Collection<City> = cache.values.toList()

    suspend fun deleteCity(id: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM cities WHERE id = ?").use { stmt ->
                    stmt.setInt(1, id)
                    val removed = stmt.executeUpdate() > 0
                    if (removed) cache.remove(id)
                    removed
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[CityManager] deleteCity($id) failed: ${e.message}")
            false
        }
    }
}
