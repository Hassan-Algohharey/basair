package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class AudiobookTest {
    @Test fun readingsHaveSourcesRightsAndActualLibraryLinks() {
        assertEquals(3, AudiobookCatalog.recordings.size)
        assertEquals(setOf("tahawiyyah", "nawawi-fifty", "tuhfat-atfal"), AudiobookCatalog.recordings.flatMap { it.bookIds }.toSet())
        val ids = mutableSetOf<String>()
        AudiobookCatalog.recordings.forEach { recording ->
            assertTrue(recording.author.isNotBlank() && recording.reader.isNotBlank())
            assertTrue(recording.rightsNote.isNotBlank() && recording.englishRightsNote.isNotBlank())
            assertEquals("complete-as-listed", recording.coverage)
            assertTrue(recording.downloadAllowed)
            assertEquals("islamhouse.com", java.net.URI(recording.sourceUrl).host)
            assertEquals("https://d1.islamhouse.com/html/faq.htm", recording.licenseUrl)
            assertEquals((1..recording.tracks.size).toList(), recording.tracks.map { it.sequence })
            recording.tracks.forEach { track ->
                assertTrue(ids.add(track.id))
                assertTrue(recording.bookIds.all { id -> Catalog.books.any { it.id == id } })
                assertEquals("https", java.net.URI(track.url).scheme)
                assertEquals("d1.islamhouse.com", java.net.URI(track.url).host)
                assertEquals("audio/mpeg", track.mimeType)
                assertTrue(track.byteSize > 0)
                assertEquals(track, audioTrack(track.id))
                assertEquals(recording, audioRecording(track.id))
            }
        }
        assertNull(audioTrack("unknown"))
        assertNull(audioRecording("unknown"))
    }

    @Test fun searchCombinesReaderTitleBookAndEveryWord() {
        assertEquals(3, searchAudiobooks("audiobooks").size)
        assertEquals(1, searchAudiobooks("salama tahawiyyah").size)
        assertEquals(1, searchAudiobooks("alansari").size)
        assertEquals(1, searchAudiobooks("إبراهيم النَّوَوِيَّة").size)
        assertTrue(searchAudiobooks("salama nonexistent").isEmpty())
        assertTrue(searchAudiobooks("salama", "nawawi-fifty").isEmpty())
        assertEquals(1, searchAudiobooks("", "nawawi-fifty").size)
    }

    @Test fun positionsClampAndCompletedReadingsRestartOnExplicitPlay() {
        assertEquals(0L, audioSeekPosition(-100, 1000))
        assertEquals(1000L, audioSeekPosition(2000, 1000))
        assertEquals(2000L, audioSeekPosition(2000, null))
        assertEquals(30_000L, audioResumePosition(30_000, 300_000))
        assertEquals(0L, audioResumePosition(299_500, 300_000))
        assertEquals(30_000L, audioResumePosition(30_000, null))
    }

    @Test fun speedAndTimeHandleInvalidAndLargeValues() {
        assertEquals(1f, audioPlaybackSpeed(Float.NaN))
        assertEquals(1f, audioPlaybackSpeed(Float.POSITIVE_INFINITY))
        assertEquals(.5f, audioPlaybackSpeed(.1f))
        assertEquals(2f, audioPlaybackSpeed(10f))
        assertEquals(1.25f, audioPlaybackSpeed(1.25f))
        assertEquals("00:00:00", audioTime(-50))
        assertEquals("01:02:03", audioTime(3_723_000))
    }

    @Test fun resumingDownloadsRequiresExactOffsetAndWholeSize() {
        assertTrue(validAudioContentRange("bytes 4096-9999/10000", 4096, 10000))
        assertTrue(validAudioContentRange("bytes 0-9999/10000", 0, 10000))
        assertFalse(validAudioContentRange("bytes 0-9999/10000", 4096, 10000))
        assertFalse(validAudioContentRange("bytes 4096-10000/10000", 4096, 10000))
        assertFalse(validAudioContentRange("bytes 4096-9999/20000", 4096, 10000))
        assertFalse(validAudioContentRange("bytes */10000", 4096, 10000))
        assertFalse(validAudioContentRange(null, 0, 10000))
    }
}
