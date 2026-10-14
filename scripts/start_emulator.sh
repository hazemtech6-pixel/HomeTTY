#!/usr/bin/env bash
# GPL-3.0-only. Start a modern project-owned AVD without replacing existing data.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17 or 21}"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$project_dir/.toolchain/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME="$project_dir/.toolchain/avd"
image='system-images;android-36;default;x86_64'
name=hometty-api36
if [ ! -f "$ANDROID_HOME/system-images/android-36/default/x86_64/package.xml" ]; then
    echo 'Android 16 is not installed yet. Let the active SDK download finish.' >&2
    exit 2
fi
mkdir -p "$ANDROID_AVD_HOME"
if [ ! -f "$ANDROID_AVD_HOME/$name.ini" ]; then
    printf 'no\n' | "$ANDROID_HOME/cmdline-tools/bin/avdmanager" create avd --name "$name" --package "$image" --device pixel_9 --path "$ANDROID_AVD_HOME/$name.avd"
fi
# Use a separate port: the interactive legacy AVD can remain open.
export QT_QPA_PLATFORM="${QT_QPA_PLATFORM:-xcb}"
exec "$ANDROID_HOME/emulator/emulator" -avd "$name" -port 5556 -no-snapshot-load -gpu swiftshader -memory 3072 -cores 2 "$@"
