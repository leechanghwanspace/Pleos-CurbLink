"""Exercise six road/cabin journeys and capture original browser pixels."""
import functools
import hashlib
import http.server
import json
import re
import struct
import threading
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'captures/web/0.5.2'
OUT.mkdir(parents=True, exist_ok=True)

class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass

server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(QuietHandler, directory=str(ROOT)))
threading.Thread(target=server.serve_forever, daemon=True).start()
errors, flows, captures = [], [], []
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, args=['--disable-gpu'])
        context = browser.new_context(viewport={'width': 1920, 'height': 1200}, device_scale_factor=1,
                                      reduced_motion='reduce', record_video_dir=str(ROOT/'work/simulation-052-video'))
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(f'http://127.0.0.1:{server.server_port}/simulator/', wait_until='networkidle', timeout=60000)
        panel = page.locator('.destination-panel')
        presets = panel.locator('.journey-presets button')
        presets.first.wait_for(timeout=60000)
        page.evaluate('document.fonts.ready')

        def start(index):
            presets.nth(index).click()
            panel.get_by_label('재생 속도', exact=True).select_option('10')

        def capture(name, selector):
            target = panel.locator(selector)
            target.screenshot(path=str(OUT/f'{name}.png'), timeout=30000)
            data = (OUT/f'{name}.png').read_bytes()
            width, height = struct.unpack('>II', data[16:24])
            assert width > 100 and height > 100
            captures.append({'file': f'{name}.png', 'width': width, 'height': height,
                             'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)})

        def completed(name):
            panel.locator('.journey-actions > span').filter(has_text=re.compile('완료')).wait_for(timeout=90000)
            flows.append(name)
            print('PASS:', name, flush=True)

        start(0)
        capture('01-driver-pickup', '.driver-scene')
        completed('PICKUP: alley, boarding, return and merge')
        start(1)
        panel.get_by_label('재생 속도', exact=True).select_option('3')
        panel.locator('.journey-actions > span').filter(has_text=re.compile(' · R · ')).wait_for(timeout=90000)
        # Pause without auto-scrolling past a short-lived reverse stage.
        panel.locator('.journey-actions > button').evaluate('el => el.click()')
        assert ' · R · ' in panel.locator('.journey-actions > span').inner_text()
        capture('02-parking-reverse', '.driver-scene')
        assert ' · R · ' in panel.locator('.journey-actions > span').inner_text()
        panel.get_by_label('재생 속도', exact=True).select_option('10')
        panel.locator('.journey-actions > button').click()
        completed('PARKING: gate, aisle, reverse and parked')
        start(2)
        panel.locator('li[aria-current="step"]').filter(has_text='비상등 점멸').wait_for(timeout=90000)
        panel.locator('.journey-actions > button').evaluate('el => el.click()')
        capture('03-short-stop', '.driver-scene')
        panel.locator('.journey-actions > button').click()
        completed('SHORT_STOP: pull over, hazards and rejoin')
        start(3)
        cabin = panel.locator('.meal-cockpit')
        cabin.get_by_role('button', name='현위치 GPS 탐색', exact=True).click()
        cabin.get_by_role('button', name='집으로 출발 · 식사 추천', exact=True).click()
        # Destination selection starts playback; pause while reviewing a menu.
        panel.locator('.journey-actions > button').click()
        capture('04-meal-menu', '.meal-layout')
        assert cabin.locator('.meal-menu-card').first.is_disabled(), 'Solo driver touch order was enabled'
        cabin.locator('.meal-voice-input').click()
        cabin.locator('.meal-voice-input').click()
        capture('05-meal-confirmed', '.meal-layout')
        panel.get_by_label('재생 속도', exact=True).select_option('10')
        panel.locator('.journey-actions > button').click()
        receive = panel.get_by_role('button', name='음식 확인 · 수령 완료', exact=True)
        receive.wait_for(timeout=90000)
        receive.click()
        completed('MEAL: GPS, menu, explicit simulated voice approval and receipt')
        start(4)
        share = panel.get_by_role('button', name='이 위치를 모의 배달원에게 전달', exact=True)
        share.wait_for(timeout=90000)
        capture('06-river-surround-handoff', '.location-console')
        share.click()
        receive.wait_for(timeout=90000)
        receive.click()
        completed('RIVER: four simulated views, location approval and receipt')
        start(5)
        capture('07-access-comparison', '.meal-layout')
        panel.locator('.access-go').click()
        checks = panel.locator('.access-checks')
        checks.wait_for(timeout=90000)
        approve = checks.get_by_role('button', name='주변 확인 · 하차 안내 시작', exact=True)
        assert approve.is_disabled(), 'Unconfirmed access was approved'
        capture('08-access-safety', '.meal-layout')
        for label in ['옆문과 보행로 여유 공간 확인', '차량 램프·리프트 준비 확인 (모의)', '요청한 직원 지원 수락 확인']:
            checks.get_by_label(label, exact=True).check()
        approve.click()
        completed('ACCESS: suitability, stopped checks, explicit approval and entrance arrival')
        capture('09-access-complete', '.meal-layout')
        assert not errors, errors
        context.close()
        browser.close()
    result = {'version': '0.5.2-arrival-preview', 'source': 'road/cabin/surround web simulation, NOT APK',
              'rendering': 'Chromium headless, software rendering (--disable-gpu); no hardware GPU certification',
              'viewport': '1920x1200', 'deviceScaleFactor': 1, 'imageProcessing': 'none; original element PNGs',
              'result': 'PASS', 'flows': flows, 'pageErrors': errors, 'captures': captures}
    (OUT/'verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS: six journeys, nine captures, no browser errors', flush=True)
finally:
    server.shutdown()
    server.server_close()
