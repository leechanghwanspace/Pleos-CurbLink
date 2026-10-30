import json, os
from pathlib import Path
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1];out=root/'release/studio';errors=[];results=[]
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True,args=['--disable-gpu'])
    context=browser.new_context(viewport={'width':1682,'height':1210},device_scale_factor=1,record_video_dir=str(root/'work/studio-recordings'),record_video_size={'width':1682,'height':1210},reduced_motion='reduce')
    page=context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto((out/'index.html').as_uri());page.wait_for_timeout(350)
    for i,name in enumerate(['01-arrival','02-dining','03-access','04-handoff']):
        page.locator('nav [data-page]').nth(i).click();page.wait_for_timeout(1500)
        page.screenshot(path=str(out/(name+'.png')),full_page=False)
    page.locator('nav [data-page]').nth(0).click()
    page.locator('[data-scenario="1"]').click();page.wait_for_timeout(1100);assert page.locator('.lane-info').inner_text().find('2·3차선 추천')>=0
    page.locator('[data-scenario="2"]').click();page.wait_for_timeout(1100);assert '1.2km' in page.locator('.lane-info').inner_text()
    page.locator('[data-scenario="3"]').click();page.wait_for_timeout(1100);assert '재탐색' in page.locator('.lane-info').inner_text()
    page.locator('[data-scenario="0"]').click()
    page.locator('nav [data-page]').nth(1).click();page.locator('#gps').click();page.locator('[data-menu="0"]').click();page.locator('#confirm').click();page.wait_for_timeout(1800);assert '도착을 함께' in page.locator('#workspace').inner_text()
    page.locator('#cancel').click()
    page.locator('nav [data-page]').nth(2).click();page.locator('#wide').check();page.locator('[data-bay="A3"]').click();page.locator('#arrived').click()
    for i in range(3):page.locator('#check'+str(i)).check()
    page.locator('#approve').click();page.wait_for_timeout(1400);assert '사용자 승인 완료' in page.locator('#workspace').inner_text()
    page.locator('#obstacle').click();page.wait_for_timeout(1400);assert '조건 변경' in page.locator('#workspace').inner_text();assert page.locator('#approve').is_disabled()
    page.locator('nav [data-page]').nth(3).click();page.locator('#photos').click();page.locator('#confirmLocation').click();page.locator('#shareLocation').click();page.wait_for_timeout(1800);assert '외부 전송은 실행하지' in page.locator('#shareResult').inner_text()
    video=page.video;context.close();video.save_as(str(out/'walkthrough.webm'))
    for width,height in [(1682,1210),(1920,1080),(1280,720),(840,1212),(480,900)]:
        ctx=browser.new_context(viewport={'width':width,'height':height});pg=ctx.new_page();pg.goto((out/'index.html').as_uri())
        for i in range(4):
            pg.locator('nav [data-page]').nth(i).click()
            assert pg.evaluate('document.documentElement.scrollWidth<=innerWidth'),f'overflow {width} page {i}'
        results.append({'viewport':f'{width}x{height}','screens':4,'horizontalOverflow':False});ctx.close()
    browser.close()
assert not errors,errors
(out/'verification.json').write_text(json.dumps({'source':'browser design mockup; NOT APK or Pleos execution','errors':errors,'layout':results,'flows':['signals','early exit','late exit reroute','explicit order','wide bay','approval invalidation','handoff preview']},ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(results))
