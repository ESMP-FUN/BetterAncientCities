[CENTER]
[SIZE=7][B]BetterAncientCities[/B][/SIZE]

[SIZE=5][B]Renewable Ancient Cities for Multiplayer[/B][/SIZE]
Auto-discovery, Per-player loot, Griefing-protected, Fully restorable.

[B]Got an "if it did [THING], I'd use it" idea? Tell me![/B] [URL='https://discord.gg/qwYcTpHsNC'][IMG]https://raw.githubusercontent.com/darkstarworks/TrialChamberPro/master/dc.png[/IMG][/URL]

Donating is Free! (for me): [ [URL='https://ko-fi.com/darkstarworks']Ko-Fi[/URL] ]
[/CENTER]

[QUOTE]
[B]Standalone[/B]
BetterAncientCities does [B]not[/B] require TrialChamberPro or any (of my) other plugin(s).
Run them side by side if you like; they don't interfere.
[/QUOTE]

[B]Full documentation:[/B] [URL]https://esmp-fun.gitbook.io/plugins/better-ancient-cities[/URL]

[SIZE=6][B]Why BetterAncientCities?[/B][/SIZE]

[LIST]
[*][B]First player takes all the loot[/B] → Per-player chest copies — everyone loots the full city, independently
[*][B]Looted chests stay empty forever[/B] → Per-city refresh cycle — the city's loot comes back on a timer
[*][B]Griefers hollow out the structure[/B] → Bounds-based griefing protection for the whole structure
[*][B]Sculk spreads / city gets damaged[/B] → Snapshot + restore returns it to pristine on demand
[*][B]No way to reset a 220-block ruin[/B] → One-click reset (or auto-restore on the refresh cycle)
[*][B]Setup overhead per city[/B] → Auto-discovery — cities register themselves from the game's own structure data
[/LIST]

[SIZE=6][B]Plug-and-Play Setup[/B][/SIZE]

Drop the jar in [ICODE]plugins/[/ICODE], (re)start your server and when an Ancient City is detected,
BetterAncientCities saves it to your server and announces its find to operators.
Confirm it right from the chat or later in the GUI to activate per-player loot, refresh and protection.

[CODE=yaml]
# plugins/BetterAncientCities/config.yml  -  default configuration
discovery:
  enabled: true            # find Ancient Cities automatically
  require-approval: true   # new cities wait for your OK before going live
[/CODE]

[QUOTE]
[B]Why is approval on by default?[/B]
Auto-discovery uses the server's real structure data, so false positives are rare, but not impossible.
[/QUOTE]

I recommend you simply follow these steps:
[LIST=1]
[*]As you see this message:
[IMG]https://github.com/user-attachments/assets/d7334c3b-a621-451b-91b9-24c0387b8fbc[/IMG]
[*]You click the [coordinates] to teleport there
[*]Quickly inspect the area to verify the dimensions are correct
[*]Click [approve]
[/LIST]

That's all of the required setup. Sorry if you hoped for more.

[SIZE=6][B]Features[/B][/SIZE]

[SIZE=5][B]Core Systems[/B][/SIZE]

