"""Probe the actual audio delivery links, comparing existing byte samples when available."""
import concurrent.futures
import hashlib
import json
from datetime import date
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def load(p): return json.loads((ROOT / p).read_text('utf-8'))
tracks = [t for r in load('content/audiobooks/catalog.json')['recordings'] for t in r['tracks']]
tracks += load('content/tajweed-media/catalog.json')['audio']
tracks += load('content/tajweed-examples/catalog.json')['recordings']

def probe(track):
    request = urllib.request.Request(track['url'], headers={'Range': 'bytes=0-4095', 'Accept-Encoding': 'identity', 'User-Agent': 'Basair-SourceCheck/0.20'})
    try:
        with urllib.request.urlopen(request, timeout=30) as r:
            body = r.read(4097)
            assert r.status == 206 and len(body) == 4096
            assert r.headers.get('Content-Range', '').startswith('bytes 0-4095/')
            expected = next((p for p in track.get('probes', []) if p['offset'] == 0 and p['bytes'] == 4096), None)
            if expected: assert hashlib.sha256(body).hexdigest() == expected['sha256']
            return {'id': track['id'], 'url': track['url'], 'httpStatus': r.status, 'contentRange': r.headers['Content-Range'],
                    'sampleSha256': hashlib.sha256(body).hexdigest(), 'priorSampleMatched': True if expected else None, 'status': 'passed'}
    except Exception as error:
        return {'id': track['id'], 'url': track['url'], 'httpStatus': getattr(error, 'code', None), 'status': 'unverified', 'error': type(error).__name__}

if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: rows = list(pool.map(probe, tracks))
    report = {'version': '0.20.0', 'checkedOn': date.today().isoformat(), 'scope': 'First 4096 bytes only, not whole audio or listening review', 'tracks': rows}
    (ROOT / 'content/review/audio-links-v020.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    print(f"Audio URLs: {len(rows)}; verified ranges: {sum(r['status'] == 'passed' for r in rows)}; unverified: {[r['id'] for r in rows if r['status'] != 'passed']}")
