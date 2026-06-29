# Troubleshooting

## "I can still break blocks in an approved city"

You're almost certainly an **operator**, and operators have `acp.bypass.protection` by default. This is intended (admins need to build/fix). Confirm protection is actually working with:

```
/acp check
```

while looking at a structure block — it reports protection independently of your bypass. To test as a normal player, use a non-op account or negate the permission (see [Permissions](reference/permissions.md)).

## A discovered city isn't active

New cities are **pending** until approved (`discovery.require-approval: true`). Approve it in `/acp menu` or with `/acp approve <id>`. To make discoveries active immediately, set `discovery.require-approval: false`.

## Nothing gets discovered

* Discovery is driven by chunk loads — travel into the city, or rely on the startup sweep for already-loaded chunks.
* Confirm `discovery.enabled: true`.
* Discovery only runs in **Overworld-type** worlds (the Deep Dark).

## A snapshot restore leaves some blocks out

Re-capture after any change to `protection.piece-padding`, since capture covers the padded piece region. The console logs `stored N/total` on capture and `placed N/total` on restore; if there are failures it names the first few with coordinates — check the server log for `[Snapshot]` warnings.

## Loot never refreshes

The refresh cycle is **per-city and starts on first-loot**. If no one has looted a city since it was approved, its timer hasn't started. Check `loot.refresh-hours` (0 disables refresh). You can always force it with **Refresh loot now** in the GUI.

## Config changes don't apply

Run `/acp reload` after editing `config.yml`. If the file won't load at all, check for **TAB characters** — YAML requires spaces only.

## Reporting bugs

Include your server version (Paper/Folia + Minecraft version), the AncientCityPro version, and any `[AncientCityPro]` / `[Snapshot]` lines from the console.
