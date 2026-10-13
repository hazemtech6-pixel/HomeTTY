# HomeTTY

An Android-only GPLv3 Termux fork and HOME launcher, application ID
`com.hazemtech.termux.hometty`. This repository preserves the upstream terminal
emulator, PTY, service, image rendering and licenses.

**Development implementation, not a complete distribution.** Launcher development
builds deliberately contain no stock Termux bootstrap. The Android system shell
is available in Tools; a complete Linux environment requires packages rebuilt for
HomeTTY's longer application prefix. See [status](docs/STATUS.md), the
[full specification](docs/SPEC.md), and [bootstrap requirements](docs/BOOTSTRAP.md).

## Build and verify

Install JDK 17 or 21, Android SDK platform 36, build-tools 35.0.0, and NDK
29.0.14206865, CMake 3.22.1, Ninja, bison and flex. Set `JAVA_HOME` and `ANDROID_HOME` to your local installations:

```sh
scripts/build_debug.sh
python3 scripts/test_repository.py
```

The combined script initializes pinned X11 native submodules and bundles all four
HomeTTY companion APKs. Package/bootstrap build instructions are in
[BOOTSTRAP.md](docs/BOOTSTRAP.md).

APKs are written under `app/build/outputs/apk/debug/`. Debug signing uses the
local SDK debug key, never the public upstream test key. The Gradle wrapper is
pinned to 8.13, compatible with Android Gradle Plugin 8.13.2.

Configuration is `~/.config/hometty/config.toml`. Runtime state and encrypted
secrets live in `~/.local/share/hometty`; cache lives in `~/.cache/hometty`.
[Configuration examples](docs/CONFIGURATION.md) and
[Android checks](docs/DEVICE_TESTS.md) describe what can currently be exercised.

Original [Termux README](docs/TERMUX_README.md), [licenses](LICENSE.md), and
[pinned provenance](docs/UPSTREAM.md) are retained. Upstream Termux distribution
instructions are not HomeTTY installation instructions.
