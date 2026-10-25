"""Review original Tajweed indexes and register source links, never rehost recordings."""
import concurrent.futures
import hashlib
import html
import json
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'content/tajweed-media'
OUT.mkdir(exist_ok=True)
DATE = '2026-10-25'
HEADERS = {'User-Agent': 'Mozilla/5.0', 'Referer': 'https://islamhouse.com/'}

def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=35) as response:
        assert response.status == 200 and response.url == url
        return response.read()

def digest(data):
    return hashlib.sha256(data).hexdigest()

def save(name, data):
    path = OUT / name
    path.write_bytes(data)
    return {'path': path.relative_to(ROOT).as_posix(), 'bytes': len(data), 'sha256': digest(data)}

catalog = json.loads((ROOT / 'content/media/catalog.json').read_text('utf-8'))
audit = json.loads((ROOT / 'content/media/audit.json').read_text('utf-8'))
scholar = {'id': 'abdulqadir-othman', 'name': 'عبد القادر العثمان', 'englishName': 'Abdul Qadir al-Othman',
           'officialUrl': 'https://qurantaj.com/', 'verificationUrl': 'https://qurantaj.com/media/article/41'}
catalog['scholars'] = [s for s in catalog['scholars'] if s['id'] != scholar['id']] + [scholar]
evidence = []
for book_id, number, label in [('tuhfat-atfal', 44, 'تحفة الأطفال'), ('jazariyyah', 41, 'الجزرية')]:
    url = f'https://qurantaj.com/media/article/{number}'
    raw = fetch(url)
    evidence.append(save(f'taj-index-{number}.html', raw))
    text = raw.decode('utf-8')
    assert 'عبد القادر العثمان' in text
    rows = []
    for match in re.finditer(r'<iframe[^>]+src="([^"]+)"[^>]*>', text):
        link = re.search(r'href="(/media/view-article\?id=\d+)"[^>]*>(.*?)</a>', text[match.end():match.end()+1200], re.S)
        if link:
            title = html.unescape(re.sub('<[^>]+>', '', link[2])).strip()
            episode = int(re.search(r'الحلقة\s+(\d+)', title)[1])
            rows.append((episode, title, 'https://qurantaj.com' + link[1], match[1]))
    assert len(rows) == (8 if number == 44 else 11)
    series_id = 'othman-' + book_id
    group = dict(id=series_id, title='شرح ' + label + ' — عبد القادر العثمان',
                 englishTitle=('Tuhfat al-Atfal' if number == 44 else 'Al-Jazariyyah') + ' commentary — Abdul Qadir al-Othman',
                 scholarId=scholar['id'], bookIds=[book_id], sourceUrl=url, coverage='partial', expectedEpisodes=None,
                 coverageNote=f'فهرسة {len(rows)} حلقات ظاهرة في صفحة مؤسسة تاج. لا تثبت اكتمال شرح المتن أو مطابقة طبعة المكتبة وصفحاتها. الفيديو يفتح من صفحة المؤسسة الأصلية.',
                 englishCoverageNote=f'{len(rows)} episodes indexed from the Taj Foundation page. This does not establish complete text coverage or matching library edition/page numbers. Videos open on the original institutional page.')
    catalog['series'] = [s for s in catalog['series'] if s['id'] != series_id] + [group]
    audit['seriesEvidence'] = [s for s in audit['seriesEvidence'] if s['seriesId'] != series_id] + [dict(seriesId=series_id, sourceUrl=url,
        httpStatus=200, pageSha256=digest(raw), coverage='partial', catalogEpisodeNumbers=[r[0] for r in rows],
        sourceStatus='INDEXED_ONLY', listedEpisodeCount=len(rows), nextPage=None, reviewedOn=DATE)]

    def review(row):
        episode, title, page_url, embed = row
        page = fetch(page_url)
        assert embed in page.decode('utf-8') and title in html.unescape(re.sub('<[^>]+>', '', page.decode('utf-8')))
        name = page_url.split('=')[-1]
        proof = save(f'taj-episode-{name}.html', page)
        return row, page, proof

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        reviewed = list(pool.map(review, rows))
    for (episode, title, page_url, embed), page, proof in reviewed:
        evidence.append(proof)
        identifier = series_id + '-' + str(episode)
        item = dict(id=identifier, scholarId=scholar['id'], title=title, englishTitle=group['englishTitle'] + f' episode {episode}',
            type='lesson', mediaKind='video', bookIds=[book_id], url=page_url, seriesId=series_id,
            seriesTitle=group['title'], episodeNumber=episode, topics=['التجويد'], englishTopics=['Tajweed'], verifiedOn=DATE,
            sourceNote='عنوان الحلقة ونسبتها ومشغلها في موقع مؤسسة تاج. ربط بأصل المتن دون مطابقة طبعة أو توقيت أو صفحة. تشغيل خارجي فقط؛ لا تنزيل أو إعادة رفع للفيديو، ولا ادعاء مراجعة جميع دقائق الدرس.',
            englishSourceNote='Title, teacher and player verified on the Taj Foundation website. Linked to the underlying text, without edition, timestamp or page synchronization. External playback only; no video downloading or rehosting, and no claim of reviewing every minute.')
        catalog['items'] = [i for i in catalog['items'] if i['id'] != identifier] + [item]
        audit['items'] = [i for i in audit['items'] if i['itemId'] != identifier] + [dict(itemId=identifier, url=page_url, httpStatus=200,
            pageSha256=digest(page), mediaElementPresent=True, reviewedOn=DATE, embedUrl=embed)]

