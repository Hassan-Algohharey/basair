"""Independent read-only validation of published Quran payloads and navigation."""
import hashlib
import json
import re
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
index = json.loads((ROOT / "app/src/main/assets/quran-index.json").read_text("utf-8"))
z = zipfile.ZipFile(ROOT / "content/incoming/hafs_tafseerMouaser_v3.zip")
xml = z.read("hafs_tafseerMouaser_v3_data/tafseerMouaser_v03.xml")
assert hashlib.sha256(xml).hexdigest() == index["sourceSha256"]
rows = list(ET.fromstring(xml))
blocks = re.findall(r"<ROW>(.*?)</ROW>", xml.decode("utf-8"), re.S)
original = {
    (int(r.findtext("sura_no")), int(r.findtext("aya_no"))):
        (r.findtext("aya_text"), re.search(r"<aya_tafseer>(.*?)</aya_tafseer>", b, re.S).group(1))
    for r, b in zip(rows, blocks)
}
assert len(index["pages"]) == 604 and len(index["surahs"]) == 114 and len(index["juzPages"]) == 30
meta = ET.parse(ROOT / "content/quran/sources/tanzil-quran-data.xml").getroot()
page_starts = {int(r.attrib["index"]): (int(r.attrib["sura"]), int(r.attrib["aya"])) for r in meta.find("pages")}
juz_starts = {int(r.attrib["index"]): (int(r.attrib["sura"]), int(r.attrib["aya"])) for r in meta.find("juzs")}
visited, juz_first, verse_pages = [], {}, {}
for page, spec in enumerate(index["pages"], 1):
    data = (ROOT / index["path"] / f"{page}.json").read_bytes()
    assert len(data) == spec["bytes"] and hashlib.sha256(data).hexdigest() == spec["sha256"]
    document = json.loads(data)
    assert document["page"] == page and document["ayahs"]
    first = document["ayahs"][0]
    assert (first["surah"], first["ayah"]) == page_starts[page]
    assert (first["surah"], first["ayah"], first["juz"]) == (spec["firstSurah"], spec["firstAyah"], spec["juz"])
    for ayah in document["ayahs"]:
        key = ayah["surah"], ayah["ayah"]
        assert (ayah["text"], ayah["tafsir"]) == original[key], ("Altered source text", key)
        visited.append(key)
        verse_pages[key] = page
        juz_first.setdefault(ayah["juz"], key)
assert visited == sorted(original) and len(visited) == 6236
archive_spec = index["archive"]
archive_data = (ROOT / archive_spec["path"]).read_bytes()
assert len(archive_data) == archive_spec["bytes"] and hashlib.sha256(archive_data).hexdigest() == archive_spec["sha256"]
with zipfile.ZipFile(ROOT / archive_spec["path"]) as archive:
    expected_names = [f"{p}.json" for p in range(1, 605)]
    assert archive.namelist() == expected_names, "Missing, duplicate, reordered or extra archive entries"
    for page, entry in enumerate(archive.infolist(), 1):
        assert entry.date_time == (1980, 1, 1, 0, 0, 0) and entry.compress_type == zipfile.ZIP_DEFLATED
        data = archive.read(entry)
        spec = index["pages"][page - 1]
        assert len(data) == spec["bytes"] and hashlib.sha256(data).hexdigest() == spec["sha256"]
        assert data == (ROOT / index["path"] / entry.filename).read_bytes()
for surah in index["surahs"]:
    s = surah["id"]
    expected_count = int(meta.find("suras")[s - 1].attrib["ayas"])
    assert surah["ayahCount"] == expected_count
    assert surah["ayahPages"] == [verse_pages[s, a] for a in range(1, expected_count + 1)]
    assert surah["firstPage"] == verse_pages[s, 1]
for j in range(1, 31):
    assert juz_first[j] == juz_starts[j]
    assert index["juzPages"][j - 1] == verse_pages[juz_starts[j]]
for font in index["fontLicenses"]:
    data = (ROOT / "app/src/main/assets" / font["asset"]).read_bytes()
    assert len(data) == font["bytes"] and hashlib.sha256(data).hexdigest() == font["sha256"]
    assert data == z.read("hafs_tafseerMouaser_v3_fonts/" + font["asset"])
    assert "Use, Copy, Distribute" in (ROOT / "app/src/main/assets" / font["licenseAsset"]).read_text("utf-8")
assert not list((ROOT / "app/src/main/assets").glob("**/[0-9]*.json")), "Page bodies must stay outside APK assets"
print("Verified every source character in 6236 ayahs and tafsirs; 604 hashes and page boundaries; 114 surah mappings; 30 juz starts; both original fonts; deterministic full archive with exactly 604 unchanged page entries.")
