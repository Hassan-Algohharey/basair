"""Download publisher-supplied originals for inspection, without modifying PDFs."""
import hashlib
import json
import urllib.request
from pathlib import Path
from pypdf import PdfReader

FILES = {
    'fiqh': 'https://d1.islamhouse.com/data/ar/ih_books/single_01/ar_The_Facilitated_Fiqh.pdf',
    'aqidah': 'https://d1.islamhouse.com/data/ar/ih_books/single_010/ar-alaqidah-altahaweah.pdf',
    'hadith': 'https://d1.islamhouse.com/data/ar/ih_books/parts/Forty_Nawawi_Hadith/ar_Forty_Nawawi_Hadith_alQasim.pdf',
    'sirah': 'https://d1.islamhouse.com/data/ar/ih_books/single_01/ar_Fusol_in_Summary_of_Biography_of_the_Rasool.pdf',
    'tafsir': 'https://d1.islamhouse.com/data/ar/ih_books/single-03/ar-Tafseer-Alsedi.pdf',
}

if __name__ == '__main__':
    target = Path('content/incoming'); target.mkdir(parents=True, exist_ok=True)
    for key, url in FILES.items():
        path = target / f'{key}.pdf'
        if not path.exists():
            request = urllib.request.Request(url, headers={'User-Agent': 'BasairLibrary/0.2'})
            with urllib.request.urlopen(request, timeout=90) as response, path.open('wb') as out:
                while block := response.read(1024 * 1024): out.write(block)
        reader = PdfReader(path)
        text = '\n'.join((page.extract_text() or '') for page in reader.pages[:5])
        (target / f'{key}-first-pages.txt').write_text(text, encoding='utf-8')
        print(json.dumps({'id': key, 'pages': len(reader.pages), 'bytes': path.stat().st_size,
                          'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'openingTextChars': len(text)}, ensure_ascii=False), flush=True)
    request = urllib.request.Request('https://d1.islamhouse.com/html/faq.htm', headers={'User-Agent': 'BasairLibrary/0.2'})
    Path('content/sources/islamhouse-permission.html').write_bytes(urllib.request.urlopen(request, timeout=30).read())
