from pathlib import Path
import subprocess, sys, time, xml.etree.ElementTree as ET, re

ADB=str(Path('work/toolchain/sdk/platform-tools/adb.exe').resolve())
def adb(*args):
    r=subprocess.run([ADB,*args],capture_output=True)
    if r.returncode: raise RuntimeError(r.stderr.decode('utf-8',errors='replace'))
    return r.stdout
def dump():
    adb('shell','uiautomator','dump','/sdcard/curblink-window.xml')
    data=adb('exec-out','cat','/sdcard/curblink-window.xml')
    Path('work/android-window.xml').write_bytes(data)
    return ET.fromstring(data)
def find(text):
    root=dump()
    return [n for n in root.iter('node') if text in n.attrib.get('text','') or text in n.attrib.get('content-desc','')]
def tap(text):
    nodes=find(text)
    if not nodes: raise RuntimeError('Not visible: '+text)
    n=nodes[0]
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
    if x2<=x1 or y2<=y1: raise RuntimeError('Empty bounds: '+text)
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(.4)
def screenshot(name):
    p=Path('outputs/CurbLink-UI-Captures')/name
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_bytes(adb('exec-out','screencap','-p'))
    print(str(p.resolve()))
if __name__=='__main__':
    action=sys.argv[1]
    if action=='dump':
        for n in dump().iter('node'):
            t=n.attrib.get('text','') or n.attrib.get('content-desc','')
            if t: print(t,n.attrib.get('bounds'),n.attrib.get('enabled'))
    elif action=='tap': tap(sys.argv[2])
    elif action=='shot': screenshot(sys.argv[2])
    elif action=='up': adb('shell','input','swipe','800','260','800','880','250')
    elif action=='down': adb('shell','input','swipe','800','880','800','280','250')
