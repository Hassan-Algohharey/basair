"""Export the current interface strings for professional language review."""
import csv
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def strings(locale):
    result = {}
    for file in sorted((ROOT / 'app/src/main/res' / locale).glob('*.xml')):
        for item in ET.parse(file).getroot().findall('string'):
            assert item.attrib['name'] not in result
            result[item.attrib['name']] = ''.join(item.itertext())
    return result
arabic, english = strings('values'), strings('values-en')
assert arabic.keys() == english.keys()
folder = ROOT / 'content/localization'
folder.mkdir(exist_ok=True)
with (folder / 'translation-review.csv').open('w', encoding='utf-8-sig', newline='') as file:
    writer = csv.writer(file)
    writer.writerow(['key', 'Arabic', 'English', 'French', 'German', 'Turkish', 'Urdu', 'Indonesian', 'review_status', 'reviewer'])
    for key in sorted(arabic):
        writer.writerow([key, arabic[key], english[key], '', '', '', '', '', 'awaiting_professional_review', ''])
print(f'Exported {len(arabic)} source strings for Arabic/English review and five future languages; no unreviewed translations enabled.')
