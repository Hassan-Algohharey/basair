"""Bounded, read-only source/link audit. Snapshots stay in ignored tmp/v020."""
import concurrent.futures
from datetime import date
import hashlib
import html
import json
import re
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'tmp/v020/sources'
DAY = date.today().isoformat()

def fetch(url, binary=False):
    headers = {'User-Agent': 'Basair-SourceCheck/0.20', 'Accept-Encoding': 'identity'}
    if binary:
        headers['Range'] = 'bytes=0-262143'
    request = urllib.request.Request(urllib.parse.quote(url, safe=':/?=&%+'), headers=headers)
    row = {'url': url, 'checkedOn': DAY}
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            limit = 262144 if binary else 5000000
            body = response.read(limit + 1)
            row.update(httpStatus=response.status, finalUrl=response.url,
                       contentType=response.headers.get('Content-Type', ''),
                       contentRange=response.headers.get('Content-Range'),
                       bytesRead=min(len(body), limit), truncated=len(body) > limit,
                       snapshotSha256=hashlib.sha256(body[:limit]).hexdigest())
            row['status'] = 'reachable' if response.status in (200, 206) else 'needs_check'
            CACHE.mkdir(parents=True, exist_ok=True)
            (CACHE / (hashlib.sha256(url.encode()).hexdigest() + ('.bin' if binary else '.html'))).write_bytes(body[:limit])
            return row, body[:limit]
    except Exception as error:
        row.update(status='unverified', httpStatus=getattr(error, 'code', None), error=type(error).__name__)
        return row, b''

def plain(value):
    return re.sub(r'\s+', ' ', html.unescape(re.sub('<[^>]+>', ' ', value))).strip()

def mp3_duration(data):
    """Only MPEG Layer III Xing/Info or VBRI frame counts; never extrapolate bitrate."""
    start = 0
    if data[:3] == b'ID3' and len(data) >= 10:
        if any(x >= 128 for x in data[6:10]):
            return None
        start = 10 + sum(x << (7 * (3 - i)) for i, x in enumerate(data[6:10]))
        if data[5] & 0x10:
            start += 10
    for offset in range(start, min(len(data) - 180, start + 4096)):
        h = int.from_bytes(data[offset:offset + 4], 'big')
        version = (h >> 19) & 3
        rate_index = (h >> 10) & 3
        bitrate_index = (h >> 12) & 15
        if h >> 21 != 0x7ff or version == 1 or (h >> 17) & 3 != 1 or rate_index == 3 or bitrate_index in (0, 15):
            continue
        rate = (44100, 48000, 32000)[rate_index] // (1 if version == 3 else 2 if version == 2 else 4)
        samples = 1152 if version == 3 else 576
        mono = (h >> 6) & 3 == 3
        side = (17 if mono else 32) if version == 3 else (9 if mono else 17)
        pos = offset + 4 + (0 if (h >> 16) & 1 else 2) + side
        if data[pos:pos + 4] in (b'Xing', b'Info') and int.from_bytes(data[pos + 4:pos + 8], 'big') & 1:
            frames = int.from_bytes(data[pos + 8:pos + 12], 'big')
            method = data[pos:pos + 4].decode() + ' MPEG frame count'
        elif data[offset + 36:offset + 40] == b'VBRI':
            frames = int.from_bytes(data[offset + 50:offset + 54], 'big')
            method = 'VBRI MPEG frame count'
        else:
            continue
        seconds = frames * samples / rate
        if 0 < seconds <= 86400:
            return {'durationSeconds': int(seconds), 'durationExactSeconds': round(seconds, 3),
                    'method': method, 'frames': frames, 'sampleRate': rate, 'samplesPerFrame': samples}
    return None

