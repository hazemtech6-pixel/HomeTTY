# Emulator development

The legacy Android 10/API 29 AVD is retained for compatibility checks. Android 16
uses a separate Pixel 9 profile, its native display density/resolution, and port
5556. Existing AVD data is never wiped or replaced.

With `JAVA_HOME` pointing to JDK 17/21 and `ANDROID_HOME` to the project SDK:

```sh
"$ANDROID_HOME/cmdline-tools/bin/sdkmanager" --sdk_root="$ANDROID_HOME" 'system-images;android-36;default;x86_64'
scripts/start_emulator.sh
```

The launcher waits for a fully installed system image rather than trying to boot a
partial download. The image's installed SDK metadata records its revision. Use
`adb -s emulator-5556` for Android 16; `emulator-5554` is the legacy AVD.

Keep the user's interactive emulator free from UI automation. Run automated UI
checks on a separate AVD. A cold boot (`-no-snapshot-load`) preserves installed
apps and private data while avoiding an incompatible graphics snapshot.
