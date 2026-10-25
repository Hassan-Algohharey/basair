"""Verify new Tajweed originals, split content streams and rendered chapter starts."""
import hashlib
import json
import subprocess
from pathlib import Path
from PIL import Image, ImageChops
from pypdf import PdfReader

root = Path(__file__).resolve().parents[1]
poppler = Path('C:/Users/hassa/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/poppler/Library/bin/pdftoppm.exe')
temp = root / 'tmp/pdfs/tajweed-verify'
temp.mkdir(parents=True, exist_ok=True)
books = [b for b in json.loads((root / 'content/books.json').read_text('utf-8')) if b['subject'] == 'tajweed']
manifest = json.loads((root / 'app/src/main/assets/page-manifest.json').read_text('utf-8'))
assert {b['id'] for b in books} == {'tuhfat-atfal', 'jazariyyah'}
count = 0
for book in books:
    source = root / 'content/originals' / Path(book['asset']).name
    assert hashlib.sha256(source.read_bytes()).hexdigest() == book['sha256']
    reader = PdfReader(source)
    assert len(reader.pages) == book['pages'] and not reader.is_encrypted
    samples = {1, book['pages']} | {c['pdfPage'] for c in book['chapters']}
    for number, page in enumerate(reader.pages, 1):
        split = root / manifest[book['id']]['path'] / f'{number}.pdf'
        extracted = PdfReader(split)
        assert len(extracted.pages) == 1
        assert extracted.pages[0].mediabox == page.mediabox
        assert extracted.pages[0].get_contents().get_data() == page.get_contents().get_data()
        if number not in samples:
            continue
        images = []
        for file, page_number, suffix in [(source, number, 'original'), (split, 1, 'split')]:
            prefix = temp / f'{book["id"]}-{number}-{suffix}'
            subprocess.run([str(poppler), '-f', str(page_number), '-l', str(page_number), '-scale-to', '900', '-singlefile', '-png', str(file), str(prefix)], check=True, capture_output=True)
            images.append(Image.open(str(prefix) + '.png').convert('RGB'))
        assert images[0].size == images[1].size and ImageChops.difference(*images).getbbox() is None
        count += 1
    print('PASS:', book['id'], book['pages'], 'unchanged page streams', flush=True)
print('PASS:', count, 'identical rendered samples; 62 page streams verified.', flush=True)
