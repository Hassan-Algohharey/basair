"""Check locale coverage and formatting contracts without translating source material."""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"

def read_locale(directory):
    result = {}
    for file in sorted((RES / directory).glob("*.xml")):
        for item in ET.parse(file).getroot().findall("string"):
            key = item.attrib["name"]
            assert key not in result, (directory, key, "duplicate")
            result[key] = "".join(item.itertext())
            assert result[key].strip(), (directory, key, "empty")
    return result

arabic, english = read_locale("values"), read_locale("values-en")
assert arabic.keys() == english.keys(), (arabic.keys() - english.keys(), english.keys() - arabic.keys())
for key in arabic:
    def placeholders(value):
        return sorted(re.findall(r"%\d+\$[sd]", value))
    assert placeholders(arabic[key]) == placeholders(english[key]), key
    for value in (arabic[key], english[key]):
        assert "%" not in re.sub(r"%\d+\$[sd]|%%", "", value), (key, "invalid format")
for file in (ROOT / "app/src/main/java").rglob("*.kt"):
    for key in re.findall(r"R\.string\.(\w+)", file.read_text(encoding="utf-8")):
        assert key in arabic, (file.name, key, "missing resource")
print(f"PASS: {len(arabic)} Arabic/English resources; matching format parameters and no missing references.")
