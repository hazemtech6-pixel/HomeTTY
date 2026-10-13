# HomeTTY specification

HomeTTY is an Android-only GPLv3 fork of Termux, application ID
`com.hazemtech.termux.hometty`, and a real Android HOME-role launcher. The project
must remain locally buildable; publication and GitHub setup are out of scope.

## Experience
Minimal, normal, terminal-first Android design. Tabs: Home, Apps, Terminal, Tools,
Desktop, Settings, and AI only when explicitly enabled. Home has configurable
widgets, real icons, pages, optional dock, wallpaper, long-press actions and normal
launcher behavior. Support Android AppWidgetHost integration into reserved regions.
Built-in weather (Open-Meteo with extensible providers), clock, battery/network,
shell/search and media widgets have configurable tap actions.

Apps: searchable alphabetical Android launcher activities, icons, shortcuts, app
info, uninstall, per-app labels/icons, pins. Terminal retains the real Termux PTY,
emulator and service. Add actual terminal image/icon rendering, not ASCII art.
Smart Search updates while typing across Android apps, commands, desktop apps,
files/folders and web targets. Sort pinned first, then usage-ranked, then remaining
results. Explicit routes: app:, shell:, desktop:, Android web and desktop web.

Tools: Files, calculator, calendar, system quick settings, Wi-Fi, Bluetooth and
RISH when enabled. Files cover private Termux home, accessible Android storage,
desktop files and external storage using Android permissions/SAF.

## Desktop and companions
Adapt GPLv3 sabamdarif/termux-desktop, its supported desktops/window managers, and
Termux:X11/VNC. HomeTTY TOML is the configuration authority. Include compatible
HomeTTY API, Widget, Tasker and X11 companions where needed, with matching namespaces,
permissions, package references and signing. Normal Termux paths retain original
companions. A custom application prefix requires custom bootstrap and repositories.

## Configuration and installation
Human-editable TOML in ~/.config/hometty; runtime state and caches in XDG data/cache
locations. Settings is a TUI editing exactly the same configuration. First run is
one installer: only HomeTTY is manually installed initially. Detect capabilities,
ask one question at a time, install selected components, configure and verify.
Persist/resume setup. Keep overall weighted progress and a small live PTY view
visible together. Incorporate measurable in-command progress. Distinguish actual
failure from WAITING_FOR_USER; give users the existing running PTY to answer prompts,
then return to normal mode. Recoverable errors offer Retry / Interact / Skip only
when safe / Cancel. Never restart or kill commands merely for being interactive.

## Security and extensibility
Backend abstraction for Android, Shizuku/RISH and root. Discover compatible Shizuku
providers by capability/declared permission, including Shizuku-Next, not one package.
Retain confirmations for destructive privileged actions. Optional locks cover apps,
Terminal, shell widgets, privileged tools, Desktop and Settings. Android authentication
and optional HomeTTY PIN/password. Keystore secrets, salted password hashes.
AI is entirely optional: disabled means no tab, key prompt, service or nag. Support
external terminal agents and optional built-in frontend; no plaintext TOML keys.
Plugins: widgets, tools, commands, integrations, optional tabs/themes; TOML manifests;
explicit capabilities. Sources: official registry, GitHub, direct catalogs, local
packages. Source registration never installs code. Provide updates and backup/restore.

## Verification
Run meaningful host tests and Android builds, diagnose/fix failures. Document device
checks for HOME, permissions, icons, widgets, shortcuts, storage, authentication,
PTY prompt handoff, desktop/X11/VNC, Shizuku providers and signed companion installs.
Do not label scaffolding or placeholders as complete functionality. Preserve scope
and provide an exact implementation/status matrix and reproducible build commands.

## Recorded implementation decisions (2026-10-13)

- Retain upstream Java namespaces and JNI names; distinguish them from HomeTTY's
  Android application ID, permissions, companion IDs and private prefix.
- Support Android 7.0+ (API 24), matching the supported Termux package baseline.
- Allow an explicitly identified development APK without a bootstrap for launcher
  testing. It offers the Android system shell and must not claim package/desktop
  support. Full distribution requirements above remain unchanged.
- Use full TOML parsing and atomic writes. Initial plugin actions are declarative,
  and capability grants bind to a manifest digest so changes require review.
- Debug builds use the local SDK debug key. Production and companions require a
  shared private signing identity, kept outside Git.