source = 'https://islamhouse.com/ar/audios/174077/'
source_bytes = fetch(source)
evidence.append(save('islamhouse-174077.html', source_bytes))
policy_url = 'https://d1.islamhouse.com/html/faq.htm'
policy = fetch(policy_url)
evidence.append(save('islamhouse-permission.html', policy))
assert 'أحمد عامر' in source_bytes.decode('utf-8')
base = 'https://d1.islamhouse.com/data/ar/ih_sounds/chain_01/Ta3lim_Tajweed/'
specs = [
    ('fatiha', 2, 'قراءة الفاتحة وأول سورة البقرة', 'Al-Fatihah and the beginning of Al-Baqarah', [], 1, 1),
    ('stopping', 3, 'الوقف والابتداء — تطبيقات من البقرة', 'Stopping and starting — Al-Baqarah practice', ['stopping'], None, None),
    ('idgham', 4, 'الإدغام — تطبيقات من البقرة', 'Idgham — Al-Baqarah practice', ['noon'], 2, 19),
    ('izhar', 6, 'الإظهار — تطبيقات من البقرة', 'Izhar — Al-Baqarah practice', ['noon'], 2, 26),
    ('ikhfa', 7, 'الإخفاء — تطبيقات من البقرة', 'Ikhfa — Al-Baqarah practice', ['noon'], 2, 32)
]
audio = []
for key, number, title, english_title, topics, surah, ayah in specs:
    url = base + f'ar_{number:02d}_Ta3lim_Tajweed.mp3'
    assert url in source_bytes.decode('utf-8')
    probes = []
    total = None
    for offset in [0, None, -1]:
        start = 0 if offset == 0 else total // 2 if offset is None else total - 4096
        request = urllib.request.Request(url, headers={**HEADERS, 'Range': f'bytes={start}-{start+4095}', 'Accept-Encoding': 'identity'})
        with urllib.request.urlopen(request, timeout=30) as response:
            assert response.status == 206 and response.url == url
            content_range = response.headers['Content-Range']
            size = int(content_range.split('/')[-1])
            assert total is None or total == size
            total = size
            assert content_range.startswith(f'bytes {start}-{start+4095}/')
            data = response.read(4097)
            assert len(data) == 4096 and response.headers.get_content_type() == 'audio/mpeg'
            probes.append(dict(offset=start, bytes=len(data), sha256=digest(data), contentRange=content_range, httpStatus=206))
    audio.append(dict(id='amer-' + key, sourceEpisode=number, title=title, englishTitle=english_title,
        speaker='أحمد عامر', englishSpeaker='Ahmed Amer', topicIds=topics, bookIds=['jazariyyah'] if key in ('fatiha','stopping') else ['tuhfat-atfal','jazariyyah'],
        sourceUrl=source, sourcePageSha256=digest(source_bytes), url=url, bytes=total, mimeType='audio/mpeg', probes=probes,
        quranStart={'surah':surah,'ayah':ayah} if surah else None,
        quranLinkBasis='بداية التطبيق بحسب عنوان المصدر؛ لا توقيت محدد ولا ادعاء وجود الحكم في كل كلمة من الآية.' if surah else None,
        riwayaStatus='not-stated-by-source', kind='lesson', downloadAllowed=True, commercialUseAllowed=False, reviewedOn=DATE))

audit['reviewedOn'] = DATE
audit['scope'] = 'مراجعة صفحات المصادر والعناوين والنسبة وعناصر الوسائط وفهارس السلاسل؛ أضيفت 0.14.0 من مصادر مؤسسة تاج. لم تتم مراجعة التسجيلات كاملة سماعًا أو مشاهدة، ولا مطابقة زمنية أو لطبعات الكتب.'
audit['delivery'] = 'هذا فهرس روابط صفحات المصادر الأصلية، مع فتح مشغلاتها خارجيًا دون تنزيل الفيديو أو إعادة رفعه. الدروس الصوتية التي يشغلها التطبيق مسجلة في فهرس content/tajweed-media المستقل.'
audit['summary'].update(scholars=4, items=52, lessons=37, series=5, newItemsIn014=19)
audit['identityEvidence'] = [r for r in audit['identityEvidence'] if r['scholarId'] != scholar['id']] + [dict(scholarId=scholar['id'], url=scholar['verificationUrl'], observedText='يسمي فهرس مؤسسة تاج مقدم حلقات الجزرية بالشيخ عبد القادر العثمان، ويربط المشغلات وصفحات الحلقات الأصلية.')]
audit['bookLinkEvidence'] = [r for r in audit['bookLinkEvidence'] if r['bookId'] not in ['tuhfat-atfal','jazariyyah']] + [dict(bookId=book, seriesUrl=url, relationship='شرح لأصل المتن من فهرس مؤسسة تاج؛ لا مطابقة مثبتة لطبعة المكتبة أو صفحاتها، ولا مزامنة بالتوقيت.') for book,url in [('tuhfat-atfal','https://qurantaj.com/media/article/44'),('jazariyyah','https://qurantaj.com/media/article/41')]]

for name, value in [('content/media/catalog.json',catalog), ('content/media/audit.json',audit)]:
    (ROOT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(OUT/'catalog.json').write_text(json.dumps(dict(audio=audio,evidence=evidence,licenseUrl=policy_url,licensePageSha256=digest(policy),
    reviewScope='Official index identity, titles, embedded players and HTTP range samples. Full recordings have not been listened through. No timestamp-level or edition-level alignment. Source does not state a recitation riwaya.'),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'PASS: 19 official video pages; {len(audio)} full practice recordings with first/middle/tail probes; no media rehosting.')
