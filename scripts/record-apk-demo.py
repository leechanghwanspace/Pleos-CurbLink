"""Record the release APK on the Pleos Connect emulator while touring the parked flows.

The vehicle stays in P with real CarService/NaviHelper readings; no TEST observations are injected,
so the driving lane screen is not exercised here (see the web simulation video for driving).

Example handoff photos: put front/rear/left/right images (jpg/png/webp) in submission/example-photos/.
They are added to the emulator gallery of the current Android user and attached by tapping each
direction slot in the app and choosing the photo in the system photo picker, exactly as a user would.

Output:
  submission/video/curblink-apk-demo.mp4      adb screenrecord at device resolution, H.264 re-encode (crf 16)
  submission/screenshots/apk/*.png            adb screencap, unchanged PNG bytes
  submission/video/apk-verification.json      device, APK hash, photos, steps, captures

Usage: python scripts/record-apk-demo.py --serial emulator-5554 [--hold 2.5]
The existing install is updated with `adb install -r`; app data is never cleared. If the installed
app has a different signature, the script stops instead of uninstalling.
"""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / 'apk/CurbLink_CurbLink_release.apk'
PKG = 'io.curblink.demo'
VIDEO = ROOT / 'submission/video'
SHOTS = ROOT / 'submission/screenshots/apk'
REMOTE = '/sdcard/curblink-apk-demo.mp4'
# Test coordinate for the emulator GPS (Seoul City Hall); not a user location.
TEST_LON, TEST_LAT = '126.9780', '37.5665'
PHOTOS = ROOT / 'submission/example-photos'
PHOTO_ORDER = ['front', 'rear', 'left', 'right']  # app slots: 전방, 후방, 좌측, 우측
PHOTO_PREFIX = 'curblink-example-'
MEDIA = 'content://media/external_primary/images/media'
MIME = {'.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp'}
photo_times = {}  # photo stem -> time label the picker shows (from the row's date_modified)
SLOT_LABEL = {'front': '전방', 'rear': '후방', 'left': '좌측', 'right': '우측'}

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument('--serial', required=True)
parser.add_argument('--adb', default=shutil.which('adb') or 'adb')
parser.add_argument('--hold', type=float, default=2.5)
args = parser.parse_args()
steps, captures = [], []


def adb(*cmd, check=True, timeout=60):
    r = subprocess.run([args.adb, '-s', args.serial, *cmd], capture_output=True, timeout=timeout)
    if check and r.returncode:
        raise RuntimeError(f'adb {" ".join(cmd)}: {r.stderr.decode("utf-8", "replace").strip()}')
    return r.stdout.decode('utf-8', 'replace').strip()


def hold(scale=1.0):
    time.sleep(args.hold * scale)


def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/curblink-ui.xml')
    root = ET.fromstring(adb('exec-out', 'cat', '/sdcard/curblink-ui.xml').encode('utf-8'))
    return list(root.iter('node'))


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    return (x1 + x2) // 2, (y1 + y2) // 2


def content_area():
    size = re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size').splitlines()[-1])
    return int(size[1]), int(size[2])


def swipe(direction='up'):
    w, h = content_area()
    x = w // 2
    a, b = (int(h * .72), int(h * .38)) if direction == 'up' else (int(h * .38), int(h * .72))
    adb('shell', 'input', 'swipe', str(x), str(a), str(x), str(b), '600')
    time.sleep(.8)


def find(text, exact=True):
    for n in nodes():
        label = n.get('text') or n.get('content-desc') or ''
        if (label == text) if exact else (text in label):
            return n
    return None