[LIST]
[*][B]Auto-Discovery[/B] — cities register themselves on chunk load (plus a startup sweep), using the game's generated-structure data for exact bounds and precise chest provenance. (No WorldEdit, no commands per city.)
[*][B]Per-Player Chest Loot[/B] — Lootr-style private copies of every container, so the second player in, never finds gutted chests.
[*][B]Per-City Refresh Cycle[/B] — the first player to loot a city starts its refresh window; when it elapses, that city's loot is fresh again.
Each city runs its own timer, so they never all refresh at once (and a 50-city world doesn't reset everything simultaneously).
[*][B]Griefing Protection[/B] — the structures and a small margin around them are protected from breaking, placing, and explosions, while the natural Deep-Dark terrain [I]between[/I] the ruins stays fully mineable.
[*][B]Snapshots[/B] — capture a city's structure and restore it on demand; reverts griefing and sculk spread to a pristine state. A baseline is captured automatically after an auto-discovery gets approved.
[*][B]Admin GUI[/B] — [ICODE]/ancient menu[/ICODE] has everything. No YAML editing required. I'm not even sure why I included commands!
[/LIST]

[SPOILER='Per-player stats, bans & the loot-diff view']
[LIST]
[*][B]Per-player stats[/B] — containers looted, time spent in the city, deaths, and denied griefing attempts, tracked per player per city, plus live "who's inside now".
[*][B]Loot bans[/B] — bar a specific player from looting a specific city (they can still walk through it).
[*][B]Loot-diff view[/B] — click a player's head to see exactly what they took from each container: a red pane marks loot they removed (hover shows the original), a glint marks partly-taken stacks, untouched items stay plain. No need to remember what a chest originally held.
[*][B]Quick actions[/B] — left-click a head to view their loot, shift-left to reset it (fresh on next open), right-click to loot-ban/unban.
[/LIST]
[/SPOILER]

[SPOILER='How the renewable model works']
City containers are never modified. The first time anyone opens one, its loot table is rolled once into a shared [B]template[/B]; every player then gets their own copy cloned from it. When a city's refresh window elapses, the next looter clears everyone's copies and a new window begins — so loot freshness is per-city, lazy (no scheduler ticking on idle cities), and naturally staggered.

Operators can sneak-open a container to [B]edit the shared template[/B], changing what every player rolls.
[/SPOILER]

[SPOILER='Technical — Folia-ready, async, dual database']
[LIST]
[*][B]Paper / Folia / Purpur[/B] — region-thread-correct block reads/writes throughout.
[*][B]Async architecture[/B] — Kotlin coroutines; database and snapshot I/O never block the main thread.
[*][B]Dual database[/B] — SQLite (default, zero-setup) or MySQL with connection pooling. The MySQL driver is bundled.
[*][B]Block-data snapshots[/B] — capture only the structure's own cells (not the whole 220³ box), gzip-compressed to disk.
[/LIST]
[/SPOILER]

[SIZE=6][B]Requirements[/B][/SIZE]

[LIST]
[*][B]Minecraft[/B] — 1.21.1+ (use the [ICODE]-mc26[/ICODE] jar for 26.x)
[*][B]Server[/B] — Paper, Folia, or Purpur
[*][B]Java[/B] — 21+
[/LIST]

No required dependencies.

[SIZE=6][B]Commands & Permissions[/B][/SIZE]

[SPOILER="Commands — everything's also in /ancient menu"]
[LIST]
[*][ICODE]/ancient menu[/ICODE] — Open the admin GUI (the recommended way to do everything)
[*][ICODE]/ancient list[/ICODE] — List cities — coordinates click-to-teleport, [ICODE][menu][/ICODE] opens the GUI
[*][ICODE]/ancient approve <id>[/ICODE] — Activate a pending city (captures a baseline snapshot)
[*][ICODE]/ancient tp <id>[/ICODE] · [ICODE]/ancient open <id>[/ICODE] — Teleport to / open a city
[*][ICODE]/ancient check[/ICODE] — Is the block you're looking at protected? (reports independently of your bypass)
[*][ICODE]/ancient snapshot <id>[/ICODE] · [ICODE]/ancient reset <id>[/ICODE] — Capture / restore a city's structure
[*][ICODE]/ancient ban|unban|bans <id> [player][/ICODE] — Manage per-city loot bans
[*][ICODE]/ancient resetloot <id> <player>[/ICODE] — Let a player loot the city fresh
[*][ICODE]/ancient reload[/ICODE] — Reload config
[/LIST]
[/SPOILER]

[SPOILER='Permissions']
[LIST]
[*][ICODE]acp.admin[/ICODE] (default: OP) — All [ICODE]/acp[/ICODE] commands and the GUI
[*][ICODE]acp.discovery.notify[/ICODE] (default: OP) — Notified when a city is auto-discovered
[*][ICODE]acp.bypass.protection[/ICODE] (default: OP) — Break/place freely inside a city
[/LIST]

[QUOTE]
[B]Heads up:[/B] operators bypass protection by default. If you can still break blocks in an active city, that's why — test with a non-op account, or just use [ICODE]/ancient check[/ICODE] to confirm protection coverage as an op.
[/QUOTE]
[/SPOILER]

[SIZE=6][B]Support[/B][/SIZE]

[LIST]
[*][B]Discord[/B] — [URL='https://discord.gg/qwYcTpHsNC']join here[/URL] for support, announcements, and feature requests.
[*][B]GitHub Issues[/B] — [URL='https://github.com/ESMP-FUN/BetterAncientCities/issues']report bugs[/URL].
[*][B]Source[/B] — [URL='https://github.com/ESMP-FUN/BetterAncientCities']github.com/ESMP-FUN/BetterAncientCities[/URL].
[/LIST]

[SIZE=6][B]Target Audience[/B][/SIZE]

[LIST]
[*][B]Survival & SMP servers[/B] — renewable Deep-Dark content with fair loot for everyone.
[*][B]Networks[/B] — cities reset staggered, never all at once, so resources stay smooth.
[*][B]Adventure / RP servers[/B] — protected, restorable Ancient Cities as set-piece dungeons.
[/LIST]

[CENTER]
[B]Paper 1.21.1+ / 26.x[/B] · [B]Folia-ready[/B] · [B]Java 21+[/B]

Made with Kotlin by [URL='https://github.com/darkstarworks']darkstarworks[/URL]

Questions, or just want to say Hi? [URL='https://discord.gg/qwYcTpHsNC']Join the Discord.[/URL]

Did you know I have other plugins? Check them out on Modrinth [ [URL='https://modrinth.com/organization/esmp']here[/URL] ] and [ [URL='https://modrinth.com/user/darkstarworks']here[/URL] ]

Donating is free! (for me): [URL='https://ko-fi.com/darkstarworks']Ko-Fi[/URL]
[/CENTER]
