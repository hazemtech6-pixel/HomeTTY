import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from prepare_bootstrap import prepare, ARCHES, PACKAGE

class BootstrapTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        self.destination = self.root / 'destination'
    def manifest(self, package=PACKAGE, unsafe=False):
        hashes = {}
        for arch in ARCHES:
            target = self.source / f'bootstrap-{arch}.zip'
            with zipfile.ZipFile(target, 'w') as z:
                z.writestr('bin/bash', b'test fixture')
                z.writestr('SYMLINKS.txt', '')
                if unsafe: z.writestr('../outside', 'bad')
            hashes[arch] = hashlib.sha256(target.read_bytes()).hexdigest()
        (self.source / 'manifest.json').write_text(json.dumps({'applicationId': package, 'prefix': f'/data/data/{package}/files/usr', 'sha256': hashes}))
    def test_empty_development_archives(self):
        prepare('', self.destination)
        for arch in ARCHES:
            with zipfile.ZipFile(self.destination / f'bootstrap-{arch}.zip') as z: self.assertEqual([], z.namelist())
    def test_stock_prefix_rejected(self):
        self.manifest('com.termux')
        with self.assertRaises(ValueError): prepare(str(self.source), self.destination)
    def test_hash_and_path_validation(self):
        self.manifest(unsafe=True)
        with self.assertRaises(ValueError): prepare(str(self.source), self.destination)
        self.manifest()
        with open(self.source / 'bootstrap-arm.zip', 'ab') as out: out.write(b'tampered')
        with self.assertRaises(ValueError): prepare(str(self.source), self.destination)
    def test_verified_archive_staged(self):
        self.manifest()
        prepare(str(self.source), self.destination)
        self.assertTrue((self.destination / 'bootstrap-aarch64.zip').is_file())

if __name__ == '__main__': unittest.main()
