"""Verify a pinned public commit serves exactly the expected Quran page bytes."""
import argparse
import concurrent.futures
import hashlib
import io
import json
import re
import urllib.request
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--base", required=True, help="https://raw.githubusercontent.com/OWNER/REPO/40-hex-commit/")
parser.add_argument("--all", action="store_true", help="Verify all 604 files instead of eight boundary samples")
args = parser.parse_args()
assert re.fullmatch(r"https://raw\.githubusercontent\.com/[^/]+/[^/]+/[a-f0-9]{40}/", args.base), "Use an immutable published commit, never a branch URL"
root = Path(__file__).resolve().parents[1]
index = json.loads((root / "app/src/main/assets/quran-index.json").read_text("utf-8"))
samples = list(range(1, 605)) if args.all else [1, 2, 121, 201, 382, 532, 586, 604]


def check(page):
    spec = index["pages"][page - 1]
    url = f"{args.base}{index['path']}/{page}.json"
    with urllib.request.urlopen(url, timeout=40) as response:
        assert response.status == 200
        data = response.read(spec["bytes"] + 1)
    assert len(data) == spec["bytes"] and hashlib.sha256(data).hexdigest() == spec["sha256"], page
    body = json.loads(data)
    assert body["page"] == page and body["ayahs"][0]["surah"] == spec["firstSurah"] and body["ayahs"][0]["ayah"] == spec["firstAyah"], page
    return page


with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    verified = list(pool.map(check, samples))
archive_spec = index["archive"]
with urllib.request.urlopen(args.base + archive_spec["path"], timeout=40) as response:
    assert response.status == 200
    data = response.read(archive_spec["bytes"] + 1)
assert len(data) == archive_spec["bytes"] and hashlib.sha256(data).hexdigest() == archive_spec["sha256"]
with zipfile.ZipFile(io.BytesIO(data)) as archive:
    assert archive.namelist() == [f"{p}.json" for p in range(1, 605)]
    for page in range(1, 605):
        entry = archive.read(f"{page}.json")
        spec = index["pages"][page - 1]
        assert len(entry) == spec["bytes"] and hashlib.sha256(entry).hexdigest() == spec["sha256"]
print(f"Verified {len(verified)} hosted Quran page hashes from an immutable commit: {', '.join(map(str, verified)) if not args.all else 'all 604 pages'}")
print("Verified hosted full archive and all 604 contained page hashes.")
