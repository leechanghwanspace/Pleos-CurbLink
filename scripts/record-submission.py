"""Record a watchable demo video and screenshots of the six web simulation journeys for contest submission.

Output (does not touch captures/web/0.5.2):
  submission/video/curblink-simulation-demo.mp4   H.264, 1920x1200 (needs ffmpeg on PATH)
  submission/screenshots/web/*.png                 original element PNGs, no image processing
  submission/video/verification.json               journeys, captures, hashes, page errors

Usage: python scripts/record-submission.py [--rate 5] [--hold 2.5] [--channel chrome]
This is the browser road/cabin/surround simulation, not the native APK.
"""
import argparse
import functools
import hashlib
import http.server
import json
import re
import shutil
import struct
import subprocess
import threading
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'submission'
SHOTS = OUT / 'screenshots/web'
VIDEO = OUT / 'video'
RAW = ROOT / 'work/submission-video'

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument('--rate', default='5', choices=['1', '3', '5', '10'], help='playback rate while driving')
parser.add_argument('--hold', type=float, default=2.5, help='seconds to hold on each key scene')
parser.add_argument('--channel', default=None, help='use an installed browser (chrome, msedge) instead of Playwright Chromium')
args = parser.parse_args()
HOLD_MS = int(args.hold * 1000)

for folder in (SHOTS, VIDEO):
    folder.mkdir(parents=True, exist_ok=True)
if RAW.exists():
    shutil.rmtree(RAW)


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass


