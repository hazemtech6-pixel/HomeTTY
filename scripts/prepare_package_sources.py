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
    # bzip2 is emitted by libbz2; custom-prefix builds cannot resolve it by
    # downloading an already-built stock package. Build its source parent.
    bootstraps = root / 'scripts/build-bootstraps.sh'
    replace_once(bootstraps, 'local package_name="$2"',
                 'local package_name="$2"\n\tif [[ "$package_name" == "bzip2" ]]; then package_name="libbz2"; fi')
    # An interrupted configure leaves a directory without a usable interpreter.
    # Resume the pinned source build rather than treating that directory as success.
    host_python = root / 'scripts/build/setup/termux_setup_build_python.sh'
    replace_once(host_python, 'if [[ ! -d "$_PYTHON_FOLDER" ]]; then',
                 'if [[ ! -x "$_PYTHON_FOLDER/host-build-prefix/bin/python${TERMUX_PYTHON_VERSION}" ]]; then')
    replace_once(host_python, 'mkdir "$_PYTHON_FOLDER"', 'mkdir -p "$_PYTHON_FOLDER"')
    replace_once(host_python, 'mkdir host-build/', 'mkdir -p host-build/')
    replace_once(properties, 'TERMUX__NAME="Termux"', 'TERMUX__NAME="HomeTTY"')
    replace_once(properties, 'TERMUX_APP__PACKAGE_NAME="com.termux"', f'TERMUX_APP__PACKAGE_NAME="{APPLICATION_ID}"')
    replace_once(properties, 'TERMUX_API_APP__PACKAGE_NAME="com.termux.api"', f'TERMUX_API_APP__PACKAGE_NAME="{APPLICATION_ID}.api"')
    # Upstream's nightly helper downloads mutable, prebuilt stock-prefix content.
    # HomeTTY builds its loader from the pinned companion sources instead.
    nightly = root / 'x11-packages/termux-x11-nightly/build.sh'
    text = nightly.read_text()
    if '# HOMETTY_NO_STOCK_X11' not in text:
        nightly.write_text(text + '\n# HOMETTY_NO_STOCK_X11\ntermux_step_get_source() {\n    termux_error_exit "Use the source-built hometty-x11 companion package, not stock nightly binaries"\n}\n')
    for script, package in [('termux_step_create_debian_package.sh', '$TERMUX_PKG_NAME'), ('termux_create_debian_subpackages.sh', '$SUB_PKG_NAME')]:
        control = root / 'scripts/build' / script
        replace_once(control, f'Package: {package}\n', f'Package: {package}\n\t\tX-HomeTTY-Application-ID: {APPLICATION_ID}\n')
    # Same upstream rxvt archive/checksum, mirrored by Debian when the origin is unavailable.
    ncurses = root / 'packages/ncurses/build.sh'
    replace_once(ncurses, 'https://dist.schmorp.de/rxvt-unicode/Attic/rxvt-unicode-${TERMUX_PKG_VERSION[1]}.tar.bz2', 'https://deb.debian.org/debian/pool/main/r/rxvt-unicode/rxvt-unicode_${TERMUX_PKG_VERSION[1]}.orig.tar.bz2')
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
    if '# HOMETTY_PREFIX_BEGIN' in text:
        text = text[:text.index('# HOMETTY_PREFIX_BEGIN')] + text[text.index('# HOMETTY_PREFIX_END') + len('# HOMETTY_PREFIX_END'):]
    for dispatcher in (
        'termux_step_post_get_source() { termux_hometty_prepare_helpers; termux_hometty_adapt_source; }',
        'termux_hometty_prepare_helpers() { termux_hometty_prepare_helpers; termux_hometty_adapt_source; }',
    ):
        text = text.replace(dispatcher, '')
    if '# HOMETTY_HELPER_BEGIN' in text:
        text = text[:text.index('# HOMETTY_HELPER_BEGIN')] + text[text.index('# HOMETTY_HELPER_END') + len('# HOMETTY_HELPER_END'):]
    elif 'HOMETTY_HELPER' in text:
        text = text[:text.index('\ntermux_step_post_get_source()')]
    tools.write_text(text.rstrip() + '\n' + addition)
    # Package-local upstream defaults are independent of the build prefix. Adapt
    # source text before compilation; never patch compiled ELF files in place.
    for package in ('termux-api', 'termux-exec', 'termux-tools'):
        script = root / 'packages' / package / 'build.sh'
        source = script.read_text()
        begin, end = '# HOMETTY_PREFIX_BEGIN', '# HOMETTY_PREFIX_END'
        if begin in source:
            source = source[:source.index(begin)] + source[source.index(end) + len(end):]
        hook = f"""
# HOMETTY_PREFIX_BEGIN
export TERMUX_APP_PACKAGE={APPLICATION_ID}
export TERMUX_PREFIX
termux_hometty_adapt_source() {{
    python3 - "$TERMUX_PKG_SRCDIR" <<'HOMETTY_SOURCE'
from pathlib import Path
import sys
root = Path(sys.argv[1])
for path in root.rglob('*'):
    if not path.is_file() or path.is_symlink(): continue
    try: text = path.read_text(encoding='utf-8')
    except (UnicodeError, OSError): continue
    replacement = text.replace('/data/data/com.termux/', '/data/data/{APPLICATION_ID}/')
    if '{package}' == 'termux-api':
        replacement = replacement.replace('com.termux.api', '{APPLICATION_ID}.api')
    if replacement != text: path.write_text(replacement, encoding='utf-8')
HOMETTY_SOURCE
}}
# HOMETTY_PREFIX_END
"""
        # Keep the existing tool wrapper adaptation and run both source hooks.
        if package == 'termux-tools':
            source = source.replace('termux_step_post_get_source() {', 'termux_hometty_prepare_helpers() {', 1)
            hook += '\ntermux_step_post_get_source() { termux_hometty_prepare_helpers; termux_hometty_adapt_source; }\n'
        else:
            hook += '\ntermux_step_post_get_source() { termux_hometty_adapt_source; }\n'
        # Remove previously appended dispatcher when applying the overlay again.
        source = source.replace('\ntermux_step_post_get_source() { termux_hometty_prepare_helpers; termux_hometty_adapt_source; }\n', '\n').replace('\ntermux_step_post_get_source() { termux_hometty_adapt_source; }\n', '\n')
        script.write_text(source.rstrip() + '\n' + hook)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('checkout', type=Path)
    prepare(parser.parse_args().checkout.resolve())
