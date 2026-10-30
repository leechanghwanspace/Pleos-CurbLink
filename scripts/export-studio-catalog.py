"""Export native fixture data for the browser; --check catches stale generated data."""
import argparse,json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/io/curblink/demo/MobilityModels.kt').read_text(encoding='utf-8-sig')
places=[]
for ident,name,subtitle,eta,meters in re.findall(r'DemoPlace\("(HOME|CULTURE|RIVER)",\s*"([^"]+)",\s*"([^"]+)",\s*(\d+),\s*(\d+)\)',source):
    places.append(dict(id=ident,name=name,subtitle=subtitle,etaSeconds=int(eta),meters=int(meters)))
menus=[]
pattern=r'DiningMenu\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*(\d+),\s*(\d+),\s*(\d+),\s*"([^"]+)"'
for ident,name,detail,price,prep,delivery,store in re.findall(pattern,source):
    menus.append(dict(id=ident,name=name,detail=detail,price=int(price),prepSeconds=int(prep),deliverySeconds=int(delivery),store=store))
assert len(places)==3 and len(menus)==5,'Native catalog schema changed; update exporter explicitly'
assert len({m['id'] for m in menus})==len(menus)
assert all(p['etaSeconds']>0 for p in places)
output='// Generated from MobilityModels.kt by scripts/export-studio-catalog.py. Times are seconds.\nconst CurbCatalog = '+json.dumps(dict(places=places,menus=menus),ensure_ascii=False,indent=2)+';\n'
target=root/'release/studio/catalog.js'
parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
if args.check:
    assert target.is_file() and target.read_text(encoding='utf-8-sig')==output,'Browser catalog is stale; run scripts/export-studio-catalog.py'
    print('PASS native/browser catalog parity: 3 destinations, 5 menus, seconds normalized')
else:
    target.write_text(output,encoding='utf-8');print(target)
