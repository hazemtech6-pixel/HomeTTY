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
    revision = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
    if revision != REVISION:
        raise RuntimeError(f'Expected pinned package revision {REVISION}, got {revision}')
    properties = root / 'scripts/properties.sh'
    replace_once(properties, 'TERMUX__NAME="Termux"', 'TERMUX__NAME="HomeTTY"')
    replace_once(properties, 'TERMUX_APP__PACKAGE_NAME="com.termux"', f'TERMUX_APP__PACKAGE_NAME="{APPLICATION_ID}"')
    replace_once(properties, 'TERMUX_API_APP__PACKAGE_NAME="com.termux.api"', f'TERMUX_API_APP__PACKAGE_NAME="{APPLICATION_ID}.api"')
    apt = root / 'packages/apt/build.sh'
    start = '\t\techo "# The main termux repository, with cloudflare cache"\n\t\techo "deb https://packages-cf.termux.dev/apt/termux-main/ stable main"\n\t\techo "# The main termux repository, without cloudflare cache"\n\t\techo "# deb https://packages.termux.dev/apt/termux-main/ stable main"'
    replace_once(apt, start, '\t\techo "# HomeTTY: only repositories rebuilt for this application prefix are compatible."\n\t\techo "# Configure a signed HomeTTY repository here; stock Termux repositories are disabled."')
    tools = root / 'packages/termux-tools/build.sh'
    # Preserve upstream helpers, but prohibit pkg/termux-change-repo silently selecting stock mirrors.
    addition = '''
termux_step_post_get_source() {
    for helper in scripts/pkg.in scripts/termux-change-repo.in; do
        if [ ! -f "$TERMUX_PKG_SRCDIR/$helper" ]; then
            termux_error_exit "HomeTTY expected helper missing: $helper"
        fi
        cat > "$TERMUX_PKG_SRCDIR/$helper" <<'HOMETTY_HELPER'
#!@TERMUX_PREFIX@/bin/bash
printf '%s\\n' 'HomeTTY requires repositories rebuilt for its application prefix.' 'Use apt with your signed HomeTTY repository; stock Termux mirror selection is disabled.' >&2
exit 1
HOMETTY_HELPER
    done
}
'''
    if 'HOMETTY_HELPER' not in tools.read_text():
        tools.write_text(tools.read_text() + addition)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('checkout', type=Path)
    prepare(parser.parse_args().checkout.resolve())
