package com.esmpfun.betterancientcities

import com.esmpfun.betterancientcities.commands.BacCommand
import com.esmpfun.betterancientcities.database.DatabaseManager
import com.esmpfun.betterancientcities.gui.MenuService
import com.esmpfun.betterancientcities.listeners.CityDeathListener
import com.esmpfun.betterancientcities.listeners.CityDiscoveryListener
import com.esmpfun.betterancientcities.listeners.CityPresenceListener
import com.esmpfun.betterancientcities.listeners.ContainerLootListener
import com.esmpfun.betterancientcities.listeners.ProtectionListener
import com.esmpfun.betterancientcities.managers.CityDiscoveryManager
import com.esmpfun.betterancientcities.managers.CityManager
import com.esmpfun.betterancientcities.managers.BanManager
import com.esmpfun.betterancientcities.managers.ContainerLootManager
import com.esmpfun.betterancientcities.managers.SnapshotManager
import com.esmpfun.betterancientcities.managers.StatsManager
import java.io.File
import com.esmpfun.betterancientcities.scheduler.SchedulerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin

/**
 * BetterAncientCities — turns naturally-generated Ancient Cities into renewable,
 * multiplayer-friendly content.
 *
 * Standalone plugin (no TrialChamberPro dependency). Reuses proven TCP patterns
 * (async-first init, [SchedulerAdapter] Paper/Folia abstraction, per-player
 * container loot, gzip snapshots) by port, not by dependency.
 *
 * Build order: discovery → loot → protection → snapshot. This skeleton wires the
 * foundation (scheduler, readiness gate, coroutine scope); managers land per phase.
 */
class BetterAncientCities : JavaPlugin() {

    /** Flips true once async init completes; gates command/listener execution. */
    @Volatile
    var isReady: Boolean = false
        private set

    /** Paper/Folia scheduler abstraction. */
    lateinit var scheduler: SchedulerAdapter
        private set

    lateinit var databaseManager: DatabaseManager
        private set

    lateinit var cityManager: CityManager
        private set

    lateinit var discoveryManager: CityDiscoveryManager
        private set

    lateinit var containerLootManager: ContainerLootManager
        private set

    lateinit var statsManager: StatsManager
        private set

    lateinit var banManager: BanManager
        private set

    lateinit var presenceListener: com.esmpfun.betterancientcities.listeners.CityPresenceListener
        private set

    lateinit var menuService: MenuService
        private set

    lateinit var snapshotManager: SnapshotManager
        private set

    /** Directory holding per-city snapshot files. */
    val snapshotsDir: File by lazy { File(dataFolder, "snapshots").apply { mkdirs() } }

    // Plugin-wide coroutine scope (SupervisorJob so one failed job doesn't tear
    // down the rest). Cancelled in onDisable.
    private val pluginJob = SupervisorJob()
    val pluginScope = CoroutineScope(Dispatchers.Default + pluginJob)

    /** Launch an async coroutine on the plugin scope. */
    fun launchAsync(block: suspend CoroutineScope.() -> Unit): Job =
        pluginScope.launch(block = block)

    override fun onEnable() {
        migrateLegacyDataFolder()
        saveDefaultConfig()
        scheduler = SchedulerAdapter.create(this)

        logger.info("BetterAncientCities starting on ${if (scheduler.isFolia) "Folia" else "Paper"}...")

        // Async-first init: heavy setup (DB, caches, discovery sweep) runs off the
        // main thread; listeners/commands register on the main thread once ready.
        databaseManager = DatabaseManager(this)
        cityManager = CityManager(this)
        discoveryManager = CityDiscoveryManager(this)
        containerLootManager = ContainerLootManager(this)
        statsManager = StatsManager(this)
        banManager = BanManager(this)
        menuService = MenuService(this)
        snapshotManager = SnapshotManager(this)

        launchAsync {
            try {
                databaseManager.initialize()
                cityManager.preload()
                banManager.preload()
                scheduler.runTask(Runnable {
                    server.pluginManager.registerEvents(CityDiscoveryListener(this@BetterAncientCities), this@BetterAncientCities)
                    server.pluginManager.registerEvents(ContainerLootListener(this@BetterAncientCities), this@BetterAncientCities)
                    server.pluginManager.registerEvents(ProtectionListener(this@BetterAncientCities), this@BetterAncientCities)
                    server.pluginManager.registerEvents(CityDeathListener(this@BetterAncientCities), this@BetterAncientCities)
                    presenceListener = CityPresenceListener(this@BetterAncientCities)
                    server.pluginManager.registerEvents(presenceListener, this@BetterAncientCities)
                    // Central GUI dispatcher — routes only BaseHolder inventories.
                    server.pluginManager.registerEvents(
                        com.esmpfun.betterancientcities.gui.framework.VcGuiListener(), this@BetterAncientCities
                    )
                    getCommand("ancient")?.let {
                        val executor = BacCommand(this@BetterAncientCities)
                        it.setExecutor(executor)
                        it.tabCompleter = executor
                    }
                    // Update checking (PluginPulse). Config in pluginpulse.yml;
                    // server owners can override mode/interval via an `update:`
                    // block in config.yml.
                    io.github.darkstarworks.pluginpulse.PluginPulse.bootstrap(this@BetterAncientCities)

                    // Anonymous usage metrics (bStats). Opt-out via metrics.enabled
                    // in config.yml or the global plugins/bStats/config.yml.
                    val metricsStatus =
                        com.esmpfun.betterancientcities.integrations.MetricsService.init(this@BetterAncientCities)
                    logger.info("bStats Metrics: $metricsStatus")

                    isReady = true
                    logger.info("BetterAncientCities ready.")
                    // Catch cities in chunks already resident at enable (the live
                    // ChunkLoadEvent covers everything loaded afterward).
                    discoveryManager.startupSweep()
                })
            } catch (e: Exception) {
                logger.severe("BetterAncientCities failed to initialize: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun onDisable() {
        isReady = false
        io.github.darkstarworks.pluginpulse.PluginPulse.shutdown(this)
        if (::presenceListener.isInitialized) presenceListener.flushAll()
        scheduler.cancelAllTasks()
        pluginScope.cancel()
        if (::databaseManager.isInitialized) databaseManager.close()
        logger.info("BetterAncientCities disabled.")
    }

    /** One-time migration from the pre-2.0 plugin name (plugins/AncientCityPro/). */
    private fun migrateLegacyDataFolder() {
        try {
            if (dataFolder.exists()) return
            val legacy = java.io.File(dataFolder.parentFile, "AncientCityPro")
            if (!legacy.isDirectory) return
            logger.info("Migrating data from plugins/AncientCityPro/ to plugins/${dataFolder.name}/ ...")
            legacy.walkTopDown().forEach { src ->
                val dest = java.io.File(dataFolder, src.relativeTo(legacy).path)
                if (src.isDirectory) dest.mkdirs() else src.copyTo(dest, overwrite = false)
            }
            logger.info("Migration complete — the old folder was kept as a backup.")
        } catch (e: Exception) {
            logger.severe("Legacy data-folder migration failed: ${e.message} — migrate manually and restart.")
        }
    }
}
