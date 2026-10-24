"""Fetch only approved manifest entries; verify hashes before admitting files."""
import hashlib
import json
import urllib.parse
import urllib.request
from pathlib import Path

root = Path(__file__).resolve().parent.parent
books = json.loads((root / 'content/books.json').read_text(encoding='utf-8'))
folder = root / 'content/incoming'
folder.mkdir(parents=True, exist_ok=True)
for book in books:
    target = folder / book['inputFile']
    if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == book['sha256']:
        print(f'Verified cached original: {book["id"]}', flush=True)
        continue
    url = urllib.parse.quote(book['downloadUrl'], safe=':/%')
    assert urllib.parse.urlparse(url).hostname in {'d1.islamhouse.com', 'upload.wikimedia.org'}
    temporary = target.with_suffix('.download')
    try:
        request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 BasairLibrary/0.4 source-verification', 'Referer': 'https://islamhouse.com/'})
        with urllib.request.urlopen(request, timeout=90) as response, temporary.open('wb') as output:
            while block := response.read(1024 * 1024): output.write(block)
        assert hashlib.sha256(temporary.read_bytes()).hexdigest() == book['sha256'], 'Source changed; manual review is required.'
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)
    print(f'Fetched verified original: {book["id"]}', flush=True)
