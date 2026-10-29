"""Real APK capture using adb; no screenshot scaling or image synthesis.

Usage: python scripts/capture-drive.py --serial DEVICE prepare|dump|tap|shot|restore
tap requires a visible text argument; shot requires a simple filename stem.
Always run restore after the capture session, including after an interruption.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--adb', default=shutil.which('adb') or str(Path(os.environ.get('ANDROID_HOME', '')) / 'platform-tools/adb.exe'))
parser.add_argument('action', choices=['prepare', 'dump', 'tap', 'shot', 'restore'])
parser.add_argument('value', nargs='?')
args = parser.parse_args()
session = ROOT / 'work' / ('capture-device-' + hashlib.sha256(args.serial.encode()).hexdigest()[:12] + '.json')
out = ROOT / 'captures' / 'android'

def adb(*cmd):
    return subprocess.run([args.adb, '-s', args.serial, *cmd], check=True, capture_output=True, timeout=60).stdout

def shell(*cmd):
    return adb('shell', *cmd).decode('utf-8', errors='replace').strip()

def setting(key):
    return shell('settings', 'get', 'system', key)

def dump():
    shell('uiautomator', 'dump', '/sdcard/curblink-capture.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/sdcard/curblink-capture.xml'))

if args.action == 'prepare':
    if session.exists():
        raise SystemExit('Restore the previous capture session before preparing another.')
    data = {'size': shell('wm', 'size'), 'density': shell('wm', 'density'),
            'accelerometer_rotation': setting('accelerometer_rotation'),
            'user_rotation': setting('user_rotation')}
    session.parent.mkdir(exist_ok=True)
    session.write_text(json.dumps(data, indent=2), encoding='utf-8')
    # A portrait natural display is rotated, not stretched after capture.
    physical = re.search(r'Physical size:\s*(\d+)x(\d+)', data['size'])
    portrait = not physical or int(physical[1]) < int(physical[2])
    shell('wm', 'size', '1380x2560' if portrait else '2560x1380')
    shell('wm', 'density', '240')
    shell('settings', 'put', 'system', 'accelerometer_rotation', '0')
    shell('wm', 'user-rotation', 'lock', '1' if portrait else '0')
    adb('install', '-r', str(ROOT / 'release/CurbLink-Drive-0.2-demo.apk'))
    shell('am', 'start', '-n', 'io.curblink.demo/.MainActivity')
    time.sleep(3)  # Avoid photographing the system rotation transition.
    print('Prepared. Unlock device if necessary. Always run restore when finished.')
elif args.action == 'restore':
    data = json.loads(session.read_text(encoding='utf-8'))
    for cmd, key in [('size', 'size'), ('density', 'density')]:
        override = re.search(r'Override (?:size|density):\s*(\S+)', data[key])
        shell('wm', cmd, override[1] if override else 'reset')
    for key in ['user_rotation', 'accelerometer_rotation']:
        value = data[key]
        shell('settings', 'delete', 'system', key) if value == 'null' else shell('settings', 'put', 'system', key, value)
    shell('rm', '-f', '/sdcard/curblink-capture.xml')
    session.unlink()
    print('Original display size, density and rotation restored.')
elif args.action == 'dump':
    for n in dump().iter('node'):
        text = n.get('text') or n.get('content-desc')
        if text:
            print(text, n.get('bounds'), n.get('enabled'))
elif args.action == 'tap':
    nodes = [n for n in dump().iter('node') if args.value == (n.get('text') or n.get('content-desc'))]
    if len(nodes) != 1 or nodes[0].get('enabled') != 'true':
        raise SystemExit('Expected exactly one enabled matching UI node.')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', nodes[0].get('bounds')))
    shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
elif args.action == 'shot':
    if not args.value or not re.fullmatch(r'[a-z0-9-]+', args.value):
        raise SystemExit('Use a lowercase filename stem.')
    resumed = shell('dumpsys', 'activity', 'activities')
    if not any('io.curblink.demo' in line and ('ResumedActivity' in line or 'topResumedActivity' in line) for line in resumed.splitlines()):
        raise SystemExit('CurbLink is not the resumed activity; screenshot rejected.')
    out.mkdir(parents=True, exist_ok=True)
    target = out / (args.value + '.png')
    time.sleep(1)  # Allow navigation/Compose transitions to settle.
    raw = adb('exec-out', 'screencap', '-p')
    import io
    with Image.open(io.BytesIO(raw)) as im:
        if im.size != (2560, 1380):
            raise SystemExit(f'Actual display {im.size} differs from 2560x1380; no image saved.')
    if len(raw) > 8_000_000:
        raise SystemExit('PNG exceeds 8MB; no image saved.')
    target.write_bytes(raw)
    apk = ROOT / 'release/CurbLink-Drive-0.2-demo.apk'
    record = {'file': target.name, 'pixels': [2560, 1380], 'format': 'PNG',
              'capture': 'adb exec-out screencap -p, unchanged bytes',
              'model': shell('getprop', 'ro.product.model'), 'android': shell('getprop', 'ro.build.version.release'),
              'features': shell('pm', 'list', 'features').splitlines(),
              'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
              'captured_at': time.strftime('%Y-%m-%dT%H:%M:%S%z'),
              'png_sha256': hashlib.sha256(raw).hexdigest()}
    target.with_suffix('.json').write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding='utf-8')
    print(target)
