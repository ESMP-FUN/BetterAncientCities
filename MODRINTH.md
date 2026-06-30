<!-- Banner: replace with the AncientCityPro banner asset URL once uploaded -->

# AncientCityPro

Renewable Ancient Cities for multiplayer -> auto-discovered, per-player loot, griefing-protected, fully restorable.

**Got an "if it did [THING], I'd use it" idea? Tell me!** [<img src="https://raw.githubusercontent.com/darkstarworks/TrialChamberPro/master/dc.png" width="30" alt="Join Discord Server">](https://discord.gg/qwYcTpHsNC)

<br>

A naturally-generated Ancient City is single-use on a multiplayer server. The first player loots every chest, the structure gets hollowed out or griefed, and there's no practical way to reset 220 blocks of irregular Deep-Dark ruins by hand.

**AncientCityPro fixes that** — every player gets their own private roll of each chest, the city's loot refreshes on its own per-city timer, the structure is protected from griefing, and you can snapshot and restore the whole thing (reverting griefing *and* runaway sculk spread). It finds and manages cities automatically, with no per-city setup.

> **Standalone** — AncientCityPro does **not** require TrialChamberPro or any other plugin. Run them side by side if you like; they don't interfere.

<br>

**Full documentation:** https://darkstarworks.gitbook.io/plugins/mc/acp-documentation

---

## Why AncientCityPro?

| Problem | Solution |
|---------|----------|
| First player takes all the loot | Per-player chest copies — everyone loots the full city, independently |
| Looted chests stay empty forever | Per-city refresh cycle — the city's loot comes back on a timer |
| Griefers hollow out the structure | Bounds-based griefing protection for the whole structure |
| Sculk spreads / city gets damaged | Snapshot + restore returns it to pristine on demand |
| No way to reset a 220-block ruin | One-click reset (or auto-restore on the refresh cycle) |
| Setup overhead per city | Auto-discovery — cities register themselves from the game's own structure data |

---

## Plug-and-Play Setup

Drop the jar in `plugins/`, start once, and walk into an Ancient City. It registers itself as its chunks load and announces it to operators. Approve it in the GUI and it's live — per-player loot, protection, and refresh all active.

```yaml
# plugins/AncientCityPro/config.yml
discovery:
  enabled: true            # find Ancient Cities automatically
  require-approval: true   # new cities wait for your OK before going live
```

> **Why approval is on by default:** auto-discovery uses the server's real structure data, so false positives are rare — but the approval step lets you eyeball each city first. Once you trust it on your world, set `require-approval: false` and discoveries go live the moment you walk in.

---

## Features

### Core Systems

- **Auto-Discovery** — cities register themselves on chunk load (plus a startup sweep), using the game's generated-structure data for exact bounds and precise chest provenance. No WorldEdit, no commands per city.
- **Per-Player Chest Loot** — Lootr-style private copies of every container, so the second player in never finds gutted chests. Chests stay chests.
- **Per-City Refresh Cycle** — the first player to loot a city starts its refresh window; when it elapses, that city's loot is fresh again. Each city runs its own timer, so they never all refresh at once (and a 50-city world doesn't reset everything simultaneously).
- **Griefing Protection** — the structure and a small margin around it are protected from breaking, placing, and explosions, while the natural Deep-Dark terrain *between* the ruins stays fully mineable.
- **Snapshots** — capture a city's structure and restore it on demand; reverts griefing and sculk spread to a pristine state. A baseline is captured automatically on approval.
- **Admin GUI** — `/acp menu` does everything. No YAML editing required.

<details>

<summary><strong>Per-player stats, bans & the loot-diff view</strong></summary>

- **Per-player stats** — containers looted, time spent in the city, deaths, and denied griefing attempts, tracked per player per city, plus live "who's inside now".
- **Loot bans** — bar a specific player from looting a specific city (they can still walk through it).
- **Loot-diff view** — click a player's head to see exactly what they took from each container: a red pane marks loot they removed (hover shows the original), a glint marks partly-taken stacks, untouched items stay plain. No need to remember what a chest originally held.
- **Quick actions** — left-click a head to view their loot, shift-left to reset it (fresh on next open), right-click to loot-ban/unban.

