"""Render the four handoff example images (front, rear, left, right) from scripts/example-photos/scene.html.

The scene is a procedural 3D riverside park parking lot (three.js). Output goes to submission/example-photos/*.jpg.
These are 3D renders for the demo, not real photographs or vehicle camera images.

Usage: python scripts/render-example-photos.py [--channel chrome]
"""
import argparse
import base64
import functools
import http.server
import shutil
import subprocess
import threading
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'submission/example-photos'
parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument('--channel', default=None)
args = parser.parse_args()


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass


server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(Quiet, directory=str(ROOT)))
threading.Thread(target=server.serve_forever, daemon=True).start()
OUT.mkdir(parents=True, exist_ok=True)
ffmpeg = shutil.which('ffmpeg')
try:
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, channel=args.channel,
                                    args=['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
        page = browser.new_page(viewport={'width': 1920, 'height': 1200})
        errors = []
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.goto(f'http://127.0.0.1:{server.server_port}/scripts/example-photos/scene.html')
        page.wait_for_function('window.sceneReady === true', timeout=120000)
        for name in ['front', 'rear', 'left', 'right']:
            data = page.evaluate('n => window.renderView(n)', name)
            png = OUT / f'{name}.png'
            png.write_bytes(base64.b64decode(data.split(',', 1)[1]))
            if ffmpeg:  # JPEG keeps the repository small; quality 2 is visually lossless
                subprocess.run([ffmpeg, '-y', '-loglevel', 'error', '-i', str(png), '-q:v', '2', str(OUT / f'{name}.jpg')], check=True)
                png.unlink()
            print('RENDERED:', name, flush=True)
        assert not errors, errors
        browser.close()
finally:
    server.shutdown()
