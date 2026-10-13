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
TOML. Hardware acceleration, themes, package recipes and companion builds are
still pending (see STATUS.md). The upstream GPL license is retained beside it.

Research references:

- [Termux custom-prefix bootstrap guidance](https://github.com/termux/termux-packages/wiki/For-maintainers)
- [Android widget hosting](https://developer.android.com/develop/ui/views/appwidgets/host)
- [Shizuku API and provider permission model](https://github.com/RikkaApps/Shizuku-API)
- [Open-Meteo API](https://open-meteo.com/en/docs)
