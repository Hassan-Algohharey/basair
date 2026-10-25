package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class AudioStudyToolsTest {
    @Test fun timerAcceptsArabicAndPersianDigitsAndRejectsSignsAndFractions() {
        assertEquals(15, parseSleepMinutes("١٥"))
        assertEquals(180, parseSleepMinutes("۱۸۰"))
        assertEquals(1, parseSleepMinutes(" 1 "))
        listOf("", "0", "181", "1000", "-1", "+1", "1.5", "1a", "1 5").forEach { assertNull(it, parseSleepMinutes(it)) }
    }
    private val track = allAudioRecordings.first().tracks.first().id
    private fun mark(id: String = "one", position: Long = 12_345, title: String = "personal") =
        AudioBookmark(id, track, position, title, "note", 123)

    @Test fun timerUsesBoundedElapsedTimeAndRejectsOverflow() {
        assertEquals(61_000L, sleepDeadline(1_000, 1))
        assertEquals(10_801_000L, sleepDeadline(1_000, 180))
        assertNull(sleepDeadline(1_000, 0))
        assertNull(sleepDeadline(1_000, 181))
        assertNull(sleepDeadline(-1, 1))
        assertNull(sleepDeadline(Long.MAX_VALUE, 1))
        assertFalse(sleepExpired(AudioSleepPlan(), Long.MAX_VALUE))
        assertFalse(sleepExpired(AudioSleepPlan(61_000), 60_999))
        assertTrue(sleepExpired(AudioSleepPlan(61_000), 61_000))
    }
    @Test fun chapterEndPausesAutoAdvanceAndRepeatButNotExplicitNext() {
        assertEquals(EndTimerAction(true, true), endTimerTransition("one", "two", true, false))
        assertEquals(EndTimerAction(true, true), endTimerTransition("one", "one", false, true))
        assertEquals(EndTimerAction(true, false), endTimerTransition("one", "two", false, false))
        assertEquals(EndTimerAction(), endTimerTransition("one", "one", false, false))
        assertEquals(EndTimerAction(), endTimerTransition(null, "two", true, true))
    }
    @Test fun bookmarksValidateTrackBoundsAndUserText() {
        assertTrue(validAudioBookmark(mark()))
        assertFalse(validAudioBookmark(mark(position = -1)))
        assertFalse(validAudioBookmark(mark(title = " ")))
        assertFalse(validAudioBookmark(mark(title = "x".repeat(121))))
        assertFalse(validAudioBookmark(mark().copy(note = "x".repeat(2001))))
        assertFalse(validAudioBookmark(mark().copy(trackId = "unknown")))
        assertFalse(validAudioBookmark(mark().copy(title = "bad\u0000")))
        val clip = allAudioRecordings.flatMap { it.tracks }.first { it.clipEndMs != null }
        assertFalse(validAudioBookmark(mark().copy(trackId = clip.id, positionMs = clip.durationMs!! + 1)))
    }
    @Test fun reachingCapacityDoesNotEvictExistingMarksAndEditingPreservesOtherItems() {
        val marks = (1..500).map { mark("mark-$it") }
        assertNull(upsertAudioBookmark(marks, mark("new")))
        val edited = mark("mark-1").copy(title = "edited", updatedAt = 500)
        val result = upsertAudioBookmark(marks, edited)!!
        assertEquals(500, result.size)
        assertEquals(edited, result.first())
        assertEquals(marks.drop(1).toSet(), result.drop(1).toSet())
        assertEquals(listOf(mark()), upsertAudioBookmark(emptyList(), mark()))
    }
    @Test fun originalDuplicateSourceNumbersDoNotLoseEitherLessonOrOldIds() {
        val items = orderedSeriesItems(MediaCatalog.items, "bin-baz-tahawi")
        assertEquals((1..32).toList(), items.map { it.episodeNumber })
        assertEquals(listOf(15, 16), items.filter { it.sourceEpisodeNumber == 15 }.map { it.episodeNumber })
        assertEquals("bin-baz-tahawi-01", items[0].id)
        assertEquals("bin-baz-tahawi-03", items[2].id)
        assertEquals("bin-baz-tahawi-04", items[3].id)
        assertNotEquals(items[14].sourceUrl, items[15].sourceUrl)
        assertTrue(items[14].title.startsWith("15 ") && items[15].title.startsWith("15 "))
    }
}
