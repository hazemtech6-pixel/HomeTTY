#!/usr/bin/env bash
# GPL-3.0-only. Build custom-prefix bootstraps in an isolated, pinned container.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
revision=e950269dd7ad5d06069281c9b4d83559800c70e1
image=ghcr.io/termux/package-builder@sha256:44999857244619e25bc8e3427904a16d36cc0c0e087704af039e5bdf94814ac9
architectures="${HOMETTY_BUILD_ARCHITECTURES:-x86_64,aarch64,arm,i686}"
[[ "$architectures" =~ ^(x86_64|aarch64|arm|i686)(,(x86_64|aarch64|arm|i686))*$ ]] || { echo 'Invalid architectures' >&2; exit 1; }
checkout="$project_dir/.toolchain/termux-packages"
mkdir -p .toolchain/termux-build .toolchain/package-rootfs
if [ ! -d "$checkout/.git" ]; then
    git clone https://github.com/termux/termux-packages.git "$checkout"
    git -C "$checkout" checkout --detach "$revision"
fi
scripts/prepare_repository_key.sh
python3 scripts/prepare_package_sources.py "$checkout"
# Prefer ordinary Docker, then the user's already-authorized sudo/polkit access.
if docker info >/dev/null 2>&1; then docker_command=(docker)
elif sudo -n docker info >/dev/null 2>&1; then docker_command=(sudo -n docker)
else docker_command=(pkexec /usr/bin/docker)
fi
"${docker_command[@]}" pull "$image"
# Foreground output is also retained by Docker; an existing container is never deleted.
# Override the name explicitly when starting another independent build.
name="${HOMETTY_BUILD_CONTAINER:-hometty-package-builder}"
[ -c /dev/fuse ] || { echo "The pinned builder requires /dev/fuse" >&2; exit 1; }
"${docker_command[@]}" run --name "$name" --user 0:0 --cpus 4 --memory 6g --device /dev/fuse --cap-add SYS_ADMIN \
    --mount "type=bind,src=$checkout,dst=/home/builder/termux-packages" \
    --mount "type=bind,src=$project_dir/.toolchain/termux-build,dst=/home/builder/.termux-build" \
    --mount "type=bind,src=$project_dir/.toolchain/package-rootfs,dst=/data" \
    --env "HOMETTY_HOST_UID=$(id -u)" --env "HOMETTY_HOST_GID=$(id -g)" \
    --env "HOMETTY_ARCHITECTURES=$architectures" --env TERMUX_PKG_MAKE_PROCESSES=4 \
    "$image" bash -lc '
        set -euo pipefail
        if ! getent group "$HOMETTY_HOST_GID" >/dev/null; then groupadd -g "$HOMETTY_HOST_GID" homettyhost; fi
        usermod -aG "$HOMETTY_HOST_GID" builder
        # Retain the image builder UID: FUSE copy-up must match the NDK owners.
        chown -R "$(id -u builder):$HOMETTY_HOST_GID" /home/builder/.termux-build /data /home/builder/termux-packages
        chmod -R g+rwX /home/builder/.termux-build /data /home/builder/termux-packages
        exec sudo -H -u builder env TERMUX_PKG_MAKE_PROCESSES=4 bash scripts/build-bootstraps.sh --architectures "$HOMETTY_ARCHITECTURES" --add python,termux-api
    '
