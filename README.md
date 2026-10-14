# HomeTTY

**Your Android Home screen, built around a terminal.**

HomeTTY is an Android-only, GPLv3 Termux fork that also works as your default
HOME launcher. It combines a terminal-themed Home screen—monospace status text,
prompt-style search and command-like controls—with real app icons, wallpaper,
images and Android widgets. The terminal uses Termux's emulator, PTY and persistent
session service, with HomeTTY extensions for modern TUI rendering.

Application ID: `com.hazemtech.termux.hometty`. Requires Android 7.0 or later.

> **In development.** The launcher and Android system shell can be exercised now.
> The complete Linux bootstrap, package repository and desktop distribution are
> still being built. Development APKs without a bootstrap cannot run a complete
> Termux/Linux environment. See the [implementation status](docs/STATUS.md) for
> verified behavior and unfinished features.

## What you can do

- **Use HomeTTY as Home:** choose it as your default launcher, launch apps from
  real icons, arrange pages, pin apps, rename labels and choose custom icons.
- **Search from a prompt:** find Android apps, Linux commands, desktop entries,
  files and web targets. Pinned and frequently used results appear first.
- **Use a real terminal:** retain sessions and interactive PTYs. The engine
  includes sixel/iTerm image support and HomeTTY's synchronized-output extension.
- **Add useful surfaces:** Android widgets, clock, battery/network status,
  optional weather and media controls, plus declarative plugin widgets/tools.
- **Manage files:** browse private Home files or grant access to Android/external
  storage through the system file picker. Create, rename, open and share files.
- **Configure everything in TOML:** use the native editor or the Python/curses
  settings TUI, which edits the same file once rebuilt Python is installed.
- **Keep extensions optional:** AI is disabled by default. Enabling it exposes an
  external terminal-agent option or the built-in Ollama chat frontend. Plugin
  manifests require explicit capability review before their actions can run.

HomeTTY also includes resumable setup, HomeTTY companion installers, desktop
installation/startup recipes, backup/restore and update controls. These parts have
varying levels of verification; they are not a finished one-click distribution yet.

## Getting started with a development build

1. Build an APK using the instructions below and install it on Android.
2. Open HomeTTY. Setup saves its progress and offers optional companion choices.
   Android still asks for permissions and installation consent.
3. Use **Choose HomeTTY as Home** on the Home screen, or select it in Android's
   default Home-app settings.
4. Explore apps and search. **Tools → Android system shell** works independently
   of the Linux bootstrap. The **Terminal** entry requires a compatible bootstrap.

Setup keeps overall progress and live terminal output together. If a command asks
for input, use **Interact** to answer in its existing PTY. **Retry**, safe **Skip**
and confirmed **Cancel** are available as appropriate. Missing rebuilt components
remain unfinished; setup does not substitute stock Termux packages.

### Search examples

| Input | Action |
| --- | --- |
| `app: camera` | Find an Android launcher activity |
| `shell: ls -la` | Review and run a shell command in the terminal |
| `desktop: firefox` | Find a locally installed desktop application |
| `file: notes` | Find indexed Home files and folders |
| `web: Android documentation` | Search using an Android browser |
| `desktop-web: Android documentation` | Search using the desktop browser |

Shell, desktop and desktop-browser actions need the corresponding rebuilt Linux
packages. Desktop entries are parsed as argument lists; shell actions retain
confirmation and configured authentication gates.

## Configuration and security

Human configuration lives in `~/.config/hometty/config.toml`, **inside HomeTTY's
Android Home directory**. Runtime state lives in `~/.local/share/hometty`; cache
lives in `~/.cache/hometty`. Home icon positions, pins and overrides are stored in
TOML and reload when the app resumes.

```toml
[home]
columns = 4
rows = 3
clock = true
dock = true

[ai]
enabled = false

[security]
lock_terminal = false
lock_settings = false
lock_apps = false
```

See [configuration examples](docs/CONFIGURATION.md) for desktop, weather, AI,
plugin sources and app overrides.

Optional locks use Android authentication or a HomeTTY password/PIN. Passwords
use unique salts and a slow KDF; credentials use Android Keystore, never plaintext
TOML. Shizuku-compatible providers, root and ordinary Android capabilities are
supported to different extents. Privilege does not remove destructive-action
confirmation. Registering a plugin source does not install or execute code.

## Linux, desktops and companions

HomeTTY has a different private application prefix from Termux. **Stock
`com.termux` bootstrap archives and binary repositories are incompatible.**
Packages and bootstrap archives must be rebuilt for HomeTTY and verified before
use. See [bootstrap and repository instructions](docs/BOOTSTRAP.md).

The project adapts all 14 desktop/window-manager choices from the pinned GPLv3
`termux-desktop` source, with X11 or local VNC startup. Desktop packages, drivers,
themes, audio and runtime verification are still incomplete.

HomeTTY builds matching **API, Widget, Tasker and X11** companions from pinned
sources. Its setup offers bundled APKs through Android's installation consent.
Normal Termux installations retain the original Termux companions.

## Backup and updates

Settings provides configuration backup/validated restore and a Home-file ZIP
backup with reviewed merge restore. The Home-file archive excludes cache,
symlinks, Keystore secrets and device-bound Android/plugin grants; replaced files
are retained in a private rollback folder. Its logic has host tests, while the
Android storage-provider flow still needs device verification. Installed-prefix
backup, symlink preservation and process-death recovery remain unfinished.

Update controls can review/upgrade packages from configured signed HomeTTY
repositories and stage a user-selected newer HomeTTY APK. APK checks require the
same application identity, shared UID and signing certificate, followed by
Android's installation consent. Remote application-update catalogs and positive
installation checks are still pending.

## Build and verify

Required tools: JDK 17 or 21; Android SDK platform 36 and build-tools 35.0.0;
NDK 29.0.14206865; CMake 3.22.1; Ninja; bison; flex. Set `JAVA_HOME` and
`ANDROID_HOME` to your installations, then run:

```sh
scripts/build_debug.sh
```

The script initializes pinned X11 native submodules, builds and bundles all four
companions, builds Android APKs and runs host checks. APKs appear under
`app/build/outputs/apk/debug/`. Debug builds use the local SDK debug key;
production builds and companions require a shared private signing identity.
The wrapper is Gradle 8.13 with Android Gradle Plugin 8.13.2.

For a separate Android 16 Pixel 9 development emulator, see
[emulator instructions](docs/EMULATOR.md). Android 10 remains a compatibility test
image. Physical-device checks are tracked in [DEVICE_TESTS.md](docs/DEVICE_TESTS.md).

## Project documentation and license

- [Full accepted specification](docs/SPEC.md)
- [Implementation and verification status](docs/STATUS.md)
- [Configuration](docs/CONFIGURATION.md)
- [Pinned upstream sources and attribution](docs/UPSTREAM.md)
- [Original Termux README](docs/TERMUX_README.md)
- [License](LICENSE.md)

HomeTTY preserves upstream copyright, licenses and provenance. Original Termux
installation instructions describe Termux, not HomeTTY.
