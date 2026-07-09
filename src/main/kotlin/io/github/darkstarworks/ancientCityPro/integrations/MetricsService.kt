package io.github.darkstarworks.ancientCityPro.integrations

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie

/**
 * bStats integration. Anonymous usage metrics that drive feature
 * prioritization: which database backend servers actually run, whether
 * discovery approval is required in the wild, per-player loot adoption,
 * and fleet city counts.
 *
 * Respect knobs (either disables collection entirely):
 *  - ACP's own `metrics.enabled` in config.yml
 *  - bStats' global opt-out (`plugins/bStats/config.yml`)
 *
 * All chart callables are evaluated by bStats on its own submission
 * schedule (~30 min) on the main thread; every supplier below reads
 * cheap in-memory state only.
 */
object MetricsService {

    /**
     * bStats service id for AncientCityPro, registered at
     * https://bstats.org/plugin/bukkit/AncientCityPro/32507.
     * A value <= 0 disables metrics init entirely.
     */
    private const val BSTATS_SERVICE_ID: Int = 32507

    fun init(plugin: AncientCityPro): String {
        if (BSTATS_SERVICE_ID <= 0) return "Disabled (no service id)"
        if (!plugin.config.getBoolean("metrics.enabled", true)) return "Disabled (config)"

        return try {
            val metrics = Metrics(plugin, BSTATS_SERVICE_ID)

            metrics.addCustomChart(SimplePie("database_type") {
                plugin.databaseManager.databaseType.toString().lowercase()
            })

            metrics.addCustomChart(SimplePie("discovery_enabled") {
                plugin.config.getBoolean("discovery.enabled", true).toString()
            })

            metrics.addCustomChart(SimplePie("require_approval") {
                plugin.config.getBoolean("discovery.require-approval", true).toString()
            })

            metrics.addCustomChart(SimplePie("per_player_loot") {
                plugin.config.getBoolean("loot.enabled", true).toString()
            })

            metrics.addCustomChart(SimplePie("city_count") {
                when (val n = plugin.cityManager.all().size) {
                    0 -> "0"
                    in 1..5 -> "1-5"
                    in 6..20 -> "6-20"
                    in 21..50 -> "21-50"
                    else -> if (n <= 100) "51-100" else "100+"
                }
            })

            "Enabled"
        } catch (e: Exception) {
            plugin.logger.warning("bStats init failed: ${e.message}")
            "Failed"
        }
    }
}
