import gzip
import hashlib
import io
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path
from build_repository import APPLICATION_ID, PREFIX, inspect_deb, build

def tar_bytes(name, data):
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode='w:xz') as archive:
        member = tarfile.TarInfo(name); member.size = len(data); archive.addfile(member, io.BytesIO(data))
    return output.getvalue()

def package(path, identity=APPLICATION_ID, payload=None, executable=b'#!/system/bin/sh\necho fixture\n'):
    control = f'Package: fixture\nVersion: 1.0\nArchitecture: x86_64\nDescription: Repository test fixture\nX-HomeTTY-Application-ID: {identity}\n'.encode()
    members = [('debian-binary', b'2.0\n'), ('control.tar.xz', tar_bytes('./control', control)), ('data.tar.xz', tar_bytes(payload or f'./{PREFIX}/bin/fixture', executable))]
    with path.open('wb') as output:
        output.write(b'!<arch>\n')
        for name, data in members:
            output.write(f'{name+"/":<16}{0:<12}{0:<6}{0:<6}{100644:<8}{len(data):<10}`\n'.encode()); output.write(data)
            if len(data) % 2: output.write(b'\n')

class RepositoryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(); self.addCleanup(self.temporary.cleanup); self.root = Path(self.temporary.name)
        self.deb = self.root / 'fixture.deb'
    def test_identity_payload_escape_and_stock_executable_are_rejected(self):
        for options in ({'identity': 'com.termux'}, {'payload': '../outside'}, {'payload': 'data/data/com.termux/files/usr/bin/fixture'}, {'executable': b'\x7fELF/data/data/com.termux/files/usr/bin/bash'}):
            package(self.deb, **options)
            with self.assertRaises(ValueError): inspect_deb(self.deb)
        package(self.deb); self.assertEqual('fixture', inspect_deb(self.deb)['Package'])
    def test_signed_indexes_match_packages_and_release(self):
        package(self.deb); key_home = self.root / 'keys'; key_home.mkdir(mode=0o700)
        base = ['gpg', '--homedir', str(key_home), '--batch', '--pinentry-mode', 'loopback', '--passphrase', '']
        try:
            subprocess.run(base + ['--quick-generate-key', 'HomeTTY repository test', 'ed25519', 'sign', '1d'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            destination = self.root / 'repository'; build(self.root, destination, key_home)
            subprocess.run(base + ['--verify', str(destination / 'dists/stable/InRelease')], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            data = (destination / 'dists/stable/main/binary-x86_64/Packages').read_bytes()
            digest = hashlib.sha256(self.deb.read_bytes()).hexdigest()
            self.assertIn(f'Filename: pool/{digest}.deb'.encode(), data)
            self.assertEqual(data, gzip.decompress((destination / 'dists/stable/main/binary-x86_64/Packages.gz').read_bytes()))
            self.assertIn(hashlib.sha256(data).hexdigest(), (destination / 'dists/stable/Release').read_text())
            self.assertEqual(b'', (destination / 'dists/stable/main/binary-aarch64/Packages').read_bytes())
        finally:
            subprocess.run(['gpgconf', '--homedir', str(key_home), '--kill', 'gpg-agent'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

if __name__ == '__main__': unittest.main()
