"""Focused browser regression for the road simulator, not native/GPU evidence."""
import functools
import http.server
import json
import threading
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
out = root / "work/ux-browser"
out.mkdir(parents=True, exist_ok=True)

class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass

server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(QuietHandler, directory=str(root)))
threading.Thread(target=server.serve_forever, daemon=True).start()
errors = []
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, args=["--disable-gpu"])
        context = browser.new_context(viewport={"width": 1682, "height": 1210}, reduced_motion="reduce",
                                      record_video_dir=str(out / "video"))
        page = context.new_page()
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto(f"http://127.0.0.1:{server.server_port}/simulator/", wait_until="networkidle", timeout=60000)
        panel = page.locator(".destination-panel")
        panel.locator(".journey-presets button").nth(5).click(timeout=60000)
        cabin = panel.locator(".access-cockpit")
        cabin.get_by_role("button", name="A2 · 이 지점 이용 불가", exact=True).click()
        assert "A3 추천" in cabin.locator(".access-go").inner_text()
        before = cabin.bounding_box()["height"]
        page.wait_for_timeout(500)
        assert abs(cabin.bounding_box()["height"] - before) < 1, "Road canvas/cabin layout keeps growing"
        cabin.evaluate("el => el.scrollIntoView({block:'start'})")
        page.screenshot(path=str(out / "01-alternative.png"))
        cabin.get_by_role("button", name="A3 · 이 지점 이용 불가", exact=True).click()
        assert cabin.locator(".access-go").is_disabled()
        assert "현장 도움" in cabin.inner_text()
        page.screenshot(path=str(out / "02-no-match.png"))
        # New trip clears exclusions. A blocked selected bay must require fresh approval.
        panel.locator(".journey-presets button").nth(5).click()
        panel.get_by_label("재생 속도").select_option("10")
        cabin.locator(".access-go").click()
        blocked = cabin.get_by_role("button", name="이 지점 이용 불가 · 대안 비교", exact=True)
        blocked.wait_for(state="visible", timeout=60000)
        blocked.click()
        assert "A3 추천" in cabin.locator(".access-go").inner_text()
        cabin.locator(".access-go").click()
        blocked.wait_for(state="visible", timeout=60000)
        assert "A-3" in cabin.locator(".access-selected").inner_text()
        assert not cabin.locator(".access-checks input").first.is_checked()
        assert cabin.get_by_role("button", name="주변 확인 · 하차 안내 시작", exact=True).is_disabled()
        cabin.evaluate("el => el.scrollIntoView({block:'start'})")
        page.screenshot(path=str(out / "03-new-approval-required.png"))
        for width, height in [(840, 1212), (480, 900)]:
            page.set_viewport_size({"width": width, "height": height})
            blocked.scroll_into_view_if_needed()
            assert blocked.is_visible()
            page.screenshot(path=str(out / f"04-recovery-{width}.png"))
        context.close()
        browser.close()
    assert not errors, errors
    result = {"source": "Chromium browser road simulator; software rendering, NOT APK or hardware GPU verification",
              "result": "PASS", "flows": ["A2 unavailable -> A3", "no suitable candidate", "new trip resets exclusions",
              "selected bay blocked -> continuous reroute -> fresh approval", "840/480 recovery control visible"], "pageErrors": errors}
    (out / "verification.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))
finally:
    server.shutdown()
    server.server_close()
