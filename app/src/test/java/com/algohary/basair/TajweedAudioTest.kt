package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class TajweedAudioTest {
    @Test fun practiceRecordingsResolveThroughTheCanonicalPlaybackRegistry() {
        assertEquals(5, TajweedAudioCatalog.lessons.size)
        assertEquals(17, allAudioRecordings.size)
        assertEquals(allAudioRecordings.size, allAudioRecordings.map { it.id }.distinct().size)
        TajweedAudioCatalog.lessons.forEach { lesson ->
            val recording = audioRecording(lesson.id + "-full")!!
            assertEquals(lesson.id, recording.id)
            assertEquals("lesson", recording.kind)
            assertEquals("partial", recording.coverage)
            assertTrue(recording.downloadAllowed)
            assertEquals(1, recording.tracks.size)
            assertEquals(recording.tracks.single(), audioTrack(lesson.id + "-full"))
            assertTrue(recording.tracks.single().byteSize > 6_000_000)
            assertEquals("d1.islamhouse.com", java.net.URI(recording.tracks.single().url).host)
            assertTrue(lesson.readings.all { it.bookId in lesson.bookIds && it.chapter.pdfPage in 1..it.book.pages })
        }
        assertNull(audioRecording("amer-unknown-full"))
    }

    @Test fun quranAnchorsAreOnlyTheStartsExplicitlyIndexedByTheSource() {
        val targets = TajweedAudioCatalog.lessons.filter { it.surah != null }.associate { it.id to (it.surah to it.ayah) }
        assertEquals(mapOf("amer-fatiha" to (1 to 1), "amer-idgham" to (2 to 19),
            "amer-izhar" to (2 to 26), "amer-ikhfa" to (2 to 32)), targets)
        assertNull(TajweedAudioCatalog.lessons.first { it.id == "amer-stopping" }.ayah)
        assertEquals(32, TajweedAudioCatalog.lessons.first { it.id == "amer-stopping" }.readings.single().chapter.pdfPage)
        assertTrue(TajweedAudioCatalog.lessons.filter { "noon" in it.topicIds }.all {
            it.readings.map { r -> r.bookId }.toSet() == setOf("tuhfat-atfal", "jazariyyah")
        })
    }

    @Test fun teacherAndRuleSearchCombinesTermsWithoutChangingBookReadingSearch() {
        assertEquals(5, searchTajweedAudio("أَحْمَد عامر").size)
        assertEquals(listOf("amer-idgham"), searchTajweedAudio("Ahmed idgham").map { it.id })
        assertTrue(searchTajweedAudio("stopping tanween").isEmpty())
        assertEquals(5, searchAudiobooks("Ahmed Amer").size)
        assertEquals(2, searchAudiobooks("audiobooks").size)
        assertEquals(19, filteredMediaItems(MediaUiState(scholars = MediaCatalog.scholars,
            items = MediaCatalog.items, query = "Abdul Qadir")).size)
    }
}
