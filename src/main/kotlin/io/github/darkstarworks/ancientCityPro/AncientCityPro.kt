package io.github.darkstarworks.ancientCityPro

import io.github.darkstarworks.ancientCityPro.commands.AcpCommand
import io.github.darkstarworks.ancientCityPro.database.DatabaseManager
import io.github.darkstarworks.ancientCityPro.listeners.CityDeathListener
import io.github.darkstarworks.ancientCityPro.listeners.CityDiscoveryListener
import io.github.darkstarworks.ancientCityPro.listeners.CityPresenceListener
import io.github.darkstarworks.ancientCityPro.listeners.ContainerLootListener
import io.github.darkstarworks.ancientCityPro.listeners.ProtectionListener
import io.github.darkstarworks.ancientCityPro.managers.CityDiscoveryManager
import io.github.darkstarworks.ancientCityPro.managers.CityManager
import io.github.darkstarworks.ancientCityPro.managers.ContainerLootManager
import io.github.darkstarworks.ancientCityPro.managers.StatsManager
import io.github.darkstarworks.ancientCityPro.scheduler.SchedulerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin

/**
 * AncientCityPro — turns naturally-generated Ancient Cities into renewable,
 * multiplayer-friendly content.
 *
 * Standalone plugin (no TrialChamberPro dependency). Reuses proven TCP patterns
 * (async-first init, [SchedulerAdapter] Paper/Folia abstraction, per-player
 * container loot, gzip snapshots) by port, not by dependency.
 *
 * Build order: discovery → loot → protection → snapshot. This skeleton wires the
 * foundation (scheduler, readiness gate, coroutine scope); managers land per phase.
 */
class AncientCityPro : JavaPlugin() {

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

    lateinit var presenceListener: io.github.darkstarworks.ancientCityPro.listeners.CityPresenceListener
        private set

    // Plugin-wide coroutine scope (SupervisorJob so one failed job doesn't tear
    // down the rest). Cancelled in onDisable.
    private val pluginJob = SupervisorJob()
    val pluginScope = CoroutineScope(Dispatchers.Default + pluginJob)

    /** Launch an async coroutine on the plugin scope. */
    fun launchAsync(block: suspend CoroutineScope.() -> Unit): Job =
        pluginScope.launch(block = block)

    override fun onEnable() {
        saveDefaultConfig()
        scheduler = SchedulerAdapter.create(this)

        logger.info("AncientCityPro starting on ${if (scheduler.isFolia) "Folia" else "Paper"}...")

        // Async-first init: heavy setup (DB, caches, discovery sweep) runs off the
        // main thread; listeners/commands register on the main thread once ready.
        databaseManager = DatabaseManager(this)
        cityManager = CityManager(this)
        discoveryManager = CityDiscoveryManager(this)
        containerLootManager = ContainerLootManager(this)
        statsManager = StatsManager(this)

        launchAsync {
            try {
                databaseManager.initialize()
                cityManager.preload()
                scheduler.runTask(Runnable {
                    server.pluginManager.registerEvents(CityDiscoveryListener(this@AncientCityPro), this@AncientCityPro)
                    server.pluginManager.registerEvents(ContainerLootListener(this@AncientCityPro), this@AncientCityPro)
                    server.pluginManager.registerEvents(ProtectionListener(this@AncientCityPro), this@AncientCityPro)
                    server.pluginManager.registerEvents(CityDeathListener(this@AncientCityPro), this@AncientCityPro)
                    presenceListener = CityPresenceListener(this@AncientCityPro)
                    server.pluginManager.registerEvents(presenceListener, this@AncientCityPro)
                    getCommand("ancientcity")?.let {
                        val executor = AcpCommand(this@AncientCityPro)
                        it.setExecutor(executor)
                        it.tabCompleter = executor
                    }
                    isReady = true
                    logger.info("AncientCityPro ready.")
                    // Catch cities in chunks already resident at enable (the live
                    // ChunkLoadEvent covers everything loaded afterward).
                    discoveryManager.startupSweep()
                })
            } catch (e: Exception) {
                logger.severe("AncientCityPro failed to initialize: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun onDisable() {
        isReady = false
        if (::presenceListener.isInitialized) presenceListener.flushAll()
        scheduler.cancelAllTasks()
        pluginScope.cancel()
        if (::databaseManager.isInitialized) databaseManager.close()
        logger.info("AncientCityPro disabled.")
    }
}
