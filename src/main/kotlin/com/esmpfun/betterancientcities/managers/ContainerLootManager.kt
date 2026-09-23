package com.esmpfun.betterancientcities.managers

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Per-player city container contents plus the shared template each first open is
 * cloned from. The real block is never touched.
 *
 * Writes go through [pending] first: the close handler records the contents
 * synchronously, reads check it before the database, and an entry only leaves once
 * its row is committed. A reopen racing the save, or a failed save, therefore never
 * serves stale contents. [writeLock] keeps a queued write from landing after a clear.
 */
class ContainerLootManager(private val plugin: BetterAncientCities) {

    data class ContainerPos(val x: Int, val y: Int, val z: Int)

    /** A player's copy, or the shared template when [player] is null. */
    private data class Key(val cityId: Int, val pos: ContainerPos, val player: UUID?)

    sealed interface Load {
        data object Missing : Load
        data object Failed : Load
        /** The row exists but can no longer be read, for example after a downgrade. */
        data object Damaged : Load
        class Found(val contents: Array<ItemStack?>) : Load
    }

    private val pending = ConcurrentHashMap<Key, Array<ItemStack?>>()
    private val writeLock = ReentrantLock()

    private val mysql get() = plugin.databaseManager.databaseType == DatabaseManager.DatabaseType.MYSQL

    suspend fun loadCopy(cityId: Int, pos: ContainerPos, player: UUID): Load =
        load(Key(cityId, pos, player))

    suspend fun loadTemplate(cityId: Int, pos: ContainerPos): Load =
        load(Key(cityId, pos, null))

