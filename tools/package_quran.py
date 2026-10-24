"""Package the official KFGQPC Hafs/Muyassar 3.0 dataset without text changes.

The original archive is a local build input, never an APK asset. Only the
navigation index and unmodified, licensed font files are bundled in the APK.
Run with Python's standard library; no OCR, Unicode normalization or rewriting.
"""
import csv
import hashlib
import io
import json
import re
import struct
import zipfile
from bisect import bisect_right
from collections import defaultdict
from html.parser import HTMLParser
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "content/incoming/hafs_tafseerMouaser_v3.zip"
OFFICIAL = "https://qurancomplex.gov.sa/quran-dev/"
DOWNLOAD = "https://download.qurancomplex.gov.sa/resources_dev/hafs_tafseerMouaser_v3.zip"
EXPECTED_MD5 = "b38703983d438a5cd22269b746eaac0c"
PREFIX = "hafs_tafseerMouaser_v3_data/"
OUT = ROOT / "content/quran"
ASSETS = ROOT / "app/src/main/assets"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    data = (json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
    path.write_bytes(data)
    return data


class PlainText(HTMLParser):
    def __init__(self, value):
        super().__init__(convert_charrefs=True)
        self.parts = []
        self.feed(value)

    def handle_data(self, value):
        self.parts.append(value)


def font_notices(data):
    """Read standard public copyright/license name records; never alter font."""
    count = struct.unpack_from(">H", data, 4)[0]
    tables = {data[12 + i * 16:16 + i * 16]: struct.unpack_from(">II", data, 20 + i * 16) for i in range(count)}
    offset, _ = tables[b"name"]
    _, count, strings = struct.unpack_from(">HHH", data, offset)
    records = []
    for i in range(count):
        platform, _, _, name_id, length, position = struct.unpack_from(">HHHHHH", data, offset + 6 + 12 * i)
        if name_id not in (0, 13, 14):
            continue
        raw = data[offset + strings + position:offset + strings + position + length]
        text = raw.decode("utf-16-be" if platform in (0, 3) else "latin1")
        if text not in records:
            records.append(text)
    assert any("Use, Copy, Distribute" in text for text in records), "Missing font redistribution permission"
    return "\n\n".join(records)


def main():
    archive = SOURCE.read_bytes()
    assert hashlib.md5(archive).hexdigest() == EXPECTED_MD5, "Official published archive checksum changed"
    z = zipfile.ZipFile(io.BytesIO(archive))
    csv_bytes = z.read(PREFIX + "tafseerMouaser_v03.csv")
    xml_bytes = z.read(PREFIX + "tafseerMouaser_v03.xml")
    csv_records = list(csv.DictReader(io.StringIO(csv_bytes.decode("utf-8-sig"), newline="")))
    xml_rows = list(ET.fromstring(xml_bytes))
    xml_blocks = re.findall(r"<ROW>(.*?)</ROW>", xml_bytes.decode("utf-8"), re.S)
    records = [{child.tag: child.text or "" for child in row} for row in xml_rows]
    for row, block in zip(records, xml_blocks):
        row["aya_tafseer"] = re.search(r"<aya_tafseer>(.*?)</aya_tafseer>", block, re.S).group(1)
    assert len(records) == len(csv_records) == len(xml_rows) == len(xml_blocks) == 6236
    metadata_file = OUT / "sources/tanzil-quran-data.xml"
    metadata = ET.fromstring(metadata_file.read_bytes())
    assert metadata.attrib["license"] == "cc-by"
    counts = {int(r.attrib["index"]): int(r.attrib["ayas"]) for r in metadata.find("suras")}
    assert len(counts) == 114 and sum(counts.values()) == 6236
    page_starts = [(int(r.attrib["sura"]), int(r.attrib["aya"])) for r in metadata.find("pages") if int(r.attrib["index"]) <= 604]
    juz_starts = [(int(r.attrib["sura"]), int(r.attrib["aya"])) for r in metadata.find("juzs") if int(r.attrib["index"]) <= 30]
    original_pages, original_juzs = defaultdict(list), defaultdict(list)
    by_page, by_surah, by_juz = defaultdict(list), defaultdict(list), defaultdict(list)
    keys, codepoints = [], set()
    csv_quote_issues = []
    for position, (row, xml_row, csv_row) in enumerate(zip(records, xml_rows, csv_records), 1):
        assert int(row["id"]) == position
        for field in ("id", "jozz", "page", "sura_no", "aya_no", "line_start", "line_end"):
            assert int(row[field]) == int(csv_row[field])
        for field in ("aya_text", "aya_text_emlaey"):
            assert row[field] == csv_row[field], (position, field)
        assert "".join(PlainText(row["aya_tafseer"]).parts) == "".join(xml_row.find("aya_tafseer").itertext()), position
        if row["aya_tafseer"] != csv_row["aya_tafseer"]:
            # The official CSV has unescaped double quotes in some tafsirs.
            # Use the official XML unchanged; accept only this known CSV defect.
            assert row["aya_tafseer"].replace('"', '') == csv_row["aya_tafseer"].replace('"', ''), position
            csv_quote_issues.append(position)
        assert row["aya_text"] and row["aya_tafseer"]
        s, a, original_p, original_j = (int(row[f]) for f in ("sura_no", "aya_no", "page", "jozz"))
        assert 1 <= s <= 114 and 1 <= a <= counts[s] and 1 <= original_p <= 604 and 1 <= original_j <= 30
        original_pages[original_p].append((s, a))
        original_juzs[original_j].append((s, a))
        p, j = bisect_right(page_starts, (s, a)), bisect_right(juz_starts, (s, a))
        keys.append((s, a))
        codepoints.update(ord(c) for c in row["aya_text"] + row["aya_tafseer"])
        item = {"surah": s, "ayah": a, "text": row["aya_text"], "tafsir": row["aya_tafseer"], "juz": j, "originalPage": original_p, "originalJuz": original_j}
        by_page[p].append(item)
        by_surah[s].append((row, p))
        by_juz[j].append((s, a, p))
    assert keys == [(s, a) for s in range(1, 115) for a in range(1, counts[s] + 1)], "Missing, duplicate or reordered ayah"
    assert sorted(by_page) == list(range(1, 605))
    assert sorted(by_juz) == list(range(1, 31))
    # Independent structural source: every page and juz starts at the same ayah.
    for row in metadata.find("pages"):
        p = int(row.attrib["index"])
        if p == 605:  # metadata sentinel at end of Quran
            continue
        actual = by_page[p][0]
        assert (actual["surah"], actual["ayah"]) == (int(row.attrib["sura"]), int(row.attrib["aya"])), ("page", p)
    for row in metadata.find("juzs"):
        j = int(row.attrib["index"])
        if j == 31:
            continue
        assert by_juz[j][0][:2] == (int(row.attrib["sura"]), int(row.attrib["aya"])), ("juz", j)
    source_sha = sha(xml_bytes)
    relative = f"content/quran/{source_sha[:16]}"
    specs = []
    for p in range(1, 605):
        value = {"page": p, "ayahs": by_page[p]}
        data = write_json(ROOT / relative / f"{p}.json", value)
        assert json.loads(data)["ayahs"] == by_page[p]
        first = by_page[p][0]
        specs.append({"sha256": sha(data), "bytes": len(data), "firstSurah": first["surah"], "firstAyah": first["ayah"], "juz": first["juz"]})
    # Fixed timestamps, flags and order make this archive reproducible.
    archive_path = ROOT / relative / "all.zip"
    with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as page_zip:
        for p in range(1, 605):
            entry = zipfile.ZipInfo(f"{p}.json", date_time=(1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            page_zip.writestr(entry, (ROOT / relative / f"{p}.json").read_bytes(), compresslevel=9)
    page_archive = archive_path.read_bytes()
    fonts = []
    for filename in ("uthmanic_hafs_v20.ttf", "uthman_tn1_ver20.ttf"):
        data = z.read("hafs_tafseerMouaser_v3_fonts/" + filename)
        (ASSETS / filename).write_bytes(data)
        license_name = filename.replace(".ttf", "-LICENSE.txt")
        (ASSETS / license_name).write_text(font_notices(data), encoding="utf-8")
        fonts.append({"asset": filename, "sha256": sha(data), "bytes": len(data), "licenseAsset": license_name})
    index = {
        "version": "kfgqpc-hafs-muyassar-3.0",
        "sourceTitle": "القرآن الكريم والتفسير الميسر",
        "sourceDetails": "نص مجمع الملك فهد لطباعة المصحف الشريف والتفسير الميسر 3.0 · فهرسة صفحات المدينة والأجزاء من مشروع تنزيل Tanzil · عرض نصي وليس صورة مطابقة للصفحة المطبوعة",
        "sourceUrl": OFFICIAL, "licenseUrl": OFFICIAL, "downloadUrl": DOWNLOAD,
        "fontAsset": fonts[0]["asset"], "tafsirFontAsset": fonts[1]["asset"],
        "fontLicenses": fonts, "sourceSha256": source_sha, "path": relative,
        "navigationSourceUrl": "https://tanzil.net/docs/quran_metadata",
        "navigationLicense": "CC-BY; (C) 2008-2009 Tanzil.info",
        "archive": {"path": f"{relative}/all.zip", "sha256": sha(page_archive), "bytes": len(page_archive)},
        "pages": specs,
        "surahs": [{"id": s, "name": by_surah[s][0][0]["sura_name_ar"], "englishName": by_surah[s][0][0]["sura_name_en"], "ayahCount": counts[s], "firstPage": by_surah[s][0][1], "ayahPages": [p for _, p in by_surah[s]]} for s in range(1, 115)],
        "juzPages": [by_juz[j][0][2] for j in range(1, 31)],
    }
    write_json(ASSETS / "quran-index.json", index)
    (OUT / "sources/official-read.me").write_bytes(z.read(PREFIX + "read.me"))
    audit = {
        "sourceUrl": OFFICIAL, "downloadUrl": DOWNLOAD, "archiveSha256": sha(archive),
        "archiveMd5": EXPECTED_MD5, "csvSha256": sha(csv_bytes), "xmlSha256": source_sha,
        "tanzilMetadataSha256": sha(metadata_file.read_bytes()),
        "counts": {"surahs": 114, "ayahs": 6236, "pages": 604, "juz": 30},
        "checks": ["All CSV numeric coordinates match original XML", "Every Quran text string matches original XML without normalization", "Every tafsir character matches original XML after stripping only span markup", "All 6236 ayah identities unique, complete and ordered", "All 604 navigation page starts match Tanzil", "All 30 navigation juz starts match Tanzil", "All 114 surah ayah counts independently match Tanzil", "JSON round-trip preserves every text and tafsir string", "Both font binaries copied byte-for-byte"],
        "fonts": fonts, "downloadBytes": sum(p["bytes"] for p in specs),
        "archive": index["archive"],
        "csvQuoteIssues": csv_quote_issues,
        "navigationPolicy": "Preserve every Quran/tafsir text string from official XML; independently index complete ayahs by the licensed Tanzil Medina-mushaf page and juz boundaries, rather than older package page/jozz fields.",
        "originalPageStarts": [{"page": p, "surah": original_pages[p][0][0], "ayah": original_pages[p][0][1]} for p in range(1, 605)],
        "originalJuzStarts": [{"juz": j, "surah": original_juzs[j][0][0], "ayah": original_juzs[j][0][1]} for j in range(1, 31)],
        "originalPageBoundaryDifferences": [{"page": p, "original": list(original_pages[p][0]), "navigation": list(page_starts[p-1])} for p in range(1, 605) if original_pages[p][0] != page_starts[p-1]],
        "originalJuzBoundaryDifferences": [{"juz": j, "original": list(original_juzs[j][0]), "navigation": list(juz_starts[j-1])} for j in range(1, 31) if original_juzs[j][0] != juz_starts[j-1]],
        "presentationFormCodepoints": [f"U+{c:04X}" for c in sorted(codepoints) if 0xFB50 <= c <= 0xFDFF or 0xFE70 <= c <= 0xFEFF],
        "privateUseCodepoints": [f"U+{c:04X}" for c in sorted(codepoints) if 0xE000 <= c <= 0xF8FF],
        "bismillahPolicy": "Keep only text supplied for each ayah. No automatic Bismillah insertion; the source contains it at 1:1 and inside 27:30.",
    }
    write_json(OUT / "audit.json", audit)
    print(f"Verified 114 surahs / 6236 ayahs / 604 pages / 30 juz; {audit['downloadBytes']} bytes outside APK")
    print(f"Source XML SHA-256: {source_sha}")
    print(f"Published page directory: {relative}")


if __name__ == "__main__":
    main()
