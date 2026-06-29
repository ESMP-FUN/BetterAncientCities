# How It Works

A short tour of the mechanics, so the settings in `config.yml` make sense.

## Discovery

When a chunk loads, AncientCityPro asks the server whether an Ancient City structure overlaps it (using the game's own generated-structure data). If so, it registers the city with **exact bounds** — the union of the structure's individual pieces — and records each piece's bounding box. There's no block-scanning or guesswork.

Because it uses the real structure data, the plugin knows precisely which chests belong to the city versus a chest a player placed nearby.

## Per-player loot

City containers are never modified. The first time anyone opens one, the plugin rolls its loot table once and stores that as a shared **template**. Every player then gets their own private **copy** cloned from the template — so each player loots the full chest, independently.

Operators holding `acp.admin` can **sneak-open** a container to edit the shared template; normal players always get their own copy.

## The refresh cycle

Loot freshness is per-city and lazy — there's no scheduler ticking on every city:

1. The first player to loot a city with no active cycle starts a refresh window (`loot.refresh-hours`, default 12).
2. During the window, everyone still gets their own private copy.
3. When the window elapses, the next player to loot triggers a refresh — all copies are cleared — and a new window begins.

Because each city's timer starts when *it* is first looted, cities refresh staggered across time rather than all at once.

## Protection

Protection is **bounds-based per structure piece**, not material-based. Ancient cities are built largely from plain deepslate and basalt, so a material allow-list would leave most of the structure exposed. Instead, any block inside a structure piece (expanded by a small `protection.piece-padding`) is protected, regardless of type — while the natural deep-dark terrain *between* the scattered ruins stays fully mineable.

Operators bypass protection by default (`acp.bypass.protection`). Use `/acp check` while looking at a block to see whether it's protected and why.

## Snapshots

A snapshot captures the block data of every cell inside the city's structure pieces (a true reset point, including air, so restoring removes sculk that has spread). Restoring rewrites those blocks back to the captured state.

* A **baseline snapshot is captured on approval** (`snapshot.auto-capture-on-approve`).
* You can re-capture or restore any time via the GUI or `/acp snapshot` / `/acp reset`.
* Optionally, the city can **auto-restore when its loot cycle refreshes** (`snapshot.auto-reset-on-refresh`, off by default).
