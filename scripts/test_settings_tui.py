import importlib.util
from pathlib import Path
import tempfile
import unittest

path = Path(__file__).resolve().parents[1] / 'app/src/main/assets/hometty_settings.py'
spec = importlib.util.spec_from_file_location('hometty_settings', path)
settings = importlib.util.module_from_spec(spec)
spec.loader.exec_module(settings)

class SettingsTest(unittest.TestCase):
    def test_preserves_comments_and_unknown_tables(self):
        source = '# Header\n[home]\ncolumns = 4 # personal layout\n[custom]\ntext = "a # literal"\n'
        changed = settings.replace_value(source, 'home.columns', 6)
        self.assertIn('columns = 6 # personal layout', changed)
        self.assertIn('[custom]\ntext = "a # literal"', changed)
        self.assertEqual(6, settings.validate(changed)['home']['columns'])
    def test_quoted_and_dotted_keys(self):
        for source in ['["ai"]\n"enabled" = false\n', 'ai.enabled = false\n']:
            changed = settings.replace_value(source, 'ai.enabled', True)
            self.assertTrue(settings.validate(changed)['ai']['enabled'])
    def test_concurrent_edit_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'config.toml'
            baseline = '[home]\ncolumns = 4\n'
            path.write_text(baseline)
            changed = settings.replace_value(baseline, 'home.columns', 6)
            outside = '[home]\ncolumns = 8\n'
            path.write_text(outside)
            with self.assertRaises(ValueError): settings.save_config(path, baseline, changed)
            self.assertEqual(outside, path.read_text())
    def test_invalid_or_secret_values_rejected_without_writes(self):
        for source in ['[home]\ncolumns = 99', '[ai]\nenabled = "false"', '[ai]\napi_key = "secret"']:
            with self.assertRaises(ValueError): settings.validate(source)
    def test_atomic_save_and_private_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'config.toml'
            baseline = '[home]\ncolumns = 4\n'
            path.write_text(baseline)
            changed = settings.replace_value(baseline, 'home.columns', 5)
            settings.save_config(path, baseline, changed)
            self.assertEqual(changed, path.read_text())
            self.assertEqual(0o600, path.stat().st_mode & 0o777)

if __name__ == '__main__': unittest.main()
