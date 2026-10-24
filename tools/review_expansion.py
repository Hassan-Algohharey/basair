"""Download candidates for review only; this does not admit books to the app."""
import concurrent.futures
import hashlib
import json
import urllib.parse
import urllib.request
from pathlib import Path
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parent.parent
FILES = {
    'four-imams': 'https://d1.islamhouse.com/data/ar/ih_books/single5/ar_Men_and_approaches_in_the_literature.pdf',
    'companions': 'https://d1.islamhouse.com/data/ar/ih_books/single_01/ar_Sur_mn_7yat_als7abh.pdf',
    'successors': 'https://d1.islamhouse.com/data/ar/ih_books/single2/ar_pictures_from_life_of_tab3een.pdf',
    'riyad': 'https://d1.islamhouse.com/data/ar/ih_books/single_01/ar_Riyad_usSaliheen.pdf',
    'tatrez': 'https://d1.islamhouse.com/data/ar/ih_books/single/ar_tatrez_reyad_salehen.pdf',
    'risalat': 'https://d1.islamhouse.com/data/ar/ih_books/single-03/ar-Risalat-ibn-Alqayyim.pdf',
    'zuhd': 'https://d1.islamhouse.com/data/ar/ih_books/single7/ar_qwasm_AzZhur_3lee_dar_AlGhrur_AzZhd.pdf',
    'safwa1': 'https://upload.wikimedia.org/wikisource/ar/e/e9/صفة_الصفوة1.pdf',
    'safwa2': 'https://upload.wikimedia.org/wikisource/ar/9/9a/صفة_الصفوة2.pdf',
    'wasael': 'https://d1.islamhouse.com/data/ar/ih_books/single_01/ar-alwasael-almofedah-llhyah-alsa3edah.pdf',
    'wasael-new': 'https://d1.islamhouse.com/data/ar/ih_books/single/ar_waseal_mofedah_new.pdf',
}

def review(item):
    name, url = item
    path = ROOT / 'content/incoming' / f'{name}.pdf'
    try:
        if not path.exists():
            request = urllib.request.Request(urllib.parse.quote(url, safe=':/%'), headers={
                'User-Agent': 'Mozilla/5.0 Basair source-verification', 'Referer': 'https://islamhouse.com/'})
            data = urllib.request.urlopen(request, timeout=90).read()
            assert data.startswith(b'%PDF'), name
            path.write_bytes(data)
        reader = PdfReader(path)
        # Extract text for review, never use this unreviewed text as app content.
        (path.with_suffix('.review.txt')).write_text('\n'.join(p.extract_text() or '' for p in reader.pages), encoding='utf-8')
        return dict(id=name, pages=len(reader.pages), bytes=path.stat().st_size,
                    sha256=hashlib.sha256(path.read_bytes()).hexdigest())
    except Exception as error:
        return dict(id=name, error=str(error))

if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(review, FILES.items()))
    (ROOT / 'content/incoming/expansion-candidates.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    for result in results: print(json.dumps(result), flush=True)
