# Implementation status

HomeTTY is a development fork, **not yet the complete distribution in SPEC.md**.
No accepted requirement is removed. The original state was the Termux import plus
HomeTTY's rules/specification; this continuation adds native launcher integration.

| Area | Implemented | Remaining / limits |
| --- | --- | --- |
| Identity/provenance | Distinct HomeTTY ID, namespaced paths/permissions, retained upstream Java classes/licenses, pinned source audit, local debug signing | Private common production/companion signing |
| HOME launcher | MAIN/HOME/DEFAULT, HOME chooser, launcher tabs, Android wallpaper, real app icons, TOML pin selection, configurable columns/dock, clock/battery/network | Home pages, drag/drop, full widget tap-action configuration, adaptive layout polish |
| Apps | Search, pin/unpin, TOML app labels and bounded custom bitmap icons, app info/uninstall, live package refresh, Android shortcut launch | Work profiles, pinned-shortcut placement, complete icon-provider/device checks |
| Terminal | Original emulator, JNI PTY, service, sessions, sixel/iTerm rendering retained; explicit system-shell entry | Rebuilt Linux bootstrap; regression/device/image rendering verification |
| Smart Search | Live unified app/desktop/command/file search, pinned/usage ordering across results, explicit routes including desktop-web; background local index | Hidden-file/configurable indexing, work-profile indexing, desktop browser runtime verification |
| Widgets | AppWidgetHost picker/configure/persistence/removal, clock/battery/network, optional Open-Meteo coordinates/provider boundary with coordinate-specific stale-labelled cache, opt-in media controls | Background refresh scheduling, per-widget tap actions, reserved-region layout |
| Tools/files | Android settings/Wi-Fi/Bluetooth/calculator/calendar intents; private-home preview/create/rename/delete/open/share; persistent SAF tree browsing and supported create/rename/delete/share actions | Copy/move between storage providers, large text editing, full external-provider/device verification |
| Configuration | Full TOML parsing, schema checks for initial fields, atomic save, native editor, same-file Python/curses settings TUI with conflict detection and comment preservation, XDG paths, reload on resume | Android TUI verification requires rebuilt Python, complete schema, full themes |
| Installer | Pure persistent weighted state machine; service-owned PTYs; live terminal plus progress; Interact/Retry/safe Skip/confirmed Cancel; session-owned raw logs and transcript snapshots; explicit/apt percentage parsing; lost-PTY detection | Full one-question-at-a-time component selection/install recipes, complete package-install recipes and apt status-fd wiring, bootstrap extraction persistence |
| Bootstrap/repositories | Stock binary download removed; custom build manifest/hash/path/symlink/stock-ELF validation; digest-pinned Docker build; signed local repository generator with package identity/payload checks; empty development archives explicitly gated | Full four-architecture bootstrap build in progress; full signed repository/desktop packages and runtime verification |
| Desktop | All 14 upstream desktop/startup mappings adapted from pinned GPL source; TOML X11/VNC start actions; HomeTTY companion routing | Rebuilt packages and X11, full upstream feature adaptation, drivers/themes/audio/setup recipes, desktop verification |
| Companions | Pinned API, Widget, Tasker and X11 sources adapted and built; X11 native libraries built for all four ABIs; matching debug signer; bundled consent-based install flow; X11 routing distinguishes original Termux | Private production signer, CLI helpers rebuilt for the custom prefix, full companion device checks |
| Privilege | Shizuku API binder/permission capability discovery without manager-package whitelist; authorization request; root detection/diagnostic/reboot with confirmation; optional RISH | RISH export/bundling, full operational backend abstraction and device/provider validation |
| Locks/secrets | Android device-credential UI gates; direct terminal/settings/setup entry guards; external RUN_COMMAND lock check; optional password/PIN enrollment/removal behind device credentials; Keystore-encrypted salted PBKDF2 record; persistent retry delay; background verification | Password UI/lifecycle/device regression checks, complete shell-widget lock handoff |
| AI | Entirely absent by default; configured external agent command; optional Ollama chat frontend with cancel, memory-only history and endpoint-bound Keystore credentials | Real model/server and physical-device checks, external agent package setup, additional provider adapters |
| Plugins | Declarative tool/command/widget/tab/theme/integration manifests; exact-content grants and revocation; per-shell-run confirmation; bundled official catalog and explicit public HTTPS/GitHub catalog browsing; checked manifest updates; sources and theme choice in TOML | Private catalog authentication, executable plugin package sandboxing, broader integration hooks, full catalog/theme/device checks |
| Backup/update | SAF configuration-only backup/validated restore | Full-state/files backup, migration, companion/package/plugin/application updates |

## Verification

- Python bootstrap boundary tests: 5 passed. Settings TUI logic tests: 5 passed. Signed repository tests: 2 passed.
- Android debug APK assembly: successful for arm64-v8a, armeabi-v7a, x86,
  x86_64 and universal.
- Upstream terminal-emulator host tests: 145 passed. Application host tests: 38 passed.
- `:app:lintDebug`: passed, with existing and new nonfatal warnings (including English UI strings needing localization).
- Android 10 x86_64 emulator: Home startup, real system-shell input/output, installer Android probe/state/log, real PTY prompt/state restore/raw output, SAF create/rename/read/path checks, Keystore tamper rejection, and asynchronous desktop search instrumentation passed (7 tests, including disabled-AI no-network/Ollama protocol checks, custom icon normalization/TOML loading, and exact-content plugin widget grants). The launcher test backgrounds its HOME activity before cleanup to accommodate Android HOME lifecycle behavior.
- API, Widget and Tasker installed through Android consent in the emulator. HOME role granted through Android consent and retained after pressing Home. X11 runtime checks ongoing.
- Physical Android checks: pending in DEVICE_TESTS.md.

## Environmental blockers

No custom-prefix bootstrap or package repository is available yet. All four companion
APKs build. The user provided privileged Docker access; the pinned builder image
is verified and an isolated custom-prefix build is running. The interrupted build resumed from 80 produced Debian packages; its interrupted host Python configure is now rebuilt and dependency compilation continues. Initial x86_64
dependency packages passed application-ID and payload checks.
No host permissions or kernel settings are changed by the project. No physical
Android device is connected. Android implementation and host testing continue;
Linux/desktop runtime verification awaits the rebuilt packages.
