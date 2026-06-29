package io.github.darkstarworks.ancientCityPro

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
        launchAsync {
            try {
                // TODO(discovery): init DatabaseManager + CityStore, preload city cache.
                // TODO(discovery): register CityDiscoveryListener + run startup sweep.
                scheduler.runTask(Runnable {
                    // TODO: registerCommand / registerListeners here (main thread).
                    isReady = true
                    logger.info("AncientCityPro ready.")
                })
            } catch (e: Exception) {
                logger.severe("AncientCityPro failed to initialize: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun onDisable() {
        isReady = false
        scheduler.cancelAllTasks()
        pluginScope.cancel()
        logger.info("AncientCityPro disabled.")
    }
}
