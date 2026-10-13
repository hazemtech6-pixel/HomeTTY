# Implementation status

HomeTTY is a development fork, **not yet the complete distribution in SPEC.md**.
No accepted requirement is removed. The original state was the Termux import plus
HomeTTY's rules/specification; this continuation adds native launcher integration.

| Area | Implemented | Remaining / limits |
| --- | --- | --- |
| Identity/provenance | Distinct HomeTTY ID, namespaced paths/permissions, retained upstream Java classes/licenses, pinned source audit, local debug signing | Private common production/companion signing |
| HOME launcher | MAIN/HOME/DEFAULT, HOME chooser, launcher tabs, Android wallpaper, real app icons, configurable columns/dock, clock/battery/network | Home pages, drag/drop, icon overrides, full widget tap-action configuration, adaptive layout polish |
| Apps | Search, pin/unpin, labels, app info/uninstall, live package refresh, Android shortcut launch | Work profiles, pinned-shortcut placement, icon overrides |
| Terminal | Original emulator, JNI PTY, service, sessions, sixel/iTerm rendering retained; explicit system-shell entry | Rebuilt Linux bootstrap; regression/device/image rendering verification |
| Smart Search | Live Android app search, pinned/usage ordering, explicit app/shell/web routes | Desktop-app/file/command indexing, desktop-web routing and unified ranking |
| Widgets | AppWidgetHost picker/configure/persistence/removal, clock/battery/network, optional Open-Meteo coordinates/provider boundary, opt-in media controls | Weather cache/scheduling, per-widget tap actions, reserved-region layout |
| Tools/files | Android settings/Wi-Fi/Bluetooth/calculator/calendar intents, private-home preview and SAF picker | Full file operations, tree browser, desktop/external-storage integration |
| Configuration | Full TOML parsing, schema checks for initial fields, atomic save, native editor, same-file terminal editor entry, XDG paths | Dedicated settings TUI, complete schema, live shell-side changes, full themes |
| Installer | Pure persistent weighted state machine; service-owned PTYs; live terminal plus progress; Interact/Retry/safe Skip/confirmed Cancel; logs; lost-PTY detection | Full one-question-at-a-time component selection/install recipes, measurable package progress wiring, bootstrap extraction persistence, signed companion delivery |
| Bootstrap/repositories | Stock binary download removed; custom build manifest/hash/path validation; empty development archives explicitly gated | All custom-prefix binaries and apt repositories, reproducible end-to-end package builds |
| Desktop | All 14 upstream desktop/startup mappings adapted from pinned GPL source; TOML X11/VNC start actions; HomeTTY companion routing | Rebuilt packages and X11, full upstream feature adaptation, drivers/themes/audio/setup recipes, desktop verification |
| Companions | Namespace constants route toward HomeTTY API/Widget/Tasker; X11 routing distinguishes original Termux | Actual HomeTTY API, Widget, Tasker and X11 source adaptations/builds/install flow |
| Privilege | Shizuku API binder/permission capability discovery without manager-package whitelist; authorization request; root detection/diagnostic/reboot with confirmation; optional RISH | RISH export/bundling, full operational backend abstraction and device/provider validation |
| Locks/secrets | Android device-credential UI gates; direct terminal/settings/setup entry guards; external RUN_COMMAND lock check; Keystore encryption helper; salted slow PBKDF2 tests | Custom PIN/password enrollment and storage, complete lock coverage and lifecycle verification; Keystore helper not wired to AI frontend |
| AI | Entirely absent by default; explicit enable exposes terminal-agent entry | Built-in optional frontend, agent setup and key-management UI |
| Plugins | Declarative TOML local imports, app/web/shell actions, explicit content-bound grants, revocation, per-shell-run confirmation, inert HTTPS source registration | Widget/tab/theme/integration plugins, registry/GitHub catalog browsing, update management, package sandboxing |
| Backup/update | SAF configuration-only backup/validated restore | Full-state/files backup, migration, companion/package/plugin/application updates |

## Verification

- Python bootstrap boundary tests: 4 passed.
- Android debug APK assembly: successful for arm64-v8a, armeabi-v7a, x86,
  x86_64 and universal.
- Upstream terminal-emulator host tests: 145 passed. Application host tests: 19 passed.
- `:app:lintDebug`: passed, with existing and new nonfatal warnings (including English UI strings needing localization).
- Physical Android checks: pending in DEVICE_TESTS.md.

## Environmental blockers

No custom-prefix bootstrap, package repository or rebuilt companion APKs are
available. Docker socket access is denied for the current user; no system
permissions were changed. No physical Android device is connected. These block a
complete Linux/desktop/companion distribution and physical-device validation, but
not ongoing Android implementation or host testing.
