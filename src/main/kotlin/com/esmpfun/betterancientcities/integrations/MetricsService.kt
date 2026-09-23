package com.esmpfun.betterancientcities.integrations

import com.esmpfun.betterancientcities.BetterAncientCities
import dev.faststats.ErrorTracker
import dev.faststats.Metrics
import dev.faststats.bukkit.BukkitContext
import dev.faststats.data.Metric

/**
 * Anonymous usage statistics through FastStats: database type, discovery settings,
 * whether per-player loot is on, and how many cities a server has. Turned off by
 * `metrics.enabled: false` or FastStats' own `plugins/faststats/config.properties`.
 *
 * Error reports are separate and off unless `metrics.error-reporting` is true,
 * because a stack trace can contain file paths.
 *
 * FastStats reads each value off the main thread, so they only read memory.
 */
object MetricsService {

    private const val FASTSTATS_TOKEN: String = "066b635ec43f8e5faaf5b6d4dc8526de"

    private var context: BukkitContext? = null

    fun init(plugin: BetterAncientCities): String {
        if (FASTSTATS_TOKEN.isBlank()) return "off"
        if (!plugin.config.getBoolean("metrics.enabled", true)) return "off (metrics.enabled is false)"

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

            if (errorReporting) {
                factory = factory.errorTrackerService(ErrorTracker.contextAware())
            }

            context = factory.create().also { it.ready() }

            if (errorReporting) "on, with error reports" else "on"
        } catch (e: Exception) {
            plugin.logger.warning("Usage statistics could not start: ${e.message}")
            "off"
        }
    }

    /** Safe to call when [init] never ran. */
    fun shutdown() {
        context?.shutdown()
        context = null
    }
}
