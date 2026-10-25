"""Read back public hosted pages and confirm their exact manifest bytes."""
import hashlib
import json
import urllib.request
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'app/src/main/assets/page-manifest.json').read_text('utf-8'))
host = 'https://raw.githubusercontent.com/Hassan-Algohharey/basair/9ca0530e333f3eb337caf6f71912e739b30d33a9/'

def verify(item):
    book_id, book = item
    for number in sorted({1, len(book['pages'])}):
        expected = book['pages'][number - 1]
        with urllib.request.urlopen(host + book['path'] + f'/{number}.pdf', timeout=40) as response:
            data = response.read(expected['bytes'] + 1)
            assert response.status == 200
        assert len(data) == expected['bytes'] and hashlib.sha256(data).hexdigest() == expected['sha256'], (book_id, number)
    return book_id

with ThreadPoolExecutor(max_workers=4) as executor:
    for name in executor.map(verify, manifest.items()):
        print('PASS hosted first/last pages:', name, flush=True)
print('PASS: all 18 books have verified first and last hosted pages.', flush=True)

# A small range confirms the host behavior used by resumable original downloads.
original = root / 'content/originals/nawawi-fifty.pdf'
request = urllib.request.Request(host + 'content/originals/nawawi-fifty.pdf', headers={'Range': 'bytes=1024-2047', 'Accept-Encoding': 'identity'})
with urllib.request.urlopen(request, timeout=40) as response:
    data = response.read(1024)
    source = original.read_bytes()
    if response.status == 206:
        assert response.headers['Content-Range'].startswith('bytes 1024-2047/') and data == source[1024:2048]
    else:
        assert response.status == 200 and data == source[:1024]
    print('PASS original range behavior:', response.status, 'verified bytes:', len(data), flush=True)
