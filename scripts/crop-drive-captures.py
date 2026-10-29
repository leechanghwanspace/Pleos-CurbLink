"""Create README app-only crops; retain untouched ADB captures separately."""
from pathlib import Path
import hashlib
import json
from PIL import Image

root = Path(__file__).resolve().parents[1]
source = root / 'captures/android'
output = source / 'display'
output.mkdir(exist_ok=True)
# Verified app window in this capture set; right/bottom are exclusive.
box = (108, 36, 2560, 1308)
for path in sorted(source.glob('*.png')):
    with Image.open(path) as image:
        if image.size != (2560, 1380):
            raise ValueError(f'Unexpected source dimensions: {path}')
        image.crop(box).save(output / path.name)
    record = {
        'source': '../' + path.name,
        'source_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
        'crop_box': box,
        'pixels': [2452, 1272],
        'processing': 'Crop only; no resizing, sharpening or compositing',
        'png_sha256': hashlib.sha256((output / path.name).read_bytes()).hexdigest(),
    }
    (output / path.with_suffix('.json').name).write_text(
        json.dumps(record, indent=2), encoding='utf-8')
print('Created six app-only PNG crops: 2452x1272')
