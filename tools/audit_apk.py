"""Verify the lightweight distributable contains verified metadata and no book PDFs."""
import hashlib
import json
import sys
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent.parent
apk = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'app/build/outputs/apk/debug/app-debug.apk'
books = json.loads((root / 'content/books.json').read_text(encoding='utf-8'))
with zipfile.ZipFile(apk) as archive:
    asset_prefix = 'base/assets/' if 'base/manifest/AndroidManifest.xml' in archive.namelist() else 'assets/'
    actual = {name for name in archive.namelist() if name.endswith('.pdf')}
    assert not actual, 'PDF files must not be bundled in the lightweight application'
    embedded = json.loads(archive.read(asset_prefix + 'library-manifest.json'))
    assert [(b['id'], b['sha256']) for b in embedded] == [(b['id'], b['sha256']) for b in books]
    pages = json.loads(archive.read(asset_prefix + 'page-manifest.json'))
    assert set(pages) == {b['id'] for b in books}
    for book in books:
        entry = pages[book['id']]
        assert entry['originalSha256'] == book['sha256'] and len(entry['pages']) == book['pages']
        for number, page in enumerate(entry['pages'], 1):
            data = (root / entry['path'] / f'{number}.pdf').read_bytes()
            assert len(data) == page['bytes'] and hashlib.sha256(data).hexdigest() == page['sha256']
        assert hashlib.sha256((root / 'content/originals' / Path(book['asset']).name).read_bytes()).hexdigest() == book['sha256']
    if (root / 'app/src/main/assets/quran-index.json').exists():
        quran = json.loads(archive.read(asset_prefix + 'quran-index.json'))
        assert quran == json.loads((root / 'app/src/main/assets/quran-index.json').read_text('utf-8'))
        assert len(quran['pages']) == 604 and len(quran['surahs']) == 114
        assert sum(s['ayahCount'] for s in quran['surahs']) == 6236
        assert not any(name.startswith(asset_prefix + 'quran/') or name.endswith('/all.zip') for name in archive.namelist())
        for number, spec in enumerate(quran['pages'], 1):
            raw = (root / quran['path'] / f'{number}.json').read_bytes()
            assert len(raw) == spec['bytes'] and hashlib.sha256(raw).hexdigest() == spec['sha256']
        for key in ('fontAsset', 'tafsirFontAsset'):
            assert archive.read(asset_prefix + quran[key]) == (root / 'app/src/main/assets' / quran[key]).read_bytes()
print(json.dumps({'apk': str(apk), 'bundledPdfs': 0, 'catalogBooks': len(books), 'pages': sum(b['pages'] for b in books),
                  'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                  'result': 'PASS: no PDFs bundled; all hosted original/page hashes and catalog match.'}, ensure_ascii=False))
