# Permissions

| Permission | Default | Grants |
| --- | --- | --- |
| `acp.admin` | op | All `/acp` admin commands and the GUI. |
| `acp.discovery.notify` | op | In-game notification when an Ancient City is auto-discovered. |
| `acp.bypass.protection` | op | Bypass city griefing protection — break and place freely inside a city. |

## Notes

<div data-gb-custom-block data-tag="hint" data-style="warning">

Operators have **every** permission by default, including `acp.bypass.protection`. That's why an op can still break structure blocks even on an active city — it's intended. To test protection as yourself, use a non-op account, or explicitly negate the bypass with a permissions plugin:

```
/lp user <name> permission set acp.bypass.protection false
```

Or just use `/acp check` while looking at a block — it reports protection independently of your bypass.

</div>

To let trusted staff manage cities without full operator status, grant `acp.admin` (and optionally `acp.discovery.notify`) via your permissions plugin and leave `acp.bypass.protection` to operators only.
