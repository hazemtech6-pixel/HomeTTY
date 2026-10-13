# Configuration

HomeTTY uses a full TOML parser. Native Settings validates and atomically saves
`~/.config/hometty/config.toml`; the terminal editor action opens that exact file
using `$EDITOR` (or `vi`) once a compatible bootstrap is installed. Unknown
settings are preserved; malformed input is rejected without resetting the file.
A specialized settings TUI is not yet implemented.

```toml
[home]
columns = 4
clock = true
dock = true

[weather]
enabled = true
latitude = 30.04
longitude = 31.24

[ai]
enabled = false

[security]
lock_terminal = false
lock_settings = false
lock_apps = false
lock_desktop = false

[desktop]
environment = "xfce"
backend = "x11" # or "vnc"

[privilege]
rish = false
```

Weather uses explicit coordinates and Open-Meteo HTTPS requests, without location
permission. Provider failure is displayed. AI has no default tab, key prompt,
network request or service; enabling it currently exposes an external-agent
terminal entry only. There is no built-in AI frontend yet.

Android device credentials gate direct Terminal/terminal-preferences entry and
setup. External RUN_COMMAND requests are rejected when the terminal lock is
configured. App locks cover launches through HomeTTY, not other Android apps,
notifications or OS Settings. These are convenience gates in HomeTTY, not a
sandbox against a user who controls its shell/configuration. Custom PIN/password
UI and persisted password records are not implemented; the salted PBKDF2 helper
is host-tested infrastructure only.

Plugins currently support declarative app, web and shell actions:

```toml
[plugin]
id = "documentation"
name = "Documentation"
capabilities = ["open_web"]
[action]
kind = "web"
target = "https://example.com/docs"
```

Import through Tools → Plugins, then review and grant requested capabilities.
The grant applies to the exact manifest content: editing it invalidates consent.
Shell plugins have the entire HomeTTY shell's authority, not a filesystem or
network sandbox, and require confirmation before each run. Sources can be
registered as HTTPS catalog URLs; registration does not fetch, install or execute
anything. Catalog browsing, plugin updates and additional plugin surfaces remain
pending.

Configuration backup/restore uses Android's document picker. Backups exclude
Keystore ciphertext, grants, widget IDs, runtime state and the terminal filesystem.
Restore parses the whole file and asks before replacing existing configuration.

The Settings TUI button stages the bundled Python/curses editor and runs it in the
retained PTY using HomeTTY's rebuilt Python. It reads the same config.toml, preserves
comments and unknown fields, and refuses concurrent overwrites. Arrow keys/j/k select,
Enter edits, s saves with confirmation, e opens the full editor, r reloads and q quits.
Native Settings reloads validated external changes when resumed. Android TUI runtime
verification remains pending the custom-prefix Python build.
