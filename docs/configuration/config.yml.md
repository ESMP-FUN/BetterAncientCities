# config.yml

Every setting, with its default. Edit `plugins/BetterAncientCities/config.yml`, then type `/ancient reload` to apply it. The `database`, `update` and `metrics` sections need a restart instead.

When you update the plugin, new settings are added to your file by themselves. Your own settings are kept, and the old file is saved as `config.yml.bak`.

```yaml
# Better Ancient Cities settings.
# After changing something here, type /ancient reload (or restart the server).

database:
  # Where the plugin keeps its data.
  #   sqlite - a single file in this folder. Nothing to install. Right for almost
  #            every server, and the default.
  #   mysql  - your own MySQL or MariaDB server, using the settings below. Only
  #            worth it if you already run one.
  # Everything needed for both is built in. Changing this needs a restart.
  type: sqlite
  # Only used when type is mysql.
  mysql:
    host: localhost
    port: 3306
    database: ancientcitypro
    username: root
    password: ""
    # How many connections to keep open at once. 10 is plenty.
    pool-size: 10

discovery:
  # Find Ancient Cities by themselves when players explore near them.
  # Turn off if you only want the cities that were found already.
  enabled: true
  # Keep each city's area to the height of its buildings (recommended).
  # When false, the area reaches as high and low as Minecraft's own outline
  # of the city, which can take in more of the rock around it.
  clamp-to-structure-y: true
  # When the server starts, also check the areas that are already loaded,
  # instead of waiting for a player to walk there.
  startup-sweep: true
  # When true, a new city starts out as "pending": it is saved, but loot and
  # protection stay off until staff approve it with /ancient approve <number>
  # or the Approve button in /ancient menu.
  # When false, every new city is active straight away.
  require-approval: true
  # Worlds where cities are never registered (names are not case-sensitive).
  # Handy for a second overworld whose cities should stay vanilla. Cities found
  # before a world was added here keep working; remove them with /ancient delete.
  # Example:
  #   excluded-worlds:
  #     - second_overworld
  excluded-worlds: []

loot:
  # Every player gets their own copy of each city chest, so the second player to
  # arrive still finds full chests. Player-placed chests are not affected.
  enabled: true
  # How many hours before a city's chests fill up again for everyone.
  # The clock starts when the first player loots the city. When it runs out,
  # the next player to open a chest there refreshes the whole city and the
  # clock starts again. Each city has its own clock.
  # Anything a player left in a chest is gone after a refresh.
  # Example: 12 = twice a day. 0 = never refresh.
  refresh-hours: 12

protection:
  # Protect the city's buildings from griefing. Any block that is part of a city
  # building (or close to one, see piece-padding) is protected, whatever it is
  # made of. The natural rock between the buildings can still be mined.
  # Protected: breaking, placing (see block-place), buckets, fire, pistons,
  # mobs changing blocks and explosions (see block-explosions).
  enabled: true
  # How many blocks around each building are protected too. This covers the
  # decoration Minecraft places just outside a building's edges.
  # Example: 3 protects 3 blocks out from every wall, floor and roof.
  piece-padding: 3
  # Stop players placing blocks or pouring buckets inside the protected area.
  block-place: true
  # Stop creepers, TNT and other explosions breaking the protected area.
  block-explosions: true
  # Show "This Ancient City is protected." above the hotbar when something is stopped.
  notify-denied: true

snapshot:
  # A snapshot saves a city's blocks so they can be put back later, undoing
  # griefing and sculk spread.
  # The largest city, in blocks, that may be saved. The default is far above a
  # real Ancient City and only guards against a broken one using up memory.
  max-cells: 3000000
  # Save a snapshot automatically when a city is approved.
  auto-capture-on-approve: true
  # Also put the city's blocks back each time its loot refreshes.
  # Off by default: players standing where blocks come back can end up inside
  # them. Needs a snapshot.
  auto-reset-on-refresh: false

update:
  # What the update checker may do:
  #   off        - never check
  #   check-only - check quietly; see the result with /ancient update status
  #   notify     - also tell staff in chat and the console when an update is out,
  #                with a button (or /ancient update download) to fetch it.
  #                It installs on the next restart. This is the default.
  #   auto-stage - fetch new versions by itself, ready for the next restart
  # It always picks the download made for your Minecraft version.
  # Changes to this section need a restart.
  mode: notify
  # Hours between checks (at least 1).
  check-interval-hours: 6
  # Only install a download that comes with a checksum to verify it.
  # Leave this on.
  require-hash: true

# Anonymous usage statistics (FastStats). No player data: only things like the
# database type, the discovery settings and how many cities a server has.
# You can also turn them off for every plugin in plugins/faststats/config.properties.
# Changes to this section need a restart.
metrics:
  enabled: true
  # Send an error report when something goes wrong in this plugin, so it can be
  # fixed without anyone reporting it. Off unless you turn it on, because an
  # error report can include folder names. Needs 'enabled: true' above.
  error-reporting: false
```

{% hint style="warning" %}
YAML does not allow TAB characters. Indent with spaces only, or the file will not load.
{% endhint %}

## Notes

* **`discovery.require-approval`.** Keep it `true` while you check how discovery does on your world. Set it to `false` once you trust it, and new cities go live straight away.
* **`discovery.excluded-worlds`.** Keeps the plugin out of those worlds entirely. Cities found before a world was added keep working; remove them with `/ancient delete <number>`.
* **`loot.refresh-hours: 0`.** Loot never refreshes by itself. Use **Refresh loot now** in the menu when you want it to.
* **`protection.piece-padding`.** Raise it if decoration at the edge of a building is not protected. Lower it (to `1`, for example) if the rock right next to a building feels over-protected. Save a new snapshot of each city after changing it, because snapshots cover the same area.
* **`snapshot.auto-reset-on-refresh`.** Keeps a city looking new all the time, but a player standing where blocks come back can end up inside them. Only turn it on if that won't catch your players out.
* **`update.mode`.** Downloads only ever take the jar made for your Minecraft version, and only when it comes with a checksum (`require-hash`). A downloaded update installs on the next restart.
* **`metrics.error-reporting`.** Off by default. When on, errors from this plugin are sent as reports so they can be fixed without anyone reporting them. An error report can include folder names, which is why it is separate from `metrics.enabled` (and it needs `metrics.enabled: true` to work).
