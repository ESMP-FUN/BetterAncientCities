# Welcome

Turn Minecraft's Ancient Cities from one-and-done loot runs into renewable, multiplayer-ready content. Per-player chest loot, a per-city refresh cycle, griefing protection, and full structure snapshots — all discovered and managed automatically, with no per-city setup.

***

## The problem it solves

A naturally-generated Ancient City is single-use on a multiplayer server. The first player loots every chest, the structure gets griefed or hollowed out, and there's nothing left for anyone else. Because cities sit in the Deep Dark and span ~220 blocks of irregular ruins, there's no practical way to "reset" one by hand.

AncientCityPro fixes that: every player gets their own private roll of each chest, the city's loot refreshes on a per-city timer, the structure is protected from griefing, and you can snapshot and restore the whole thing — reverting both griefing and runaway sculk spread.

***

## What you can do

* **Auto-discovery** — the plugin finds Ancient Cities as their chunks load and registers them, using the server's own structure data for exact bounds. No WorldEdit, no commands per city.
* **Per-player chest loot** — every player who opens a city chest sees their own private copy, so the second player in never finds gutted containers. Chests stay chests.
* **Per-city refresh cycle** — the first player to loot a city starts a refresh window; when it elapses, that city's loot is fresh again. Each city is on its own timer, so they never all refresh at once.
* **Griefing protection** — the structure (and a small margin around it) is protected from breaking, placing, and explosions, while the natural deep-dark terrain around it stays fully mineable.
* **Snapshots** — capture a city's structure and restore it on demand, reverting griefing and sculk spread to a pristine state.
* **Per-player stats** — containers looted, time in city, deaths, and denied griefing attempts, all per player per city.
* **Loot bans** — bar a specific player from looting a specific city (they can still walk through it).
* **Admin GUI** — `/acp menu` handles everything: browse cities, teleport, approve, snapshot/reset, view per-player stats, inspect what each player looted (with a visual diff vs. the original), and ban/unban.

***

## Requirements

* **Minecraft 1.21.1+** (use the `-mc26` build for Minecraft 26.x)
* **Paper, Folia, or Purpur**
* **Java 21+**

AncientCityPro is standalone — it does **not** require TrialChamberPro or any other plugin.

***

## Next steps

<div data-gb-custom-block data-tag="content-ref" data-url="getting-started/installation.md">

[installation.md](getting-started/installation.md)

</div>

<div data-gb-custom-block data-tag="content-ref" data-url="getting-started/quick-start.md">

[quick-start.md](getting-started/quick-start.md)

</div>
