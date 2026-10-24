"""Regression checks for the public release packaging boundary."""
from contextlib import contextmanager
import hashlib
import os
from pathlib import Path
import runpy
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).with_name('prepare-release.py').resolve()


class ReleaseAssetsTest(unittest.TestCase):
    @contextmanager
    def bundle(self):
        original = Path.cwd()
        with tempfile.TemporaryDirectory() as directory:
            os.chdir(directory)
            try:
                for name in ['app/app.jar', 'frontend/dist/index.html', 'frontend/nginx.conf',
                             'docs/index.html', 'docker-compose.server.yml', 'DATABASE-UPGRADE.md',
                             '.env.example', 'setup-env.py', 'install.py', 'update.py', 'operations.py',
                             'release_tools.py', 'OPERATIONS.md', 'plugins/example.jar', 'LICENSE', 'NOTICE']:
                    path = Path('deploy-bundle') / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text('public example', encoding='utf-8')
                yield
            finally:
                os.chdir(original)

    def test_public_assets_include_templates_and_verifiable_checksums(self):
        with self.bundle():
            runpy.run_path(str(SCRIPT))
            with zipfile.ZipFile('release-assets/ciphergate-deploy.zip') as archive:
                self.assertIn('.env.example', archive.namelist())
                self.assertIn('setup-env.py', archive.namelist())
                self.assertNotIn('.env', archive.namelist())
            checksums = Path('release-assets/SHA256SUMS').read_text().splitlines()
            self.assertEqual(7, len(checksums))
            for line in checksums:
                expected, name = line.split('  ', 1)
                self.assertEqual(expected, hashlib.sha256((Path('release-assets') / name).read_bytes()).hexdigest())
            for name in ['ciphergate-deploy.zip', 'frontend-dist.zip', 'docs-dist.zip']:
                with zipfile.ZipFile(Path('release-assets') / name) as archive:
                    self.assertIn('LICENSE', archive.namelist())
                    self.assertIn('NOTICE', archive.namelist())

    def test_regeneration_drops_stale_release_assets(self):
        with self.bundle():
            Path('release-assets').mkdir()
            Path('release-assets/old-release.zip').write_text('stale')
            runpy.run_path(str(SCRIPT))
            self.assertFalse(Path('release-assets/old-release.zip').exists())

    def test_any_installed_environment_file_is_rejected(self):
        for name in ['.env', '.env.server', 'nested/.env']:
            with self.subTest(name=name), self.bundle():
                path = Path('deploy-bundle') / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('SECRET=do-not-publish', encoding='utf-8')
                with self.assertRaisesRegex(SystemExit, 'Forbidden deployment entry'):
                    runpy.run_path(str(SCRIPT))
                self.assertFalse(Path('release-assets').exists())

    def test_missing_backend_or_plugins_prevents_release(self):
        for name in ['app/app.jar', 'plugins/example.jar']:
            with self.subTest(name=name), self.bundle():
                (Path('deploy-bundle') / name).unlink()
                with self.assertRaises(SystemExit):
                    runpy.run_path(str(SCRIPT))


if __name__ == '__main__':
    unittest.main()
