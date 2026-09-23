# Installation

Install Better Ancient Cities on your server.

## Before you start

You need:

* **Paper**, or a fork (Purpur, Folia). Pick the download that matches your Minecraft version: the plain jar for 1.21.x, `-mc26` for 26.0 to 26.2, and `-mc263` for 26.3 (both 26.x downloads need Java 25).
* **Java 21** or newer.

{% hint style="info" %}
Spigot and CraftBukkit will not work. Better Ancient Cities uses Paper-only features.
{% endhint %}

## 1. Download the plugin

Get the latest jar from [GitHub Releases](https://github.com/ESMP-FUN/BetterAncientCities/releases). Each release has three downloads:

| Your Minecraft version | Download |
| --- | --- |
| 1.21.1 to 1.21.x | `BetterAncientCities-<version>.jar` |
| 26.0 to 26.2 | `BetterAncientCities-<version>-mc26.jar` |
| 26.3 | `BetterAncientCities-<version>-mc263.jar` |

## 2. Install

1. Stop your server with `/stop`.
2. Move the jar into your server's `plugins/` folder.
3. Start your server.
4. Watch the console for these lines:

```
[BetterAncientCities] Database ready (sqlite)
[BetterAncientCities] Loaded 0 Ancient Cities
[BetterAncientCities] Better Ancient Cities is ready.
```

{% hint style="success" %}
Seeing errors instead? Check the [Troubleshooting](../troubleshooting.md) page.
{% endhint %}

The plugin creates these files in `plugins/BetterAncientCities/`:

```
plugins/BetterAncientCities/
├── config.yml      # Settings
├── database.db     # Plugin data (SQLite)
└── snapshots/      # Saved city blocks (created later)
```

## 3. Verify

Run this in-game:

```
/ancient help
```

If you see a list of commands, the plugin is installed.

{% hint style="success" %}
**Next: [Quick Start](quick-start.md).** Find your first city and switch it on.
{% endhint %}

## Database

The plugin uses SQLite by default, which needs nothing installed. If you already run a MySQL or MariaDB server and want to use it, change `config.yml`:

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

The MySQL driver is built in. Restart the server after changing the database.

## Updating

1. Stop your server.
2. Back up your `plugins/BetterAncientCities/` folder.
3. Replace the old jar with the new one.
4. Start your server.

New settings are added to your `config.yml` by themselves. Your own settings are kept, and the old file is saved as `config.yml.bak`.

{% hint style="warning" %}
Always back up `plugins/BetterAncientCities/` before updating. It holds your cities, player data and snapshots.
{% endhint %}

## Permissions

Everything defaults to operators. See [Permissions](../reference/permissions.md) to give staff access through a permissions plugin instead.

***

## Good to know

{% hint style="info" %}
**Folia:** Better Ancient Cities works on Folia with no configuration. Folia is detected automatically.
{% endhint %}

{% hint style="info" %}
**Coming from AncientCityPro:** the plugin's old name. On first start, your `plugins/AncientCityPro/` folder is copied across and the old folder is kept as a backup.
{% endhint %}
