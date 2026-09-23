<div align="center">

# Better Ancient Cities

Free forever.<br>

[![Discord](https://img.shields.io/badge/join_%E2%86%92_-_Discord-gray?style=flat&logo=discord&logoSize=amd)](https://discord.gg/qwYcTpHsNC)
[![Ko-Fi](https://img.shields.io/badge/support_%E2%86%92_-_KoFi-gray?style=flat&logo=kofi&logoSize=amd)](https://ko-fi.com/darkstarworks)

Turn one-time Ancient Cities into loot runs every player gets to enjoy.

<br>

| **Vanilla** | **With this plugin** |
|---------|------------------|
| First player empties every chest | Every player gets their own chests |
| Chests stay empty forever | Loot comes back on a timer |
| The city gets dug out and burned | Protected |
| Sculk spreads over everything | Put the city back as it was, in one click |
| Set up each city by hand | Found automatically |

</div>

<br>

### Setup

1. Drop the jar in `/plugins`
2. Start the server
3. Explore. When an Ancient City is found, staff get a message in chat:

<img width="520" height="40" alt="The chat message shown when a city is found" src="https://github.com/user-attachments/assets/d7334c3b-a621-451b-91b9-24c0387b8fbc" />

4. Click the green coordinates to go there and take a look
5. Click **[approve]**

That's the whole setup.

> **Why approve first?** The plugin finds cities from Minecraft's own structure data, so mistakes are rare. Approving lets you check before loot and protection switch on. Turn it off with `discovery.require-approval: false`.

<br>

### What it does

**Loot**
- Every player gets their own copy of each city chest
- The second player to arrive still finds full chests
- Each city refills on its own timer (12 hours by default), starting when someone first loots it
- Staff can change what's in a chest for everyone: sneak and open it
- Chests players place themselves stay normal

**Protection**
- Nobody breaks, places, pours, burns, pushes or blows up the city's buildings
- The rock between the buildings can still be mined
- Ban a player from looting one city. They can still walk through it

**Snapshots**
- Save a city's blocks, put them back later
- Undoes griefing and sculk spread
- Saved by itself when you approve a city
- Optional: put the city back every time its loot refreshes

**Staff menu**
- `/ancient menu` covers everything
- See what each player took from each chest, compared with the original loot
- Chests looted, time spent, deaths and blocked griefing, per player, per city
- Who is in a city right now

**Under the hood**
- Paper, Purpur and Folia
- Big cities are saved and restored a chunk at a time, so the server keeps running smoothly
- SQLite or MySQL
- Config updates itself, keeping your settings

<br>

### Reference

<details>
<summary><strong>Commands</strong></summary>

| Command | What it does |
|---------|-------------|
| `/ancient menu` | Everything, in a menu |
| `/ancient list` | All cities found |
| `/ancient approve <number>` | Switch on a pending city |
| `/ancient tp <number>` | Go there |
| `/ancient check` | Is the block you look at protected? |
| `/ancient snapshot <number>` | Save the city's blocks |
| `/ancient reset <number>` | Put them back |
| `/ancient ban <number> <player>` | Stop a player looting a city |
| `/ancient resetloot <number> <player>` | Let a player loot a city fresh |
| `/ancient reload` | Reload config |

[All commands](https://esmp-fun.gitbook.io/plugins/better-ancient-cities/reference/commands)

</details>

<details>
<summary><strong>Permissions</strong></summary>

| Permission | What it does | Default |
|------------|--------------|---------|
| `bac.admin` | Everything | OP |
| `bac.discovery.notify` | Get told when a city is found | OP |
| `bac.bypass.protection` | Build inside cities | OP |

> Protection looks broken when you test as OP! Operators can build anywhere. Use `/ancient check`, or test with a player who is not an operator.

</details>

<details>
<summary><strong>Settings people change</strong></summary>

```yaml
discovery:
  require-approval: true   # false = new cities are active straight away

loot:
  refresh-hours: 12        # hours before chests refill. 0 = never

protection:
  piece-padding: 3         # blocks around each building that are protected too

snapshot:
  auto-reset-on-refresh: false   # true = put the blocks back when loot refreshes
```

[config.yml](https://esmp-fun.gitbook.io/plugins/better-ancient-cities/configuration/config.yml)

</details>

<details>
<summary><strong>Requirements</strong></summary>

- Paper, Purpur or Folia
- Minecraft 1.21.1 or newer. Use the `-mc26` jar for 26.0 to 26.2, and the `-mc263` jar for 26.3
- Java 21 or newer (26.x servers run on Java 25)
- Nothing else. Works on its own

</details>

<br>

### Support

- [Discord](https://discord.gg/qwYcTpHsNC) - help, news and ideas
- [GitHub Issues](https://github.com/ESMP-FUN/BetterAncientCities/issues) - bug reports
- [Documentation](https://esmp-fun.gitbook.io/plugins/better-ancient-cities)

---

<div align="center">

Made with Kotlin by [darkstarworks](https://github.com/darkstarworks). More plugins: [ESMP](https://modrinth.com/organization/esmp) · [darkstarworks](https://modrinth.com/user/darkstarworks)

[![Servers](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Fbetter-ancient-cities%3Fmetric%3Dservers%26color%3Dblue%26icon%3D1&style=flat)](https://faststats.dev/project/better-ancient-cities)

</div>
