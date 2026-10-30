"""Check the single HTML offline and compare its embedded APK to the built file."""
import hashlib
from pathlib import Path
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1];release=root/'release'
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True,args=['--disable-gpu'])
    context=browser.new_context(viewport={'width':1682,'height':1210},accept_downloads=True)
    page=context.new_page();errors=[];external=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    def gate(route):
        if route.request.url.startswith(('http:','https:')):
            external.append(route.request.url);route.abort()
        else:route.continue_()
    page.route('**/*',gate)
    page.goto((release/'CurbLink-Drive-0.5.1-mockup.html').as_uri())
    assert page.locator('nav button').count()==4
    page.locator('details summary').click()
    assert page.locator('.gallery img').evaluate_all('(images)=>images.every(i=>i.complete&&i.naturalWidth===1682)')
    page.locator('video').evaluate('(v)=>v.load()')
    page.wait_for_function('document.querySelector("video").readyState>=1')
    duration=page.locator('video').evaluate('(v)=>v.duration')
    assert duration>12,duration
    with page.expect_download() as event:page.locator('footer a').click()
    download=event.value;target=root/'work/embedded-apk-check.apk';download.save_as(str(target))
    expected=hashlib.sha256((release/'CurbLink-Drive-0.5.1-arrival-studio.apk').read_bytes()).hexdigest()
    assert hashlib.sha256(target.read_bytes()).hexdigest()==expected
    assert not errors,errors
    assert not external,external
    print(f'PASS standalone offline; 4 images; video {duration:.1f}s; APK SHA256 matches {expected}')
    context.close();browser.close()
