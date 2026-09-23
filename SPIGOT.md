[CENTER]
[SIZE=7][B]Better Ancient Cities[/B][/SIZE]

Free forever.

[URL='https://discord.gg/qwYcTpHsNC']Discord[/URL] · [URL='https://ko-fi.com/darkstarworks']Ko-Fi[/URL]

[SIZE=4]Turn one-time Ancient Cities into loot runs every player gets to enjoy.[/SIZE]
[/CENTER]

[LIST]
[*][B]Vanilla:[/B] the first player empties every chest. [B]With this plugin:[/B] every player gets their own chests.
[*][B]Vanilla:[/B] chests stay empty forever. [B]With this plugin:[/B] loot comes back on a timer.
[*][B]Vanilla:[/B] the city gets dug out and burned. [B]With this plugin:[/B] protected.
[*][B]Vanilla:[/B] sculk spreads over everything. [B]With this plugin:[/B] put the city back as it was, in one click.
[*][B]Vanilla:[/B] set up each city by hand. [B]With this plugin:[/B] found automatically.
[/LIST]

[SIZE=6][B]Setup[/B][/SIZE]

[LIST=1]
[*]Drop the jar in [ICODE]/plugins[/ICODE]
[*]Start the server
[*]Explore. When an Ancient City is found, staff get a message in chat:
[IMG]https://github.com/user-attachments/assets/d7334c3b-a621-451b-91b9-24c0387b8fbc[/IMG]
[*]Click the green coordinates to go there and take a look
[*]Click [B][approve][/B]
[/LIST]

That's the whole setup.

[QUOTE][B]Why approve first?[/B] The plugin finds cities from Minecraft's own structure data, so mistakes are rare. Approving lets you check before loot and protection switch on. Turn it off with [ICODE]discovery.require-approval: false[/ICODE].[/QUOTE]

[SIZE=6][B]What it does[/B][/SIZE]

[B]Loot[/B]
[LIST]
[*]Every player gets their own copy of each city chest
[*]The second player to arrive still finds full chests
[*]Each city refills on its own timer (12 hours by default), starting when someone first loots it
[*]Staff can change what's in a chest for everyone: sneak and open it
[*]Chests players place themselves stay normal
[/LIST]

[B]Protection[/B]
[LIST]
[*]Nobody breaks, places, pours, burns, pushes or blows up the city's buildings
[*]The rock between the buildings can still be mined
[*]Ban a player from looting one city. They can still walk through it
[/LIST]

[B]Snapshots[/B]
[LIST]
[*]Save a city's blocks, put them back later
[*]Undoes griefing and sculk spread
[*]Saved by itself when you approve a city
[*]Optional: put the city back every time its loot refreshes
[/LIST]

[B]Staff menu[/B]
[LIST]
[*][ICODE]/ancient menu[/ICODE] covers everything
[*]See what each player took from each chest, compared with the original loot
[*]Chests looted, time spent, deaths and blocked griefing, per player, per city
[*]Who is in a city right now
[/LIST]

[B]Under the hood[/B]
[LIST]
[*]Paper, Purpur and Folia
[*]Big cities are saved and restored a chunk at a time, so the server keeps running smoothly
[*]SQLite or MySQL
[*]Config updates itself, keeping your settings
[/LIST]

[SIZE=6][B]Reference[/B][/SIZE]

[SPOILER='Commands']
[LIST]
[*][ICODE]/ancient menu[/ICODE] - everything, in a menu
[*][ICODE]/ancient list[/ICODE] - all cities found
[*][ICODE]/ancient approve <number>[/ICODE] - switch on a pending city
[*][ICODE]/ancient tp <number>[/ICODE] - go there
[*][ICODE]/ancient check[/ICODE] - is the block you look at protected?
[*][ICODE]/ancient snapshot <number>[/ICODE] - save the city's blocks
[*][ICODE]/ancient reset <number>[/ICODE] - put them back
[*][ICODE]/ancient ban <number> <player>[/ICODE] - stop a player looting a city
[*][ICODE]/ancient resetloot <number> <player>[/ICODE] - let a player loot a city fresh
[*][ICODE]/ancient reload[/ICODE] - reload config
[/LIST]
[URL='https://esmp-fun.gitbook.io/plugins/better-ancient-cities/reference/commands']All commands[/URL]
[/SPOILER]

[SPOILER='Permissions']
[LIST]
[*][ICODE]bac.admin[/ICODE] (OP) - everything
[*][ICODE]bac.discovery.notify[/ICODE] (OP) - get told when a city is found
[*][ICODE]bac.bypass.protection[/ICODE] (OP) - build inside cities
[/LIST]
[QUOTE]Protection looks broken when you test as OP! Operators can build anywhere. Use [ICODE]/ancient check[/ICODE], or test with a player who is not an operator.[/QUOTE]
[/SPOILER]

[SPOILER='Settings people change']
[CODE=yaml]
discovery:
  require-approval: true   # false = new cities are active straight away

loot:
  refresh-hours: 12        # hours before chests refill. 0 = never

protection:
  piece-padding: 3         # blocks around each building that are protected too

snapshot:
  auto-reset-on-refresh: false   # true = put the blocks back when loot refreshes
[/CODE]
[URL='https://esmp-fun.gitbook.io/plugins/better-ancient-cities/configuration/config.yml']config.yml[/URL]
[/SPOILER]

[SPOILER='Requirements']
[LIST]
[*]Paper, Purpur or Folia
[*]Minecraft 1.21.1 or newer. Use the [ICODE]-mc26[/ICODE] jar for 26.0 to 26.2, and the [ICODE]-mc263[/ICODE] jar for 26.3
[*]Java 21 or newer (26.x servers run on Java 25)
[*]Nothing else. Works on its own
[/LIST]
[/SPOILER]

[SIZE=6][B]Support[/B][/SIZE]

[LIST]
[*][URL='https://discord.gg/qwYcTpHsNC']Discord[/URL] - help, news and ideas
[*][URL='https://github.com/ESMP-FUN/BetterAncientCities/issues']GitHub Issues[/URL] - bug reports
[*][URL='https://esmp-fun.gitbook.io/plugins/better-ancient-cities']Documentation[/URL]
[/LIST]

[CENTER]
Made with Kotlin by [URL='https://github.com/darkstarworks']darkstarworks[/URL]. More plugins: [URL='https://modrinth.com/organization/esmp']ESMP[/URL] · [URL='https://modrinth.com/user/darkstarworks']darkstarworks[/URL]

[URL='https://faststats.dev/project/better-ancient-cities'][IMG]https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Fbetter-ancient-cities%3Fmetric%3Dservers%26color%3Dblue%26icon%3D1&style=flat[/IMG][/URL]
[/CENTER]
