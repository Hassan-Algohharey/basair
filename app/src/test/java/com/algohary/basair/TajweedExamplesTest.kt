package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class TajweedExamplesTest {
    @Test fun examplesUseReviewedVerseBoundariesAndPreserveOriginalSourceIdentity() {
        assertEquals(6, TajweedExamples.examples.size)
        assertEquals(setOf("meem", "ghunnah", "madd", "articulation", "noon", "lam"), TajweedExamples.examples.map { it.topicId }.toSet())
        val anchors = mapOf("meem" to (105 to 4), "ghunnah" to (108 to 1), "madd" to (114 to 1),
            "articulation" to (114 to 1), "noon" to (112 to 4), "lam" to (114 to 1))
        TajweedExamples.examples.forEach { e ->
            assertEquals(anchors[e.id], e.surah to e.ayah)
            assertTrue(e.topic.readings.isNotEmpty())
            assertTrue(e.topic.readings.all { it.chapter.pdfPage in 1..it.book.pages })
            val track = e.recording.tracks.single()
            assertEquals(e.recording, audioRecording(track.id))
            assertEquals("verse-example", e.recording.coverage)
            assertTrue(track.durationMs!! in 4_000L..8_000L)
            assertEquals(track.clipEndMs!! - track.clipStartMs, track.durationMs)
            val bounds = mapOf(105 to (24847L to 32707L), 108 to (6024L to 12644L),
                112 to (16553L to 20953L), 114 to (6017L to 11327L))
            assertEquals(bounds[e.surah], track.clipStartMs to track.clipEndMs)
            val source = TajweedExamples.sourceRecordings.first { it.id == "huthify-${e.surah}" }.tracks.single()
            assertEquals(source.url, track.url)
            assertEquals(source.sha256, track.sha256)
            assertEquals(source.byteSize, track.byteSize)
            assertTrue(track.clipEndMs!! <= source.durationMs!!)
            assertNotEquals(source.id, track.id)
            assertEquals("download.qurancomplex.gov.sa", java.net.URI(track.url).host)
            assertTrue(e.recording.downloadAllowed)
            assertEquals("https://qurancomplex.gov.sa/quran-audios/", e.recording.licenseUrl)
        }
        assertEquals(6, TajweedExamples.recordings.size)
        assertEquals(4, TajweedExamples.sourceRecordings.size)
    }

    @Test fun bilingualDiacriticInsensitiveSearchKeepsOldLessonResultsIndependent() {
        assertEquals(6, searchTajweedExamples("حَفْص الحذيفي").size)
        assertEquals(listOf("meem"), searchTajweedExamples("meem ikhfa").map { it.id })
        assertEquals(listOf("madd"), searchTajweedExamples("المد الطبيعي").map { it.id })
        assertTrue(searchTajweedExamples("unknown-example").isEmpty())
        assertEquals(6, searchAudiobooks("Hafs").size)
        assertEquals(3, searchAudiobooks("An-Nas").size)
        assertEquals(3, searchTajweedExamples("الناس").size)
        assertTrue(searchAudiobooks("").none { it.id in TajweedExamples.sourceRecordings.map { s -> s.id } })
        assertEquals(3, searchAudiobooks("audiobooks").size)
        assertEquals(5, searchTajweedAudio("Ahmed").size)
    }

    @Test fun fullLengthReadingsAndLessonsKeepUnclippedPlaybackAndSavedSourceIdsStillResolve() {
        (AudiobookCatalog.recordings + TajweedAudioCatalog.recordings + TajweedExamples.sourceRecordings).forEach { recording ->
            recording.tracks.forEach { track ->
                assertEquals(0L, track.clipStartMs)
                assertNull(track.clipEndMs)
                assertEquals(track, audioTrack(track.id))
            }
        }
        assertEquals(allAudioRecordings.flatMap { it.tracks }.size,
            allAudioRecordings.flatMap { it.tracks }.map { it.id }.distinct().size)
        val track = TajweedExamples.recordings.first().tracks.single()
        assertEquals(track.durationMs, audioSeekPosition(Long.MAX_VALUE, track.durationMs))
        assertEquals(0L, audioResumePosition(track.durationMs!!, track.durationMs))
    }
}
