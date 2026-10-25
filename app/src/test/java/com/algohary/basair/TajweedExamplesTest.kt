package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class TajweedExamplesTest {
    @Test fun examplesLinkToKnownTopicsVersesBooksAndCanonicalUnchangedSurahs() {
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
            assertEquals("complete-surah", e.recording.coverage)
            assertTrue(track.durationMs!! in 20_000L..60_000L)
            assertEquals("download.qurancomplex.gov.sa", java.net.URI(track.url).host)
            assertTrue(e.recording.downloadAllowed)
            assertEquals("https://qurancomplex.gov.sa/quran-audios/", e.recording.licenseUrl)
        }
        assertEquals(4, TajweedExamples.recordings.size)
    }

    @Test fun bilingualDiacriticInsensitiveSearchKeepsOldLessonResultsIndependent() {
        assertEquals(6, searchTajweedExamples("حَفْص الحذيفي").size)
        assertEquals(listOf("meem"), searchTajweedExamples("meem ikhfa").map { it.id })
        assertEquals(listOf("madd"), searchTajweedExamples("المد الطبيعي").map { it.id })
        assertTrue(searchTajweedExamples("unknown-example").isEmpty())
        assertEquals(4, searchAudiobooks("Hafs").size)
        assertEquals(2, searchAudiobooks("audiobooks").size)
        assertEquals(5, searchTajweedAudio("Ahmed").size)
    }
}
