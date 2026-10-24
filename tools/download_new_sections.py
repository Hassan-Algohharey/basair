"""Fetch official originals for the biographies and purification sections for review."""
import hashlib
import urllib.request
from pathlib import Path
from pypdf import PdfReader

root = Path(__file__).resolve().parent.parent
for name, url in [
    ('tazkiyah-hearts.pdf', 'https://d1.islamhouse.com/data/ar/ih_books/single2/ar_hearts_and_to_heal_diseases.pdf'),
    ('salaf.pdf', 'https://d1.islamhouse.com/data/ar/ih_books/single9/ar_tragm_AlQra.pdf'),
]:
    path = root / 'content/incoming' / name
    if not path.exists():
        request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://islamhouse.com/'})
        path.write_bytes(urllib.request.urlopen(request, timeout=60).read())
    reader = PdfReader(path)
    print(name, len(reader.pages), len(path.read_bytes()), hashlib.sha256(path.read_bytes()).hexdigest())
    (root / 'content/incoming' / (name + '.txt')).write_text('\n'.join(p.extract_text() or '' for p in reader.pages), encoding='utf-8')
