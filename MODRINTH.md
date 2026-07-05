<div align="center">

# AncientCityPro

### Renewable Ancient Cities for Multiplayer <br>
Auto-discovery, Per-player loot, Griefing-protected, Fully restorable.

<br>

**Got an "if it did [THING], I'd use it" idea? Tell me!** [<img src="https://raw.githubusercontent.com/darkstarworks/TrialChamberPro/master/dc.png" width="20" alt="Join Discord Server">](https://discord.gg/qwYcTpHsNC) <br>

Donating is Free! (for me): [ [Ko-Fi](https://ko-fi.com/darkstarworks) ]

</div>

<br>

> **Standalone** <br>
> AncientCityPro does **not** require TrialChamberPro or any (of my) other plugin(s). <br>
> Run them side by side if you like; they don't interfere.

<br>

**Full documentation:** https://darkstarworks.gitbook.io/plugins/mc/acp-documentation

<br>

## Why AncientCityPro?

| Problem | Solution |
|---------|----------|
| First player takes all the loot | Per-player chest copies — everyone loots the full city, independently |
| Looted chests stay empty forever | Per-city refresh cycle — the city's loot comes back on a timer |
| Griefers hollow out the structure | Bounds-based griefing protection for the whole structure |
| Sculk spreads / city gets damaged | Snapshot + restore returns it to pristine on demand |
| No way to reset a 220-block ruin | One-click reset (or auto-restore on the refresh cycle) |
| Setup overhead per city | Auto-discovery — cities register themselves from the game's own structure data |

<br>

## Plug-and-Play Setup

Drop the jar in `plugins/`, (re)start your server and when an Ancient City is detected, <br>
AncientCityPro saves it to your server and announces its find to operators. <br>
Confirm it right from the chat or later in the GUI to activate per-player loot, refresh and protection.

<br>

```yaml
# plugins/AncientCityPro/config.yml  -  default configuration
discovery:
  enabled: true            # find Ancient Cities automatically
  require-approval: true   # new cities wait for your OK before going live
```

> **Why is approval on by default?** 
> Auto-discovery uses the server's real structure data, so false positives are rare, but not impossible. <br>

<br>

I recommend you simply follow these steps: 
1. As you see this message:
<img width="520" height="40" alt="image" src="https://github.com/user-attachments/assets/d7334c3b-a621-451b-91b9-24c0387b8fbc" />

2. You click the [coordinates] to teleport there 
3. Quickly inspect the area to verify the dimensions are correct
4. Click [approve]

That's all of the required setup. Sorry if you hoped for more.

<br>

## Features

### Core Systems

- **Auto-Discovery** — cities register themselves on chunk load (plus a startup sweep), using the game's generated-structure data for exact bounds and precise chest provenance. (No WorldEdit, no commands per city.)
- **Per-Player Chest Loot** — Lootr-style private copies of every container, so the second player in, never finds gutted chests.
- **Per-City Refresh Cycle** — the first player to loot a city starts its refresh window; when it elapses, that city's loot is fresh again. 
> Each city runs its own timer, so they never all refresh at once (and a 50-city world doesn't reset everything simultaneously).
- **Griefing Protection** — the structures and a small margin around them are protected from breaking, placing, and explosions, while the natural Deep-Dark terrain *between* the ruins stays fully mineable.
- **Snapshots** — capture a city's structure and restore it on demand; reverts griefing and sculk spread to a pristine state. A baseline is captured automatically after an auto-discovery gets approved.
- **Admin GUI** — `/acp menu` has everything. No YAML editing required. I'm not even sure why I included commands!
<br>
<details>

<summary><strong>Per-player stats, bans & the loot-diff view</strong></summary>

- **Per-player stats** — containers looted, time spent in the city, deaths, and denied griefing attempts, tracked per player per city, plus live "who's inside now".
- **Loot bans** — bar a specific player from looting a specific city (they can still walk through it).
- **Loot-diff view** — click a player's head to see exactly what they took from each container: a red pane marks loot they removed (hover shows the original), a glint marks partly-taken stacks, untouched items stay plain. No need to remember what a chest originally held.
- **Quick actions** — left-click a head to view their loot, shift-left to reset it (fresh on next open), right-click to loot-ban/unban.

</details>
<br>
<details>

<summary><strong>How the renewable model works</strong></summary>

City containers are never modified. The first time anyone opens one, its loot table is rolled once into a shared **template**; every player then gets their own copy cloned from it. When a city's refresh window elapses, the next looter clears everyone's copies and a new window begins — so loot freshness is per-city, lazy (no scheduler ticking on idle cities), and naturally staggered.

Operators can sneak-open a container to **edit the shared template**, changing what every player rolls.

</details>
<br>
<details>

<summary><strong>Technical</strong> — Folia-ready, async, dual database</summary>

- **Paper / Folia / Purpur** — region-thread-correct block reads/writes throughout.
- **Async architecture** — Kotlin coroutines; database and snapshot I/O never block the main thread.
- **Dual database** — SQLite (default, zero-setup) or MySQL with connection pooling. The MySQL driver is bundled.
- **Block-data snapshots** — capture only the structure's own cells (not the whole 220³ box), gzip-compressed to disk.

</details>

<br>

## Requirements

| Requirement | Version |
|-------------|---------|
| **Minecraft** | 1.21.1+ (use the `-mc26` jar for 26.x) |
| **Server** | Paper, Folia, or Purpur |
| **Java** | 21+ |

No required dependencies.

<br>

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

<br>

## Support

- **Discord** — [join here](https://discord.gg/qwYcTpHsNC) for support, announcements, and feature requests.
- **GitHub Issues** — [report bugs](https://github.com/darkstarworks/AncientCityPro/issues).
- **Source** — [github.com/darkstarworks/AncientCityPro](https://github.com/darkstarworks/AncientCityPro).

<br>

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

Did you know I have other plugins? Check them out on Modrinth [ [here](https://modrinth.com/organization/esmp) ] and [ [here](https://modrinth.com/user/darkstarworks) ] <br>

Donating is free! (for me): [Ko-Fi](https://ko-fi.com/darkstarworks)

</div>
