# Commands

All commands are under `/ancient` (aliases `/bac`, `/acp`, `/ancientcity`, `/betterancientcities`) and require `bac.admin` (default: operators). `<id>` is a city id from `/ancient list`.

| Command | What it does |
| --- | --- |
| `/ancient menu` | Open the admin GUI (the recommended way to do everything below). |
| `/ancient list` | List discovered cities (active + pending). Coordinates are click-to-teleport; `[menu]` opens the city in the GUI. |
| `/ancient info <id>` | Show a city's bounds, piece count, and status. |
| `/ancient approve <id>` | Activate a pending city (enables loot + protection; captures a baseline snapshot). |
| `/ancient delete <id>` | Unregister a city and all its data. |
| `/ancient tp <id>` | Teleport to a city's centre. |
| `/ancient open <id>` | Open a specific city directly in the GUI. |
| `/ancient check` | Look at a block and report whether it's in a city, inside a structure piece, and whether it's protected — **independent of your own bypass**. |
| `/ancient snapshot <id>` | Capture the city's structure as a restore point. |
| `/ancient reset <id>` | Restore the city from its snapshot (reverts griefing + sculk spread). |
| `/ancient ban <id> <player> [reason]` | Loot-ban a player from a city (they can still walk through it). |
| `/ancient unban <id> <player>` | Lift a loot ban. |
| `/ancient bans <id>` | List a city's loot bans. |
| `/ancient resetloot <id> <player>` | Clear a player's loot copies so they can loot the city fresh. |
| `/ancient reload` | Reload `config.yml`. |
| `/ancient help` | Show the command list in-game. |

<div data-gb-custom-block data-tag="hint" data-style="info">

`/ancient check` is the quickest way to confirm protection coverage as an operator — operators bypass protection, so simply trying to break a block won't tell you whether it's protected. `check` reports the rule regardless of your bypass.

</div>
