"""Compare extracted page rendering with original PDF rendering, pixel for pixel."""
import json
import subprocess
from pathlib import Path
from PIL import Image, ImageChops
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
POPPLER = Path('C:/Users/hassa/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/poppler/Library/bin/pdftoppm.exe')
temp = ROOT / 'tmp/pdfs/v082-verify'
temp.mkdir(parents=True, exist_ok=True)
books = json.loads((ROOT / 'content/books.json').read_text('utf-8'))
manifest = json.loads((ROOT / 'app/src/main/assets/page-manifest.json').read_text('utf-8'))
count = 0
for book in books:
    source = ROOT / 'content/originals' / Path(book['asset']).name
    reader = PdfReader(source)
    samples = sorted({1, book['pages']} | {c['pdfPage'] for c in book.get('chapters', [])})
    for number, page in enumerate(reader.pages, 1):
        split = ROOT / manifest[book['id']]['path'] / f'{number}.pdf'
        extracted = PdfReader(split)
        original_contents = page.get_contents()
        new_contents = extracted.pages[0].get_contents()
        assert (original_contents.get_data() if original_contents else b'') == (new_contents.get_data() if new_contents else b'')
    for number in samples:
        split = ROOT / manifest[book['id']]['path'] / f'{number}.pdf'
        for input_file, file_page, suffix in [(source, number, 'original'), (split, 1, 'extracted')]:
            prefix = temp / f'{book["id"]}-{number}-{suffix}'
            subprocess.run([str(POPPLER), '-f', str(file_page), '-l', str(file_page), '-scale-to', '1000', '-singlefile', '-png', str(input_file), str(prefix)], check=True, capture_output=True)
        original = Image.open(temp / f'{book["id"]}-{number}-original.png').convert('RGB')
        extracted = Image.open(temp / f'{book["id"]}-{number}-extracted.png').convert('RGB')
        assert original.size == extracted.size and ImageChops.difference(original, extracted).getbbox() is None, (book['id'], number)
        count += 1
    print('PASS', book['id'], 'content streams and', len(samples), 'pixel comparisons', flush=True)
print('PASS: all 3322 page content streams unchanged;', count, 'rendered samples identical.', flush=True)
