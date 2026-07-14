# Permissions

| Permission | Default | Grants |
| --- | --- | --- |
| `bac.admin` | op | All `/ancient` admin commands and the GUI. |
| `bac.discovery.notify` | op | In-game notification when an Ancient City is auto-discovered. |
| `bac.bypass.protection` | op | Bypass city griefing protection — break and place freely inside a city. |

## Notes

<div data-gb-custom-block data-tag="hint" data-style="warning">

Operators have **every** permission by default, including `bac.bypass.protection`. That's why an op can still break structure blocks even on an active city — it's intended. To test protection as yourself, use a non-op account, or explicitly negate the bypass with a permissions plugin:

```
/lp user <name> permission set bac.bypass.protection false
```

Or just use `/ancient check` while looking at a block — it reports protection independently of your bypass.

</div>

To let trusted staff manage cities without full operator status, grant `bac.admin` (and optionally `bac.discovery.notify`) via your permissions plugin and leave `bac.bypass.protection` to operators only.
