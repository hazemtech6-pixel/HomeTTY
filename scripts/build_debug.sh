#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17 or 21}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
export PATH="$JAVA_HOME/bin:$PATH"
git submodule update --init --depth 1
./gradlew -p companions/x11 :lorie-app:assembleSharedUidDebug :shell-loader:buildCompanionPackage --console=plain
export HOMETTY_X11_APK="$project_dir/companions/x11/lorie-app/build/outputs/apk/sharedUid/debug/hometty-x11-universal-sharedUid-debug.apk"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :terminal-emulator:testDebugUnitTest :hometty-tasker:testDebugUnitTest :app:lintDebug --console=plain
python3 scripts/test_bootstrap.py
python3 scripts/test_settings_tui.py
python3 scripts/test_repository.py
