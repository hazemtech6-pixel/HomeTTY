# Custom-prefix bootstrap and package boundary

HomeTTY's prefix is `/data/data/com.hazemtech.termux.hometty/files/usr`.
Stock `/data/data/com.termux/files/usr` binaries cannot be moved or byte-patched
into it. Package dependencies, ELF interpreters/rpaths, shebangs, helper APKs,
permissions and scripts all need the correct build-time identity.

## Development build

With no `HOMETTY_BOOTSTRAP_DIR`, Gradle embeds empty ZIPs and sets
`BuildConfig.HOMETTY_HAS_BOOTSTRAP=false`. The installer refuses to extract them.
Home, Apps, widgets, Settings and Tools can be exercised; Tools offers the
Android `/system/bin/sh` through the original PTY and service. This is explicitly
not a working Termux package environment. Do not point it at stock repositories.

## Bootstrap-enabled build contract

Rebuild using the termux-packages revision pinned in UPSTREAM.md. Upstream's
`scripts/build-bootstraps.sh` builds from local sources, without requiring an
already-published apt repository. Its build environment and `TERMUX_APP_PACKAGE`
setting must be configured for HomeTTY. Review upstream's current build guidance
before running it; no mutable remote installer is executed by this repository.

Supply these files in a local directory:

- `bootstrap-aarch64.zip`, `bootstrap-arm.zip`, `bootstrap-i686.zip`,
  `bootstrap-x86_64.zip`, all rebuilt for HomeTTY.
- `manifest.json` containing `applicationId`, `prefix`, and `sha256` object with
  the four architecture names as keys and the corresponding SHA-256 values.

```json
{
  "applicationId": "com.hazemtech.termux.hometty",
  "prefix": "/data/data/com.hazemtech.termux.hometty/files/usr",
  "sha256": {
    "aarch64": "REPLACE_WITH_SHA256",
    "arm": "REPLACE_WITH_SHA256",
    "i686": "REPLACE_WITH_SHA256",
    "x86_64": "REPLACE_WITH_SHA256"
  }
}
```

Set `HOMETTY_BOOTSTRAP_DIR` to that directory and run the normal Gradle build.
Preparation checks identity, hashes, required entries and archive paths before
packaging. A manifest is a build-producer attestation, not proof that every
package was correctly rebuilt. Test actual executables and package installation
on Android. `clean` does not delete supplied custom archives.

The current workstation has no custom-prefix archives or repository. Docker is
installed but the current user cannot access its socket. A complete desktop and
companion distribution remains blocked on a usable package-build environment,
rebuild work, common private signing, and Android validation. No stock package
fallback is allowed. Granting Docker access is an administrative decision; this
project does not change host groups, daemon permissions or system configuration.
