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

Run `scripts/build_bootstraps.sh` with Docker access to build the pinned package
sources in the digest-pinned upstream builder. The script mounts only isolated
project build directories and does not change host permissions or kernel settings.
`HOMETTY_BUILD_ARCHITECTURES` selects architectures (all four by default); x86_64
runs first for emulator verification. Containers and caches remain for diagnosis.
The overlay changes the application/API identities while retaining Java namespaces,
disables stock apt sources and stock mirror selection, and includes Python/API
helpers. The verified builder is compiling custom packages; no complete custom archive is
available yet. The container requires /dev/fuse and SYS_ADMIN for the upstream
FUSE toolchain overlay; it has no host PID namespace, Docker socket or personal
files mounted. Its original builder UID/group are preserved for copy-up ownership.
A signed custom repository and desktop packages still need implementation.

The package overlay adds `X-HomeTTY-Application-ID` to Debian control metadata and
installs only the local HomeTTY repository public key in the rebuilt keyring.
`scripts/prepare_repository_key.sh` creates a **development-only** signing key in
ignored local build storage. Production signing requires a separately managed key.
No key is committed, and no repository is hosted or published by these scripts.

After packages are built, generate a local signed repository:

```sh
python3 scripts/build_repository.py .toolchain/termux-packages/output .toolchain/repository --signing-home .toolchain/repository-signing
```

The generator checks exact build identity, payload paths and executable prefixes;
creates hashed package indexes, Release/InRelease signatures, a public keyring and
repository identity metadata. Unit tests verify signatures and package/index hashes.
Client-side repository delivery and the complete desktop package set remain pending.