def tap(text, exact=True, scrolls=5, label=None):
    for attempt in range(scrolls + 1):
        n = find(text, exact)
        if n is not None:
            x, y = center(n)
            adb('shell', 'input', 'tap', str(x), str(y))
            steps.append(label or text)
            print('TAP:', label or text, flush=True)
            time.sleep(1.2)
            return
        swipe('up' if attempt < scrolls // 2 + 1 else 'down')
    raise RuntimeError('Not visible: ' + text)


def tap_any(*texts):
    for text in texts:
        if find(text) is not None:
            return tap(text, scrolls=0)
    return tap(texts[0])


def switch_for(label):
    all_nodes = nodes()
    row = next((n for n in all_nodes if n.get('text') == label), None)
    if row is None:
        return None
    _, ry = center(row)
    return next((n for n in all_nodes if n.get('checkable') == 'true' and abs(center(n)[1] - ry) < 40), None)


def set_switch(label, on=True, record=True):
    """Set the Switch on the same row as a toggle label, re-reading its state after each tap."""
    for attempt in range(6):
        sw = switch_for(label)
        if sw is None:
            swipe('up')
            continue
        if (sw.get('checked') == 'true') == on:
            if record:
                steps.append(f'switch {"on" if on else "off"}: {label}')
                print('SWITCH:', label, on, flush=True)
            return
        x, y = center(sw)
        adb('shell', 'input', 'tap', str(x), str(y))
        time.sleep(1.0)
    raise RuntimeError('Switch did not change: ' + label)


def ensure_app():
    """Bring CurbLink back to the front if another app (e.g. Pleos navigation) took over."""
    for _ in range(3):
        top = adb('shell', 'dumpsys', 'activity', 'activities').split('topResumedActivity', 1)[-1][:300]
        if PKG in top:
            return
        adb('shell', 'am', 'start', '-n', f'{PKG}/.MainActivity')
        time.sleep(3)
    raise RuntimeError('CurbLink is not in the foreground')


def tab(label):
    ensure_app()
    # Tabs live above the scrolling content; scroll content back to the top first.
    for _ in range(3):
        swipe('down')
    tap(label, scrolls=0)


def shot(name):
    SHOTS.mkdir(parents=True, exist_ok=True)
    raw = subprocess.run([args.adb, '-s', args.serial, 'exec-out', 'screencap', '-p'], capture_output=True, timeout=60).stdout
    assert raw[:8] == b'\x89PNG\r\n\x1a\n', 'screencap did not return PNG'
    path = SHOTS / f'{name}.png'
    path.write_bytes(raw)
    width, height = int.from_bytes(raw[16:20], 'big'), int.from_bytes(raw[20:24], 'big')
    captures.append({'file': f'screenshots/apk/{name}.png', 'width': width, 'height': height,
                     'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw)})
    print('SHOT:', name, flush=True)


def example_photos():
    found = []
    for name in PHOTO_ORDER:
        match = [p for p in PHOTOS.glob(name + '.*') if p.suffix.lower() in MIME]
        if len(match) != 1:
            raise SystemExit(f'Expected exactly one {name}.jpg/png/webp in {PHOTOS}')
        found.append(match[0])
    return found


def stage_photos(user, photos):
    """Publish the photos to the current user's gallery: newest first = front, so the picker grid
    shows them as front, rear, left, right. Rows are written while pending so the picker never
    indexes a partial file."""
    adb('shell', 'content', 'delete', '--user', user, '--uri', f"'{MEDIA}?includePending=1'",
        '--where', f"\"_display_name LIKE '{PHOTO_PREFIX}%'\"", check=False)
    for path in reversed(photos):
        display = PHOTO_PREFIX + path.name
        adb('shell', 'content', 'insert', '--user', user, '--uri', MEDIA,
            '--bind', f'_display_name:s:{display}', '--bind', f'mime_type:s:{MIME[path.suffix.lower()]}',
            '--bind', 'relative_path:s:Pictures/CurbLink/', '--bind', 'is_pending:i:1')
        rows = adb('shell', 'content', 'query', '--user', user, '--uri', f"'{MEDIA}?includePending=1'",
                   '--projection', '_id:_display_name')
        row_id = re.search(rf'_id=(\d+), _display_name={re.escape(display)}', rows)[1]
        # exec-in truncates larger inputs, so stage the file where the shell user can read it.
        staged = f'/data/local/tmp/{display}'
        adb('push', str(path), staged, timeout=120)
        adb('shell', f"content write --user {user} --uri {MEDIA}/{row_id} < '{staged}'", timeout=120)
        adb('shell', 'rm', '-f', staged, check=False)
        adb('shell', 'content', 'update', '--user', user, '--uri', f"'{MEDIA}/{row_id}?includePending=1'",
            '--bind', 'is_pending:i:0')
        for _ in range(10):
            row = re.search(rf'_id={row_id}, _size=(\d+), date_modified=(\d+)', adb(
                'shell', 'content', 'query', '--user', user, '--uri', MEDIA, '--projection', '_id:_size:date_modified'))
            if row and int(row[1]) == path.stat().st_size:
                break
            time.sleep(.5)
        else:
            raise SystemExit(f'Gallery copy of {path.name} is incomplete')
        # The picker labels each tile with its time in the device timezone, e.g. "오후 3:43:54".
        offset = adb('shell', 'date', '+%z')
        sign = -1 if offset.startswith('-') else 1
        local = int(row[2]) + sign * (int(offset[1:3]) * 3600 + int(offset[3:5]) * 60)
        h, m, s = (local // 3600) % 24, (local // 60) % 60, local % 60
        photo_times[path.stem] = f'{"오전" if h < 12 else "오후"} {(h % 12) or 12}:{m:02d}:{s:02d}'
        print('PHOTO:', path.name, '->', row_id, photo_times[path.stem], flush=True)
        time.sleep(1.2)  # distinct date_added seconds keep every tile label unique


def wait_picker(open_=True):
    for _ in range(20):
        top = adb('shell', 'dumpsys', 'activity', 'activities').split('topResumedActivity', 1)[-1][:300]
        if ('PhotoPicker' in top) == open_:
            return
        time.sleep(.5)
    raise RuntimeError('System photo picker did not ' + ('open' if open_ else 'close'))


def pick_slot(name):
    """Tap one direction slot in the app, then choose that direction's photo in the system picker.
    Each slot opens a single-photo picker, so the photo lands in exactly that slot."""
    tap(f'눌러서 {SLOT_LABEL[name]} 사진 선택', label=f'slot {SLOT_LABEL[name]}: open photo picker')
    wait_picker(True)
    hold(.5)
    stamp = photo_times[name]
    tiles = [n for n in nodes() if '촬영' in (n.get('content-desc') or '') and n.get('clickable') == 'true']
    match = [n for n in tiles if stamp in n.get('content-desc')]
    assert len(match) == 1, f'Picker tile for {name} ({stamp}) not found: {[n.get("content-desc") for n in tiles]}'
    x, y = center(match[0])
    adb('shell', 'input', 'tap', str(x), str(y))
    steps.append(f'photo picker: choose {name}')
    print('PICK:', name, stamp, flush=True)
    wait_picker(False)
    time.sleep(1.5)  # thumbnail decodes off the main thread


# --- prepare device -------------------------------------------------------------------------
photos = example_photos()
for _ in range(120):
    if adb('shell', 'getprop', 'sys.boot_completed', check=False) == '1':
        break
    time.sleep(2)
else:
    raise SystemExit('Emulator did not finish booting.')
adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP', check=False)
install = subprocess.run([args.adb, '-s', args.serial, 'install', '-r', str(APK)], capture_output=True, timeout=300)
if install.returncode:
    msg = install.stderr.decode('utf-8', 'replace') + install.stdout.decode('utf-8', 'replace')
    if 'UPDATE_INCOMPATIBLE' in msg:
        raise SystemExit('Installed CurbLink has a different signature. Not uninstalling (would delete app data).')
    raise SystemExit('Install failed: ' + msg.strip())
for perm in ['android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION',
             'android.car.permission.CAR_SPEED']:
    adb('shell', 'pm', 'grant', PKG, perm, check=False)
adb('emu', 'geo', 'fix', TEST_LON, TEST_LAT, check=False)
adb('shell', 'am', 'force-stop', PKG)
adb('shell', 'rm', '-f', REMOTE, check=False)
current_user = adb('shell', 'am', 'get-current-user')
stage_photos(current_user, photos)
for old in SHOTS.glob('*.png'):
    old.unlink()

# --- reset the demo trip through the app's own controls (no data clearing), before recording ---
adb('shell', 'am', 'start', '-n', f'{PKG}/.MainActivity')
time.sleep(5)
tab('02  도착 맞춤 식사')
if find('견본 주문 취소') is not None:
    tap('견본 주문 취소', scrolls=0, label='pre-roll: cancel previous sample order')
tab('01  도착 안내')
tap('문화센터', label='pre-roll: switch destination')
tap('집 · 리버빌라 101', label='pre-roll: start a new trip to home')
tab('03  편안한 하차')
for label, on in [('휠체어 옆문 공간', True), ('문을 넓게 열 공간 · 2m 이상', False),
                  ('더 완만한 경사 · 2% 이하', False), ('현장 직원 지원', True)]:
    set_switch(label, on, record=False)
tab('04  현장 인계')
if find('첨부 사진 모두 제거') is not None:
    tap('첨부 사진 모두 제거', label='pre-roll: remove previous photos')
tab('01  도착 안내')
steps.clear()
adb('shell', 'am', 'force-stop', PKG)

# --- record ---------------------------------------------------------------------------------
recorder = subprocess.Popen([args.adb, '-s', args.serial, 'shell', 'screenrecord', '--bit-rate', '20000000',
                             '--time-limit', '180', REMOTE])
time.sleep(1.5)
try:
    adb('shell', 'am', 'start', '-n', f'{PKG}/.MainActivity')
    time.sleep(5)  # wait for CarService/NaviHelper connection badge
    ensure_app()
    assert find('P · 차량 연결') is not None, 'Vehicle connection badge (P) not shown'
    shot('01-arrival-connected')
    hold()

    tab('02  도착 맞춤 식사')
    hold(.6)
    adb('emu', 'geo', 'fix', TEST_LON, TEST_LAT, check=False)
    tap_any('현재 위치 확인', '현재 위치 다시 확인')
    hold(.6)
    menu = next((n.get('text') for n in nodes() if n.get('text') in
                 {'닭구이 덮밥', '연어 포케 샐러드', '더블 트러플 수제버거 세트', '시그니처 바닐라 라떼 & 샌드위치', '생모짜렐라 토마토 파스타'}), None)
    assert menu, 'No recommended menu visible (location or vehicle not ready)'
    shot('02-dining-recommended')
    tap(menu, label='menu: ' + menu)
    hold(.8)
    shot('03-dining-review')
    tap('확인 · 견본 주문 예약')
    hold()
    shot('04-dining-order-tracking')

    tab('03  편안한 하차')
    hold(.8)
    shot('05-access-comparison')
    tap('A2 · 이 지점 이용 불가')
    hold()
    shot('06-access-a2-unavailable-alternative')
    tap('이 지점으로 안내', label='choose A-3')
    hold(.6)
    tap('정차 후 도착 확인')
    hold(.6)
    for label in ['문 개방 공간 확인', '램프·리프트 준비 확인', '직원 지원 확인']:
        set_switch(label, True)
    # A vehicle-connection refresh intentionally clears the space check; confirm it again if so.
    set_switch('문 개방 공간 확인', True, record=False)
    tap('주변 확인 · 하차 안내 승인')
    hold(.6)
    tap('입구 도착 확인')
    hold()
    shot('07-access-entrance-confirmed')

    tab('04  현장 인계')
    hold(.6)
    for name in PHOTO_ORDER:
        pick_slot(name)
        hold(.4)
    assert find('사진을 불러오지 못했습니다 · 다시 선택해 주세요') is None, 'An attached photo failed to load'
    assert find('주변 사진 선택 · 최대 4장 (4/4)') is not None, 'Four photos were not attached'
    hold()
    shot('08-handoff-photos-attached')
    tap('현재 GPS 다시 확인')
    tap('이 위치와 사진을 확인했어요')
    hold()
    shot('09-handoff-location-card')
    hold()
finally:
    adb('shell', 'pkill', '-INT', 'screenrecord', check=False)
    try:
        recorder.wait(timeout=20)
    except subprocess.TimeoutExpired:
        recorder.kill()
    time.sleep(2)

VIDEO.mkdir(parents=True, exist_ok=True)
mp4 = VIDEO / 'curblink-apk-demo.mp4'
original = ROOT / 'work/submission-video/curblink-apk-demo-original.mp4'
original.parent.mkdir(parents=True, exist_ok=True)
adb('pull', REMOTE, str(original), timeout=180)
ffmpeg = shutil.which('ffmpeg')
if not ffmpeg:
    raise SystemExit('ffmpeg not found; original recording kept at ' + str(original))
# Re-encode at the device resolution with near-lossless quality: no scaling, cuts or overlays.
subprocess.run([ffmpeg, '-y', '-loglevel', 'error', '-i', str(original), '-c:v', 'libx264', '-preset', 'slow',
                '-crf', '16', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(mp4)], check=True)
adb('shell', 'rm', '-f', REMOTE, '/sdcard/curblink-ui.xml', check=False)
duration = None
ffprobe = shutil.which('ffprobe')
if ffprobe:
    out = subprocess.run([ffprobe, '-v', 'error', '-show_entries', 'format=duration:stream=width,height',
                          '-of', 'json', str(mp4)], capture_output=True, text=True).stdout
    info = json.loads(out or '{}')
    duration = round(float(info.get('format', {}).get('duration', 0)), 1)
    size = next(({'width': s['width'], 'height': s['height']} for s in info.get('streams', []) if 'width' in s), None)
data = mp4.read_bytes()
record = {
    'source': 'release APK on Pleos Connect emulator; real CarService/NaviHelper readings, vehicle parked (P)',
    'notInjected': 'no TEST vehicle/lane observations; driving lane guidance not exercised in this video',
    'sampleData': 'stores, menus, facility bays and order are sample data; nothing is sent or paid',
    'device': {'model': adb('shell', 'getprop', 'ro.product.model'), 'android': adb('shell', 'getprop', 'ro.build.version.release'),
               'display': adb('shell', 'wm', 'size').splitlines()[-1], 'density': adb('shell', 'wm', 'density').splitlines()[-1]},
    'testLocation': {'lon': TEST_LON, 'lat': TEST_LAT, 'note': 'emulator geo fix test coordinate'},
    'apk': {'file': 'apk/CurbLink_CurbLink_release.apk', 'sha256': hashlib.sha256(APK.read_bytes()).hexdigest()},
    'examplePhotos': {'note': 'example images supplied by the team in submission/example-photos (see its README); '
                              'attached through the app and the system photo picker, not vehicle camera images',
                      'files': [{'slot': s, 'file': f'example-photos/{p.name}', 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
                                for s, p in zip(['전방', '후방', '좌측', '우측'], photos)]},
    'video': {'file': 'video/curblink-apk-demo.mp4',
              'capture': 'adb shell screenrecord at device resolution (20 Mbps), re-encoded with ffmpeg H.264 crf 16 at the same resolution; no cuts or overlays',
              'durationSeconds': duration, 'size': size if ffprobe else None,
              'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
              'originalSha256': hashlib.sha256(original.read_bytes()).hexdigest(),
              'originalKeptLocally': 'work/submission-video (not committed)'},
    'steps': steps, 'captures': captures, 'result': 'PASS'}
(VIDEO / 'apk-verification.json').write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'PASS: {len(steps)} steps, {len(captures)} screenshots, video {duration}s', flush=True)
