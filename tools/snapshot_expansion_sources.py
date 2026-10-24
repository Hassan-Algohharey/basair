"""Preserve the primary source rights pages reviewed for v0.4."""
import urllib.parse
import urllib.request
from pathlib import Path

root = Path(__file__).resolve().parent.parent / 'content/sources'
for name, url in [
    ('wikisource-safwa1.html', 'https://ar.wikisource.org/w/index.php?title=ملف:صفة_الصفوة1.pdf&oldid=115074'),
    ('wikisource-safwa2.html', 'https://ar.wikisource.org/w/index.php?title=ملف:صفة_الصفوة2.pdf&oldid=115078'),
    ('islamhouse-permission-v04.html', 'https://d1.islamhouse.com/html/faq.htm'),
]:
    request = urllib.request.Request(urllib.parse.quote(url, safe=':/?=&%'), headers={'User-Agent': 'BasairLibrary/0.4 source-verification'})
    data = urllib.request.urlopen(request, timeout=50).read()
    (root / name).write_bytes(data)
    print(name, len(data))
