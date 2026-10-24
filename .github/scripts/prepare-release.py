"""Validate both platform bundles and emit a common set of release assets."""
from pathlib import Path
import hashlib
import shutil
import zipfile

bundle = Path('deploy-bundle')
required = ['app/app.jar', 'frontend/dist/index.html', 'frontend/nginx.conf',
            'docs/index.html', 'docker-compose.server.yml', 'DATABASE-UPGRADE.md', '.env.example', 'setup-env.py',
            'install.py', 'update.py', 'operations.py', 'release_tools.py', 'OPERATIONS.md',
            'LICENSE', 'NOTICE']
for relative in required:
    if not (bundle / relative).is_file():
        raise SystemExit(f'Missing deployment asset: {relative}')
if not list((bundle / 'plugins').glob('*.jar')):
    raise SystemExit('No plugins were packaged')
# A public artifact must carry only a template; deployment secrets are generated
# once on the installation host by setup-env.py.
for forbidden in ['.env', '.env.server', '.git', 'node_modules']:
    if list(bundle.rglob(forbidden)):
        raise SystemExit(f'Forbidden deployment entry: {forbidden}')
assets = Path('release-assets')
if assets.exists():
    shutil.rmtree(assets)
assets.mkdir()

def archive(name, root):
    with zipfile.ZipFile(assets / name, 'w', zipfile.ZIP_DEFLATED) as output:
        for file in sorted(root.rglob('*')):
            if file.is_file():
                output.write(file, file.relative_to(root))
        for license_name in ['LICENSE', 'NOTICE']:
            if not (root / license_name).is_file():
                output.write(bundle / license_name, license_name)

archive('ciphergate-deploy.zip', bundle)
archive('frontend-dist.zip', bundle / 'frontend' / 'dist')
archive('docs-dist.zip', bundle / 'docs')
shutil.copy2(bundle / 'app' / 'app.jar', assets / 'app.jar')
for name in ['LICENSE', 'NOTICE']:
    shutil.copy2(bundle / name, assets / name)
for plugin in (bundle / 'plugins').glob('*.jar'):
    shutil.copy2(plugin, assets / plugin.name)
with (assets / 'SHA256SUMS').open('w', encoding='utf-8', newline='\n') as output:
    for file in sorted(assets.iterdir()):
        if file.name != 'SHA256SUMS':
            with file.open("rb") as content:
                digest = hashlib.file_digest(content, "sha256").hexdigest()
            output.write(f'{digest}  {file.name}\n')
