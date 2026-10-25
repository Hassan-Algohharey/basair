"""Package reviewed search aids, bound to their original PDF edition."""
import json
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
books = {b['id']: b for b in json.loads((ROOT / 'content/books.json').read_text(encoding='utf-8'))}
lines = ['package com.algohary.basair', '', '// Generated from reviewed transcription; the PDF remains the reading source.', 'internal val reviewedTextEntries = listOf(']
def q(v):
    return json.dumps(v, ensure_ascii=False).replace('$', '\\$')
count = 0
for path in sorted((ROOT / 'content/search').glob('*.json')):
    data = json.loads(path.read_text(encoding='utf-8'))
    book = books[data['bookId']]
    assert data['bookSha256'] == book['sha256']
    assert hashlib.sha256((ROOT / 'content/originals' / Path(book['asset']).name).read_bytes()).hexdigest() == book['sha256']
    entries = data['entries']
    assert [e['number'] for e in entries] == list(range(1, 62)), 'Reviewed scope is the complete 61-verse Tuhfah text'
    for e in entries:
        assert 4 <= e['pdfPage'] <= 18 and e['printedPage'] == str(e['pdfPage'] + 21)
        assert e['text'].strip() and len(e['text']) < 500
        args = [q(book['id']), q(book['sha256']), str(e['number']), str(e['pdfPage']), q(e['printedPage']), q(e['text'])]
        lines.append('    BookTextEntry(' + ', '.join(args) + '),')
        count += 1
lines += [')', '']
(ROOT / 'app/src/main/java/com/algohary/basair/BookTextData.kt').write_text('\n'.join(lines), encoding='utf-8')
print(f'PASS: {count} reviewed verse search entries; edition hashes and page coverage valid.')
