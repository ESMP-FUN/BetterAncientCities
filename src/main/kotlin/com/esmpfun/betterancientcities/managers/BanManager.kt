package com.esmpfun.betterancientcities.managers

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-city loot bans. A banned player can walk through the city but not open its
 * containers. Banned UUIDs are kept in memory so the chest check never waits on
 * the database.
 */
class BanManager(private val plugin: BetterAncientCities) {

    data class BanRecord(val playerUuid: UUID, val reason: String?, val bannedBy: UUID?, val bannedAt: Long)

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
            plugin.logger.warning("Could not load the loot bans: ${e.message}")
        }
    }

    fun isBanned(cityId: Int, player: UUID): Boolean = cache[cityId]?.contains(player) == true

    fun forgetCity(cityId: Int) {
        cache.remove(cityId)
    }

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
                    stmt.setString(3, reason?.take(255))
                    stmt.setString(4, by?.toString())
                    stmt.setLong(5, System.currentTimeMillis())
                    stmt.executeUpdate()
                }
            }
            cache.getOrPut(cityId) { ConcurrentHashMap.newKeySet() }.add(player)
            true
        } catch (e: Exception) {
            plugin.logger.warning("Could not save a loot ban for city #$cityId: ${e.message}")
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
            plugin.logger.warning("Could not lift a loot ban for city #$cityId: ${e.message}")
            false
        }
    }

    /** Full ban records for a city, newest first. */
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
            plugin.logger.warning("Could not list the loot bans of city #$cityId: ${e.message}")
        }
        out
    }
}
