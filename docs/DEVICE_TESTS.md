# Android verification checklist

A successful APK build or host test does not establish Android runtime behavior.
Record device/emulator model, Android version, APK commit, exact steps and outcome.
Physical-device checks remain required even after emulator smoke tests.

- [ ] Install alongside stock Termux. Confirm distinct application/data/permissions,
  no shared UID with stock Termux, no replacement of Termux's launcher icon.
- [ ] Choose HomeTTY as default HOME, press Home from another app, reboot, rotate,
  enter split screen, change font/display size, use gesture and button navigation.
- [ ] App discovery, icons, search, pinned-first and usage ordering, long-press
  rename/pin/info/uninstall; cancel uninstall; install/remove another app and refresh.
- [ ] Android shortcut access before/after becoming default HOME, shortcut launch,
  stale/uninstalled shortcut and work-profile behavior (profiles not implemented).
- [ ] Wallpaper visibility and picker; add/bind/configure/remove widgets, cancel
  picker/configuration, rotate during widget addition, reboot with widgets retained,
  remove widget provider. No automatic widget bind without OS consent.
- [ ] Clock/battery/network display, weather explicit coordinates, offline/error
  handling, permission denial and weather provider attribution.
- [ ] Files preview, symlink containment, large-file limit, SAF tree grant/denial,
  removable storage, config export/import/cancel and invalid backup rejection.
- [ ] AI disabled: no tab, key prompt, outbound request or background service.
- [ ] Configuration edits from native Settings and terminal editor use the same
  file; malformed/oversized/wrongly typed TOML offers recovery without a reset.
- [ ] Device-credential Terminal and Settings locks: direct activity, launcher
  shortcut, notification, background/foreground, rotation, screen lock, failed and
  cancelled auth. RUN_COMMAND rejects while terminal lock is configured.
- [ ] App-launch lock via HomeTTY only; explicitly verify it does not claim to
  block OS notifications/recents/other launchers. No custom-password UI yet.
- [ ] Android system shell uses real PTY, accepts input, resizes, preserves sessions
  through launcher switches, shows keyboard, displays exit status, renders images
  using retained upstream sixel/iTerm implementation.
- [ ] No-bootstrap Terminal path explains the missing custom build and does not
  install stock packages. Failsafe shell leaves no fake package installation.
- [ ] Setup shows live output and weighted overall progress simultaneously; Interact
  preserves PTY identity; input completes normally; Retry never starts a second
  live process; required/live steps cannot be skipped; Cancel is confirmed.
- [ ] Setup activity destruction/recreation reconnects to existing service PTY;
  process loss produces a recoverable error, never silently re-executes commands;
  successful steps and output logs survive relaunch. Fill storage to test writes.
- [ ] Rebuilt bootstrap per ABI: ELF paths, shebangs, apt/dpkg/repo trust, TLS,
  environment helpers; no stock com.termux binary fallback.
- [ ] Rebuilt X11 and VNC desktop: every supported desktop startup, clean exit,
  keyboard/audio/graphics, VNC localhost binding/password, desktop file integration.
- [ ] Capability-based Shizuku / Shizuku-Next, root and ordinary Android providers:
  unavailable/denied/granted/revoked/restarted, no silent privilege escalation,
  destructive confirmation remains even with root. Integration is incomplete.
- [ ] Signed HomeTTY API, Widget, Tasker and X11 install with common private signer,
  namespaced permissions and routing; original Termux companions stay with Termux.
- [ ] Plugin import does not execute; grant exact capabilities, run, edit manifest,
  verify changed content loses grants; revoke; reject unknown capabilities and
  malformed/oversized manifests. Registering a catalog does not download code.

## Recorded results

Host and build results are recorded in STATUS.md. Device results will be added
below only after execution. No physical Android device was attached at initial
inspection of this continuation.
