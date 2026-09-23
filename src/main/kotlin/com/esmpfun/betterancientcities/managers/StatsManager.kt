package com.esmpfun.betterancientcities.managers

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Per-(city, player) counts for the player data screen, one upserted row per pair. */
class StatsManager(private val plugin: BetterAncientCities) {

    data class CityPlayerStats(
        val playerUuid: UUID,
        val containersLooted: Int,
        val griefAttempts: Int,
        val timeMs: Long,
        val deaths: Int,
        val lastSeen: Long,
    )

    fun incrementLooted(cityId: Int, player: UUID) = bump(cityId, player, "containers_looted", 1L)
    fun incrementGrief(cityId: Int, player: UUID) = bump(cityId, player, "grief_attempts", 1L)
    fun incrementDeaths(cityId: Int, player: UUID) = bump(cityId, player, "deaths", 1L)

    fun addTime(cityId: Int, player: UUID, deltaMs: Long) {
        if (deltaMs > 0) bump(cityId, player, "time_ms", deltaMs)
    }

    /** Writes time spent right away and waits. For shutdown, when background work is cancelled. */
    fun addTimeBlocking(cityId: Int, player: UUID, deltaMs: Long) {
        if (deltaMs > 0 && plugin.databaseManager.isOpen) write(cityId, player, "time_ms", deltaMs)
    }

    private fun bump(cityId: Int, player: UUID, column: String, delta: Long) {
        plugin.launchAsync { withContext(Dispatchers.IO) { write(cityId, player, column, delta) } }
    }

    /** [column] is always one of the constants above, never player input. */
    private fun write(cityId: Int, player: UUID, column: String, delta: Long) {
        val sql = if (plugin.databaseManager.databaseType == DatabaseManager.DatabaseType.MYSQL) {
            "INSERT INTO city_player_stats (city_id, player_uuid, $column, last_seen) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE $column = $column + VALUES($column), last_seen = VALUES(last_seen)"
        } else {
            "INSERT INTO city_player_stats (city_id, player_uuid, $column, last_seen) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(city_id, player_uuid) DO UPDATE SET $column = $column + excluded.$column, last_seen = excluded.last_seen"
        }
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setString(2, player.toString())
                    stmt.setLong(3, delta)
                    stmt.setLong(4, System.currentTimeMillis())
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Could not save player stats for city #$cityId: ${e.message}")
        }
    }

    /** Every player who has done something in [cityId], most recently seen first. */
    suspend fun listForCity(cityId: Int): List<CityPlayerStats> = withContext(Dispatchers.IO) {
        val out = mutableListOf<CityPlayerStats>()
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT player_uuid, containers_looted, grief_attempts, time_ms, deaths, last_seen " +
                        "FROM city_player_stats WHERE city_id = ? ORDER BY last_seen DESC"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val uuid = runCatching { UUID.fromString(rs.getString("player_uuid")) }.getOrNull() ?: continue
                            out.add(
                                CityPlayerStats(
                                    playerUuid = uuid,
                                    containersLooted = rs.getInt("containers_looted"),
                                    griefAttempts = rs.getInt("grief_attempts"),
                                    timeMs = rs.getLong("time_ms"),
                                    deaths = rs.getInt("deaths"),
                                    lastSeen = rs.getLong("last_seen"),
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Could not load player stats for city #$cityId: ${e.message}")
        }
        out
    }
}
