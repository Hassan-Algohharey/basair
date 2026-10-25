package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class QualityV020Test {
    @Test fun durationDisplayKeepsHoursAndPadsMinutesAndSeconds() {
        assertEquals("0:00", mediaDurationLabel(-1))
        assertEquals("0:59", mediaDurationLabel(59))
        assertEquals("3:00", mediaDurationLabel(180))
        assertEquals("56:51", mediaDurationLabel(3411))
        assertEquals("1:01:01", mediaDurationLabel(3661))
    }
    @Test fun newPlayerDurationsDoNotMakeUndocumentedAudioDurationsKnown() {
        val first = MediaCatalog.items.single { it.id == "othman-tuhfat-atfal-1" }
        assertEquals(3411, first.durationSeconds)
        val audio = MediaCatalog.items.single { it.id == "bin-baz-tahawi-01" }
        assertNull(audio.durationSeconds)
        assertTrue(matchesMediaDuration(audio.durationSeconds, "unknown"))
        assertFalse(matchesMediaDuration(audio.durationSeconds, "long"))
        assertTrue(filteredMediaItems(MediaUiState(items = MediaCatalog.items, durationFilter = "long"))
            .any { it.id == first.id })
    }
    @Test fun GregorianSourceDatesAreNotGuessedAsHijriYears() {
        val video = MediaCatalog.items.single { it.id == "othman-tuhfat-atfal-1" }
        assertTrue(video.publishedDateLabel.contains("٢٠١٩"))
        assertNull(video.publishedHijriYear)
        val result = filteredMediaItems(MediaUiState(items = MediaCatalog.items, yearFilter = 1434))
        assertEquals(7, result.size)
        assertTrue(result.all { it.scholarId == "al-barrak" })
    }
    @Test fun linkAuditIsDatedAndDoesNotExpandTheNavigationAllowlist() {
        assertTrue(MediaCatalog.items.all { it.sourceCheckedOn == "2026-10-25" && it.sourceReachable == true })
        assertFalse(allowedMediaUrl("https://www.youtube.com/watch?v=8Do87VxmhLQ"))
        assertFalse(allowedMediaUrl(MediaCatalog.items.first().sourceUrl + "#unreviewed"))
    }
}