def metadata(item, body):
    s = body.decode('utf-8', 'replace')
    result = {}
    patterns = {
        'almunajjid': r'<i class="fa fa-calendar"></i>\s*([^<]+)',
        'al-barrak': r'<span data-sut="post-date"[^>]*>(.*?)</span>',
        'abdulqadir-othman': r'<div class="date">\s*<img[^>]*>\s*<span>(.*?)</span>',
    }
    match = re.search(patterns.get(item['scholarId'], r'(?!)'), s, re.S)
    if match:
        label = plain(match.group(1)); normal = label.translate(str.maketrans('٠١٢٣٤٥٦٧٨٩', '0123456789'))
        result['publishedDateLabel'] = label
        years = re.findall(r'(?<!\d)(1[34]\d{2})(?!\d)', normal)
        if years:
            result['publishedHijriYear'] = int(years[0])
        result['dateMethod'] = 'Visible item date; not file upload or series creation date'
    audio = re.search(r'<(?:audio|source)\b[^>]*\bsrc=["\x27]([^"\x27]+)', s, re.I)
    if audio:
        url = urllib.parse.urljoin(item['url'], html.unescape(audio.group(1)))
        if urllib.parse.urlparse(url).hostname in {'files.zadapps.info', 'almunajjid.com', 'binbaz.org.sa', 'sh-albarrak.com'}:
            probe, data = fetch(url, True)
            result['audioProbe'] = probe
            duration = mp3_duration(data)
            if duration:
                result.update(duration)
    return result

def main():
    catalog = json.loads((ROOT / 'content/media/catalog.json').read_text('utf-8'))
    requests = {}
    def add(url, category, binary=False):
        if url:
            requests.setdefault((url, binary), set()).add(category)
    for item in catalog['items']: add(item['url'], 'media')
    for s in catalog['scholars']:
        add(s['officialUrl'], 'scholar'); add(s['verificationUrl'], 'identity')
    for s in catalog['series']: add(s['sourceUrl'], 'series')
    for book in json.loads((ROOT / 'content/books.json').read_text('utf-8')):
        for key in ['source', 'licenseUrl']: add(book[key], 'book-' + key)
        add(book['downloadUrl'], 'book-original', True)
    for file in ['content/audiobooks/catalog.json', 'content/tajweed-media/catalog.json']:
        if not (ROOT / file).exists(): continue
        c = json.loads((ROOT / file).read_text('utf-8'))
        for recording in c.get('recordings', []):
            add(recording['sourceUrl'], 'audio-source')
            for track in recording['tracks']: add(track['url'], 'audio-file', True)
    rows = []
    metadata_rows = []
    def check(pair):
        (url, binary), categories = pair
        row, body = fetch(url, binary)
        row['categories'] = sorted(categories)
        if 'media' in categories:
            item = next(i for i in catalog['items'] if i['url'] == url)
            row['itemId'] = item['id']
        if 'media' in categories and body:
            row['title'] = plain(re.search(r'<title[^>]*>(.*?)</title>', body.decode('utf-8', 'replace'), re.S).group(1)) if re.search(b'<title', body) else ''
            meta = metadata(item, body)
            meta.update(itemId=item['id'], url=url, pageSha256=row['snapshotSha256'], checkedOn=DAY)
            return row, meta
        return row, None
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for row, meta in pool.map(check, requests.items()):
            rows.append(row)
            if meta: metadata_rows.append(meta)
    report = {'version': '0.20.0', 'checkedOn': DAY, 'scope': 'Bounded HTTP snapshot, not perpetual availability or scholarly/listening review. Binary probes do not verify entire file hashes. No upload timestamps inferred as publication dates.',
              'links': sorted(rows, key=lambda r: r['url']), 'metadata': sorted(metadata_rows, key=lambda r: r['itemId'])}
    (ROOT / 'content/media/link-audit-v020.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    print(json.dumps({'links': len(rows), 'reachable': sum(r['status'] == 'reachable' for r in rows), 'dated': sum('publishedDateLabel' in r for r in metadata_rows), 'duration': sum('durationSeconds' in r for r in metadata_rows)}))

if __name__ == '__main__': main()
