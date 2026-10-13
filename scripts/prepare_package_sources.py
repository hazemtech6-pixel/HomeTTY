#!/usr/bin/env python3
"""Apply the HomeTTY identity boundary to the pinned, isolated package checkout."""
import argparse
import subprocess
from pathlib import Path

REVISION = 'e950269dd7ad5d06069281c9b4d83559800c70e1'
APPLICATION_ID = 'com.hazemtech.termux.hometty'

def replace_once(path, before, after):
    source = path.read_text()
    if after in source:
        return
    if source.count(before) != 1:
        raise RuntimeError(f'Unexpected upstream contents: {path.name}')
    path.write_text(source.replace(before, after))

def prepare(root):
    revision = subprocess.check_output(['git', '-c', f'safe.directory={root}', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
    if revision != REVISION:
        raise RuntimeError(f'Expected pinned package revision {REVISION}, got {revision}')
    properties = root / 'scripts/properties.sh'
    replace_once(properties, 'TERMUX__NAME="Termux"', 'TERMUX__NAME="HomeTTY"')
    replace_once(properties, 'TERMUX_APP__PACKAGE_NAME="com.termux"', f'TERMUX_APP__PACKAGE_NAME="{APPLICATION_ID}"')
    replace_once(properties, 'TERMUX_API_APP__PACKAGE_NAME="com.termux.api"', f'TERMUX_API_APP__PACKAGE_NAME="{APPLICATION_ID}.api"')
    for script, package in [('termux_step_create_debian_package.sh', '$TERMUX_PKG_NAME'), ('termux_create_debian_subpackages.sh', '$SUB_PKG_NAME')]:
        control = root / 'scripts/build' / script
        replace_once(control, f'Package: {package}\n', f'Package: {package}\n\t\tX-HomeTTY-Application-ID: {APPLICATION_ID}\n')
    apt = root / 'packages/apt/build.sh'
    start = '\t\techo "# The main termux repository, with cloudflare cache"\n\t\techo "deb https://packages-cf.termux.dev/apt/termux-main/ stable main"\n\t\techo "# The main termux repository, without cloudflare cache"\n\t\techo "# deb https://packages.termux.dev/apt/termux-main/ stable main"'
    replace_once(apt, start, '\t\techo "# HomeTTY: only repositories rebuilt for this application prefix are compatible."\n\t\techo "# Configure a signed HomeTTY repository here; stock Termux repositories are disabled."')
    tools = root / 'packages/termux-tools/build.sh'
    # Preserve upstream helpers, but prohibit pkg/termux-change-repo silently selecting stock mirrors.
    keyring = root / 'packages/termux-keyring/build.sh'
    text = keyring.read_text()
    start = text.index('termux_step_make_install() {')
    end = text.index('\ntermux_step_create_debscripts()', start)
    keyring.write_text(text[:start] + """termux_step_make_install() {
    # HomeTTY trusts only its own custom-prefix repository signing key.
    test -s "$TERMUX_PKG_BUILDER_DIR/hometty.gpg" || termux_error_exit "HomeTTY repository key missing"
    install -Dm600 "$TERMUX_PKG_BUILDER_DIR/hometty.gpg" "$TERMUX_PREFIX/share/termux-keyring/hometty.gpg"
    mkdir -p "$TERMUX_PREFIX/etc/apt/trusted.gpg.d"
    ln -sf "$TERMUX_PREFIX/share/termux-keyring/hometty.gpg" "$TERMUX_PREFIX/etc/apt/trusted.gpg.d/hometty.gpg"
}
""" + text[end:])
    replace_once(keyring, 'TERMUX_PKG_DESCRIPTION="GPG public keys for the official Termux repositories"', 'TERMUX_PKG_DESCRIPTION="GPG public key for the HomeTTY custom-prefix repository"')
    pkg_wrapper = Path(__file__).with_name('package-pkg.in').read_text()
    addition = """
# HOMETTY_HELPER_BEGIN
termux_step_post_get_source() {
    if [ ! -f "$TERMUX_PKG_SRCDIR/scripts/pkg.in" ] || [ ! -f "$TERMUX_PKG_SRCDIR/scripts/termux-change-repo.in" ]; then
        termux_error_exit "HomeTTY expected package helpers missing"
    fi
    cat > "$TERMUX_PKG_SRCDIR/scripts/pkg.in" <<'HOMETTY_PKG'
""" + pkg_wrapper + """HOMETTY_PKG
    cat > "$TERMUX_PKG_SRCDIR/scripts/termux-change-repo.in" <<'HOMETTY_MIRRORS'
#!@TERMUX_PREFIX@/bin/bash
printf '%s\\n' 'Stock Termux mirrors are incompatible with HomeTTY.' 'Configure a signed custom-prefix repository in $PREFIX/etc/apt/sources.list.' >&2
exit 1
HOMETTY_MIRRORS
}
# HOMETTY_HELPER_END
"""
    text = tools.read_text()
    if '# HOMETTY_HELPER_BEGIN' in text:
        text = text[:text.index('# HOMETTY_HELPER_BEGIN')] + text[text.index('# HOMETTY_HELPER_END') + len('# HOMETTY_HELPER_END'):]
    elif 'HOMETTY_HELPER' in text:
        text = text[:text.index('\ntermux_step_post_get_source()')]
    tools.write_text(text.rstrip() + '\n' + addition)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('checkout', type=Path)
    prepare(parser.parse_args().checkout.resolve())
