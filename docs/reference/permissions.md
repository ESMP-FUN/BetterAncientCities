# Permissions

## How to grant a permission

1. Install a permissions plugin (LuckPerms is the common choice).
2. Grant a node to a player or group. With LuckPerms:
   - One player: `/lp user Steve permission set bac.admin true`
   - A group: `/lp group builder permission set bac.bypass.protection true`
3. To take a node away, set it to `false`.

**Defaults if you grant nothing:** operators get all three nodes. Other players get none, and need none to loot a city.

## All permission nodes

| Node | What it grants | Default |
| --- | --- | --- |
| `bac.admin` | Every `/ancient` command and the menu. Also lets you sneak and open a city chest to change its loot for everyone. | op |
| `bac.discovery.notify` | A chat message when a new Ancient City is found. | op |
| `bac.bypass.protection` | Break and place blocks inside protected cities. | op |

## Old `acp.*` names

The names from before 2.0 (`acp.admin`, `acp.discovery.notify`, `acp.bypass.protection`) still work. Each one only grants its `bac.*` counterpart and defaults to `false`, so it does nothing unless you grant it. Use the `bac.*` names for anything new.

{% hint style="warning" %}
Operators have every permission, including `bac.bypass.protection`. That's why an operator can still break blocks in an active city. To test protection yourself, use a player who is not an operator, take the bypass away for a moment:

```
/lp user <name> permission set bac.bypass.protection false
```

or simply look at a block and type `/ancient check`.
{% endhint %}

To let staff manage cities without making them operators, give them `bac.admin` (and `bac.discovery.notify` if they should hear about new cities). Leave `bac.bypass.protection` to the people who really need to build inside cities.
