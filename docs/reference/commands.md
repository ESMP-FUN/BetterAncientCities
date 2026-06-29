# Commands

All commands are under `/acp` (alias of `/ancientcity`) and require `acp.admin` (default: operators). `<id>` is a city id from `/acp list`.

| Command | What it does |
| --- | --- |
| `/acp menu` | Open the admin GUI (the recommended way to do everything below). |
| `/acp list` | List discovered cities (active + pending). Coordinates are click-to-teleport; `[menu]` opens the city in the GUI. |
| `/acp info <id>` | Show a city's bounds, piece count, and status. |
| `/acp approve <id>` | Activate a pending city (enables loot + protection; captures a baseline snapshot). |
| `/acp delete <id>` | Unregister a city and all its data. |
| `/acp tp <id>` | Teleport to a city's centre. |
| `/acp open <id>` | Open a specific city directly in the GUI. |
| `/acp check` | Look at a block and report whether it's in a city, inside a structure piece, and whether it's protected — **independent of your own bypass**. |
| `/acp snapshot <id>` | Capture the city's structure as a restore point. |
| `/acp reset <id>` | Restore the city from its snapshot (reverts griefing + sculk spread). |
| `/acp ban <id> <player> [reason]` | Loot-ban a player from a city (they can still walk through it). |
| `/acp unban <id> <player>` | Lift a loot ban. |
| `/acp bans <id>` | List a city's loot bans. |
| `/acp resetloot <id> <player>` | Clear a player's loot copies so they can loot the city fresh. |
| `/acp reload` | Reload `config.yml`. |
| `/acp help` | Show the command list in-game. |

<div data-gb-custom-block data-tag="hint" data-style="info">

`/acp check` is the quickest way to confirm protection coverage as an operator — operators bypass protection, so simply trying to break a block won't tell you whether it's protected. `check` reports the rule regardless of your bypass.

</div>
