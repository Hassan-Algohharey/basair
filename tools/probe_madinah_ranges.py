"""Probe a few official archive byte ranges without downloading the full archive."""
import hashlib
import json
import urllib.request
import zlib
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest = json.loads((ROOT / 'content/quran-madinah/manifest.json').read_text(encoding='utf-8'))
def probe(page):
    row = manifest['pages'][page - 1]
    start = row['compressedOffset']
    end = start + row['compressedBytes'] - 1
    request = urllib.request.Request(manifest['sourceUrl'], headers={'Range': f'bytes={start}-{end}', 'Accept-Encoding': 'identity'})
    with urllib.request.urlopen(request, timeout=45) as response:
        assert response.status == 206
        assert response.headers['Content-Range'] == f'bytes {start}-{end}/{manifest["archiveBytes"]}'
        data = response.read(row['compressedBytes'] + 1)
    assert len(data) == row['compressedBytes']
    original = zlib.decompress(data, -15)
    assert len(original) == row['bytes'] and hashlib.sha256(original).hexdigest() == row['sha256']
    assert original.startswith(b'%PDF-')
    return {'page': page, 'status': 206, 'compressedBytes': len(data), 'originalBytes': len(original), 'sha256': row['sha256']}

with ThreadPoolExecutor(max_workers=3) as pool:
    results = list(pool.map(probe, [1, 121, 604]))
print(json.dumps({'result': 'PASS', 'probes': results}, ensure_ascii=False))
