"""Make the requested single-file mockup, including verified APK and web media."""
import base64
import hashlib
from pathlib import Path
root=Path(__file__).resolve().parents[1]
release=root/'release'; studio=release/'studio'
apk=release/'CurbLink-Drive-0.5-arrival-studio.apk'
assert apk.is_file(), 'Build and verify APK first'
html=(studio/'index.html').read_text(encoding='utf-8-sig')
for source in ['../../simulator/js/lane-engine.js','studio.js']:
    path=(studio/source).resolve()
    html=html.replace(f'<script src="{source}"></script>','<script>'+path.read_text(encoding='utf-8-sig')+'</script>')
for name in ['01-arrival.png','02-dining.png','03-access.png','04-handoff.png','walkthrough.webm']:
    mime='video/webm' if name.endswith('.webm') else 'image/png'
    url=f'data:{mime};base64,'+base64.b64encode((studio/name).read_bytes()).decode()
    html=html.replace(f'"{name}"',f'"{url}"')
old='../../captures/android/vehicle-app-preview/01-pleos-connected.png'
html=html.replace(old,'data:image/png;base64,'+base64.b64encode((root/'captures/android/vehicle-app-preview/01-pleos-connected.png').read_bytes()).decode())
html=html.replace('<a href="../../simulator/index.html">기존 주행 참고 시뮬레이터</a>','<span>기존 주행 시뮬레이터는 저장소 simulator/ 참조</span>')
html=html.replace('../CurbLink-Drive-0.5-arrival-studio.apk','#apk')
payload=base64.b64encode(apk.read_bytes()).decode()
html+=f'''<script>
const apkBytes=Uint8Array.from(atob('{payload}'),c=>c.charCodeAt(0));
const apkDownload=URL.createObjectURL(new Blob([apkBytes],{{type:'application/vnd.android.package-archive'}}));
document.querySelectorAll('a[href="#apk"]').forEach(a=>{{a.href=apkDownload;a.download='{apk.name}';}});
</script>'''
output=release/'CurbLink-Drive-0.5-mockup.html'
output.write_text(html,encoding='utf-8')
for path in [apk,output]:
    path.with_suffix(path.suffix+'.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n',encoding='ascii')
print(f'{output}: {output.stat().st_size:,} bytes; embedded APK + 4 screenshots + web video')
