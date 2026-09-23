package com.esmpfun.betterancientcities.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.esmpfun.betterancientcities.BetterAncientCities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.configuration.file.FileConfiguration
import java.io.File
import java.sql.Connection

/** HikariCP-pooled database access, SQLite (default) or MySQL. */
class DatabaseManager(private val plugin: BetterAncientCities) {

    private lateinit var dataSource: HikariDataSource
    private var _databaseType: DatabaseType = DatabaseType.SQLITE

    val databaseType: DatabaseType get() = _databaseType

    enum class DatabaseType { SQLITE, MYSQL }

    /** A pooled connection. Always use inside `.use { }`. */
    val connection: Connection get() = dataSource.connection

    val isOpen: Boolean get() = ::dataSource.isInitialized && !dataSource.isClosed

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val config = plugin.config
        _databaseType = try {
            DatabaseType.valueOf(config.getString("database.type", "SQLITE")!!.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            plugin.logger.warning("database.type in config.yml must be sqlite or mysql. Using sqlite.")
            DatabaseType.SQLITE
        }

        dataSource = when (_databaseType) {
            DatabaseType.SQLITE -> createSQLiteDataSource()
            DatabaseType.MYSQL -> createMySQLDataSource(config)
        }
        plugin.logger.info("Database ready (${_databaseType.name.lowercase()})")
        createTables()
        migrate()
    }

    private fun createSQLiteDataSource(): HikariDataSource {
        val dbFile = File(plugin.dataFolder, "database.db")
        plugin.dataFolder.mkdirs()
        return HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}?foreign_keys=on"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 5
            minimumIdle = 1
            connectionTimeout = 30000
            idleTimeout = 300000
            maxLifetime = 600000
            connectionTestQuery = "SELECT 1"
            poolName = "BetterAncientCities-SQLite"
            addDataSourceProperty("journal_mode", "WAL")
            addDataSourceProperty("synchronous", "NORMAL")
            addDataSourceProperty("busy_timeout", "5000")
        })
    }

    private fun createMySQLDataSource(config: FileConfiguration): HikariDataSource {
        val host = config.getString("database.mysql.host", "localhost")!!
        val port = config.getInt("database.mysql.port", 3306)
        val database = config.getString("database.mysql.database", "ancientcitypro")!!
        val username = config.getString("database.mysql.username", "root")!!
        val password = config.getString("database.mysql.password", "")!!
        return HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC"
            driverClassName = "com.mysql.cj.jdbc.Driver"
            this.username = username
            this.password = password
            maximumPoolSize = config.getInt("database.mysql.pool-size", 10).coerceIn(2, 50)
            connectionTestQuery = "SELECT 1"
            poolName = "BetterAncientCities-MySQL"
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
        })
    }

    private fun createTables() {
        val mysql = _databaseType == DatabaseType.MYSQL
        val autoId = if (mysql) "INT AUTO_INCREMENT PRIMARY KEY" else "INTEGER PRIMARY KEY AUTOINCREMENT"
        // MySQL TEXT stops at 64 KB, which a chest of full shulker boxes can pass.
        val bigText = if (mysql) "MEDIUMTEXT" else "TEXT"
        connection.use { conn ->
            conn.createStatement().use { stmt ->
                // (origin_x/y/z) is the structure's min corner, the one identity every
                // chunk of the city agrees on.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS cities (
                        id $autoId,
                        world VARCHAR(64) NOT NULL,
                        min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL,
                        max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL,
                        origin_x INT NOT NULL, origin_y INT NOT NULL, origin_z INT NOT NULL,
                        created_at BIGINT NOT NULL,
                        last_reset BIGINT,
                        snapshot_file VARCHAR(255),
                        loot_cycle_start BIGINT,
                        approved INT NOT NULL DEFAULT 0,
                        UNIQUE (world, origin_x, origin_y, origin_z)
                    )
                    """.trimIndent()
                )
                // MySQL has no CREATE INDEX IF NOT EXISTS, so there the index is declared inline.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS city_pieces (
                        id $autoId,
                        city_id INT NOT NULL,
                        min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL,
                        max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL,
                        ${if (mysql) "INDEX idx_city_pieces_city (city_id)," else ""}
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                if (!mysql) stmt.execute("CREATE INDEX IF NOT EXISTS idx_city_pieces_city ON city_pieces(city_id)")

                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS player_container_loot (
                        city_id INT NOT NULL,
                        x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        contents $bigText NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (city_id, x, y, z, player_uuid),
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS city_player_stats (
                        city_id INT NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        containers_looted INT NOT NULL DEFAULT 0,
                        grief_attempts INT NOT NULL DEFAULT 0,
                        time_ms BIGINT NOT NULL DEFAULT 0,
                        deaths INT NOT NULL DEFAULT 0,
                        last_seen BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (city_id, player_uuid),
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS city_bans (
                        city_id INT NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        reason VARCHAR(255),
                        banned_by VARCHAR(36),
                        banned_at BIGINT NOT NULL,
                        PRIMARY KEY (city_id, player_uuid),
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                // The shared contents every first-open copy is cloned from. Kept across
                // loot refreshes so edits made by staff stick.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS container_template (
                        city_id INT NOT NULL,
                        x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
                        contents $bigText NOT NULL,
                        material VARCHAR(64) NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (city_id, x, y, z),
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS plugin_meta (
                        meta_key VARCHAR(64) NOT NULL PRIMARY KEY,
                        meta_value VARCHAR(255) NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }

    private fun migrate() {
        connection.use { conn ->
            val version = conn.prepareStatement("SELECT meta_value FROM plugin_meta WHERE meta_key = 'schema_version'").use { stmt ->
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1).toIntOrNull() ?: 1 else 1 }
            }
            if (version >= SCHEMA_VERSION) return

            conn.autoCommit = false
            try {
                if (version < 2) {
                    // Cities saved before schema 2 had their far edges stored one block
                    // short (the structure box max is already inclusive).
                    conn.createStatement().use { stmt ->
                        val cities = stmt.executeUpdate("UPDATE cities SET max_x = max_x + 1, max_y = max_y + 1, max_z = max_z + 1")
                        stmt.executeUpdate("UPDATE city_pieces SET max_x = max_x + 1, max_y = max_y + 1, max_z = max_z + 1")
                        if (cities > 0) plugin.logger.info("Corrected the edges of $cities saved Ancient ${if (cities == 1) "City" else "Cities"}. Nothing to do on your end.")
                    }
                }
                val upsert = if (_databaseType == DatabaseType.MYSQL)
                    "INSERT INTO plugin_meta (meta_key, meta_value) VALUES ('schema_version', ?) ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)"
                else
                    "INSERT INTO plugin_meta (meta_key, meta_value) VALUES ('schema_version', ?) ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value"
                conn.prepareStatement(upsert).use { stmt ->
                    stmt.setString(1, SCHEMA_VERSION.toString())
                    stmt.executeUpdate()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }

            // MySQL cannot roll back a column change, so it runs on its own after the commit.
            if (version < 2 && _databaseType == DatabaseType.MYSQL) {
                try {
                    conn.createStatement().use { stmt ->
                        stmt.execute("ALTER TABLE player_container_loot MODIFY contents MEDIUMTEXT NOT NULL")
                        stmt.execute("ALTER TABLE container_template MODIFY contents MEDIUMTEXT NOT NULL")
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Could not make room for bigger chest contents in MySQL: ${e.message}")
                }
            }
        }
    }

    fun close() {
        if (isOpen) {
            dataSource.close()
            plugin.logger.info("Database closed")
        }
    }

    private companion object {
        const val SCHEMA_VERSION = 2
    }
}
