# HomeTTY X11 companion

Adapted from https://github.com/termux/termux-x11 at
`fa3a8b430e2896a19f44c99a9cb056254615ae06`. Upstream GPL source and LICENSE are
retained. Native dependencies are exact upstream submodule revisions in the root
.gitmodules and gitlinks; their own licenses remain in those sources.

HomeTTY uses the sharedUid variant, ID `com.hazemtech.termux.hometty.x11`, shared
UID/process `com.hazemtech.termux.hometty`, and the main app's local SDK debug key.
Java/JNI namespaces remain upstream names. Intent actions and CLI loader routing
use the HomeTTY identity. The loader checks the companion's signing certificate.
The generated CLI package installs under HomeTTY's longer prefix; it contains
Android app_process wrappers and a DEX loader, not stock Termux ELF binaries.

The source is built with this repository's Gradle 8.13 wrapper and AGP 8.13.2,
SDK 36 and NDK 29.0.14206865. Version metadata is fixed to the upstream source
revision; package archive timestamps default to the pinned upstream commit's
Unix timestamp and may be overridden with SOURCE_DATE_EPOCH. The upstream README
continues to describe upstream distribution, not HomeTTY installation.

Build:

```sh
./gradlew -p companions/x11 :lorie-app:assembleSharedUidDebug :shell-loader:buildCompanionPackage
```

See scripts/build_debug.sh for the combined build with all companions bundled.
Android UI startup alone does not verify desktop clients, graphics, audio or
package-helper behavior; those checks are tracked in docs/DEVICE_TESTS.md.
