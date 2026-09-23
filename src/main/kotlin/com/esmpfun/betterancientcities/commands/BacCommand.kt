package com.esmpfun.betterancientcities.commands

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.managers.SnapshotManager
import com.esmpfun.betterancientcities.models.City
import com.esmpfun.betterancientcities.utils.CityTeleport
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

/** `/ancient`, the staff command. Everything here is also in the menu. */
class BacCommand(private val plugin: BetterAncientCities) : CommandExecutor, TabCompleter {

    private val mm = MiniMessage.miniMessage()

    private fun send(sender: CommandSender, text: String) = sender.sendMessage(mm.deserialize(text))

    private fun esc(text: String) = mm.escapeTags(text)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!plugin.isReady) {
            send(sender, if (plugin.startupFailed)
                "<red>Better Ancient Cities could not start. The server console says why."
            else
                "<red>Better Ancient Cities is still starting. Try again in a moment.")
            return true
        }
        when (args.getOrNull(0)?.lowercase()) {
            null, "menu" -> openMenu(sender)
            "help" -> sendHelp(sender)
            "list" -> handleList(sender)
            "info" -> handleInfo(sender, args.getOrNull(1))
            "approve" -> handleApprove(sender, args.getOrNull(1))
            "delete" -> handleDelete(sender, args.getOrNull(1), args.getOrNull(2))
            "tp" -> handleTp(sender, args.getOrNull(1))
            "open" -> handleOpen(sender, args.getOrNull(1))
            "check" -> handleCheck(sender)
            "snapshot" -> handleSnapshot(sender, args.getOrNull(1))
            "reset" -> handleReset(sender, args.getOrNull(1))
            "reload" -> {
                plugin.reloadConfig()
                send(sender, "<green>config.yml reloaded.")
            }
            "ban" -> handleBan(sender, args)
            "unban" -> handleUnban(sender, args.getOrNull(1), args.getOrNull(2))
            "bans" -> handleBans(sender, args.getOrNull(1))
            "resetloot" -> handleResetLoot(sender, args.getOrNull(1), args.getOrNull(2))
            "update" -> plugin.handleUpdateCommand(sender, args.copyOfRange(1, args.size))
            else -> send(sender, "<red>Unknown command. <gray>Type <white>/ancient help<gray> for the list.")
        }
        return true
    }

    private fun openMenu(sender: CommandSender) {
        val player = sender as? Player ?: run { send(sender, "<red>The menu can only be opened in-game. Try <white>/ancient list<red>."); return }
        plugin.menuService.openCityList(player)
    }

    private fun sendHelp(sender: CommandSender) {
        val lines = listOf(
            "menu" to "open the menu",
            "list" to "list every city found, active and pending",
            "info <id>" to "show one city's details",
            "approve <id>" to "turn on loot and protection for a pending city",
            "delete <id>" to "forget a city and everything saved for it",
            "tp <id>" to "teleport to a city",
            "check" to "is the block you look at protected? (ignores your own bypass)",
            "snapshot <id>" to "save the city's blocks so it can be restored",
            "reset <id>" to "put the city's blocks back as saved",
            "ban <id> <player> [reason]" to "stop a player looting a city",
            "unban <id> <player>" to "let a player loot a city again",
            "bans <id>" to "list who may not loot a city",
            "resetloot <id> <player>" to "let a player loot a city fresh",
            "reload" to "reload config.yml",
            "update" to "check for a new version",
        )
        send(sender, "<light_purple>Better Ancient Cities <gray>commands")
        for ((cmd, what) in lines) send(sender, "<white>/ancient ${esc(cmd)} <gray>- $what")
    }

    private fun handleList(sender: CommandSender) {
        val cities = plugin.cityManager.all().sortedBy { it.id }
        if (cities.isEmpty()) {
            send(sender, "<gray>No Ancient Cities found yet. They are found as players explore near them.")
            return
        }
        send(sender, "<light_purple>Ancient Cities <gray>(${cities.size})")
        for (c in cities) {
            val tag = if (c.approved) "<green>active" else "<yellow>pending"
            val r = c.region
            send(sender,
                "<gray>#<white>${c.id} <gray>[$tag<gray>] <white>${esc(c.world)} " +
                    "<click:run_command:'/ancient tp ${c.id}'><hover:show_text:'<gray>Teleport to city <white>#${c.id}'>" +
                    "<green>[${r.minX} ${r.minY} ${r.minZ}]</green></hover></click> " +
                    "<dark_gray>• ${c.pieces.size} pieces  " +
                    "<click:run_command:'/ancient open ${c.id}'><hover:show_text:'<gray>Open city <white>#${c.id}<gray> in the menu'>" +
                    "<yellow>[menu]</yellow></hover></click>"
            )
        }
    }

    private fun handleOpen(sender: CommandSender, idArg: String?) {
        val player = sender as? Player ?: run { send(sender, "<red>This can only be used in-game."); return }
        val city = resolve(sender, idArg) ?: return
        plugin.menuService.openCityDetail(player, city)
    }

    private fun handleCheck(sender: CommandSender) {
        val player = sender as? Player ?: run { send(sender, "<red>This can only be used in-game."); return }
        val block = player.getTargetBlockExact(8) ?: run { send(sender, "<red>Look at a block no more than 8 blocks away."); return }
        val pad = plugin.config.getInt("protection.piece-padding", 3)
        val loc = block.location
        val city = plugin.cityManager.getAnyCityAt(loc)
        val inPiece = city?.inStructurePiece(loc, pad) == true
        val protectionOn = plugin.config.getBoolean("protection.enabled", true)
        val activeNow = city != null && city.approved && protectionOn && inPiece

        send(sender, "<light_purple>/ancient check <gray>- <white>${block.type.name.lowercase()} <gray>at <white>${loc.blockX}, ${loc.blockY}, ${loc.blockZ}")
        send(sender, "<gray>Inside a city: " +
            if (city != null) "<green>#${city.id} ${if (city.approved) "(active)" else "<yellow>(pending)"}" else "<red>no")
        send(sender, "<gray>Part of a city building (or within $pad blocks of one): ${if (inPiece) "<green>yes" else "<red>no"}")
        send(sender, "<gray>Protected right now: ${if (activeNow) "<green>YES" else "<red>no"}")
        when {
            city != null && inPiece && !city.approved ->
                send(sender, "<yellow>This city is waiting for approval, so it is not protected yet. Approve it with <white>/ancient approve ${city.id}<yellow>.")
            !protectionOn && inPiece ->
                send(sender, "<yellow>protection.enabled is set to false in config.yml.")
            activeNow && player.hasPermission("bac.bypass.protection") ->
                send(sender, "<yellow>You have bac.bypass.protection, so you can still break it. Test with a player who is not an operator.")
        }
    }

    private fun handleInfo(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        val r = city.region
        send(sender, "<light_purple>Ancient City <gray>#<white>${city.id}")
        send(sender, "<gray>World: <white>${esc(city.world)}")
        send(sender, "<gray>From <white>${r.minX}, ${r.minY}, ${r.minZ} <gray>to <white>${r.maxX}, ${r.maxY}, ${r.maxZ}")
        send(sender, "<gray>Buildings: <white>${city.pieces.size}")
        send(sender, "<gray>Status: ${if (city.approved) "<green>active" else "<yellow>waiting for approval"}")
        send(sender, "<gray>Snapshot: ${if (plugin.snapshotManager.hasSnapshot(city.id)) "<green>saved" else "<red>none"}")
    }

    private fun handleApprove(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        plugin.menuService.beginApproval(sender, city, reopenDetail = false)
    }

    private fun handleDelete(sender: CommandSender, idArg: String?, confirm: String?) {
        val city = resolve(sender, idArg) ?: return
        if (!confirm.equals("confirm", ignoreCase = true)) {
            send(sender, "<yellow>This forgets city #${city.id} with its saved chests, stats, loot bans and snapshot. It cannot be undone.")
            send(sender, "<yellow>Type <white>/ancient delete ${city.id} confirm<yellow> to go ahead.")
            return
        }
        plugin.launchAsync {
            val ok = plugin.cityManager.deleteCity(city.id)
            send(sender, if (ok) "<green>City #${city.id} deleted." else "<red>City #${city.id} could not be deleted. The server console says why.")
        }
    }

    private fun handleTp(sender: CommandSender, idArg: String?) {
        val player = sender as? Player ?: run { send(sender, "<red>This can only be used in-game."); return }
        val city = resolve(sender, idArg) ?: return
        CityTeleport.teleport(plugin, player, city)
    }

    /** Looks a player up without ever asking Mojang, which would freeze the server. */
    private fun resolveTarget(sender: CommandSender, name: String?): UUID? {
        if (name.isNullOrBlank()) {
            send(sender, "<red>Add a player name.")
            return null
        }
        plugin.server.getPlayerExact(name)?.let { return it.uniqueId }
        val cached = plugin.server.getOfflinePlayerIfCached(name)
        if (cached == null) {
            send(sender, "<red>No player called '${esc(name)}' has played on this server.")
            return null
        }
        return cached.uniqueId
    }

    private fun handleBan(sender: CommandSender, args: Array<out String>) {
        val city = resolve(sender, args.getOrNull(1)) ?: return
        val target = resolveTarget(sender, args.getOrNull(2)) ?: return
        val reason = if (args.size > 3) args.drop(3).joinToString(" ") else null
        val by = (sender as? Player)?.uniqueId
        val name = esc(args[2])
        plugin.launchAsync {
            val ok = plugin.banManager.ban(city.id, target, reason, by)
            send(sender, if (ok) "<green>$name can no longer loot city #${city.id}${reason?.let { " <gray>(${esc(it)})" } ?: ""}<green>."
                else "<red>The ban could not be saved. The server console says why.")
        }
    }

    private fun handleUnban(sender: CommandSender, idArg: String?, name: String?) {
        val city = resolve(sender, idArg) ?: return
        val target = resolveTarget(sender, name) ?: return
        val shown = esc(name!!)
        plugin.launchAsync {
            val ok = plugin.banManager.unban(city.id, target)
            send(sender, if (ok) "<green>$shown can loot city #${city.id} again." else "<gray>$shown was not banned from city #${city.id}.")
        }
    }

    private fun handleBans(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        plugin.launchAsync {
            val bans = plugin.banManager.listBans(city.id)
            if (bans.isEmpty()) {
                send(sender, "<gray>Nobody is banned from looting city #${city.id}.")
                return@launchAsync
            }
            send(sender, "<light_purple>Banned from looting <gray>city #${city.id} (${bans.size}):")
            for (b in bans) {
                val name = plugin.server.getOfflinePlayer(b.playerUuid).name ?: b.playerUuid.toString()
                send(sender, "<white>${esc(name)}${b.reason?.let { " <gray>- ${esc(it)}" } ?: ""}")
            }
        }
    }

    private fun handleResetLoot(sender: CommandSender, idArg: String?, name: String?) {
        val city = resolve(sender, idArg) ?: return
        val target = resolveTarget(sender, name) ?: return
        val shown = esc(name!!)
        plugin.launchAsync {
            val n = plugin.containerLootManager.clearPlayer(city.id, target)
            send(sender, "<green>$shown can loot city #${city.id} fresh. <gray>($n saved ${if (n == 1) "chest" else "chests"} cleared)")
        }
    }

    private fun handleSnapshot(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        send(sender, "<gray>Saving the blocks of city #${city.id}. This can take a few seconds.")
        plugin.launchAsync {
            val n = plugin.snapshotManager.capture(city)
            send(sender, when {
                n == SnapshotManager.BUSY -> "<yellow>City #${city.id} is already being saved or restored. Wait for that to finish."
                n >= 0 -> "<green>City #${city.id} saved ($n blocks)."
                else -> "<red>City #${city.id} could not be saved. The server console says why."
            })
        }
    }

    private fun handleReset(sender: CommandSender, idArg: String?) {
        val city = resolve(sender, idArg) ?: return
        if (!plugin.snapshotManager.hasSnapshot(city.id)) {
            send(sender, "<red>City #${city.id} has no snapshot yet. Save one first with <white>/ancient snapshot ${city.id}<red>.")
            return
        }
        send(sender, "<gray>Restoring city #${city.id}. This can take a few seconds.")
        plugin.launchAsync {
            val n = plugin.snapshotManager.restore(city)
            send(sender, when {
                n == SnapshotManager.BUSY -> "<yellow>City #${city.id} is already being saved or restored. Wait for that to finish."
                n >= 0 -> "<green>City #${city.id} restored ($n blocks)."
                else -> "<red>City #${city.id} could not be restored. The server console says why."
            })
        }
    }

    private fun resolve(sender: CommandSender, idArg: String?): City? {
        val id = idArg?.toIntOrNull() ?: run {
            send(sender, "<red>Add the city's number. <white>/ancient list<red> shows them.")
            return null
        }
        return plugin.cityManager.byId(id) ?: run {
            send(sender, "<red>There is no city #$id. <white>/ancient list<red> shows them.")
            null
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> {
        if (!plugin.isReady) return emptyList()
        return when (args.size) {
            1 -> SUBCOMMANDS.filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "info", "approve", "delete", "tp", "open", "snapshot", "reset", "ban", "unban", "bans", "resetloot" ->
                    plugin.cityManager.all().map { it.id.toString() }.filter { it.startsWith(args[1]) }
                "update" -> plugin.updateTabComplete(args.copyOfRange(1, args.size))
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "ban", "unban", "resetloot" -> plugin.server.onlinePlayers.map { it.name }
                    .filter { it.startsWith(args[2], ignoreCase = true) }
                "delete" -> listOf("confirm").filter { it.startsWith(args[2].lowercase()) }
                "update" -> plugin.updateTabComplete(args.copyOfRange(1, args.size))
                else -> emptyList()
            }
            else -> if (args[0].equals("update", ignoreCase = true)) plugin.updateTabComplete(args.copyOfRange(1, args.size)) else emptyList()
        }
    }

    private companion object {
        val SUBCOMMANDS = listOf(
            "menu", "list", "info", "approve", "delete", "tp", "open", "check", "snapshot", "reset",
            "reload", "ban", "unban", "bans", "resetloot", "update", "help",
        )
    }
}
