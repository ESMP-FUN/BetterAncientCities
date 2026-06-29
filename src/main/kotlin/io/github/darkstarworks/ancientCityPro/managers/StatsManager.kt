package io.github.darkstarworks.ancientCityPro.managers

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Per-(city, player) admin statistics: containers looted, denied griefing
 * attempts, time spent in the city, and deaths. One upserted row per pair in
 * `city_player_stats`. Powers the GUI player-data view; cascade-deleted with the
 * city.
 */
class StatsManager(private val plugin: AncientCityPro) {

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

    /**
     * Fire-and-forget upsert that adds [delta] to [column] for the (city, player)
     * row, creating it if absent, and stamps last_seen. [column] is an internal
     * constant (never user input), so string-building it is safe.
     */
    private fun bump(cityId: Int, player: UUID, column: String, delta: Long) {
        plugin.launchAsync {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
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
                            stmt.setLong(4, now)
                            stmt.executeUpdate()
                        }
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("[Stats] bump($column) failed for city $cityId / $player: ${e.message}")
                }
            }
        }
    }

    /** Stats for one player in one city, or null if they've never interacted with it. */
    suspend fun get(cityId: Int, player: UUID): CityPlayerStats? = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT containers_looted, grief_attempts, time_ms, deaths, last_seen " +
                        "FROM city_player_stats WHERE city_id = ? AND player_uuid = ?"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setString(2, player.toString())
                    stmt.executeQuery().use { rs -> if (rs.next()) read(player, rs) else null }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Stats] get failed for city $cityId / $player: ${e.message}")
            null
        }
    }

    /** Every player who has interacted with [cityId], most-recently-seen first. */
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
                            out.add(read(uuid, rs))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Stats] listForCity($cityId) failed: ${e.message}")
        }
        out
    }

    private fun read(uuid: UUID, rs: java.sql.ResultSet) = CityPlayerStats(
        playerUuid = uuid,
        containersLooted = rs.getInt("containers_looted"),
        griefAttempts = rs.getInt("grief_attempts"),
        timeMs = rs.getLong("time_ms"),
        deaths = rs.getInt("deaths"),
        lastSeen = rs.getLong("last_seen"),
    )
}
