# Quick Start

From a fresh install to a fully-managed, renewable Ancient City in a few steps.

## 1. Find a city

Locate an Ancient City the normal way:

```
/locate structure ancient_city
```

Travel there. As its chunks load, AncientCityPro discovers it automatically and announces it to online operators:

```
[AncientCityPro] Discovered an Ancient City #1 at -781, -52, -426 in world — pending approval.
```

## 2. Approve it

By default a discovered city is **pending** — its loot and protection stay inactive until you approve it. This is a safety step so a mis-detection never goes live on its own.

Open the admin GUI and approve it:

```
/acp menu
```

Click the city → **Approve city**. (Or from the command line: `/acp approve 1`.)

On approval, a **baseline snapshot** is captured automatically, so you always have a restore point.

<div data-gb-custom-block data-tag="hint" data-style="info">

Once you've confirmed discovery works well on your world, set `discovery.require-approval: false` in `config.yml` to make new cities active immediately.

</div>

## 3. That's it

The city is now live:

* **Players loot chests** and each gets their own private copy — no more gutted containers for the second player in.
* **The structure is protected** from griefing.
* **Loot refreshes** per the `loot.refresh-hours` window (default 12h), per city.

## Managing a city

Everything is in `/acp menu` → click a city:

* **Teleport** to it.
* **Capture snapshot** / **Reset to snapshot** — save the structure, or restore it (reverts griefing and sculk spread).
* **Refresh loot now** — clear everyone's loot copies so the city is fresh immediately.
* **Player data** — per-player stats (looted, time, deaths, griefing attempts); left-click a head to see exactly what they looted (with a visual diff vs. the original), shift-left-click to reset their loot, right-click to loot-ban them.
* **Containers** — browse every container and inspect its loot.

See [Commands](../reference/commands.md) for the full command-line equivalents.
