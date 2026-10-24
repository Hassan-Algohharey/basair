"""Read-only source discovery; PDFs are never rewritten."""
import json
import urllib.request
import sys
from html.parser import HTMLParser
from pathlib import Path

class Links(HTMLParser):
    def __init__(self):
        super().__init__(); self.links = []
    def handle_starttag(self, tag, attrs):
        if tag == 'a':
            href = dict(attrs).get('href', '')
            if '.pdf' in href.lower() or '.docx' in href.lower(): self.links.append(href)

if __name__ == '__main__':
    for identifier in (sys.argv[1:] or ['203277', '1899', '5271', '2459', '2422']):
        url = f'https://islamhouse.com/ar/books/{identifier}/'
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'BasairLibrary/0.2 (source-verification)'})
            html = urllib.request.urlopen(request, timeout=40).read().decode('utf-8')
            target = Path('content/sources'); target.mkdir(parents=True, exist_ok=True)
            (target / f'islamhouse-{identifier}.html').write_text(html, encoding='utf-8')
            parser = Links(); parser.feed(html)
            print(json.dumps({'id': identifier, 'files': list(dict.fromkeys(parser.links))}, ensure_ascii=False))
        except Exception as error:
            print(json.dumps({'id': identifier, 'error': str(error)}))
