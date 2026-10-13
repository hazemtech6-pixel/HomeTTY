# HomeTTY project rules

Build HomeTTY locally as an Android-only GPLv3 Termux fork and actual HOME launcher.
Application ID: com.hazemtech.termux.hometty. Preserve upstream copyright, licenses,
and provenance. Do not create GitHub resources or publish without instruction.

Work autonomously through implementation and verification. Keep builds runnable;
fix failures; never claim untested Android behavior works. Device-only checks belong
in docs/DEVICE_TESTS.md. Record incomplete functionality honestly in docs/STATUS.md.
The full accepted scope is docs/SPEC.md. Do not drop requirements to simplify work.

Architecture: retain upstream terminal emulator, PTY and service; add Android-native
launcher surfaces and TUI controls. Human configuration lives in ~/.config/hometty
as TOML; state belongs in ~/.local/share/hometty and cache in ~/.cache/hometty.
Secrets use Android Keystore, never TOML. Passwords use unique salts and a slow KDF.
Optional AI is absent everywhere when disabled. Privilege never bypasses HomeTTY
confirmation for destructive actions. Plugins require explicit capability grants;
adding a source never installs or executes code.

The installer is persistent, resumable, weighted, and PTY-backed. WAITING_FOR_USER
is not failure. Never kill a live interactive process because it asks a question.
Expose Retry / Interact / safe Skip / Cancel. Maintain command output and overall
progress simultaneously. Use HomeTTY companions in HomeTTY, originals in Termux.
Custom Termux prefixes require rebuilt packages/bootstrap; never pretend stock
com.termux binaries are compatible with HomeTTY's longer application ID.

Use minimal Android-first visual design, real app icons/widgets and wallpaper.
Support capability-based Shizuku providers including Shizuku-Next, root and ordinary
Android backends. Respect Android permission and user consent boundaries.

Verify pure logic with host tests and build Android APKs when tooling permits.
Track pinned upstream revisions and retain source licenses. Avoid downloading and
executing mutable remote installer scripts. No keys or machine paths in commits.
