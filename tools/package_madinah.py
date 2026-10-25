"""Verify the official archive and index unchanged, PDF-compatible Illustrator pages."""
import hashlib
import io
import json
import re
import struct
import sys
import zipfile
from pathlib import Path
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
source = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'tmp/v019/1441-AI-hafs.zip'
EXPECTED_SHA = '280c5d71ca16aaeeb3a343be1b92c76fa6df71d0671e202c026fde12402d9eef'
assert source.stat().st_size == 466430736
with source.open('rb') as file:
    assert hashlib.file_digest(file, 'sha256').hexdigest() == EXPECTED_SHA
rows = []
with zipfile.ZipFile(source) as archive, source.open('rb') as raw_file:
    files = sorted((f for f in archive.infolist() if f.filename.lower().endswith('.ai')), key=lambda f: f.filename)
    assert len(files) == 604
    for n, info in enumerate(files, 1):
        assert re.match(rf'{n:03}___Hafs39__DM\.ai$', Path(info.filename).name)
        assert info.compress_type == zipfile.ZIP_DEFLATED
        raw = archive.read(info)
        assert raw.startswith(b'%PDF-')
        reader = PdfReader(io.BytesIO(raw))
        assert not reader.is_encrypted and len(reader.pages) == 1
        assert float(reader.pages[0].mediabox.width) > 0 and float(reader.pages[0].mediabox.height) > 0
        raw_file.seek(info.header_offset)
        header = struct.unpack('<4s5H3I2H', raw_file.read(30))
        assert header[0] == b'PK\x03\x04'
        offset = info.header_offset + 30 + header[-2] + header[-1]
        rows.append(dict(sha256=hashlib.sha256(raw).hexdigest(), bytes=len(raw), compressedOffset=offset,
                         compressedBytes=info.compress_size, sourceFile=Path(info.filename).name))
        if n in (1, 2, 121, 300, 582, 603, 604):
            (ROOT / f'tmp/v019/madinah-{n}.pdf').write_bytes(raw)
manifest = dict(bookId='madinah-hafs-1441', originalSha256=EXPECTED_SHA, archiveBytes=source.stat().st_size,
                sourceUrl='https://dm.qurancomplex.gov.sa/Download/1441-AI-hafs.zip', pages=rows)
text_index = json.loads((ROOT / 'app/src/main/assets/quran-index.json').read_text(encoding='utf-8'))
printed = {p: [] for p in range(1, 605)}
for text_page in range(1, 605):
    source_page = json.loads((ROOT / text_index['path'] / f'{text_page}.json').read_text(encoding='utf-8'))
    for verse in source_page['ayahs']:
        printed[verse['originalPage']].append((verse['surah'], verse['ayah'], text_page))
assert sum(len(v) for v in printed.values()) == 6236
anchors = [(s, a) for page in range(1, 605) for s, a, _ in sorted(printed[page])]
assert len(set(anchors)) == 6236 and anchors == sorted(anchors)
for n, row in enumerate(rows, 1):
    verses = sorted(printed[n])
    assert verses
    row.update(textPages=sorted({v[2] for v in verses}), verseCount=len(verses),
               firstSurah=verses[0][0], firstAyah=verses[0][1])
manifest['textSourceSha256'] = text_index['sourceSha256']
folder = ROOT / 'content/quran-madinah'
folder.mkdir(exist_ok=True)
text = json.dumps(manifest, ensure_ascii=False, indent=2) + '\n'
(folder / 'manifest.json').write_text(text, encoding='utf-8')
(ROOT / 'app/src/main/assets/madinah-pages.json').write_text(text, encoding='utf-8')
print(f'PASS: official archive SHA-256, ZIP CRC and 604 single-page PDF-compatible originals; no page content changed. Total uncompressed bytes: {sum(r["bytes"] for r in rows)}')
