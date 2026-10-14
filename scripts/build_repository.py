#!/usr/bin/env python3
"""Create a local signed APT repository from audited custom-prefix Debian packages.
GPL-3.0-only. Package contents are inspected, never extracted or executed.
"""
import argparse
import email.utils
import gzip
import hashlib
import io
import os
import json
import posixpath
import re
import subprocess
import tarfile
import time
import tempfile
from pathlib import Path, PurePosixPath

APPLICATION_ID = 'com.hazemtech.termux.hometty'
ARCHES = ('aarch64', 'arm', 'i686', 'x86_64')
PREFIX = f'data/data/{APPLICATION_ID}/files/usr'
STOCK = b'/data/data/com.termux/'

def ar_members(path, snapshot=None):
    with (io.BytesIO(snapshot) if snapshot is not None else path.open('rb')) as source:
        if source.read(8) != b'!<arch>\n':
            raise ValueError('Not a Debian ar archive')
        while header := source.read(60):
            if len(header) != 60 or header[-2:] != b'`\n':
                raise ValueError('Malformed Debian member header')
            name = header[:16].decode('ascii').strip().rstrip('/')
            size = int(header[48:58])
            if size < 0 or size > 1073741824:
                raise ValueError('Debian archive member too large')
            data = source.read(size)
            if len(data) != size:
                raise ValueError('Truncated Debian member')
            if size % 2: source.read(1)
            yield name, data

def inspect_deb(path, snapshot=None):
    fields = {}; control = None; saw_data = False; names = set()
    for name, data in ar_members(path, snapshot):
        if name in names: raise ValueError('Duplicate Debian archive member')
        names.add(name)
        if name == 'debian-binary':
            if data != b'2.0\n': raise ValueError('Unsupported Debian format')
        elif name.startswith('control.tar.'):
            with tarfile.open(fileobj=io.BytesIO(data), mode='r:*') as archive:
                for member in archive:
                    if not member.isfile(): continue
                    if member.size > 1048576: raise ValueError('Oversized control file')
                    content = archive.extractfile(member).read()
                    if PurePosixPath(member.name).name == 'control': control = content.decode('utf-8')
                    elif STOCK in content: raise ValueError('Stock prefix in maintainer script')
        elif name.startswith('data.tar.'):
            saw_data = True
            with tarfile.open(fileobj=io.BytesIO(data), mode='r:*') as archive:
                for member in archive:
                    item = PurePosixPath(member.name); normalized = str(item)
                    if item.is_absolute() or '..' in item.parts: raise ValueError('Unsafe package path')
                    if normalized == '.' and member.isdir(): continue
                    ancestors = {'data', 'data/data', f'data/data/{APPLICATION_ID}', f'data/data/{APPLICATION_ID}/files'}
                    if member.isdir() and normalized in ancestors: continue
                    if normalized != PREFIX and not normalized.startswith(PREFIX + '/'): raise ValueError('Package payload outside HomeTTY prefix')
                    if not (member.isfile() or member.isdir() or member.issym() or member.islnk()): raise ValueError('Unsupported package file type')
                    if member.issym():
                        resolved = posixpath.normpath(posixpath.join('/' + posixpath.dirname(normalized), member.linkname))
                        if not resolved.startswith((f'/data/data/{APPLICATION_ID}/', '/system/', '/proc/', '/dev/')): raise ValueError('Unsafe package symlink')
                    if member.islnk() and not str(PurePosixPath(member.linkname)).startswith(PREFIX + '/'): raise ValueError('Unsafe package hardlink')
                    if member.isfile():
                        content = archive.extractfile(member); first = content.read(4)
                        if first.startswith((b'\x7fELF', b'#!')):
                            tail = first
                            while chunk := content.read(65536):
                                tail += chunk
                                if STOCK in tail: raise ValueError('Stock-prefix executable')
                                tail = tail[-len(STOCK):]
    if 'debian-binary' not in names or control is None or not saw_data: raise ValueError('Incomplete Debian package')
    last = None
    for line in control.splitlines():
        if line.startswith((' ', '\t')) and last: fields[last] += '\n' + line
        elif ':' in line:
            last, value = line.split(':', 1)
            if last in fields: raise ValueError('Duplicate control field')
            fields[last] = value.strip()
        elif line: raise ValueError('Malformed control field')
    if fields.get('X-HomeTTY-Application-ID') != APPLICATION_ID: raise ValueError('Package lacks exact HomeTTY build identity')
    if fields.get('Architecture') not in (*ARCHES, 'all'): raise ValueError('Unsupported package architecture')
    if not re.fullmatch(r'[a-z0-9][a-z0-9+.-]+', fields.get('Package', '')) or not fields.get('Version'): raise ValueError('Invalid package identity')
    for generated in ('Filename', 'Size', 'SHA256', 'SHA512', 'MD5sum'): fields.pop(generated, None)
    return fields

