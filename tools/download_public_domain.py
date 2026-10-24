import hashlib
import json
import urllib.parse
import urllib.request
from pathlib import Path
from pypdf import PdfReader

files = {
    'sirah-public': 'https://upload.wikimedia.org/wikisource/ar/f/f6/مختصر_سيرة_الرسول.pdf',
    'tafsir-public': 'https://upload.wikimedia.org/wikisource/ar/2/2f/تفسير_الجلالين_مع_الكمالين.pdf'
}
for identifier, raw_url in files.items():
    url = urllib.parse.quote(raw_url, safe=':/')
    path = Path(f'content/incoming/{identifier}.pdf')
    if not path.exists():
        request = urllib.request.Request(url, headers={'User-Agent': 'BasairLibrary/0.2 source-verification'})
        with urllib.request.urlopen(request, timeout=90) as response:
            data = response.read()
        if not data.startswith(b'%PDF'): raise ValueError('Not a PDF')
        path.write_bytes(data)
    print(json.dumps({'id': identifier, 'pages': len(PdfReader(path).pages), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}), flush=True)
