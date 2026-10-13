#!/usr/bin/env bash
# Local development signing only. No secret or public key is committed.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
key_home="$project_dir/.toolchain/repository-signing"
mkdir -p "$key_home"
chmod 700 "$key_home"
count="$(gpg --homedir "$key_home" --batch --with-colons --list-secret-keys 2>/dev/null | awk -F: '$1=="sec" {n++} END {print n+0}')"
if [ "$count" = 0 ]; then
    gpg --homedir "$key_home" --batch --pinentry-mode loopback --passphrase '' --quick-generate-key 'HomeTTY local development repository' ed25519 sign 1y
elif [ "$count" != 1 ]; then
    echo 'Expected exactly one local repository signing key; select a dedicated key directory.' >&2
    exit 1
fi
: "${HOMETTY_PACKAGE_CHECKOUT:=$project_dir/.toolchain/termux-packages}"
gpg --homedir "$key_home" --batch --export > "$HOMETTY_PACKAGE_CHECKOUT/packages/termux-keyring/hometty.gpg"
