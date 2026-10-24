import hashlib
import json
import urllib.request
from pathlib import Path
from pypdf import PdfReader

for volume in [31, 43]:
    url = f'https://d1.islamhouse.com/data/ar/ih_books/chain/ar_encyclopedia_fiqhiyyah_kuwait/ar_{volume:02d}_encyclopedia_fiqhiyyah_kuwait.pdf'
    path = Path(f'content/incoming/kuwait-{volume}.pdf')
    if not path.exists():
        request = urllib.request.Request(url, headers={'User-Agent': 'BasairLibrary/0.2'})
        with urllib.request.urlopen(request, timeout=60) as response:
            data = response.read()
        if not data.startswith(b'%PDF'): raise ValueError('Not a PDF')
        path.write_bytes(data)
    print(json.dumps({'volume': volume, 'pages': len(PdfReader(path).pages), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}), flush=True)
