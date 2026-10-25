"""Use only item dates and durations from the item's original embedded player."""
import concurrent.futures
import hashlib
import html
import json
import re
from audit_sources_v020 import ROOT, CACHE, fetch

def player_duration(page, video_id):
    for match in re.finditer(r'(?:var\s+)?ytInitialPlayerResponse\s*=\s*', page):
        try:
            obj, _ = json.JSONDecoder().raw_decode(page[match.end():])
            details = obj.get('videoDetails', {})
            seconds = int(details.get('lengthSeconds', '0'))
            if details.get('videoId') == video_id and 0 < seconds <= 86400 and not details.get('isLiveContent'):
                return {'durationSeconds': seconds, 'videoId': video_id, 'playerTitle': details.get('title'),
                        'channelId': details.get('channelId'), 'method': 'Original embedded YouTube player: videoDetails.lengthSeconds'}
        except (ValueError, TypeError):
            continue
    return None

def main():
    path = ROOT / 'content/media/link-audit-v020.json'
    report = json.loads(path.read_text('utf-8'))
    def enrich(meta):
        snapshot = CACHE / (hashlib.sha256(meta['url'].encode()).hexdigest() + '.html')
        s = snapshot.read_text('utf-8', errors='replace')
        match = re.search(r'<iframe\b[^>]*\bsrc=["\x27]https://(?:www\.)?youtube(?:-nocookie)?\.com/embed/([A-Za-z0-9_-]{11})', s, re.I)
        if match:
            video_id = match.group(1)
            probe, body = fetch('https://www.youtube.com/watch?v=' + video_id)
            meta['playerProbe'] = probe
            duration = player_duration(body.decode('utf-8', 'replace'), video_id)
            if duration:
                meta.update(duration)
        return meta
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        report['metadata'] = list(pool.map(enrich, report['metadata']))
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    catalog_path = ROOT / 'content/media/catalog.json'
    audit_path = ROOT / 'content/media/audit.json'
    catalog = json.loads(catalog_path.read_text('utf-8'))
    audit = json.loads(audit_path.read_text('utf-8'))
    for meta in report['metadata']:
        item = next(i for i in catalog['items'] if i['id'] == meta['itemId'])
        if meta.get('publishedDateLabel'):
            item['publishedDateLabel'] = meta['publishedDateLabel']
            if meta.get('publishedHijriYear'): item['publishedHijriYear'] = meta['publishedHijriYear']
            audit['dateEvidence'] = [e for e in audit.get('dateEvidence', []) if e['itemId'] != item['id']]
            audit['dateEvidence'].append({'itemId': item['id'], 'url': item['url'], 'pageSha256': meta['pageSha256'],
                                          'label': meta['publishedDateLabel'], 'hijriYear': meta.get('publishedHijriYear'),
                                          'method': meta['dateMethod'], 'reviewedOn': meta['checkedOn']})
        if meta.get('durationSeconds'):
            item['durationSeconds'] = meta['durationSeconds']
            audit['durationEvidence'] = [e for e in audit.get('durationEvidence', []) if e['itemId'] != item['id']]
            audit['durationEvidence'].append({'itemId': item['id'], 'url': item['url'], 'pageSha256': meta['pageSha256'],
                                             'durationSeconds': meta['durationSeconds'], 'method': meta['method'],
                                             'playerEvidence': meta.get('playerProbe'), 'videoId': meta.get('videoId'), 'reviewedOn': meta['checkedOn']})
    scope = ' في0.20.0 فُحصت الروابط بفحص HTTP محدود واستكملت تواريخ المواد الظاهرة ومدد المشغلات الأصلية المضمنة؛ ليس فحص مشاهدة أو سماع شاملًا. التفاصيل في link-audit-v020.json.'
    if scope not in audit['scope']: audit['scope'] += scope
    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    audit_path.write_text(json.dumps(audit, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    print(json.dumps({'dated': sum(bool(i.get('publishedDateLabel')) for i in catalog['items']),
                      'hijriDated': sum(bool(i.get('publishedHijriYear')) for i in catalog['items']),
                      'durations': sum(bool(i.get('durationSeconds')) for i in catalog['items'])}))

if __name__ == '__main__': main()
