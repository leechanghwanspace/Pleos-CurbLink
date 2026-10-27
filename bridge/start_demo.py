"""Start the local simulation services; Ctrl+C stops only the children started here."""
import socket
import subprocess
import sys
import time
import webbrowser
from pathlib import Path

root = Path(__file__).resolve().parents[1]
simulator = root / 'simulator'
if not (simulator / 'index.html').exists():
    simulator = root.parent / 'simulator'
if not (simulator / 'index.html').exists():
    sys.exit('Missing simulator/index.html')
try:
    import websockets
except ImportError:
    sys.exit('Run: python -m pip install -r bridge/requirements.txt')
for port in (8765, 8766, 8080):
    with socket.socket() as probe:
        if probe.connect_ex(('127.0.0.1', port)) == 0:
            sys.exit(f'Port {port} is already in use. Stop the previous demo service before restarting.')
children = []
try:
    children.append(subprocess.Popen([sys.executable, '-u', str(root/'bridge/server.py')], cwd=root))
    children.append(subprocess.Popen([sys.executable, '-m', 'http.server', '8080', '--bind', '127.0.0.1', '--directory', str(simulator)], cwd=root))
    time.sleep(1)
    if any(p.poll() is not None for p in children):
        raise RuntimeError('A demo service failed to start')
    print('Simulation: http://127.0.0.1:8080/index.html | Ctrl+C to stop')
    print('APK and AAOS emulator must be started separately. No real vehicle/OEM connection.')
    webbrowser.open('http://127.0.0.1:8080/index.html')
    while all(p.poll() is None for p in children):
        time.sleep(1)
except KeyboardInterrupt:
    pass
finally:
    for child in children:
        if child.poll() is None:
            child.terminate()
            try:
                child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()
