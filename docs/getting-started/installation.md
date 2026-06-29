# Installation

1. Download the correct jar for your server:
   * **`AncientCityPro-1.0.0.jar`** — Minecraft 1.21.1 through 1.21.x
   * **`AncientCityPro-1.0.0-mc26.jar`** — Minecraft 26.x
2. Drop it into your server's `plugins/` folder.
3. Restart the server.
4. That's it. AncientCityPro creates its config and database on first start and begins discovering Ancient Cities as their chunks load.

<div data-gb-custom-block data-tag="hint" data-style="info">

AncientCityPro is **standalone** — it doesn't depend on TrialChamberPro or any other plugin. Running both side by side is fine; they don't interfere.

</div>

## Database

By default the plugin uses an embedded **SQLite** database (`plugins/AncientCityPro/database.db`) — zero setup. For large networks you can switch to **MySQL** in `config.yml`:

```yaml
database:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: ancientcitypro
    username: root
    password: ""
```

The MySQL driver is bundled, so no extra downloads are needed.

## Permissions

All admin features default to operators. See [Permissions](../reference/permissions.md) to grant them to staff via a permissions plugin instead.
