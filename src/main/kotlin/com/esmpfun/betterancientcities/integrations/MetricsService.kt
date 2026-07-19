package com.esmpfun.betterancientcities.integrations

import com.esmpfun.betterancientcities.BetterAncientCities
import dev.faststats.ErrorTracker
import dev.faststats.Metrics
import dev.faststats.bukkit.BukkitContext
import dev.faststats.data.Metric

/**
 * FastStats integration. Anonymous usage metrics that drive feature
 * prioritization: which database backend servers actually run, whether
 * discovery approval is required in the wild, per-player loot adoption,
 * and fleet city counts.
 *
 * Respect knobs (either disables collection entirely):
 *  - ACP's own `metrics.enabled` in config.yml
 *  - FastStats' global opt-out (`plugins/faststats/config.properties`)
 *
 * Error reporting is a separate, opt-in concern: it ships stack traces
 * (which can carry paths and third-party plugin internals) rather than
 * the aggregate counters above, so it stays off unless an admin sets
 * `metrics.error-reporting: true`. It also requires `metrics.enabled`,
 * since the whole context is skipped when metrics are off.
 *
 * Every metric callable below is evaluated by FastStats on its own
 * submission schedule, off the main thread — so each one reads cheap
 * in-memory state only and must stay thread-safe and side-effect free.
 */
object MetricsService {

    /**
     * FastStats project token for BetterAncientCities. A blank value
     * disables metrics init entirely.
     */
    private const val FASTSTATS_TOKEN: String = "066b635ec43f8e5faaf5b6d4dc8526de"

    /** Live context, retained so [shutdown] can tear it down on disable. */
    private var context: BukkitContext? = null

    fun init(plugin: BetterAncientCities): String {
        if (FASTSTATS_TOKEN.isBlank()) return "Disabled (no token)"
        if (!plugin.config.getBoolean("metrics.enabled", true)) return "Disabled (config)"

        // Opt-in, and deliberately defaulted to false — unlike metrics.enabled.
        val errorReporting = plugin.config.getBoolean("metrics.error-reporting", false)

        return try {
            var factory = BukkitContext.Factory(plugin, FASTSTATS_TOKEN)
                .metrics { factory ->
                    factory
                        .addMetric(Metric.string("database_type") {
                            plugin.databaseManager.databaseType.toString().lowercase()
                        })
                        .addMetric(Metric.bool("discovery_enabled") {
                            plugin.config.getBoolean("discovery.enabled", true)
                        })
                        .addMetric(Metric.bool("require_approval") {
                            plugin.config.getBoolean("discovery.require-approval", true)
                        })
                        .addMetric(Metric.bool("per_player_loot") {
                            plugin.config.getBoolean("loot.enabled", true)
                        })
                        .addMetric(Metric.number("city_count") {
                            plugin.cityManager.all().size
                        })
                        .create()
                }

            // contextAware() hooks uncaught errors from this plugin's class
            // loader, so only attach it when the admin has asked for it.
            if (errorReporting) {
                factory = factory.errorTrackerService(ErrorTracker.contextAware())
            }

            context = factory.create().also { it.ready() }

            if (errorReporting) "Enabled (with error reporting)" else "Enabled"
        } catch (e: Exception) {
            plugin.logger.warning("FastStats init failed: ${e.message}")
            "Failed"
        }
    }

    /** Flushes and stops submission. Safe to call when init never ran. */
    fun shutdown() {
        context?.shutdown()
        context = null
    }
}
