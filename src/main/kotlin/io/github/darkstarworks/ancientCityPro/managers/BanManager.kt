package io.github.darkstarworks.ancientCityPro.managers

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-city loot bans. A banned player may still walk through the city but cannot
 * open its containers (enforced in [io.github.darkstarworks.ancientCityPro.listeners.ContainerLootListener]).
 *
 * Banned UUIDs are mirrored in memory (preloaded at startup) so the loot hot path
 * can check synchronously without a DB round-trip; full records are read from the
 * DB for listing.
 */
class BanManager(private val plugin: AncientCityPro) {

    data class BanRecord(val playerUuid: UUID, val reason: String?, val bannedBy: UUID?, val bannedAt: Long)

    /** cityId -> banned player UUIDs (fast synchronous membership). */
    private val cache = ConcurrentHashMap<Int, MutableSet<UUID>>()

    suspend fun preload() = withContext(Dispatchers.IO) {
        cache.clear()
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("SELECT city_id, player_uuid FROM city_bans").use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val uuid = runCatching { UUID.fromString(rs.getString("player_uuid")) }.getOrNull() ?: continue
                            cache.getOrPut(rs.getInt("city_id")) { ConcurrentHashMap.newKeySet() }.add(uuid)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Bans] preload failed: ${e.message}")
        }
        plugin.logger.info("Loaded ${cache.values.sumOf { it.size }} city loot ban(s)")
    }

    /** Synchronous: is [player] loot-banned from [cityId]? */
    fun isBanned(cityId: Int, player: UUID): Boolean = cache[cityId]?.contains(player) == true

    suspend fun ban(cityId: Int, player: UUID, reason: String?, by: UUID?): Boolean = withContext(Dispatchers.IO) {
        val sql = if (plugin.databaseManager.databaseType == DatabaseManager.DatabaseType.MYSQL) {
            "INSERT INTO city_bans (city_id, player_uuid, reason, banned_by, banned_at) VALUES (?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE reason = VALUES(reason), banned_by = VALUES(banned_by), banned_at = VALUES(banned_at)"
        } else {
            "INSERT INTO city_bans (city_id, player_uuid, reason, banned_by, banned_at) VALUES (?,?,?,?,?) " +
                "ON CONFLICT(city_id, player_uuid) DO UPDATE SET reason = excluded.reason, banned_by = excluded.banned_by, banned_at = excluded.banned_at"
        }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setString(2, player.toString())
                    stmt.setString(3, reason)
                    stmt.setString(4, by?.toString())
                    stmt.setLong(5, System.currentTimeMillis())
                    stmt.executeUpdate()
                }
            }
            cache.getOrPut(cityId) { ConcurrentHashMap.newKeySet() }.add(player)
            true
        } catch (e: Exception) {
            plugin.logger.warning("[Bans] ban failed (city $cityId / $player): ${e.message}")
            false
        }
    }

    suspend fun unban(cityId: Int, player: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM city_bans WHERE city_id = ? AND player_uuid = ?").use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setString(2, player.toString())
                    val removed = stmt.executeUpdate() > 0
                    if (removed) cache[cityId]?.remove(player)
                    removed
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Bans] unban failed (city $cityId / $player): ${e.message}")
            false
        }
    }

    /** Full ban records for a city (for listing in the GUI / command). */
    suspend fun listBans(cityId: Int): List<BanRecord> = withContext(Dispatchers.IO) {
        val out = mutableListOf<BanRecord>()
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT player_uuid, reason, banned_by, banned_at FROM city_bans WHERE city_id = ? ORDER BY banned_at DESC"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val uuid = runCatching { UUID.fromString(rs.getString("player_uuid")) }.getOrNull() ?: continue
                            out.add(
                                BanRecord(
                                    uuid,
                                    rs.getString("reason"),
                                    rs.getString("banned_by")?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                                    rs.getLong("banned_at"),
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Bans] listBans($cityId) failed: ${e.message}")
        }
        out
    }
}
