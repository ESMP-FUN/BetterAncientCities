package com.esmpfun.betterancientcities

import com.esmpfun.betterancientcities.commands.BacCommand
import com.esmpfun.betterancientcities.database.DatabaseManager
import com.esmpfun.betterancientcities.gui.MenuService
import com.esmpfun.betterancientcities.gui.framework.VcGuiListener
import com.esmpfun.betterancientcities.integrations.MetricsService
import com.esmpfun.betterancientcities.listeners.CityDeathListener
import com.esmpfun.betterancientcities.listeners.CityDiscoveryListener
import com.esmpfun.betterancientcities.listeners.CityPresenceListener
import com.esmpfun.betterancientcities.listeners.ContainerLootListener
import com.esmpfun.betterancientcities.listeners.ProtectionListener
import com.esmpfun.betterancientcities.managers.BanManager
import com.esmpfun.betterancientcities.managers.CityDiscoveryManager
import com.esmpfun.betterancientcities.managers.CityManager
import com.esmpfun.betterancientcities.managers.ContainerLootManager
import com.esmpfun.betterancientcities.managers.SnapshotManager
import com.esmpfun.betterancientcities.managers.StatsManager
import com.esmpfun.betterancientcities.scheduler.SchedulerAdapter
import io.github.darkstarworks.pluginpulse.UpdateMode
import io.github.darkstarworks.pluginpulse.UpdateSubcommand
import io.github.darkstarworks.pluginpulse.Updater
import io.github.darkstarworks.pluginpulse.source.GitHubReleasesSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.time.Duration

class BetterAncientCities : JavaPlugin() {

    /** True once the database is up and listeners are registered. */
    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    var startupFailed: Boolean = false
        private set

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
    lateinit var presenceListener: CityPresenceListener
        private set
    lateinit var menuService: MenuService
        private set
    lateinit var snapshotManager: SnapshotManager
        private set

    private var containerLootListener: ContainerLootListener? = null
    private var updater: Updater? = null
    private var updateSubcommand: UpdateSubcommand? = null

    val snapshotsDir: File by lazy { File(dataFolder, "snapshots").apply { mkdirs() } }

    private val pluginJob = SupervisorJob()
    val pluginScope = CoroutineScope(Dispatchers.Default + pluginJob)

    fun launchAsync(block: suspend CoroutineScope.() -> Unit): Job =
        pluginScope.launch(block = block)

    override fun onEnable() {
        migrateLegacyDataFolder()
        saveDefaultConfig()
        mergeConfigDefaults()
        reloadConfig()
        scheduler = SchedulerAdapter.create(this)

        logger.info("Starting on ${if (scheduler.isFolia) "Folia" else "Paper"}...")

        databaseManager = DatabaseManager(this)
        cityManager = CityManager(this)
        discoveryManager = CityDiscoveryManager(this)
        containerLootManager = ContainerLootManager(this)
        statsManager = StatsManager(this)
        banManager = BanManager(this)
        menuService = MenuService(this)
        snapshotManager = SnapshotManager(this)

        // Registered straight away so an early /ancient says "still starting" instead of nothing.
        getCommand("ancient")?.let {
            val executor = BacCommand(this)
            it.setExecutor(executor)
            it.tabCompleter = executor
        }
        startUpdater()

        launchAsync {
            try {
                databaseManager.initialize()
                cityManager.preload()
                banManager.preload()
            } catch (e: Exception) {
                startupFailed = true
                logger.severe("Better Ancient Cities could not start, because the database could not be opened: ${e.message}")
                logger.severe("Check the database section of config.yml, then restart the server.")
                return@launchAsync
            }
            if (!isEnabled) return@launchAsync
            scheduler.runTask(Runnable {
                if (!isEnabled) return@Runnable
                val pm = server.pluginManager
                pm.registerEvents(CityDiscoveryListener(this@BetterAncientCities), this@BetterAncientCities)
                containerLootListener = ContainerLootListener(this@BetterAncientCities).also { pm.registerEvents(it, this@BetterAncientCities) }
                pm.registerEvents(ProtectionListener(this@BetterAncientCities), this@BetterAncientCities)
                pm.registerEvents(CityDeathListener(this@BetterAncientCities), this@BetterAncientCities)
                presenceListener = CityPresenceListener(this@BetterAncientCities).also { pm.registerEvents(it, this@BetterAncientCities) }
                pm.registerEvents(VcGuiListener(), this@BetterAncientCities)

                logger.info("Usage statistics: ${MetricsService.init(this@BetterAncientCities)}")

                isReady = true
                logger.info("Better Ancient Cities is ready.")
                discoveryManager.startupSweep()
            })
        }
    }

