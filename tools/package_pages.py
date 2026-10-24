"""Extract unchanged page content for verified on-demand reading, outside APK assets."""
import hashlib
import json
from pathlib import Path
from pypdf import PdfReader, PdfWriter

ROOT = Path(__file__).resolve().parents[1]
books = json.loads((ROOT / 'content/books.json').read_text('utf-8'))
manifest = {}
for book in books:
    original = ROOT / 'content/incoming' / book['inputFile']
    assert hashlib.sha256(original.read_bytes()).hexdigest() == book['sha256']
    reader = PdfReader(original)
    folder = Path('content/pages') / (book['id'] + '-' + book['sha256'][:16])
    (ROOT / folder).mkdir(parents=True, exist_ok=True)
    rows = []
    for number, page in enumerate(reader.pages, 1):
        target = ROOT / folder / f'{number}.pdf'
        if not target.exists():
            writer = PdfWriter()
            writer.add_page(page)
            writer.add_metadata({'/Title': book['title'], '/Source': book['source']})
            with target.open('wb') as output:
                writer.write(output)
        data = target.read_bytes()
        extracted = PdfReader(target)
        assert len(extracted.pages) == 1
        assert extracted.pages[0].mediabox == page.mediabox
        rows.append({'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)})
    manifest[book['id']] = {'originalSha256': book['sha256'], 'path': folder.as_posix(), 'pages': rows}
    print(book['id'], len(rows), sum(p['bytes'] for p in rows), flush=True)
(ROOT / 'app/src/main/assets/page-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
print('Verified page manifest:', sum(len(v['pages']) for v in manifest.values()), flush=True)
