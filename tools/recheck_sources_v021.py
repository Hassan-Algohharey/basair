"""Retry only the seven previously rate-limited source files, with bounded reads."""
import json
from pathlib import Path
import audit_sources_v020 as transport

ROOT = Path(__file__).resolve().parents[1]
transport.CACHE = ROOT / 'tmp/v021/rechecks'
baseline = json.loads((ROOT / 'content/media/link-audit-v020.json').read_text('utf-8'))
rows = [transport.fetch(r['url'], True)[0] for r in baseline['links'] if r.get('httpStatus') == 429]
report = {'version': '0.21.0', 'checkedOn': transport.DAY,
          'scope': 'Previously rate-limited original files; bounded HTTP range, not whole-file verification', 'links': rows}
(ROOT / 'content/review/source-rechecks-v021.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', 'utf-8')
print(f"Rechecked {len(rows)}; reachable {sum(r['status'] == 'reachable' for r in rows)}.")