    private suspend fun load(key: Key): Load {
        pending[key]?.let { return Load.Found(cloneAll(it)) }
        return withContext(Dispatchers.IO) {
            pending[key]?.let { return@withContext Load.Found(cloneAll(it)) }
            try {
                val sql = if (key.player != null)
                    "SELECT contents FROM player_container_loot WHERE city_id = ? AND x = ? AND y = ? AND z = ? AND player_uuid = ?"
                else
                    "SELECT contents FROM container_template WHERE city_id = ? AND x = ? AND y = ? AND z = ?"
                plugin.databaseManager.connection.use { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        bindKey(stmt, key)
                        stmt.executeQuery().use { rs ->
                            if (!rs.next()) Load.Missing
                            else decodeContents(rs.getString("contents"))?.let { Load.Found(it) } ?: Load.Damaged
                        }
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("Could not read a city chest at ${key.pos.x}, ${key.pos.y}, ${key.pos.z}: ${e.message}")
                Load.Failed
            }
        }
    }

    /** Records a player's copy now and writes it in the background. */
    fun queueCopySave(cityId: Int, pos: ContainerPos, player: UUID, contents: Array<ItemStack?>) =
        queue(Key(cityId, pos, player), contents)

    /** Records an edited template now and writes it in the background. */
    fun queueTemplateSave(cityId: Int, pos: ContainerPos, contents: Array<ItemStack?>) =
        queue(Key(cityId, pos, null), contents)

    private fun queue(key: Key, contents: Array<ItemStack?>) {
        pending[key] = contents
        plugin.launchAsync { withContext(Dispatchers.IO) { write(key) } }
    }

    private fun write(key: Key): Boolean = writeLock.withLock {
        val snapshot = pending[key] ?: return true
        val ok = try {
            val encoded = encodeContents(snapshot)
            plugin.databaseManager.connection.use { conn ->
                if (key.player != null) {
                    val sql = if (mysql)
                        "INSERT INTO player_container_loot (city_id, x, y, z, player_uuid, contents, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE contents = VALUES(contents), updated_at = VALUES(updated_at)"
                    else
                        "INSERT INTO player_container_loot (city_id, x, y, z, player_uuid, contents, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT(city_id, x, y, z, player_uuid) DO UPDATE SET contents = excluded.contents, updated_at = excluded.updated_at"
                    conn.prepareStatement(sql).use { stmt ->
                        bindKey(stmt, key)
                        stmt.setString(6, encoded)
                        stmt.setLong(7, System.currentTimeMillis())
                        stmt.executeUpdate()
                    }
                } else {
                    conn.prepareStatement(
                        "UPDATE container_template SET contents = ?, updated_at = ? WHERE city_id = ? AND x = ? AND y = ? AND z = ?"
                    ).use { stmt ->
                        stmt.setString(1, encoded)
                        stmt.setLong(2, System.currentTimeMillis())
                        stmt.setInt(3, key.cityId)
                        stmt.setInt(4, key.pos.x); stmt.setInt(5, key.pos.y); stmt.setInt(6, key.pos.z)
                        stmt.executeUpdate()
                    }
                }
            }
            true
        } catch (e: Exception) {
            plugin.logger.warning(
                "Could not save a city chest at ${key.pos.x}, ${key.pos.y}, ${key.pos.z} (kept in memory, will try again): ${e.message}"
            )
            false
        }
        if (ok) pending.remove(key, snapshot)
        ok
    }

    /**
     * Writes every queued change and waits for it. For plugin shutdown, after the
     * background writers can no longer be relied on.
     */
    fun flushBlocking() {
        if (!plugin.databaseManager.isOpen) return
        val keys = pending.keys.toList()
        val failed = keys.count { !write(it) }
        if (failed > 0) plugin.logger.severe("$failed city chest(s) could not be saved before shutdown. See the warnings above.")
    }

    /**
     * Stores [rolled] as the template unless one already exists (or [replace] is set),
     * and returns the stored template, so two players opening a new chest together
     * share one roll.
     */
    suspend fun insertTemplate(
        cityId: Int,
        pos: ContainerPos,
        rolled: Array<ItemStack?>,
        material: Material,
        replace: Boolean = false,
    ): Array<ItemStack?>? {
        val inserted = withContext(Dispatchers.IO) {
            val verb = when {
                replace -> if (mysql) "REPLACE INTO" else "INSERT OR REPLACE INTO"
                else -> if (mysql) "INSERT IGNORE INTO" else "INSERT OR IGNORE INTO"
            }
            val sql = "$verb container_template (city_id, x, y, z, contents, material, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
            try {
                plugin.databaseManager.connection.use { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setInt(1, cityId)
                        stmt.setInt(2, pos.x); stmt.setInt(3, pos.y); stmt.setInt(4, pos.z)
                        stmt.setString(5, encodeContents(rolled))
                        stmt.setString(6, material.name)
                        stmt.setLong(7, System.currentTimeMillis())
                        stmt.executeUpdate() > 0
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("Could not save the loot for a city chest at ${pos.x}, ${pos.y}, ${pos.z}: ${e.message}")
                return@withContext null
            }
        } ?: return null
        if (inserted) return rolled
        return (loadTemplate(cityId, pos) as? Load.Found)?.contents
    }

    data class TemplateRow(val pos: ContainerPos, val contents: Array<ItemStack?>, val material: Material)

    suspend fun listTemplates(cityId: Int): List<TemplateRow> = withContext(Dispatchers.IO) {
        val out = mutableListOf<TemplateRow>()
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT x, y, z, contents, material FROM container_template WHERE city_id = ? ORDER BY x, y, z"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val pos = ContainerPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"))
                            val contents = pending[Key(cityId, pos, null)]?.let(::cloneAll)
                                ?: decodeContents(rs.getString("contents")) ?: arrayOfNulls(0)
                            val material = Material.matchMaterial(rs.getString("material")) ?: Material.CHEST
                            out.add(TemplateRow(pos, contents, material))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Could not list the chests of city #$cityId: ${e.message}")
        }
        out
    }

    data class PlayerCopy(val pos: ContainerPos, val contents: Array<ItemStack?>)

    suspend fun listPlayerCopies(cityId: Int, player: UUID): List<PlayerCopy> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<ContainerPos, Array<ItemStack?>>()
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT x, y, z, contents FROM player_container_loot WHERE city_id = ? AND player_uuid = ? ORDER BY x, y, z"
                ).use { stmt ->
                    stmt.setInt(1, cityId)
                    stmt.setString(2, player.toString())
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val pos = ContainerPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"))
                            found[pos] = decodeContents(rs.getString("contents")) ?: arrayOfNulls(0)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Could not list what a player looted in city #$cityId: ${e.message}")
        }
        for ((key, contents) in pending) {
            if (key.cityId == cityId && key.player == player) found[key.pos] = cloneAll(contents)
        }
        found.map { (pos, contents) -> PlayerCopy(pos, contents) }
    }

    /** Drops every player's copies for a city so its loot is fresh. Templates are kept. */
    suspend fun clearCity(cityId: Int): Int = clear(
        "DELETE FROM player_container_loot WHERE city_id = ?",
        { it.cityId == cityId && it.player != null },
    ) { it.setInt(1, cityId) }

    /** Drops one player's copies for a city so they loot it fresh. */
    suspend fun clearPlayer(cityId: Int, player: UUID): Int = clear(
        "DELETE FROM player_container_loot WHERE city_id = ? AND player_uuid = ?",
        { it.cityId == cityId && it.player == player },
    ) { it.setInt(1, cityId); it.setString(2, player.toString()) }

    /** Forgets queued changes for a city that is being deleted. */
    fun forgetCity(cityId: Int) = writeLock.withLock {
        pending.keys.removeIf { it.cityId == cityId }
    }

    private suspend fun clear(
        sql: String,
        matches: (Key) -> Boolean,
        bind: (java.sql.PreparedStatement) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        writeLock.withLock {
            pending.keys.removeIf(matches)
            try {
                plugin.databaseManager.connection.use { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        bind(stmt)
                        stmt.executeUpdate()
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("Could not clear saved chest contents: ${e.message}")
                0
            }
        }
    }

    private fun bindKey(stmt: java.sql.PreparedStatement, key: Key) {
        stmt.setInt(1, key.cityId)
        stmt.setInt(2, key.pos.x); stmt.setInt(3, key.pos.y); stmt.setInt(4, key.pos.z)
        if (key.player != null) stmt.setString(5, key.player.toString())
    }

    private fun cloneAll(contents: Array<ItemStack?>): Array<ItemStack?> =
        Array(contents.size) { contents[it]?.clone() }

    // Base64 of a length-prefixed list of ItemStack.serializeAsBytes() blobs; -1 marks an empty slot.

    fun encodeContents(contents: Array<ItemStack?>): String {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(contents.size)
            for (item in contents) {
                if (item == null || item.type.isAir) {
                    out.writeInt(-1)
                } else {
                    val bytes = item.serializeAsBytes()
                    out.writeInt(bytes.size)
                    out.write(bytes)
                }
            }
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    fun decodeContents(encoded: String): Array<ItemStack?>? = try {
        val bytes = Base64.getDecoder().decode(encoded)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val size = input.readInt()
            require(size in 0..128) { "implausible container size $size" }
            Array(size) {
                val len = input.readInt()
                if (len < 0) null
                else {
                    val buf = ByteArray(len)
                    input.readFully(buf)
                    ItemStack.deserializeBytes(buf)
                }
            }
        }
    } catch (e: Exception) {
        plugin.logger.warning("Skipped a damaged saved chest: ${e.message}")
        null
    }
}
