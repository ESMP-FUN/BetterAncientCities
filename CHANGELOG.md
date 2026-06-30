# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [1.0.1] - 2026-06-30
### Changed
- **The auto-discovery alert now matches the `/acp list` format — and acts straight from chat.** A newly-discovered city is announced as a `/acp list`-style line: clickable green coordinates teleport you to the city, and a yellow **[approve]** (or **[menu]** once active) lets you approve/open it without typing — click the coords, look it over, click approve. Bold text was removed from both the alert and `/acp list`; the colours stay, at normal weight.

## [1.0.0] - 2026-06-30
### Added
- **Initial release.** Turns naturally-generated Ancient Cities into renewable, multiplayer-ready content. Standalone — no dependency on TrialChamberPro or any other plugin.
- **Auto-discovery** — cities register themselves as their chunks load (plus a startup sweep over already-loaded chunks), using the server's own generated-structure data for exact bounds and precise chest provenance. Discovered cities are **pending** until an operator approves them (`discovery.require-approval`, default on); flip it off to make them active on contact.
- **Per-player chest loot** — every player who opens a city container gets their own private copy (Lootr-style), so the second player in never finds gutted chests. Containers are never modified; a shared template is rolled once and operators can sneak-open a container to edit it.
- **Per-city refresh cycle** — the first player to loot a city starts its refresh window (`loot.refresh-hours`, default 12); when it elapses, that city's loot is fresh again. Each city runs its own timer, so cities refresh staggered rather than all at once.
- **Griefing protection** — bounds-based, per structure piece (expanded by `protection.piece-padding`): the structure is protected from breaking, placing, and explosions regardless of block type, while the natural Deep-Dark terrain between the ruins stays mineable.
- **Snapshots** — capture a city's structure and restore it on demand, reverting griefing and sculk spread to a pristine state. A baseline is captured automatically on approval; an optional `snapshot.auto-reset-on-refresh` ties a restore to the loot cycle.
- **Admin GUI** (`/acp menu`) — browse cities, teleport, approve, capture/reset snapshots, see live occupancy, view per-player stats, inspect exactly what each player looted (with a visual diff against the original), and ban/unban — no YAML editing required.
- **Per-player stats & loot bans** — containers looted, time in city, deaths, and denied griefing attempts, tracked per player per city; bar a player from looting a specific city.
- **`/acp` command suite** — `menu`, `list`, `info`, `approve`, `delete`, `tp`, `open`, `check`, `snapshot`, `reset`, `ban`/`unban`/`bans`, `resetloot`, `reload`. `/acp check` reports whether a block is protected independently of your operator bypass.
- **SQLite (default) or MySQL** storage with connection pooling; the MySQL driver is bundled.
- **Paper / Folia / Purpur**, Java 21+. A separate `-mc26` jar targets Minecraft 26.x.
