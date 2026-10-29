"""Bundle the reviewed Android source and APK; exclude toolchains, keys and local state."""
from pathlib import Path
import hashlib
import zipfile

root = Path(__file__).resolve().parents[1]
release = root / 'release'
apk = release / 'CurbLink-Drive-0.2-demo.apk'
if not apk.is_file():
    raise SystemExit('Build the APK first with scripts/build-drive.ps1')
files = []
for name in ['README.md', 'AGENTS.md', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'run-domain-tests.ps1', '.gitignore', 'app/build.gradle.kts']:
    p = root / name
    if p.is_file(): files.append(p)
for directory in ['app/src', 'gradle/wrapper', 'simulator', 'bridge']:
    files.extend(p for p in (root / directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts and p.suffix != '.pyc')
for name in ['scripts/build-drive.ps1', 'scripts/package-drive.py', 'release/CurbLink-Drive-0.2-demo.apk', 'release/CurbLink-Drive-0.2-demo.apk.sha256', 'release/README.txt', 'release/BUILD-VERIFICATION.txt']:
    files.append(root / name)
archive = release / 'CurbLink-Drive-0.2-source.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as out:
    for path in sorted(set(files)):
        out.write(path, 'CurbLink-Drive-0.2/' + path.relative_to(root).as_posix())
with zipfile.ZipFile(archive) as check:
    if check.testzip(): raise SystemExit('Archive CRC check failed')
    assert not any('/build/' in name or 'local.properties' in name or name.endswith('.keystore') for name in check.namelist())
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
archive.with_suffix('.zip.sha256').write_text(digest + '\n', encoding='ascii')
print(f'{archive}\n{len(set(files))} files; {archive.stat().st_size:,} bytes; CRC verified')
