"""Regression tests for extracting only the requested player's duration."""
import unittest
from enrich_media_v020 import player_duration
from audit_sources_v020 import mp3_duration

class SourceMetadataTests(unittest.TestCase):
    def test_rejects_suggested_video_and_live_duration(self):
        data = 'var ytInitialPlayerResponse = {"videoDetails":{"videoId":"requested01","lengthSeconds":"3411"}};'
        self.assertEqual(3411, player_duration(data, 'requested01')['durationSeconds'])
        self.assertIsNone(player_duration(data, 'suggested01'))
        self.assertIsNone(player_duration(data.replace('"lengthSeconds"', '"isLiveContent":true,"lengthSeconds"'), 'requested01'))

    def test_does_not_take_arbitrary_duration_elsewhere_in_html(self):
        self.assertIsNone(player_duration('<script>var recommendation={"lengthSeconds":"999"};</script>', 'requested01'))
        self.assertIsNone(player_duration('ytInitialPlayerResponse = broken;', 'requested01'))

    def test_mp3_does_not_guess_duration_from_partial_bytes(self):
        self.assertIsNone(mp3_duration(b'\xff\xfb\x90\x00' + b'\x00' * 2048))
        data = bytearray(b'\xff\xfb\x90\x00' + b'\x00' * 2048)
        data[36:48] = b'Xing\x00\x00\x00\x01\x00\x00\x03\xe8'
        self.assertEqual(26, mp3_duration(data)['durationSeconds'])
        data[40:44] = b'\x00\x00\x00\x00'
        self.assertIsNone(mp3_duration(data))

if __name__ == '__main__': unittest.main()
