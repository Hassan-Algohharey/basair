"""Check stable catalog relationships without judging scholarly content."""
import json
from datetime import date
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def load(path): return json.loads((ROOT / path).read_text('utf-8'))
catalog = load('content/media/catalog.json')
items = catalog['items']
assert len({i['id'] for i in items}) == len(items)
assert len({i['url'] for i in items}) == len(items)
previous = json.loads(subprocess.check_output(['git', '-c', f'safe.directory={ROOT.as_posix()}', 'show', 'eb13b24:content/media/catalog.json'], cwd=ROOT).decode('utf-8'))
assert {i['id']: i['url'] for i in previous['items']} == {i['id']: i['url'] for i in items}, 'Existing IDs or source links changed'
books = load('app/src/main/assets/library-manifest.json')
pages = load('app/src/main/assets/page-manifest.json')
chapter_count = 0
for book in books:
    entry = pages[book['id']]
    assert len(entry['pages']) == book['pages']
    assert entry['originalSha256'] == book['sha256']
    chapters = book.get('chapters', [])
    positions = [c['pdfPage'] for c in chapters]
    assert positions == sorted(positions) and all(1 <= p <= book['pages'] for p in positions)
    chapter_count += len(chapters)
series_rows = []
for series in catalog['series']:
    numbers = sorted(i['episodeNumber'] for i in items if i.get('seriesId') == series['id'])
    assert len(numbers) == len(set(numbers))
    expected = series.get('expectedEpisodes')
    gaps = sorted(set(range(1, (expected or max(numbers)) + 1)) - set(numbers))
    assert not gaps or series['coverage'] == 'partial'
    series_rows.append({'id': series['id'], 'indexed': len(numbers), 'coverage': series['coverage'], 'missingEpisodeNumbers': gaps})
report = {'version': '0.20.0', 'checkedOn': date.today().isoformat(), 'stableMediaIdsAndUrls': len(items),
          'books': len(books), 'pages': sum(b['pages'] for b in books), 'chapterEntries': chapter_count,
          'series': series_rows, 'result': 'passed', 'scope': 'Structural and edition/hash consistency, not approval of scholarly attributions or text.'}
(ROOT / 'content/review/index-audit-v020.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', 'utf-8')
print(json.dumps({k:v for k,v in report.items() if k != 'series'}, ensure_ascii=False))
