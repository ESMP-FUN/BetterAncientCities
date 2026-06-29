package io.github.darkstarworks.ancientCityPro.commands

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.models.City
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * `/acp` admin command. Fallback to the GUI (`/acp menu`, added later); for now
 * it exposes the city lifecycle: list / info / approve / delete / tp.
 */
class AcpCommand(private val plugin: AncientCityPro) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!plugin.isReady) {
            sender.sendMessage("§cAncientCityPro is still starting up.")
            return true
        }
        when (args.getOrNull(0)?.lowercase()) {
            null, "help" -> sendHelp(sender)
            "list" -> handleList(sender)
            "info" -> handleInfo(sender, args.getOrNull(1))
            "approve" -> handleApprove(sender, args.getOrNull(1))
            "delete" -> handleDelete(sender, args.getOrNull(1))
            "tp" -> handleTp(sender, args.getOrNull(1))
            else -> sender.sendMessage("§cUnknown subcommand. §7Try §f/acp help§7.")
        }
        return true
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§5§lAncientCityPro §7— admin commands")
        sender.sendMessage("§f/acp list §7— list discovered cities (approved + pending)")
        sender.sendMessage("§f/acp info <id> §7— details of a city")
        sender.sendMessage("§f/acp approve <id> §7— activate a pending city")
        sender.sendMessage("§f/acp delete <id> §7— unregister a city")
        sender.sendMessage("§f/acp tp <id> §7— teleport to a city")
    }

    private fun handleList(sender: CommandSender) {
        val cities = plugin.cityManager.all().sortedBy { it.id }
        if (cities.isEmpty()) {
            sender.sendMessage("§7No Ancient Cities discovered yet.")
            return
        }
        sender.sendMessage("§5Ancient Cities §7(${cities.size}):")
        for (c in cities) {
            val tag = if (c.approved) "§aactive" else "§epending"
            sender.sendMessage(
                "§7#§f${c.id} §7[$tag§7] §f${c.world} §7(${c.region.minX}, ${c.region.minY}, ${c.region.minZ}) " +
                    "§8• ${c.pieces.size} pieces"
            )
        }
    }

    private fun handleInfo(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        val r = city.region
        sender.sendMessage("§5Ancient City §7#§f${city.id}")
        sender.sendMessage("§7World: §f${city.world}")
        sender.sendMessage("§7Bounds: §f(${r.minX}, ${r.minY}, ${r.minZ}) §7→ §f(${r.maxX}, ${r.maxY}, ${r.maxZ})")
        sender.sendMessage("§7Pieces: §f${city.pieces.size}")
        sender.sendMessage("§7Status: ${if (city.approved) "§aactive" else "§epending approval"}")
    }

    private fun handleApprove(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        if (city.approved) {
            sender.sendMessage("§7City §f#${city.id} §7is already active.")
            return
        }
        plugin.launchAsync {
            val ok = plugin.cityManager.approveCity(city.id)
            sender.sendMessage(if (ok) "§aApproved city #${city.id} — loot and protection are now active." else "§cApproval failed.")
        }
    }

    private fun handleDelete(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        plugin.launchAsync {
            val ok = plugin.cityManager.deleteCity(city.id)
            sender.sendMessage(if (ok) "§aDeleted city #${city.id}." else "§cDelete failed.")
        }
    }

    private fun handleTp(sender: CommandSender, idArg: String?) {
        val player = sender as? Player ?: run { sender.sendMessage("§cPlayers only."); return }
        val city = resolve(sender, idArg) ?: return
        val world = city.getWorld() ?: run { sender.sendMessage("§cWorld '${city.world}' is not loaded."); return }
        val r = city.region
        val dest = Location(world, (r.minX + r.maxX) / 2.0 + 0.5, r.maxY + 1.0, (r.minZ + r.maxZ) / 2.0 + 0.5)
        plugin.scheduler.runAtEntity(player, Runnable { player.teleport(dest) })
        player.sendMessage("§7Teleporting to city §f#${city.id}§7.")
    }

    private fun resolve(sender: CommandSender, idArg: String?): City? {
        val id = idArg?.toIntOrNull() ?: run {
            sender.sendMessage("§cUsage: provide a numeric city id (see §f/acp list§c).")
            return null
        }
        return plugin.cityManager.byId(id) ?: run {
            sender.sendMessage("§cNo city with id $id.")
            null
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("list", "info", "approve", "delete", "tp", "help").filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "info", "approve", "delete", "tp" -> plugin.cityManager.all().map { it.id.toString() }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