server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(QuietHandler, directory=str(ROOT)))
threading.Thread(target=server.serve_forever, daemon=True).start()
errors, flows, captures = [], [], []
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, channel=args.channel, args=['--disable-gpu'])
        context = browser.new_context(viewport={'width': 1920, 'height': 1200}, device_scale_factor=1,
                                      record_video_dir=str(RAW), record_video_size={'width': 1920, 'height': 1200})
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(f'http://127.0.0.1:{server.server_port}/simulator/', wait_until='networkidle', timeout=60000)
        panel = page.locator('.destination-panel')
        presets = panel.locator('.journey-presets button')
        presets.first.wait_for(timeout=60000)
        page.evaluate('document.fonts.ready')
        status = panel.locator('.journey-actions > span')
        toggle = panel.locator('.journey-actions > button')
        rate = panel.get_by_label('재생 속도', exact=True)
        hold = lambda: page.wait_for_timeout(HOLD_MS)

        def start(index):
            presets.nth(index).scroll_into_view_if_needed()
            presets.nth(index).click()
            rate.select_option(args.rate)

        def capture(name, selector):
            target = panel.locator(selector)
            target.scroll_into_view_if_needed()
            path = SHOTS / f'{name}.png'
            target.screenshot(path=str(path), timeout=30000)
            data = path.read_bytes()
            width, height = struct.unpack('>II', data[16:24])
            assert width > 100 and height > 100, name
            captures.append({'file': f'screenshots/web/{name}.png', 'width': width, 'height': height,
                             'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)})
            hold()

        def completed(name):
            status.filter(has_text=re.compile('완료')).wait_for(timeout=180000)
            hold()
            flows.append(name)
            print('PASS:', name, flush=True)

        hold()
        start(0)
        capture('01-pickup-driving', '.driver-scene')
        completed('PICKUP: alley, boarding, return and merge')

        start(1)
        rate.select_option('3')
        status.filter(has_text=re.compile(' · R · ')).wait_for(timeout=180000)
        toggle.evaluate('el => el.click()')
        assert ' · R · ' in status.inner_text()
        capture('02-parking-reverse', '.driver-scene')
        rate.select_option(args.rate)
        toggle.click()
        completed('PARKING: gate, aisle, reverse and parked')

        start(2)
        panel.locator('li[aria-current="step"]').filter(has_text='비상등 점멸').wait_for(timeout=180000)
        toggle.evaluate('el => el.click()')
        capture('03-short-stop-hazards', '.driver-scene')
        toggle.click()
        completed('SHORT_STOP: pull over, hazards and rejoin')

        start(3)
        cabin = panel.locator('.meal-cockpit')
        cabin.get_by_role('button', name='현위치 GPS 탐색', exact=True).click()
        hold()
        cabin.get_by_role('button', name='집으로 출발 · 식사 추천', exact=True).click()
        toggle.click()
        capture('04-meal-menu-touch-blocked', '.meal-layout')
        assert cabin.locator('.meal-menu-card').first.is_disabled(), 'Solo driver touch order was enabled'
        cabin.locator('.meal-voice-input').click()
        hold()
        cabin.locator('.meal-voice-input').click()
        capture('05-meal-voice-confirmed', '.meal-layout')
        rate.select_option(args.rate)
        toggle.click()
        receive = panel.get_by_role('button', name='음식 확인 · 수령 완료', exact=True)
        receive.wait_for(timeout=180000)
        hold()
        receive.click()
        completed('MEAL: GPS, menu, explicit simulated voice approval and receipt')

        start(4)
        share = panel.get_by_role('button', name='이 위치를 모의 배달원에게 전달', exact=True)
        share.wait_for(timeout=180000)
        capture('06-river-surround-handoff', '.location-console')
        share.click()
        receive.wait_for(timeout=180000)
        hold()
        receive.click()
        completed('RIVER: four simulated views, location approval and receipt')

        start(5)
        capture('07-access-bay-comparison', '.meal-layout')
        panel.locator('.access-go').click()
        checks = panel.locator('.access-checks')
        checks.wait_for(timeout=180000)
        approve = checks.get_by_role('button', name='주변 확인 · 하차 안내 시작', exact=True)
        assert approve.is_disabled(), 'Unconfirmed access was approved'
        capture('08-access-safety-checks', '.meal-layout')
        for label in ['옆문과 보행로 여유 공간 확인', '차량 램프·리프트 준비 확인 (모의)', '요청한 직원 지원 수락 확인']:
            checks.get_by_label(label, exact=True).check()
            page.wait_for_timeout(600)
        approve.click()
        completed('ACCESS: suitability, stopped checks, explicit approval and entrance arrival')
        capture('09-access-entrance-arrived', '.meal-layout')
        assert not errors, errors
        video_path = Path(page.video.path())
        context.close()
        browser.close()

    mp4 = VIDEO / 'curblink-simulation-demo.mp4'
    ffmpeg = shutil.which('ffmpeg')
    if not ffmpeg:
        raise SystemExit('ffmpeg not found; raw video kept at ' + str(video_path))
    subprocess.run([ffmpeg, '-y', '-loglevel', 'error', '-i', str(video_path), '-c:v', 'libx264', '-preset', 'medium',
                    '-crf', '23', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(mp4)], check=True)
    probe = subprocess.run([shutil.which('ffprobe') or 'ffprobe', '-v', 'error', '-show_entries', 'format=duration',
                            '-of', 'default=nw=1:nk=1', str(mp4)], capture_output=True, text=True)
    data = mp4.read_bytes()
    result = {'source': 'road/cabin/surround web simulation, NOT the native APK',
              'rendering': f'{args.channel or "Playwright Chromium"} headless, software rendering (--disable-gpu)',
              'viewport': '1920x1200', 'playbackRate': args.rate, 'holdSeconds': args.hold,
              'video': {'file': 'video/curblink-simulation-demo.mp4', 'codec': 'H.264',
                        'durationSeconds': round(float(probe.stdout.strip() or 0), 1),
                        'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()},
              'result': 'PASS', 'flows': flows, 'pageErrors': errors, 'captures': captures}
    (VIDEO / 'verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'PASS: six journeys, {len(captures)} screenshots, video {result["video"]["durationSeconds"]}s', flush=True)
finally:
    server.shutdown()
    server.server_close()
