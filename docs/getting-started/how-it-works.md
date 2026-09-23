# How It Works

A short tour of what the plugin does, so the settings in `config.yml` make sense.

## Finding cities

When part of the world loads, the plugin asks Minecraft whether an Ancient City is there. If so, it saves the city along with the exact outline of each of its buildings. There is no guessing from block types.

Because it knows the buildings exactly, the plugin can tell a city chest from a chest a player placed nearby.

If you delete a city, it is found again the next time players go near it, unless its world is in `discovery.excluded-worlds`.

## Per-player chests

The real chests in the city are never changed. The first time anyone opens one, the plugin rolls Minecraft's loot for it once and keeps that as the chest's **loot for everyone**. Each player then gets their own **copy** of it, so every player loots the full chest.

Staff with `bac.admin` can **sneak and open** a chest to change the loot for everyone. Players who have already opened that chest keep what they had until the next refresh.

Chests, trapped chests, barrels, dispensers and droppers inside the city's buildings all work this way. Hoppers can't take items out of them.

## Loot refresh

Each city has its own clock:

1. The first player to loot a city starts its clock (`loot.refresh-hours`, 12 by default).
2. Until the clock runs out, every player keeps their own copies.
3. After that, the next player to open a chest in the city refreshes it: everyone's copies are cleared and the clock starts again.

Because each clock starts when that city is first looted, cities refresh at different times instead of all at once.

{% hint style="warning" %}
Anything a player leaves in a city chest is gone after a refresh. City chests are not storage.
{% endhint %}

## Protection

Protection goes by position, not by block type. Ancient Cities are mostly plain deepslate, so a list of protected blocks would miss most of the city. Instead, every block inside a city building, and within `protection.piece-padding` blocks of one, is protected whatever it is made of. The natural rock **between** the buildings can still be mined.

Inside the protected area, players can't break or place blocks or pour buckets, fire can't burn or spread, pistons can't move blocks, mobs can't change blocks, and explosions leave the buildings alone.

Operators can still build, because they have `bac.bypass.protection`. Look at a block and type `/ancient check` to see whether it is protected and why.

## Snapshots

A snapshot saves every block of the city's buildings, including the empty space inside them. Restoring it puts all of those blocks back, which also removes sculk that has spread since.

* A snapshot is saved **when you approve a city** (`snapshot.auto-capture-on-approve`).
* Save or restore one any time with `/ancient snapshot` and `/ancient reset`, or from the menu.
* The city can also be **put back every time its loot refreshes** (`snapshot.auto-reset-on-refresh`, off by default).

Saving and restoring work through the city a chunk at a time, so even a big city does not freeze the server. It takes a few seconds.

{% hint style="warning" %}
A player standing where blocks are put back can end up inside them. Restore a city when nobody is exploring it, or warn players first.
{% endhint %}
