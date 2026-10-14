# Upstream provenance

HomeTTY retains the Termux terminal emulator, PTY JNI, view, service, file provider,
licenses, authorship, and source namespaces. The Android application ID is separate
from those Java namespaces: `com.hazemtech.termux.hometty`.

| Source | Pinned revision | Use |
| --- | --- | --- |
| https://github.com/termux/termux-app | `8629e632fcb95da272221be327db653fb24befe9` | Existing imported application and libraries |
| https://github.com/sabamdarif/termux-desktop | `9070d1bcd33e0e1618be0e0d2a0b880c5f328307` | Desktop selection/startup table, GPLv3 source snapshot |
| https://github.com/termux/termux-packages | `e950269dd7ad5d06069281c9b4d83559800c70e1` | Reference for rebuilding custom-prefix packages; no binary packages imported |

The initial HomeTTY commit `eabf5f7` was compared against the pinned termux-app
revision: 334 tracked files matched byte-for-byte. `gradlew.bat` differed in line
endings. The HomeTTY rules, specification, and accidentally tracked toolchain
archive were the only paths absent upstream. The toolchain archive and public
upstream test signing key have been removed from tracking, without deleting local
files or rewriting history. Debug signing now uses the developer's local Android
SDK debug key. Production and companion signing must use a privately held common
key; never commit it.

`vendor/termux-desktop/setup-termux-desktop.upstream` is an unmodified reference
snapshot, **not an executable HomeTTY installer**. Its mutable remote downloads,
stock Termux binaries, release APKs, and configuration file are not run. The
adaptation in `DesktopCatalog` retains all 14 desktop choices and uses HomeTTY
TOML. Hardware acceleration, themes, package recipes are
still pending (see STATUS.md). The upstream GPL license is retained beside it.

Research references:

- [Termux custom-prefix bootstrap guidance](https://github.com/termux/termux-packages/wiki/For-maintainers)
- [Android widget hosting](https://developer.android.com/develop/ui/views/appwidgets/host)
- [Shizuku API and provider permission model](https://github.com/RikkaApps/Shizuku-API)
- [Open-Meteo API](https://open-meteo.com/en/docs)

## HomeTTY companion sources

The imported source modules preserve their upstream notices and READMEs. Their
package identity, prefix, permissions and shared UID target HomeTTY; Java class
names remain upstream names. They depend on the local adapted termux-shared,
rather than a stock shared-library artifact. All companion debug APKs use the same local
SDK signing key. No upstream public signing key was imported.

| Companion | Revision | Module |
| --- | --- | --- |
| https://github.com/termux/termux-api | `fc26ce17e3badf85d4df191af364fcf7798047f1` | `:hometty-api` |
| https://github.com/termux/termux-widget | `b3aacb8175fb2d7167ce8d1ad6817087498aa044` | `:hometty-widget` |
| https://github.com/termux/termux-tasker | `dbf685fe2973c3490a27cbd37f31909ad5eb3bb3` | `:hometty-tasker` |
| https://github.com/termux/termux-x11 | `fa3a8b430e2896a19f44c99a9cb056254615ae06` | Standalone `companions/x11`; all-ABI native build and CLI packages |

API command-line helpers must be rebuilt for the custom application prefix and
broadcast to `com.hazemtech.termux.hometty.api/com.termux.api.TermuxApiReceiver`.
APK assembly and matching signatures alone do not verify those Linux helpers.

The pinned X11 native dependencies are recorded as Git submodules in .gitmodules.
Run scripts/build_debug.sh to build X11 and bundle all four companion APKs.
Package builds use termux-packages `e950269dd7ad5d06069281c9b4d83559800c70e1`
and builder digest `sha256:44999857244619e25bc8e3427904a16d36cc0c0e087704af039e5bdf94814ac9`.

HomeTTY adds DEC mode 2026 synchronized output to the retained Termux engine,
following [Contour’s synchronized-output specification](https://contour-terminal.org/vt-extensions/synchronized-output/).
The view holds the preceding rendered frame while parsing continues, with a
one-second producer timeout. Frame caching is activated only when requested and
is bounded to four million pixels; oversized views or allocation failure use
normal rendering. Native rendering/performance checks remain device work.
