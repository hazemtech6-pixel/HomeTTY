#!/usr/bin/env python3
"""Stage audited custom-prefix bootstraps, or empty development archives."""
import hashlib
import json
import posixpath
import stat
from pathlib import Path, PurePosixPath
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
    validated = []
    for arch in ARCHES:
        name = f'bootstrap-{arch}.zip'
        data = (source / name).read_bytes()
        if hashlib.sha256(data).hexdigest() != manifest['sha256'][arch]:
            raise ValueError(f'Bootstrap checksum mismatch: {arch}')
        with zipfile.ZipFile(source / name) as archive:
            if not {'bin/bash', 'SYMLINKS.txt'}.issubset(archive.namelist()):
                raise ValueError(f'Incomplete bootstrap: {arch}')
            entries = set()
            total = 0
            for entry in archive.infolist():
                path = PurePosixPath(entry.filename)
                if path.is_absolute() or '..' in path.parts or '\\' in entry.filename or not path.parts:
                    raise ValueError('Unsafe bootstrap path')
                normalized = str(path)
                if normalized in entries or stat.S_ISLNK(entry.external_attr >> 16):
                    raise ValueError('Duplicate bootstrap path or embedded symlink')
                entries.add(normalized)
                total += entry.file_size
                if total > 1073741824:
                    raise ValueError('Bootstrap exceeds extraction size limit')
                with archive.open(entry) as contents:
                    header = contents.read(4)
                    if header == b'\x7fELF' and b'/data/data/com.termux/files/' in header + contents.read():
                        raise ValueError('Stock-prefix ELF in bootstrap')
            links = set()
            for line in archive.read('SYMLINKS.txt').decode('utf-8').splitlines():
                parts = line.split('←')
                if len(parts) != 2 or not all(parts):
                    raise ValueError('Malformed bootstrap symlink')
                target, link_destination = parts
                path = PurePosixPath(link_destination)
                if path.is_absolute() or '..' in path.parts or '\\' in link_destination or not path.parts:
                    raise ValueError('Unsafe bootstrap symlink link_destination')
                link_destination = str(path)
                if link_destination in links or link_destination in entries:
                    raise ValueError('Conflicting bootstrap symlink')
                resolved = posixpath.normpath(posixpath.join(manifest['prefix'], posixpath.dirname(link_destination), target))
                app_root = f'/data/data/{PACKAGE}/'
                if not (resolved.startswith(app_root) or resolved.startswith(('/system/', '/proc/', '/dev/'))):
                    raise ValueError('Bootstrap symlink points outside permitted Android paths')
                links.add(link_destination)
            if not links:
                raise ValueError('Bootstrap needs actual symlink records')
            for link in links:
                if any(name.startswith(link + '/') for name in entries | links):
                    raise ValueError('Bootstrap paths cannot traverse a declared symlink')
        validated.append(name)
    for name in validated:
        shutil.copyfile(source / name, destination / name)

if __name__ == '__main__':
    prepare(sys.argv[1], Path(sys.argv[2]))
