"""Index only the original series pages; preserve original titles and duplicate numbering."""
import concurrent.futures
import copy
import hashlib
import html
import json
import re
from pathlib import Path
from audit_sources_v020 import fetch, plain, DAY
import audit_sources_v020 as transport

ROOT = Path(__file__).resolve().parents[1]
transport.CACHE = ROOT / 'tmp/v021/sources'
def load(p): return json.loads((ROOT / p).read_text('utf-8'))
def save(p, v): (ROOT / p).write_text(json.dumps(v, ensure_ascii=False, indent=2) + '\n', 'utf-8')
catalog = load('content/media/catalog.json')
audit = load('content/media/audit.json')
links = load('content/media/link-audit-v020.json')
links['version'] = '0.21.0'
series = next(s for s in catalog['series'] if s['id'] == 'bin-baz-tahawi')
rows = []
indexes = []
for n in (1, 2, 3):
    u = series['sourceUrl'] + ('' if n == 1 else '?page=' + str(n))
    probe, body = fetch(u)
    assert probe.get('httpStatus') == 200 and not probe.get('truncated')
    indexes.append(probe)
    for url, content in re.findall(r'<a[^>]+href="(https://binbaz.org.sa/audios/\d+/[^"]+)"[^>]*>(.*?)</a>', body.decode('utf-8'), re.S):
        title = plain(content)
        if re.match(r'^\d\d\s', title): rows.append((url, title))
assert len(rows) == 32 and len(set(u for u, _ in rows)) == 32
assert [int(t[:2]) for _, t in rows] == list(range(1, 16)) + list(range(15, 32))
template = next(i for i in catalog['items'] if i['id'] == 'bin-baz-tahawi-01')

def inspect(row):
    url, title = row
    probe, body = fetch(url)
    assert probe.get('httpStatus') == 200 and not probe.get('truncated')
    source = body.decode('utf-8')
    heading = plain(re.search(r'<h1[^>]*>(.*?)</h1>', source, re.S).group(1))
    assert heading == title
    assert re.search(r'<audio\b', source, re.I)
    return probe, heading

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: results = list(pool.map(inspect, rows))
new_ids = []
for order, ((url, title), (probe, heading)) in enumerate(zip(rows, results), 1):
    existing = next((i for i in catalog['items'] if i['url'] == url), None)
    if existing:
        assert existing['episodeNumber'] == order
        item = existing
    else:
        item = copy.deepcopy(template)
        source_id = re.search(r'/audios/(\d+)/', url).group(1)
        item.update(id='bin-baz-tahawi-source-' + source_id, title=title,
                    englishTitle=f'Tahawiyyah commentary — index {order}, source {int(title[:2])}', url=url,
                    verifiedOn=DAY, episodeNumber=order)
        catalog['items'].append(item)
        new_ids.append(item['id'])
        audit['items'].append({'itemId': item['id'], 'url': url, 'httpStatus': 200, 'finalUrl': probe['finalUrl'],
            'pageSha256': probe['snapshotSha256'], 'observedHeading': heading, 'mediaElementPresent': True,
            'embeddedMediaKinds': ['audio'], 'reviewedOn': DAY})
    item['sourceEpisodeNumber'] = int(title[:2])
    probe.update(itemId=item['id'], categories=['media'], title=heading)
    links['links'] = [p for p in links['links'] if p.get('itemId') != item['id']] + [probe]
series.update(coverage='indexed', expectedEpisodes=32,
    coverageNote='فهرسة32 مادة من صفحات فهرس المصدر الثلاث. يحمل درسان مختلفان الرقم15 في الأصل؛ ترتيب التنقل1–32 مستقل عن رقم المصدر الظاهر بالعنوان. ليست مراجعة سماعية للاكتمال أو مطابقة لطبعة المكتبة.',
    englishCoverageNote='All 32 items from the three source index pages. Two different lessons carry source number 15; navigation order 1–32 is separate from the source number in each title. Not a full listening review or an edition/page match.')
e = next(e for e in audit['seriesEvidence'] if e['seriesId'] == series['id'])
e.update(coverage='indexed', catalogEpisodeNumbers=list(range(1, 33)), reviewedOn=DAY,
    pageSha256=indexes[0]['snapshotSha256'], indexPages=indexes, listedEpisodeCount=32, nextPage=None,
    sourceNumbering=[int(t[:2]) for _, t in rows], evidenceNote=series['coverageNote'])
save('content/media/catalog.json', catalog)
save('content/media/audit.json', audit)
save('content/media/link-audit-v021.json', links)
baseline_ids = {r.get('itemId') for r in load('content/media/link-audit-v020.json')['links']}
new_ids = [i['id'] for i in catalog['items'] if i['id'] not in baseline_ids]
save('content/review/expansion-v021.json', {'version': '0.21.0', 'checkedOn': DAY, 'newIds': new_ids,
     'sourceIndexPages': indexes, 'duplicateSourceNumbers': [15], 'navigationCount': 32,
     'scope': 'Original source titles, audio presence and index coverage; no listening, doctrinal review or edition synchronization.'})
print(f'Indexed 32 source items, added {len(new_ids)}; total {len(catalog["items"])}. Original duplicate source number 15 preserved.')
