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
    assert not any(name.lower().endswith(('.mp3', '.mp4', '.m4a', '.webm', '.ogg')) for name in archive.namelist()), 'Media files must remain outside the application'
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
    audio_catalog = root / 'content/audiobooks/catalog.json'
    if audio_catalog.exists():
        recordings = json.loads(audio_catalog.read_text('utf-8'))['recordings']
        dex = b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
        for recording in recordings:
            assert recording['id'].encode() in dex
            assert recording['licenseUrl'].encode() in dex
            for track in recording['tracks']:
                assert track['id'].encode() in dex and track['url'].encode() in dex
        tajweed_audio = root / 'content/tajweed-media/catalog.json'
        if tajweed_audio.exists():
            lessons = json.loads(tajweed_audio.read_text('utf-8'))['audio']
            for lesson in lessons:
                assert lesson['id'].encode() in dex and (lesson['id'] + '-full').encode() in dex
                assert lesson['url'].encode() in dex and lesson['sourceUrl'].encode() in dex
        examples = json.loads((root / 'content/tajweed-examples/catalog.json').read_text('utf-8'))
        for recording in examples['recordings']:
            assert (recording['id'] + '-full').encode() in dex
            assert recording['url'].encode() in dex and recording['sha256'].encode() in dex
        assert examples['licenseUrl'].encode() in dex
        for example in examples['examples']:
            assert (example['recordingId'] + '-clip').encode() in dex
        license_path = 'licenses/androidx-media3-LICENSE.txt'
        assert archive.read(asset_prefix + license_path) == (root / 'app/src/main/assets' / license_path).read_bytes()
    image_manifest = root / 'content/quran-madinah/manifest.json'
    if image_manifest.exists():
        images = json.loads(archive.read(asset_prefix + 'madinah-pages.json'))
        assert images == json.loads(image_manifest.read_text('utf-8'))
        assert images['archiveBytes'] == 466430736 and len(images['pages']) == 604
        assert images['originalSha256'] == '280c5d71ca16aaeeb3a343be1b92c76fa6df71d0671e202c026fde12402d9eef'
        assert sum(row['verseCount'] for row in images['pages']) == 6236
        assert images['textSourceSha256'] == json.loads(archive.read(asset_prefix + 'quran-index.json'))['sourceSha256']
        for n, row in enumerate(images['pages'], 1):
            assert row['sourceFile'] == f'{n:03}___Hafs39__DM.ai'
            assert row['compressedOffset'] >= 0 and 0 < row['compressedBytes'] <= 8_000_000
            assert row['compressedOffset'] + row['compressedBytes'] <= images['archiveBytes']
            assert 0 < row['bytes'] <= 12_000_000 and len(row['sha256']) == 64
            assert row['textPages'] and all(1 <= page <= 604 for page in row['textPages'])
        assert not any(name.lower().endswith(('.ai', '.zip')) for name in archive.namelist())
print(json.dumps({'apk': str(apk), 'bundledPdfs': 0, 'catalogBooks': len(books), 'pages': sum(b['pages'] for b in books),
                  'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                  'result': 'PASS: no PDFs bundled; all hosted original/page hashes and catalog match.'}, ensure_ascii=False))
