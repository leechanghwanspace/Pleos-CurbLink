import urllib.request, concurrent.futures, pathlib, zipfile
root=pathlib.Path('work/toolchain')
items=[('jdk','https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse'),('gradle','https://services.gradle.org/distributions/gradle-8.9-bin.zip'),('android','https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip')]
def get(item):
 name,url=item
 p=root/(name+'.zip')
 if not p.exists(): urllib.request.urlretrieve(url,p)
 with zipfile.ZipFile(p) as z: z.extractall(root/name)
 return name+' ready'
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as ex:
 for f in concurrent.futures.as_completed([ex.submit(get,i) for i in items]):
  try: print(f.result(),flush=True)
  except Exception as e: print(type(e).__name__,str(e),flush=True)
