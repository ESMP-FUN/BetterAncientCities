# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [2.1.0] - 2026-07-20
### Changed
- **Usage metrics moved from bStats to FastStats.** Same aggregate config/feature data, still no player data. The server-wide opt-out is now `plugins/faststats/config.properties`; `metrics.enabled: false` in config.yml is unchanged. City count is now reported as a plain number rather than a pre-bucketed range.

### Added
- **Opt-in error reporting.** New `metrics.error-reporting` in config.yml, **off by default**, sends stack traces from BetterAncientCities so bugs get fixed without needing a report. Kept separate from `metrics.enabled` because a stack trace can include file paths, unlike the aggregate usage metrics.

## [2.0.0] - 2026-07-11
### Changed
- **The plugin is now Better Ancient Cities.** Same free plugin, new name — part of the ESMP rebrand. The project now lives at https://github.com/ESMP-FUN/BetterAncientCities.
- Main command is `/ancient` (aliases `/bac`, legacy `/acp`, `/ancientcity`). Permissions moved from `acp.*` to `bac.*`; old grants keep working as legacy aliases.
- Internal packages moved to `com.esmpfun.betterancientcities`.

### Added
- Automatic data-folder migration from `plugins/AncientCityPro/` on first start (old folder kept as backup).

## [1.1.1] - 2026-07-10
### Added
- **Anonymous usage metrics (bStats).** Aggregate config/feature usage only — no player data. Opt out via `metrics.enabled: false` in config.yml or globally in `plugins/bStats/config.yml`.
- **World exclusion.** New `discovery.excluded-worlds` list in config.yml keeps ACP from registering Ancient Cities in named worlds — useful when a second overworld-type world has cities you want to leave vanilla. Cities registered before a world was excluded keep working; remove them with `/ancient delete <id>`.

### Changed
- Updated the bundled PluginPulse updater to v0.8.0 (configurable source order, authenticated private-repo updates, Jenkins update source).

## [1.1.0] - 2026-07-05
### Added
- **Built-in update checking.** BetterAncientCities now checks GitHub Releases for new versions and notifies admins. The new `/ancient update` command adds `check`, `download` (fetch a new build, verify its checksum, back up the current jar, and stage it in the server's update folder to install on the next restart), `restore` (roll back), and `status`. Default `notify` (announce only) — set `update.mode` to `download` or `auto-stage` in config.yml to enable installs. Powered by [PluginPulse](https://github.com/darkstarworks/PluginPulse).

## [1.0.2] - 2026-06-30
### Fixed
- **Approving a city gave no feedback until its baseline snapshot finished — so the `[approve]` link could be clicked several times, each firing another approval/snapshot.** Approving now responds instantly (chat link, `/ancient approve`, or the GUI button): a confirmation line, an animated busy indicator on the action bar while the snapshot is captured, then a result line. Repeat clicks while an approval is already running are ignored ("already approving #N…"), so a slow capture can no longer pile up duplicate approvals.

## [1.0.1] - 2026-06-30
### Changed
- **The auto-discovery alert now matches the `/ancient list` format — and acts straight from chat.** A newly-discovered city is announced as a `/ancient list`-style line: clickable green coordinates teleport you to the city, and a yellow **[approve]** (or **[menu]** once active) lets you approve/open it without typing — click the coords, look it over, click approve. Bold text was removed from both the alert and `/ancient list`; the colours stay, at normal weight.

## [1.0.0] - 2026-06-30
### Added
- **Initial release.** Turns naturally-generated Ancient Cities into renewable, multiplayer-ready content. Standalone — no dependency on TrialChamberPro or any other plugin.
- **Auto-discovery** — cities register themselves as their chunks load (plus a startup sweep over already-loaded chunks), using the server's own generated-structure data for exact bounds and precise chest provenance. Discovered cities are **pending** until an operator approves them (`discovery.require-approval`, default on); flip it off to make them active on contact.
- **Per-player chest loot** — every player who opens a city container gets their own private copy (Lootr-style), so the second player in never finds gutted chests. Containers are never modified; a shared template is rolled once and operators can sneak-open a container to edit it.
- **Per-city refresh cycle** — the first player to loot a city starts its refresh window (`loot.refresh-hours`, default 12); when it elapses, that city's loot is fresh again. Each city runs its own timer, so cities refresh staggered rather than all at once.
- **Griefing protection** — bounds-based, per structure piece (expanded by `protection.piece-padding`): the structure is protected from breaking, placing, and explosions regardless of block type, while the natural Deep-Dark terrain between the ruins stays mineable.
- **Snapshots** — capture a city's structure and restore it on demand, reverting griefing and sculk spread to a pristine state. A baseline is captured automatically on approval; an optional `snapshot.auto-reset-on-refresh` ties a restore to the loot cycle.
- **Admin GUI** (`/ancient menu`) — browse cities, teleport, approve, capture/reset snapshots, see live occupancy, view per-player stats, inspect exactly what each player looted (with a visual diff against the original), and ban/unban — no YAML editing required.
- **Per-player stats & loot bans** — containers looted, time in city, deaths, and denied griefing attempts, tracked per player per city; bar a player from looting a specific city.
- **`/acp` command suite** — `menu`, `list`, `info`, `approve`, `delete`, `tp`, `open`, `check`, `snapshot`, `reset`, `ban`/`unban`/`bans`, `resetloot`, `reload`. `/ancient check` reports whether a block is protected independently of your operator bypass.
- **SQLite (default) or MySQL** storage with connection pooling; the MySQL driver is bundled.
- **Paper / Folia / Purpur**, Java 21+. A separate `-mc26` jar targets Minecraft 26.x.

[1.0.2]: https://github.com/ESMP-FUN/BetterAncientCities/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/ESMP-FUN/BetterAncientCities/compare/v1.0.0...v1.0.1
