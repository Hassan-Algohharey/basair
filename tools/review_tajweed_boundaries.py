"""Compare official per-ayah audio with official surah audio; never infer word timestamps.

Dev dependencies: numpy, rarfile, imageio-ffmpeg (optional --module-dir for local tools).
Only 29 archive header bytes and the final 4 MiB are fetched. No audio is published.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys
import urllib.request
import zlib

parser = argparse.ArgumentParser()
parser.add_argument('--work-dir', type=Path, required=True)
parser.add_argument('--module-dir', type=Path)
parser.add_argument('--offline', action='store_true')
args = parser.parse_args()
if args.module_dir:
    sys.path.insert(0, str(args.module_dir.resolve()))
import numpy as np
import rarfile
import imageio_ffmpeg

root = Path(__file__).resolve().parents[1]
work = args.work_dir
work.mkdir(parents=True, exist_ok=True)
archive_url = 'https://download.qurancomplex.gov.sa/new-sounds/huthify-01/hafs/huthify-ayat.rar'
total = 842634506
tail_start = total - 4 * 1024 * 1024

def fetch_range(start, end, name):
    path = work/name
    if not args.offline:
        request = urllib.request.Request(archive_url, headers={'Range': f'bytes={start}-{end}', 'Accept-Encoding': 'identity'})
        with urllib.request.urlopen(request, timeout=60) as response:
            assert response.status == 206
            assert response.headers['Content-Range'] == f'bytes {start}-{end}/{total}'
            path.write_bytes(response.read())
    raw = path.read_bytes()
    assert len(raw) == end-start+1
    return raw

head = fetch_range(0, 28, 'archive-main.bin')
tail = fetch_range(tail_start, total-1, 'archive-tail.bin')
assert head[:8] == b'Rar!\x1a\x07\x01\x00'

def vint(position):
    value = shift = 0
    for index in range(position, min(position+10, len(tail))):
        value |= (tail[index] & 127) << shift
        if tail[index] < 128:
            return value, index+1
        shift += 7
    raise ValueError('Invalid archive header')

# Locate a genuine independent file header by header CRC, then parse the suffix normally.
first = None
position = tail.find(b'10-')
while position >= 0 and first is None:
    for candidate in range(max(0, position-180), position):
        try:
            size, end = vint(candidate+4)
            if 10 <= size <= 500 and end+size <= len(tail) and zlib.crc32(tail[candidate+4:end+size]) == struct.unpack_from('<I', tail, candidate)[0]:
                first = candidate
                break
        except (ValueError, struct.error):
            continue
    position = tail.find(b'10-', position+3)
assert first is not None
suffix = work/'ayah-suffix.rar'
suffix.write_bytes(head + tail[first:])
rar = rarfile.RarFile(suffix)
catalog = json.loads((root/'content/tajweed-examples/catalog.json').read_text('utf-8'))
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()

def pcm(path):
    result = subprocess.run([ffmpeg, '-v', 'error', '-i', str(path), '-ac', '1', '-ar', '16000', '-f', 's16le', '-'], capture_output=True, check=True)
    return np.frombuffer(result.stdout, dtype='<i2').astype(float)

rows = []
for key in ['105004', '108001', '112004', '114001']:
    surah, ayah = int(key[:3]), int(key[3:])
    info = next(item for item in rar.infolist() if item.filename.endswith(f'10-{key}-A01.mp3'))
    assert info.file_compress_flags & 64 == 0, 'Solid archives cannot be extracted independently'
    original = subprocess.run(['tar', '-xOf', str(suffix), info.filename], capture_output=True, check=True).stdout
    assert len(original) == info.file_size and zlib.crc32(original) == info.CRC
    reference_path = work/f'ayah-{key}.mp3'
    reference_path.write_bytes(original)
    source = next(r for r in catalog['recordings'] if r['surah'] == surah)
    source_path = work/f'{surah}.mp3'
    if not args.offline:
        with urllib.request.urlopen(source['url'], timeout=60) as response:
            source_path.write_bytes(response.read())
    assert hashlib.sha256(source_path.read_bytes()).hexdigest() == source['sha256']
    reference, full = pcm(reference_path), pcm(source_path)
    # Match a central 2 s fingerprint, keeping the integer sample offset throughout.
    center = max(0, len(reference)//2-16000)
    fingerprint = reference[center:center+32000]
    fft_size = 1 << (len(full)+len(fingerprint)-1).bit_length()
    correlation = np.fft.irfft(np.fft.rfft(full, fft_size)*np.conj(np.fft.rfft(fingerprint, fft_size)), fft_size)
    offset = int(np.argmax(correlation[:len(full)-len(fingerprint)+1]))-center
    assert offset >= 0
    # Verify every voiced half-second window, not just the matched fingerprint.
    checks = []
    for index in range(0, len(reference)-8000, 8000):
        segment = reference[index:index+8000]
        if np.sqrt(np.mean(segment**2)) < 500:
            continue
        match = full[offset+index:offset+index+8000]
        score = float(np.corrcoef(segment, match)[0, 1])
        assert score > .90, (key, index/16000, score)
        checks.append(round(score, 6))
    assert len(checks) >= 6
    # Reference file padding is not an ayah boundary in continuous surah audio.
    # Retain 200 ms before/after the reference's audible envelope; never include its 2–3 s padding.
    count = len(reference)//160
    rms = np.sqrt(np.mean(reference[:count*160].reshape(count, 160)**2, axis=1))
    voiced = np.where(rms >= 25)[0]
    start = round(offset/16 + int(voiced[0])*10 - 200)
    end = round(offset/16 + (int(voiced[-1])+1)*10 + 200)
    assert 0 <= start < end <= source['durationMs']
    rows.append({'surah': surah, 'ayah': ayah, 'archiveMember': info.filename,
        'archiveMemberCrc32': f'{info.CRC:08x}', 'referenceBytes': len(original),
        'referenceSha256': hashlib.sha256(original).hexdigest(), 'sourceSha256': source['sha256'],
        'referenceOffsetSamples16k': offset, 'referenceDurationSamples16k': len(reference),
        'voicedWindowCorrelations': checks, 'startMs': start, 'endMs': end,
        'scope': 'complete-ayah-context-not-isolated-word'})
    print(f'PASS {surah}:{ayah}: {start}–{end} ms; {len(checks)} voiced windows; min correlation {min(checks):.4f}')

report = {'reviewedOn': '2026-10-25', 'method': 'official-per-ayah-source-and-waveform-comparison',
    'specialistListeningReview': False, 'wordLevelTiming': False,
    'archiveUrl': archive_url, 'archiveWholeHashVerified': False,
    'archiveBytes': total, 'tailStart': tail_start,
    'mainHeaderSha256': hashlib.sha256(head).hexdigest(), 'tailSha256': hashlib.sha256(tail).hexdigest(),
    'sampleRate': 16000, 'correlationWindowMs': 500, 'correlationWindowMinimumRms': 500,
    'envelopeWindowMs': 10, 'envelopeThresholdPcm16Rms': 25, 'contextMarginMs': 200,
    'verses': rows}
(work/'boundary-review.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