def atomic_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix='.' + path.name + '-', dir=path.parent)
    try:
        with os.fdopen(descriptor, 'wb') as output:
            output.write(data); output.flush(); os.fsync(output.fileno())
        os.chmod(temporary, 0o644)
        os.replace(temporary, path)
        directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try: os.fsync(directory)
        finally: os.close(directory)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)

def build(source, destination, signing_home, extra_packages=()):
    packages = sorted(set(source.glob('*.deb')) | set(extra_packages))
    if not packages: raise ValueError('No Debian packages to publish locally')
    records = {arch: [] for arch in ARCHES}; pool = destination / 'pool'; pool.mkdir(parents=True, exist_ok=True)
    for package in packages:
        # Audit, hash and copy one snapshot, even while other packages are built.
        payload = package.read_bytes(); fields = inspect_deb(package, payload)
        digest = hashlib.sha256(payload).hexdigest()
        filename = f'pool/{digest}.deb'; target = destination / filename
        if target.is_symlink(): raise ValueError('Package pool contains a symlink')
        if target.exists():
            if hashlib.sha256(target.read_bytes()).hexdigest() != digest: raise ValueError('Package pool object is corrupt')
        else: atomic_write(target, payload)
        fields.update(Filename=filename, Size=str(len(payload)), SHA256=digest)
        record = '\n'.join(f'{key}: {value}' for key, value in fields.items()) + '\n\n'
        for arch in ARCHES:
            if fields['Architecture'] in (arch, 'all'): records[arch].append(record)
    indexes = []
    for arch in ARCHES:
        directory = destination / 'dists/stable/main' / f'binary-{arch}'; directory.mkdir(parents=True, exist_ok=True)
        data = ''.join(records[arch]).encode('utf-8')
        for name, content in [('Packages', data), ('Packages.gz', gzip.compress(data, mtime=0))]:
            atomic_write(directory / 'by-hash/SHA256' / hashlib.sha256(content).hexdigest(), content)
            atomic_write(directory / name, content)
        indexes.extend((directory / 'Packages', directory / 'Packages.gz'))
    now = time.time(); release = destination / 'dists/stable/Release'
    text = f'Origin: HomeTTY\nLabel: HomeTTY local custom-prefix packages\nSuite: stable\nCodename: stable\nDate: {email.utils.formatdate(now, usegmt=True)}\nValid-Until: {email.utils.formatdate(now + 14 * 86400, usegmt=True)}\nArchitectures: {" ".join(ARCHES)}\nComponents: main\nX-HomeTTY-Application-ID: {APPLICATION_ID}\nSHA256:\n'
    for index in indexes:
        data = index.read_bytes(); text += f' {hashlib.sha256(data).hexdigest()} {len(data):16d} {index.relative_to(release.parent).as_posix()}\n'
    text = text.replace('SHA256:\n', 'Acquire-By-Hash: yes\nSHA256:\n')
    command = ['gpg', '--homedir', str(signing_home), '--batch', '--yes', '--pinentry-mode', 'loopback', '--passphrase', '']
    with tempfile.TemporaryDirectory(prefix='.release-', dir=release.parent) as temporary:
        staged = Path(temporary); (staged / 'Release').write_text(text)
        subprocess.run(command + ['--clearsign', '--output', str(staged / 'InRelease'), str(staged / 'Release')], check=True)
        subprocess.run(command + ['--detach-sign', '--output', str(staged / 'Release.gpg'), str(staged / 'Release')], check=True)
        for name in ['Release', 'Release.gpg', 'InRelease']: atomic_write(release.parent / name, (staged / name).read_bytes())
    with (destination / 'hometty-keyring.gpg').open('wb') as output: subprocess.run(command + ['--export'], stdout=output, check=True)
    (destination / 'hometty-repository.json').write_text(json.dumps({'applicationId': APPLICATION_ID, 'prefix': '/' + PREFIX, 'architectures': list(ARCHES)}, indent=2) + '\n')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('packages', type=Path); parser.add_argument('destination', type=Path); parser.add_argument('--signing-home', type=Path, required=True)
    parser.add_argument('--extra-package', type=Path, action='append', default=[], help='Also audit and index a locally built companion Debian package')
    args = parser.parse_args(); build(args.packages, args.destination, args.signing_home, args.extra_package)