</details>

<details>

<summary><strong>How the renewable model works</strong></summary>

City containers are never modified. The first time anyone opens one, its loot table is rolled once into a shared **template**; every player then gets their own copy cloned from it. When a city's refresh window elapses, the next looter clears everyone's copies and a new window begins — so loot freshness is per-city, lazy (no scheduler ticking on idle cities), and naturally staggered.

Operators can sneak-open a container to **edit the shared template**, changing what every player rolls.

</details>

<details>

<summary><strong>Technical</strong> — Folia-ready, async, dual database</summary>

- **Paper / Folia / Purpur** — region-thread-correct block reads/writes throughout.
- **Async architecture** — Kotlin coroutines; database and snapshot I/O never block the main thread.
- **Dual database** — SQLite (default, zero-setup) or MySQL with connection pooling. The MySQL driver is bundled.
- **Block-data snapshots** — capture only the structure's own cells (not the whole 220³ box), gzip-compressed to disk.

</details>

---

## Requirements

| Requirement | Version |
|-------------|---------|
| **Minecraft** | 1.21.1+ (use the `-mc26` jar for 26.x) |
| **Server** | Paper, Folia, or Purpur |
| **Java** | 21+ |

No required dependencies.

---

## Commands & Permissions

<details>

<summary><strong>Commands</strong> — everything's also in <code>/acp menu</code></summary>

| Command | Description |
|---------|-------------|
| `/acp menu` | Open the admin GUI (the recommended way to do everything) |
| `/acp list` | List cities — coordinates click-to-teleport, `[menu]` opens the GUI |
| `/acp approve <id>` | Activate a pending city (captures a baseline snapshot) |
| `/acp tp <id>` · `/acp open <id>` | Teleport to / open a city |
| `/acp check` | Is the block you're looking at protected? (reports independently of your bypass) |
| `/acp snapshot <id>` · `/acp reset <id>` | Capture / restore a city's structure |
| `/acp ban\|unban\|bans <id> [player]` | Manage per-city loot bans |
| `/acp resetloot <id> <player>` | Let a player loot the city fresh |
| `/acp reload` | Reload config |

</details>

<details>

<summary><strong>Permissions</strong></summary>

| Permission | Default | Grants |
|------------|---------|--------|
| `acp.admin` | OP | All `/acp` commands and the GUI |
| `acp.discovery.notify` | OP | Notified when a city is auto-discovered |
| `acp.bypass.protection` | OP | Break/place freely inside a city |

> **Heads up:** operators bypass protection by default. If you can still break blocks in an active city, that's why — test with a non-op account, or just use `/acp check` to confirm protection coverage as an op.

</details>

---

## Support

- **Discord** — [join here](https://discord.gg/qwYcTpHsNC) for support, announcements, and feature requests.
- **GitHub Issues** — [report bugs](https://github.com/darkstarworks/AncientCityPro/issues).
- **Source** — [github.com/darkstarworks/AncientCityPro](https://github.com/darkstarworks/AncientCityPro).

---

## Target Audience

- **Survival & SMP servers** — renewable Deep-Dark content with fair loot for everyone.
- **Networks** — cities reset staggered, never all at once, so resources stay smooth.
- **Adventure / RP servers** — protected, restorable Ancient Cities as set-piece dungeons.

---

<div align="center">

**Paper 1.21.1+ / 26.x** · **Folia-ready** · **Java 21+**

Made with Kotlin by [darkstarworks](https://github.com/darkstarworks)

---

Questions, or just want to say Hi? [Join the Discord.](https://discord.gg/qwYcTpHsNC)

Prefer to stay quiet? (Anonymous) donations are **VERY** welcome: https://ko-fi.com/darkstarworks

</div>
