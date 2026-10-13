#!/usr/bin/env python3
"""Stage audited custom-prefix bootstraps, or empty development archives."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import zipfile

PACKAGE = 'com.hazemtech.termux.hometty'
ARCHES = ('aarch64', 'arm', 'i686', 'x86_64')

def prepare(source, destination):
    destination.mkdir(parents=True, exist_ok=True)
    if not source:
        for arch in ARCHES:
            with zipfile.ZipFile(destination / f'bootstrap-{arch}.zip', 'w'):
                pass
        return
    source = Path(source)
    manifest = json.loads((source / 'manifest.json').read_text())
    if manifest.get('applicationId') != PACKAGE or manifest.get('prefix') != f'/data/data/{PACKAGE}/files/usr':
        raise ValueError('Bootstrap manifest must declare the exact HomeTTY application ID and prefix')
    for arch in ARCHES:
        name = f'bootstrap-{arch}.zip'
        data = (source / name).read_bytes()
        if hashlib.sha256(data).hexdigest() != manifest['sha256'][arch]:
            raise ValueError(f'Bootstrap checksum mismatch: {arch}')
        with zipfile.ZipFile(source / name) as archive:
            if not {'bin/bash', 'SYMLINKS.txt'}.issubset(archive.namelist()):
                raise ValueError(f'Incomplete bootstrap: {arch}')
            for entry in archive.infolist():
                if entry.filename.startswith('/') or '..' in Path(entry.filename).parts:
                    raise ValueError('Unsafe bootstrap path')
        shutil.copyfile(source / name, destination / name)

if __name__ == '__main__':
    prepare(sys.argv[1], Path(sys.argv[2]))
