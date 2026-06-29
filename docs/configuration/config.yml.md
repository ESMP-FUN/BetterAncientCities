# config.yml

The full configuration, with defaults. Edit `plugins/AncientCityPro/config.yml` and run `/acp reload` to apply changes.

```yaml
database:
  # 'sqlite' (default, zero-setup) or 'mysql'
  type: sqlite
  mysql:
    host: localhost
    port: 3306
    database: ancientcitypro
    username: root
    password: ""

discovery:
  # Auto-register naturally-generated Ancient Cities as they load.
  enabled: true
  # Tighten the registered region to the actual structure pieces (recommended).
  clamp-to-structure-y: true
  # On enable, sweep already-loaded chunks so resident cities are caught
  # without waiting for a chunk to load.
  startup-sweep: true
  # New cities register as PENDING; loot + protection activate only after an
  # operator approves them. Set false to make discoveries active immediately.
  require-approval: true

loot:
  # Per-player container loot. Each player gets their own private copy.
  enabled: true
  # Per-city loot refresh window, in hours. First-loot starts the window for
  # that city; when it elapses, the next looter refreshes it. 0 = never refresh.
  refresh-hours: 12

protection:
  # Bounds-based griefing protection for the structure pieces.
  enabled: true
  # Blocks to expand each structure piece by (covers edge decoration + a thin
  # shell). Small — this is per-piece, not whole-area.
  piece-padding: 3
  # Deny placing blocks inside a protected structure piece.
  block-place: true
  # Protect structure blocks from creeper / TNT / other explosions.
  block-explosions: true
  # Action-bar message when a break/place is denied.
  notify-denied: true

snapshot:
  # Hard cap on cells a single snapshot may capture (memory safety).
  max-cells: 3000000
  # Capture a baseline snapshot automatically on approval.
  auto-capture-on-approve: true
  # Also restore blocks from the snapshot when the loot cycle refreshes.
  # OFF by default — a restore rewrites blocks and can disrupt players inside.
  auto-reset-on-refresh: false

debug:
  verbose-logging: false
```

## Notes

<div data-gb-custom-block data-tag="hint" data-style="warning">

YAML is indentation-sensitive and does **not** allow TAB characters — use spaces only, or the file will fail to load.

</div>

* **`discovery.require-approval`** — keep it `true` while you validate detection on your world; flip to `false` once you trust it so new cities go live on contact.
* **`loot.refresh-hours: 0`** — disables refresh entirely; per-player copies persist forever (until a manual refresh/reset).
* **`protection.piece-padding`** — raise it if edge decoration is being left unprotected; lower it toward `1` if natural terrain right next to a building feels over-protected.
* **`snapshot.auto-reset-on-refresh`** — powerful for a truly self-healing city, but only enable it once you're comfortable that a restore at refresh time won't surprise players mid-explore.