    override fun onDisable() {
        isReady = false
        MetricsService.shutdown()
        updater?.shutdown()
        // Plugins are disabled before players are kicked, so open city chests are saved here.
        containerLootListener?.closeOpenCopies()
        if (::presenceListener.isInitialized) presenceListener.flushAllBlocking()
        if (::containerLootManager.isInitialized) containerLootManager.flushBlocking()
        if (::scheduler.isInitialized) scheduler.cancelAllTasks()
        pluginScope.cancel()
        if (::databaseManager.isInitialized) databaseManager.close()
    }

    private fun startUpdater() {
        try {
            val mode = when (config.getString("update.mode", "notify")!!.trim().lowercase()) {
                "off", "false", "disabled" -> return
                "check-only", "check", "silent" -> UpdateMode.CHECK_ONLY
                "download" -> UpdateMode.DOWNLOAD
                "auto-stage", "auto" -> UpdateMode.AUTO_STAGE
                else -> UpdateMode.NOTIFY
            }
            val built = Updater.builder(this)
                // Each GitHub release carries every build, so take the jar made for this one.
                .source(GitHubReleasesSource("ESMP-FUN/BetterAncientCities", null) { name -> isOwnJar(name.lowercase()) })
                .mode(mode)
                .checkInterval(Duration.ofHours(config.getLong("update.check-interval-hours", 6L).coerceAtLeast(1)))
                .requireHash(config.getBoolean("update.require-hash", true))
                .permission("bac.admin")
                .commandRoot("/ancient")
                .prefix("<gold>[BAC]</gold>")
                .userAgentContact("https://github.com/ESMP-FUN/BetterAncientCities")
                .build()
            built.start()
            updater = built
            updateSubcommand = UpdateSubcommand(built)
        } catch (e: Exception) {
            logger.warning("Update checks are off, because the updater could not start: ${e.message}")
        }
    }

    /** The jar name this build is published under. The master and mc26 builds differ here. */
    private fun isOwnJar(name: String): Boolean = name.endsWith("-mc263.jar")

    fun handleUpdateCommand(sender: CommandSender, args: Array<out String>) {
        val sub = updateSubcommand
        if (sub == null) {
            sender.sendMessage("Update checks are turned off (update.mode in config.yml).")
            return
        }
        // Installing without a restart can never work here: the bundled SQLite driver
        // cannot be loaded twice in one server run.
        if (args.firstOrNull()?.lowercase() in setOf("apply", "reload")) {
            sender.sendMessage("Updates install when the server restarts.")
            return
        }
        sub.handle(sender, arrayOf(*args))
    }

    fun updateTabComplete(args: Array<out String>): List<String> {
        if (args.size > 1) return emptyList()
        val typed = args.firstOrNull()?.lowercase() ?: ""
        return (updateSubcommand?.tabComplete(arrayOf(*args)) ?: emptyList())
            .filter { it != "apply" && it.startsWith(typed) }
    }

    /**
     * Adds settings introduced in newer versions to an existing config.yml, with
     * their comments. The owner's own values are never changed.
     */
    private fun mergeConfigDefaults() {
        val file = File(dataFolder, "config.yml")
        val resource = getResource("config.yml") ?: return
        if (!file.exists()) return
        try {
            val defaults = InputStreamReader(resource, Charsets.UTF_8).use { YamlConfiguration.loadConfiguration(it) }
            val current = YamlConfiguration.loadConfiguration(file)
            val missing = defaults.getKeys(true).filter { !current.isSet(it) && !defaults.isConfigurationSection(it) }
            if (missing.isEmpty()) return
            file.copyTo(File(dataFolder, "config.yml.bak"), overwrite = true)
            for (key in missing) {
                current.set(key, defaults.get(key))
                current.setComments(key, defaults.getComments(key))
                current.setInlineComments(key, defaults.getInlineComments(key))
                val parent = key.substringBeforeLast('.', "")
                if (parent.isNotEmpty() && current.getComments(parent).isEmpty()) {
                    current.setComments(parent, defaults.getComments(parent))
                }
            }
            current.save(file)
            logger.info("Added ${missing.size} new setting(s) to config.yml. Your own settings are unchanged; the old file is saved as config.yml.bak.")
        } catch (e: Exception) {
            logger.warning("Could not add new settings to config.yml: ${e.message}")
        }
    }

    /** One-time move from the pre-2.0 folder, plugins/AncientCityPro/. */
    private fun migrateLegacyDataFolder() {
        try {
            if (dataFolder.exists()) return
            val legacy = File(dataFolder.parentFile, "AncientCityPro")
            if (!legacy.isDirectory) return
            logger.info("Copying your data from plugins/AncientCityPro/ to plugins/${dataFolder.name}/ ...")
            legacy.walkTopDown().forEach { src ->
                val dest = File(dataFolder, src.relativeTo(legacy).path)
                if (src.isDirectory) dest.mkdirs() else src.copyTo(dest, overwrite = false)
            }
            logger.info("Done. The old folder is kept as a backup.")
        } catch (e: Exception) {
            logger.severe("Could not copy your data from plugins/AncientCityPro/: ${e.message}. Copy the folder over by hand and restart.")
        }
    }
}
