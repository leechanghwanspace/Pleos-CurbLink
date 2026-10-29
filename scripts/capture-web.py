"""Capture the web reference demo as lossless PNG, without resizing.

Requires: pip install playwright; playwright install chromium
Run a project-root HTTP server on port 8000 before running this script.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'captures' / 'web'
OUT.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1920, 'height': 1200}, device_scale_factor=1)
    page.emulate_media(reduced_motion='reduce')
    page.goto('http://127.0.0.1:8000/simulator/')
    presets = page.locator('.journey-presets button')
    presets.first.wait_for()
    page.evaluate('document.fonts.ready')

    def capture(name, selector='.journey-demo'):
        page.wait_for_timeout(300)
        # Element screenshots retain the native browser pixels; no JPEG conversion.
        page.locator(selector).screenshot(path=str(OUT / (name + '.png')))

    presets.nth(0).click()
    page.wait_for_timeout(1200)
    page.locator('.journey-actions button').click()
    capture('driver-view', '.driver-scene')
    presets.nth(3).click()
    capture('meal-cabin-gps', '.meal-layout')
    page.get_by_role('button', name='현위치 GPS 탐색', exact=True).click()
    page.get_by_role('button', name='집으로 출발 · 식사 추천', exact=True).wait_for()
    page.get_by_role('button', name='집으로 출발 · 식사 추천', exact=True).click()
    page.locator('.journey-actions button').click()
    capture('meal-cabin-menu', '.meal-layout')
    presets.nth(5).click()
    capture('access-comparison', '.meal-layout')
    page.locator('.access-go').click()
    page.get_by_label('재생 속도', exact=True).select_option('10')
    page.locator('.access-checks').wait_for(timeout=90000)
    capture('access-arrival', '.meal-layout')
    presets.nth(4).click()
    page.get_by_label('재생 속도', exact=True).select_option('10')
    page.locator('.location-console').wait_for(timeout=90000)
    capture('river-surround-handoff', '.location-console')
    browser.close()
print(OUT)
