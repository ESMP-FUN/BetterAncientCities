# Quick Start

From a fresh install to a working Ancient City in a few steps.

## 1. Find a city

Find an Ancient City the normal way:

```
/locate structure ancient_city
```

Go there. As soon as the city's area loads, the plugin finds it and tells everyone with `bac.discovery.notify` (operators, by default):

<img width="890" height="67" alt="The chat message shown when a city is found" src="https://github.com/user-attachments/assets/cc1b5da0-e49f-4f95-8d5c-f7fddb1a858c" />

The green coordinates teleport you to the city. **[approve]** switches it on.

## 2. Approve it

A new city starts out **pending**: it is saved, but loot and protection stay off until you approve it. This gives you the chance to check it first.

Approve it in one of three ways:

* Click **[approve]** in the chat message.
* Open `/ancient menu`, click the city, then **Approve city**.
* Type `/ancient approve 1` (use the city's number from `/ancient list`).

When a city is approved, its blocks are saved as a snapshot, so you can always put the city back later.

{% hint style="info" %}
Once you trust the discovery on your world, set `discovery.require-approval: false` in `config.yml`. New cities are then active straight away.
{% endhint %}

## 3. That's it

The city is now live:

* **Every player gets their own chests.** The second player to arrive still finds full chests.
* **The city's buildings are protected** from griefing.
* **Loot refreshes** 12 hours after the city was first looted (change it with `loot.refresh-hours`).

## Managing a city

Open `/ancient menu` and click a city:

* **Teleport here.** Takes you to a safe spot in the middle of the city.
* **Player data.** Chests looted, time spent, deaths and blocked griefing for each player. Click a player to see exactly what they took from each chest. Shift-click to let them loot the city fresh. Right-click to ban them from looting it.
* **Chests.** See the loot in each chest that has been opened.
* **Refresh loot now.** Every player finds full chests again, straight away.
* **Save snapshot** and **Restore from snapshot.** Save the city's blocks, or put them back to undo griefing and sculk spread.
* **Delete city.** Shift-click to forget the city.

{% hint style="info" %}
**Changing a chest for everyone:** sneak and open it. You are then editing what every player finds in that chest. Players who have already opened it keep what they had.
{% endhint %}

See [Commands](../reference/commands.md) for the same actions as commands.
