package com.esmpfun.betterancientcities.gui

import com.esmpfun.betterancientcities.BetterAncientCities
import com.esmpfun.betterancientcities.gui.framework.BaseHolder
import com.esmpfun.betterancientcities.gui.framework.VcGui
import com.esmpfun.betterancientcities.gui.framework.VcGuiItem
import com.esmpfun.betterancientcities.managers.StatsManager
import com.esmpfun.betterancientcities.models.City
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.UUID

/**
 * One head per player who has done something in a city, with their stats in the
 * tooltip. Click to see what they looted, shift-click to let them loot fresh,
 * right-click to ban or unban them from looting.
 */
class PlayerListView(
    private val plugin: BetterAncientCities,
    private val menu: MenuService,
    private val city: City,
    private val stats: List<StatsManager.CityPlayerStats>,
    private val banned: Map<UUID, Boolean>,
    private val page: Int = 0,
) : VcGui(6, Component.text("City #${city.id} players", NamedTextColor.DARK_PURPLE), Holder(), "bac.admin") {

    class Holder : BaseHolder()

    private companion object {
        const val PER_PAGE = 45
        val mm: MiniMessage = MiniMessage.miniMessage()
    }

    init { layout() }

    private fun layout() {
        clear()
        val pages = maxOf(1, (stats.size + PER_PAGE - 1) / PER_PAGE)
        val p = page.coerceIn(0, pages - 1)
        val slice = stats.drop(p * PER_PAGE).take(PER_PAGE)

        if (stats.isEmpty()) {
            set(22, guiItem(Material.BARRIER, "<gray>No players here yet",
                listOf("<gray>Players show up once they loot,", "<gray>visit or try to grief this city.")))
        }

        slice.forEachIndexed { i, s ->
            set(i, VcGuiItem.wrap(playerHead(s), onClick = { ctx ->
                when {
                    ctx.click.isShiftClick && ctx.click.isLeftClick -> resetLoot(ctx.player, s.playerUuid)
                    ctx.click.isLeftClick -> menu.openPlayerContainers(ctx.player, city, s.playerUuid)
                    ctx.click.isRightClick -> toggleBan(ctx.player, s.playerUuid)
                }
            }))
        }

        if (p > 0) set(48, guiItem(Material.ARROW, "<white>« Previous page") { ctx ->
            PlayerListView(plugin, menu, city, stats, banned, p - 1).open(ctx.player)
        })
        set(49, guiItem(Material.BOOK, "<gray>Page <white>${p + 1}<gray>/<white>$pages",
            listOf("<gray>${stats.size} players")))
        if (p < pages - 1) set(50, guiItem(Material.ARROW, "<white>Next page »") { ctx ->
            PlayerListView(plugin, menu, city, stats, banned, p + 1).open(ctx.player)
        })
        set(45, guiItem(Material.ARROW, "<white>« Back") { ctx -> menu.openCityDetail(ctx.player, city) })
        set(53, guiItem(Material.BARRIER, "<red>Close") { ctx -> ctx.player.closeInventory() })
    }

    private fun playerHead(s: StatsManager.CityPlayerStats): ItemStack {
        val off = Bukkit.getOfflinePlayer(s.playerUuid)
        val name = off.name ?: s.playerUuid.toString().take(8)
        val isBanned = banned[s.playerUuid] == true
        val stack = ItemStack(Material.PLAYER_HEAD, 1)
        stack.editMeta(SkullMeta::class.java) { meta ->
            meta.owningPlayer = off
            meta.displayName(mm.deserialize(
                "${if (isBanned) "<red>" else "<white>"}$name"
            ).decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(
                "<gray>Chests looted: <white>${s.containersLooted}",
                "<gray>Time in city: <white>${formatTime(s.timeMs)}",
                "<gray>Deaths: <white>${s.deaths}",
                "<gray>Blocked griefing attempts: ${if (s.griefAttempts > 0) "<red>" else "<white>"}${s.griefAttempts}",
                "<gray>Banned from looting: ${if (isBanned) "<red>yes" else "<green>no"}",
                "",
                "<yellow>Click<gray>: see what they looted",
                "<yellow>Shift-click<gray>: let them loot the city fresh",
                "<yellow>Right-click<gray>: ${if (isBanned) "allow looting again" else "ban from looting"}",
            ).map { mm.deserialize(it).decoration(TextDecoration.ITALIC, false) })
        }
        return stack
    }

    private fun resetLoot(admin: org.bukkit.entity.Player, target: UUID) {
        plugin.launchAsync {
            val n = plugin.containerLootManager.clearPlayer(city.id, target)
            admin.sendMessage(mm.deserialize("<green>${nameOf(target)} can loot city #${city.id} fresh. <gray>($n saved ${if (n == 1) "chest" else "chests"} cleared)"))
            refresh(admin)
        }
    }

    private fun toggleBan(admin: org.bukkit.entity.Player, target: UUID) {
        plugin.launchAsync {
            val name = nameOf(target)
            if (plugin.banManager.isBanned(city.id, target)) {
                val ok = plugin.banManager.unban(city.id, target)
                admin.sendMessage(mm.deserialize(if (ok) "<green>$name can loot city #${city.id} again." else "<red>The ban could not be lifted. The server console says why."))
            } else {
                val ok = plugin.banManager.ban(city.id, target, "from the menu", admin.uniqueId)
                admin.sendMessage(mm.deserialize(if (ok) "<green>$name can no longer loot city #${city.id}." else "<red>The ban could not be saved. The server console says why."))
            }
            refresh(admin)
        }
    }

    private fun nameOf(uuid: UUID): String = mm.escapeTags(Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString().take(8))

    /** Reopens the screen so the change shows straight away. */
    private fun refresh(admin: org.bukkit.entity.Player) {
        plugin.scheduler.runAtEntity(admin, Runnable { if (admin.isOnline) menu.openPlayerList(admin, city) })
    }

    private fun formatTime(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
