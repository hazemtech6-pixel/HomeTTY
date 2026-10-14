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
# Explicitly resolve the image: some unpacked command-line-tools layouts infer
# the SDK's parent directory and write a doubled android-sdk path into the AVD.
python3 - "$ANDROID_AVD_HOME/$name.avd/config.ini" "$ANDROID_HOME/system-images/android-36/default/x86_64/" <<'PY'
import sys
from pathlib import Path
config = Path(sys.argv[1])
lines = config.read_text().splitlines()
lines = [line for line in lines if not line.startswith('image.sysdir.1=')]
lines.append('image.sysdir.1=' + sys.argv[2])
config.write_text('\n'.join(lines) + '\n')
PY
# Use a separate port: the interactive legacy AVD can remain open.
export QT_QPA_PLATFORM="${QT_QPA_PLATFORM:-xcb}"
exec "$ANDROID_HOME/emulator/emulator" -avd "$name" -port 5556 -no-snapshot-load -gpu "${HOMETTY_EMULATOR_GPU:-host}" -memory "${HOMETTY_EMULATOR_MEMORY:-4096}" -cores "${HOMETTY_EMULATOR_CORES:-4}" "$@"
