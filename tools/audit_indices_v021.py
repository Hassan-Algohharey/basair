"""Audit the expanded catalog without changing or renumbering existing identifiers."""
import json
import subprocess
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def load(p): return json.loads((ROOT / p).read_text('utf-8'))
catalog = load('content/media/catalog.json')
previous = json.loads(subprocess.check_output(['git', '-c', f'safe.directory={ROOT.as_posix()}', 'show', '967ffea:content/media/catalog.json'], cwd=ROOT).decode('utf-8'))
items = catalog['items']
current = {i['id']: i for i in items}
assert len(current) == len(items) == 117
assert len({i['url'] for i in items}) == len(items)
for old in previous['items']:
    now = current[old['id']]
    assert all(now[k] == old[k] for k in ['url', 'title', 'scholarId', 'bookIds'])
links = load('content/media/link-audit-v021.json')
for probe in load('content/review/source-rechecks-v021.json')['links']:
    old = next(r for r in links['links'] if r['url'] == probe['url'])
    old.update(probe)
indexes = load('content/review/expansion-v021.json')['sourceIndexPages']
for probe in indexes:
    old = next((r for r in links['links'] if r['url'] == probe['url']), None)
    if old: old.update(probe)
    else: links['links'].append(dict(probe, categories=['series-index']))
(ROOT / 'content/media/link-audit-v021.json').write_text(json.dumps(links, ensure_ascii=False, indent=2) + '\n', 'utf-8')
series_reports = []
for series in catalog['series']:
    group = sorted((i for i in items if i.get('seriesId') == series['id']), key=lambda i: i['episodeNumber'])
    numbers = [i['episodeNumber'] for i in group]
    assert len(numbers) == len(set(numbers))
    if series['coverage'] in ('indexed', 'complete'):
        assert numbers == list(range(1, series['expectedEpisodes'] + 1))
    series_reports.append({'id': series['id'], 'items': len(group), 'coverage': series['coverage']})
books = load('app/src/main/assets/library-manifest.json')
page_manifest = load('app/src/main/assets/page-manifest.json')
for book in books:
    assert page_manifest[book['id']]['originalSha256'] == book['sha256']
    assert len(page_manifest[book['id']]['pages']) == book['pages']
    positions = [c['pdfPage'] for c in book.get('chapters', [])]
    assert positions == sorted(positions) and all(1 <= n <= book['pages'] for n in positions)
report = {'version': '0.21.0', 'checkedOn': date.today().isoformat(), 'preservedOldIdsAndUrls': len(previous['items']),
    'mediaItems': len(items), 'newItems': len(items) - len(previous['items']), 'series': series_reports,
    'books': len(books), 'pages': sum(b['pages'] for b in books), 'chapterEntries': sum(len(b.get('chapters', [])) for b in books),
    'datedItems': sum(bool(i.get('publishedDateLabel')) for i in items), 'durationItems': sum(bool(i.get('durationSeconds')) for i in items),
    'combinedSameDayLinkChecks': len(links['links']), 'reachableCombinedLinks': sum(r['status'] == 'reachable' for r in links['links']),
    'scope': 'Combines same-day 0.20 checks with 0.21 additions/rechecks. Structural/source coverage only, not scholarly or listening review.'}
(ROOT / 'content/review/index-audit-v021.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', 'utf-8')
print(json.dumps(report, ensure_ascii=False))
