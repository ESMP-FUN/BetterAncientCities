package io.github.darkstarworks.ancientCityPro.commands

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.models.City
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.util.UUID

/**
 * `/acp` admin command. Fallback to the GUI (`/acp menu`, added later); for now
 * it exposes the city lifecycle: list / info / approve / delete / tp.
 */
class AcpCommand(private val plugin: AncientCityPro) : CommandExecutor, TabCompleter {

    private val mm = MiniMessage.miniMessage()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!plugin.isReady) {
            sender.sendMessage("§cAncientCityPro is still starting up.")
            return true
        }
        when (args.getOrNull(0)?.lowercase()) {
            null, "menu" -> openMenu(sender)
            "help" -> sendHelp(sender)
            "list" -> handleList(sender)
            "info" -> handleInfo(sender, args.getOrNull(1))
            "approve" -> handleApprove(sender, args.getOrNull(1))
            "delete" -> handleDelete(sender, args.getOrNull(1))
            "tp" -> handleTp(sender, args.getOrNull(1))
            "open" -> handleOpen(sender, args.getOrNull(1))
            "check" -> handleCheck(sender)
            "snapshot" -> handleSnapshot(sender, args.getOrNull(1))
            "reset" -> handleReset(sender, args.getOrNull(1))
            "reload" -> { plugin.reloadConfig(); sender.sendMessage("§aAncientCityPro config reloaded.") }
            "ban" -> handleBan(sender, args)
            "unban" -> handleUnban(sender, args.getOrNull(1), args.getOrNull(2))
            "bans" -> handleBans(sender, args.getOrNull(1))
            "resetloot" -> handleResetLoot(sender, args.getOrNull(1), args.getOrNull(2))
            else -> sender.sendMessage("§cUnknown subcommand. §7Try §f/acp help§7.")
        }
        return true
    }

    private fun openMenu(sender: CommandSender) {
        val player = sender as? Player ?: run { sender.sendMessage("§cThe menu is players-only. Try §f/acp list§c."); return }
        plugin.menuService.openCityList(player)
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§5§lAncientCityPro §7— admin commands")
        sender.sendMessage("§f/acp menu §7— open the admin GUI")
        sender.sendMessage("§f/acp list §7— list discovered cities (approved + pending)")
        sender.sendMessage("§f/acp info <id> §7— details of a city")
        sender.sendMessage("§f/acp approve <id> §7— activate a pending city")
        sender.sendMessage("§f/acp delete <id> §7— unregister a city")
        sender.sendMessage("§f/acp tp <id> §7— teleport to a city")
        sender.sendMessage("§f/acp check §7— is the block you're looking at protected? (ignores your bypass)")
        sender.sendMessage("§f/acp snapshot <id> §7— capture the city's structure for restoration")
        sender.sendMessage("§f/acp reset <id> §7— restore the city from its snapshot")
        sender.sendMessage("§f/acp reload §7— reload config.yml")
        sender.sendMessage("§f/acp ban <id> <player> [reason] §7— loot-ban a player from a city")
        sender.sendMessage("§f/acp unban <id> <player> §7— lift a loot ban")
        sender.sendMessage("§f/acp bans <id> §7— list a city's loot bans")
        sender.sendMessage("§f/acp resetloot <id> <player> §7— let a player loot the city fresh")
    }

    private fun handleList(sender: CommandSender) {
        val cities = plugin.cityManager.all().sortedBy { it.id }
        if (cities.isEmpty()) {
            sender.sendMessage("§7No Ancient Cities discovered yet.")
            return
        }
        sender.sendMessage("§5Ancient Cities §7(${cities.size})")
        for (c in cities) {
            val tag = if (c.approved) "<green>active" else "<yellow>pending"
            val r = c.region
            sender.sendMessage(mm.deserialize(
                "<gray>#<white>${c.id} <gray>[$tag<gray>] <white>${c.world} " +
                    "<click:run_command:'/acp tp ${c.id}'><hover:show_text:'<gray>Teleport to city <white>#${c.id}'>" +
                    "<green>[${r.minX} ${r.minY} ${r.minZ}]</green></hover></click> " +
                    "<dark_gray>• ${c.pieces.size} pieces  " +
                    "<click:run_command:'/acp open ${c.id}'><hover:show_text:'<gray>Open city <white>#${c.id}<gray> in the GUI'>" +
                    "<yellow>[menu]</yellow></hover></click>"
            ))
        }
    }

    private fun handleOpen(sender: CommandSender, idArg: String?) {
        val player = sender as? Player ?: run { sender.sendMessage("§cPlayers only."); return }
        val city = resolve(sender, idArg) ?: return
        plugin.menuService.openCityDetail(player, city)
    }

    private fun handleCheck(sender: CommandSender) {
        val player = sender as? Player ?: run { sender.sendMessage("§cPlayers only."); return }
        val block = player.getTargetBlockExact(8) ?: run { sender.sendMessage("§cLook at a block within 8 blocks."); return }
        val pad = plugin.config.getInt("protection.piece-padding", 3)
        val loc = block.location
        // Geometry is independent of approval — use ANY city for the rule test.
        val city = plugin.cityManager.getAnyCityAt(loc)
        val inPiece = city?.inStructurePiece(loc, pad) == true
        val protectionEnabled = plugin.config.getBoolean("protection.enabled", true)
        val activeNow = city != null && city.approved && protectionEnabled && inPiece

        sender.sendMessage("§5/acp check §7— §f${block.type.name.lowercase()} §7at §f${loc.blockX}, ${loc.blockY}, ${loc.blockZ}")
        sender.sendMessage("§7In a city region: " +
            if (city != null) "§a#${city.id} ${if (city.approved) "§a(active)" else "§e(pending)"}" else "§cno")
        sender.sendMessage("§7Inside a structure piece (±$pad): ${if (inPiece) "§ayes" else "§cno"}")
        // The rule (geometry) vs. whether it's actually enforced right now.
        sender.sendMessage("§7Protected by the rule: ${if (inPiece) "§a§lYES" else "§cno"}")
        sender.sendMessage("§7Enforced right now: ${if (activeNow) "§a§lYES" else "§cno"}")
        when {
            inPiece && city != null && !city.approved ->
                sender.sendMessage("§e→ City is pending approval, so protection isn't active yet. Approve it with §f/acp approve ${city.id}§e.")
            !protectionEnabled && inPiece ->
                sender.sendMessage("§e→ protection.enabled is false in config.")
            activeNow && player.hasPermission("acp.bypass.protection") ->
                sender.sendMessage("§e⚠ You have §facp.bypass.protection§e, so YOU can still break it — test with a non-op account.")
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
            if (ok && plugin.config.getBoolean("snapshot.auto-capture-on-approve", true)) {
                plugin.cityManager.byId(city.id)?.let { c ->
                    val n = plugin.snapshotManager.capture(c)
                    if (n >= 0) sender.sendMessage("§7Baseline snapshot captured ($n cells).")
                }
            }
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

    @Suppress("DEPRECATION")
    private fun resolveTarget(sender: CommandSender, name: String?): UUID? {
        if (name.isNullOrBlank()) {
            sender.sendMessage("§cProvide a player name.")
            return null
        }
        // Prefer online, else cached offline profile; admin command, so a lookup is fine.
        plugin.server.getPlayerExact(name)?.let { return it.uniqueId }
        val off = plugin.server.getOfflinePlayer(name)
        if (!off.hasPlayedBefore() && !off.isOnline) {
            sender.sendMessage("§cNo player named '$name' has been seen on this server.")
            return null
        }
        return off.uniqueId
    }

    private fun handleBan(sender: CommandSender, args: Array<out String>) {
        val city = resolve(sender, args.getOrNull(1)) ?: return
        val target = resolveTarget(sender, args.getOrNull(2)) ?: return
        val reason = if (args.size > 3) args.drop(3).joinToString(" ") else null
        val by = (sender as? Player)?.uniqueId
        plugin.launchAsync {
            val ok = plugin.banManager.ban(city.id, target, reason, by)
            sender.sendMessage(if (ok) "§aLoot-banned ${args[2]} from city #${city.id}${reason?.let { " (§7$it§a)" } ?: ""}." else "§cBan failed.")
        }
    }

    private fun handleUnban(sender: CommandSender, idArg: String?, name: String?) {
        val city = resolve(sender, idArg) ?: return
        val target = resolveTarget(sender, name) ?: return
        plugin.launchAsync {
            val ok = plugin.banManager.unban(city.id, target)
            sender.sendMessage(if (ok) "§aLifted loot ban on $name for city #${city.id}." else "§7$name was not banned from city #${city.id}.")
        }
    }

    private fun handleBans(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        plugin.launchAsync {
            val bans = plugin.banManager.listBans(city.id)
            if (bans.isEmpty()) {
                sender.sendMessage("§7No loot bans for city #${city.id}.")
                return@launchAsync
            }
            sender.sendMessage("§5Loot bans §7for city #${city.id} (${bans.size}):")
            for (b in bans) {
                @Suppress("DEPRECATION") val name = plugin.server.getOfflinePlayer(b.playerUuid).name ?: b.playerUuid.toString()
                sender.sendMessage("§f$name §7${b.reason?.let { "— $it" } ?: ""}")
            }
        }
    }

    private fun handleResetLoot(sender: CommandSender, idArg: String?, name: String?) {
        val city = resolve(sender, idArg) ?: return
        val target = resolveTarget(sender, name) ?: return
        plugin.launchAsync {
            val n = plugin.containerLootManager.clearPlayer(city.id, target)
            sender.sendMessage("§aCleared $n container copy/copies for $name in city #${city.id} — they can loot it fresh.")
        }
    }

    private fun handleSnapshot(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        sender.sendMessage("§7Capturing snapshot of city #${city.id} — this may take a few seconds…")
        plugin.launchAsync {
            val n = plugin.snapshotManager.capture(city)
            sender.sendMessage(if (n >= 0) "§aSnapshot captured for city #${city.id} ($n cells)." else "§cSnapshot failed (see console).")
        }
    }

    private fun handleReset(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        if (!plugin.snapshotManager.hasSnapshot(city.id)) {
            sender.sendMessage("§cCity #${city.id} has no snapshot. Capture one first with §f/acp snapshot ${city.id}§c.")
            return
        }
        sender.sendMessage("§7Restoring city #${city.id} from snapshot…")
        plugin.launchAsync {
            val n = plugin.snapshotManager.restore(city)
            sender.sendMessage(if (n >= 0) "§aRestored city #${city.id} ($n cells)." else "§cRestore failed (see console).")
        }
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
            1 -> listOf("menu", "list", "info", "approve", "delete", "tp", "check", "snapshot", "reset", "reload", "ban", "unban", "bans", "resetloot", "help")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "info", "approve", "delete", "tp", "open", "snapshot", "reset", "ban", "unban", "bans", "resetloot" ->
                    plugin.cityManager.all().map { it.id.toString() }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "ban", "unban", "resetloot" -> plugin.server.onlinePlayers.map { it.name }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
