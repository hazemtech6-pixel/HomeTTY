# Configuration

HomeTTY uses a full TOML parser. Native Settings validates and atomically saves
`~/.config/hometty/config.toml`; the terminal editor action opens that exact file
using `$EDITOR` (or `vi`) once a compatible bootstrap is installed. Unknown
settings are preserved; malformed input is rejected without resetting the file.
The Python/curses settings TUI edits the same file and requires the rebuilt HomeTTY Python package.

```toml
[home]
columns = 4
clock = true
dock = true
# Android components and search-result IDs selected by long-press.
pins = ["com.example/com.example.Main"]

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

Optional HomeTTY password/PIN enrollment and removal require Android device
credentials. Once enrolled, enabled HomeTTY locks use the encrypted password
record; removing it restores Android authentication. Hashing uses a fresh salt
and PBKDF2-HMAC-SHA256 with 600,000 iterations, including an equivalent HMAC
implementation for Android 7. Verification runs off the UI thread. Failed attempts
have a persistent increasing delay after five failures. Passwords and hash records
are excluded from configuration backup and never written to TOML. Full UI/lifecycle
verification remains on the Android checklist.

The optional built-in frontend uses the [Ollama chat API](https://docs.ollama.com/api/chat).
No model or server is selected automatically. Enable it explicitly:

```toml
[ai]
enabled = true
backend = "ollama"
endpoint = "https://your-server.example/ollama"
model = "your-installed-model"
# Alternatively backend = "external" with an installed ai.external_command.
```

The endpoint is a server base URL; HomeTTY appends `api/chat`. HTTPS is required
for remote servers and for all credentials. Plain HTTP is allowed only for localhost
or 127.0.0.1; debug APKs also permit the Android emulator's host alias. Redirects are
not followed. Credentials are encrypted, bound to the endpoint, and entered only
inside enabled AI. Conversations stay in memory; leaving the foreground cancels
pending work. Responses are displayed as text and cannot run tools or commands.
The Android fixture-server test verifies protocol behavior and zero connections
while disabled; it does not verify a real model/server.

App label/icon overrides use literal Android component keys in the same TOML:

```toml
[app_overrides."com.example/com.example.MainActivity"]
label = "Example"
icon = "~/.config/hometty/icons/your-icon.png"
```

Long-press an app to rename it, choose a bitmap icon through Android's picker,
or reset its icon. Images are imported off the UI thread, limited to 1 MiB/8192
source pixels, normalized to at most 128 pixels and stored by content hash.

Plugins can declare `tool`, `command`, `widget`, `tab`, `theme` or `integration`
surfaces. Widget/tab/theme capabilities must be explicitly requested and granted.
Every activation revalidates the manifest digest. Shell actions still require
HomeTTY confirmation and the terminal lock gate. The bundled official registry
contains declarative Wi-Fi, calendar widget and night-theme entries. Browsing a
catalog downloads data for review; importing never executes or grants capabilities.
Catalog checksums, plugin ID and version must match before import. Public GitHub
blob URLs are resolved to their raw catalog data; direct catalogs require HTTPS.

```toml
[[plugin_sources]]
name = "My public catalog"
url = "https://example.org/catalog.toml"

[theme]
plugin = "night"
```

Theme selection edits TOML and takes effect only while the exact theme manifest
has a capability grant. Revocation returns the native launcher to default colors.
Scalar edits preserve unrelated values/comments and reject ambiguous complex TOML
instead of replacing it. Advanced syntax remains editable through the full editor.

Pin changes are written to `home.pins`. Existing runtime-state pins remain readable
until the first pin/unpin change copies them into TOML. An explicit empty array
clears the selections and overrides legacy pins. Usage counts remain runtime state.

The installer records optional component choices in `[setup]` (`api`, `widget`,
`tasker`, `x11`, `tools`, `desktop`, all booleans). Desktop and display choices
use `desktop.environment` and `desktop.backend`. Its running step, PTY identity,
progress and completion records remain in XDG data, outside human configuration.

Home pages use `home.columns` (2–8), `home.rows` (1–8, default 3), and
`home.layout`, an ordered array of Android launcher component IDs. Empty strings
reserve positions; unavailable apps retain their slots. Without a layout, Home
uses the current app list. Long-press an icon to drag it within its page; long-press
its label for app actions and “Move to Home page”, which can create another page.
The first move writes the current arrangement into TOML.
