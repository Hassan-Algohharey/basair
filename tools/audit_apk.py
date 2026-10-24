"""Verify the distributable contains exactly the approved, unchanged originals."""
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
    expected = {asset_prefix + book['asset'] for book in books}
    actual = {name for name in archive.namelist() if name.startswith(asset_prefix + 'books/') and name.endswith('.pdf')}
    assert actual == expected, 'Unexpected or missing book assets'
    for book in books:
        data = archive.read(asset_prefix + book['asset'])
        assert hashlib.sha256(data).hexdigest() == book['sha256'], book['id']
    embedded = json.loads(archive.read(asset_prefix + 'library-manifest.json'))
    assert [(b['id'], b['sha256']) for b in embedded] == [(b['id'], b['sha256']) for b in books]
print(json.dumps({'apk': str(apk), 'approvedPdfs': len(expected), 'pages': sum(b['pages'] for b in books),
                  'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                  'result': 'PASS: original bytes and source manifest match; no rejected/reference PDFs bundled.'}, ensure_ascii=False))
