# Troubleshooting

## "I can still break blocks in an approved city"

You are almost certainly an **operator**, and operators have `bac.bypass.protection`, so staff can repair and build. Look at a building block and type:

```
/ancient check
```

It tells you whether the block is protected, ignoring your own bypass. To test as a normal player, use an account that is not an operator, or take the permission away (see [Permissions](reference/permissions.md)).

## A city was found but nothing happens

New cities are **pending** until approved (`discovery.require-approval: true`). Approve it in `/ancient menu` or with `/ancient approve <number>`. To make new cities active straight away, set `discovery.require-approval: false`.

## No cities are found

* Cities are found as their area loads. Go near one, or let the check at startup find the ones that are already loaded.
* Make sure `discovery.enabled` is `true`.
* Make sure the world is not in `discovery.excluded-worlds`.
* Only overworld-type worlds are checked, because that is where Ancient Cities are.

## A deleted city came back

A deleted city is found again the next time players go near it. To keep the plugin out of a whole world, add the world to `discovery.excluded-worlds`, then delete its cities.

## A chest says "This chest could not be opened right now"

The plugin could not read the database for a moment. Nothing was lost. Try again; if it keeps happening, the console says why.

If the console says a player's copy "could not be read", give them a fresh one with `/ancient resetloot <number> <player>`.

## A restore leaves some blocks out

Save a new snapshot after changing `protection.piece-padding`, because a snapshot covers the area that was protected when it was saved. The console says how many blocks were saved and restored, and names any that failed.

## Loot never refreshes

Each city's clock starts when the city is **first looted**. If nobody has looted it since it was approved, its clock has not started. Check `loot.refresh-hours` (0 turns refreshing off). **Refresh loot now** in the menu refreshes a city straight away.

## Settings don't apply

Type `/ancient reload` after editing `config.yml`. The `database`, `update` and `metrics` sections need a restart. If the file won't load at all, look for **TAB characters**; YAML only allows spaces.

## "Better Ancient Cities could not start"

The database could not be opened. The console lines just above say why. With `type: mysql`, check the host, port, database name, username and password in `config.yml`, and that the MySQL server is running.

## Reporting bugs

Include your server software and Minecraft version, the Better Ancient Cities version, and any `[BetterAncientCities]` lines from the console.
