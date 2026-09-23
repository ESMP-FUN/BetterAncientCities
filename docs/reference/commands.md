# Commands

Every command starts with `/ancient` (you can also type `/bac`, `/acp`, `/ancientcity` or `/betterancientcities`) and needs `bac.admin`, which operators have by default. `<number>` is the city's number from `/ancient list`.

| Command | What it does |
| --- | --- |
| `/ancient menu` | Open the menu. Everything below is in there too. |
| `/ancient list` | List every city found, active and pending. Click the coordinates to teleport, or **[menu]** to open the city in the menu. |
| `/ancient info <number>` | Show where a city is, how many buildings it has, and whether it is active. |
| `/ancient approve <number>` | Switch on loot and protection for a pending city, and save its snapshot. |
| `/ancient delete <number> confirm` | Forget a city with its saved chests, stats, loot bans and snapshot. Without `confirm` it only tells you what will happen. |
| `/ancient tp <number>` | Teleport to a safe spot in the middle of a city. |
| `/ancient open <number>` | Open one city straight away in the menu. |
| `/ancient check` | Look at a block and see whether it is in a city, part of a building, and protected. Your own bypass is ignored, so this works for operators. |
| `/ancient snapshot <number>` | Save the city's blocks so they can be put back later. |
| `/ancient reset <number>` | Put the city's blocks back as saved, undoing griefing and sculk spread. |
| `/ancient ban <number> <player> [reason]` | Stop a player looting a city. They can still walk through it. |
| `/ancient unban <number> <player>` | Let a player loot a city again. |
| `/ancient bans <number>` | List who may not loot a city. |
| `/ancient resetloot <number> <player>` | Clear a player's chests in a city, so they can loot it fresh. |
| `/ancient reload` | Reload `config.yml`. |
| `/ancient update` | Check for a new version. `/ancient update status` shows the last result, `/ancient update download` fetches it to install on the next restart, and `/ancient update restore` puts the previous version back. |
| `/ancient help` | Show the command list in-game. |

{% hint style="info" %}
Operators can build anywhere, so breaking a block doesn't tell you whether it is protected. `/ancient check` does.
{% endhint %}
