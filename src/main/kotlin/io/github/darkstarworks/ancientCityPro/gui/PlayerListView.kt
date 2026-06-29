package io.github.darkstarworks.ancientCityPro.gui

import io.github.darkstarworks.ancientCityPro.AncientCityPro
import io.github.darkstarworks.ancientCityPro.gui.framework.BaseHolder
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGui
import io.github.darkstarworks.ancientCityPro.gui.framework.VcGuiItem
import io.github.darkstarworks.ancientCityPro.managers.StatsManager
import io.github.darkstarworks.ancientCityPro.models.City
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.UUID

/**
 * Per-city player grid: one head per player who has interacted with the city,
 * with stats in the tooltip and quick admin actions —
 * left-click resets their loot (fresh on next open), right-click toggles a loot
 * ban. Stats + ban flags are prefetched by [MenuService.openPlayerList].
 */
class PlayerListView(
    private val plugin: AncientCityPro,
    private val menu: MenuService,
    private val city: City,
    private val stats: List<StatsManager.CityPlayerStats>,
    private val banned: Map<UUID, Boolean>,
    private val page: Int = 0,
) : VcGui(6, Component.text("§5City #${city.id} — Players"), Holder(), "acp.admin") {

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
            set(22, guiItem(Material.BARRIER, "<gray>No player activity recorded yet",
                listOf("<gray>Stats appear once players loot,", "<gray>spend time in, or grief this city.")))
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

        if (p > 0) set(48, guiItem(Material.ARROW, "<white>◀ Previous") { ctx ->
            PlayerListView(plugin, menu, city, stats, banned, p - 1).open(ctx.player)
        })
        set(49, guiItem(Material.BOOK, "<gray>Page <white>${p + 1}<gray>/<white>$pages",
            listOf("<gray>${stats.size} players")))
        if (p < pages - 1) set(50, guiItem(Material.ARROW, "<white>Next ▶") { ctx ->
            PlayerListView(plugin, menu, city, stats, banned, p + 1).open(ctx.player)
        })
        set(45, guiItem(Material.ARROW, "<white>◀ Back") { ctx -> menu.openCityDetail(ctx.player, city) })
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
                "${if (isBanned) "<red>⛔ " else "<white>"}$name"
            ).decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(
                "<gray>Containers looted: <white>${s.containersLooted}",
                "<gray>Time in city: <white>${formatTime(s.timeMs)}",
                "<gray>Deaths: <white>${s.deaths}",
                "<gray>Griefing attempts: ${if (s.griefAttempts > 0) "<red>" else "<white>"}${s.griefAttempts}",
                "<gray>Loot ban: ${if (isBanned) "<red>yes" else "<green>no"}",
                "",
                "<yellow>Left-click<gray>: view what they looted",
                "<yellow>Shift+Left<gray>: reset their loot (fresh)",
                "<yellow>Right-click<gray>: ${if (isBanned) "lift loot ban" else "loot-ban"}",
            ).map { mm.deserialize(it).decoration(TextDecoration.ITALIC, false) })
        }
        return stack
    }

    private fun resetLoot(admin: org.bukkit.entity.Player, target: UUID) {
        plugin.launchAsync {
            val n = plugin.containerLootManager.clearPlayer(city.id, target)
            admin.sendMessage("§aReset loot for ${Bukkit.getOfflinePlayer(target).name ?: target} — cleared $n copy/copies.")
            refresh(admin)
        }
    }

    private fun toggleBan(admin: org.bukkit.entity.Player, target: UUID) {
        plugin.launchAsync {
            val currentlyBanned = plugin.banManager.isBanned(city.id, target)
            if (currentlyBanned) {
                plugin.banManager.unban(city.id, target)
                admin.sendMessage("§aLifted loot ban on ${Bukkit.getOfflinePlayer(target).name ?: target}.")
            } else {
                plugin.banManager.ban(city.id, target, "via GUI", admin.uniqueId)
                admin.sendMessage("§aLoot-banned ${Bukkit.getOfflinePlayer(target).name ?: target} from city #${city.id}.")
            }
            refresh(admin)
        }
    }

    /** Re-open the view so the changed ban flag / stats show immediately. */
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
