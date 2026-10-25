package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class BatchDevelopmentTest {
    @Test fun imageTafsirUsesOriginalPageAndExcludesAdjacentTextIndexVerses() {
        val first = QuranVerse(5, 77, "text", "tafsir", 6, 120)
        val second = QuranVerse(5, 78, "text", "tafsir", 6, 121)
        val third = QuranVerse(5, 83, "text", "tafsir", 7, 121)
        assertEquals(listOf(second, third), versesForPrintedPage(listOf(third, first, second, second), 121))
        assertEquals(listOf(first), versesForPrintedPage(listOf(first, second), 120))
        assertTrue(versesForPrintedPage(listOf(first, second), 122).isEmpty())
    }
    @Test fun imageRangeChecksRejectOutOfBoundsAndOversizedDownloads() {
        assertTrue(validMadinahRange(250, 200000, 500000))
        assertFalse(validMadinahRange(-1, 20, 100))
        assertFalse(validMadinahRange(MADINAH_ARCHIVE_BYTES - 5, 20, 100))
        assertFalse(validMadinahRange(Long.MAX_VALUE, 20, 100))
        assertFalse(validMadinahRange(20, 8000001, 100))
        assertFalse(validMadinahRange(20, 100, 12000001))
        assertTrue(madinahBook.pageOnly)
        assertEquals(604, madinahBook.pages)
    }
    @Test fun durationFiltersRespectBoundariesAndUnknownValues() {
        assertTrue(matchesMediaDuration(180, "short"))
        assertFalse(matchesMediaDuration(181, "short"))
        assertTrue(matchesMediaDuration(181, "medium"))
        assertTrue(matchesMediaDuration(1800, "medium"))
        assertTrue(matchesMediaDuration(1801, "long"))
        assertFalse(matchesMediaDuration(null, "short"))
        assertTrue(matchesMediaDuration(null, "unknown"))
        assertFalse(matchesMediaDuration(59, "unknown"))
        assertFalse(matchesMediaDuration(59, "invalid"))
        val result = filteredMediaItems(MediaUiState(items = MediaCatalog.items, durationFilter = "short", yearFilter = 0))
        assertEquals(listOf("araby-khazain-allah"), result.map { it.id })
    }
    @Test fun fullTuhfahTextIsBoundToTheReviewedEditionAndPages() {
        val entries = availableBookText("tuhfat-atfal")
        assertEquals((1..61).toList(), entries.map { it.number })
        assertEquals((4..18).toList(), entries.map { it.pdfPage }.distinct())
        assertTrue(entries.all { it.printedPage == (it.pdfPage + 21).toString() })
        assertTrue(availableBookText("jazariyyah").isEmpty())
        assertTrue(availableBookText("missing").isEmpty())
    }
    @Test fun textSearchRequiresEveryTermAndNormalizesArabicAndDigits() {
        assertEquals(listOf(1), searchBookText("الْجَمْزُورِي").map { it.number })
        assertEquals(listOf(61), searchBookText("٦١").map { it.number })
        assertEquals(listOf(42), searchBookText("٤٢ للمد").map { it.number })
        assertTrue(searchBookText("المد nonexistent").isEmpty())
        assertTrue(searchBookText(" ").isEmpty())
        assertTrue(searchBookText("14", "nawawi-fifty").isEmpty())
        assertTrue(searchBookText("المد").all { it.bookId == "tuhfat-atfal" })
    }
    @Test fun publicationYearFilterCombinesWithoutGuessingUnknownDates() {
        val all = MediaUiState(items = MediaCatalog.items)
        assertEquals(43, filteredMediaItems(all.copy(yearFilter = 1435)).size)
        assertEquals(55, filteredMediaItems(all.copy(yearFilter = 0)).size)
        assertEquals(7, filteredMediaItems(all.copy(yearFilter = 1434)).size)
        assertEquals(43, filteredMediaItems(all.copy(yearFilter = 1435, scholarFilter = "almunajjid", seriesFilter = "munajjid-nawawi")).size)
        assertTrue(filteredMediaItems(all.copy(yearFilter = 1435, scholarFilter = "bin-baz")).isEmpty())
        assertTrue(MediaCatalog.items.filter { it.publishedHijriYear != null }.all { it.publishedDateLabel.isNotBlank() })
    }
    @Test fun sourceIndexContainsAll43NumberedEpisodesWithoutReplacingOldIds() {
        val episodes = orderedSeriesItems(MediaCatalog.items, "munajjid-nawawi")
        assertEquals((1..43).toList(), episodes.map { it.episodeNumber })
        assertEquals((1..8).map { "munajjid-nawawi-%02d".format(java.util.Locale.ROOT, it) }, episodes.take(8).map { it.id })
        assertEquals("indexed", MediaCatalog.series.first { it.id == "munajjid-nawawi" }.coverage)
    }
    @Test fun audiobookChaptersHaveDistinctTracksAndEditionSpecificPageLinks() {
        val reading = AudiobookCatalog.recordings.first { it.id == "tuhfa-alhashim" }
        assertEquals((1..7).toList(), reading.tracks.map { it.sequence })
        assertEquals(7, reading.tracks.map { it.url }.distinct().size)
        assertNull(reading.tracks.first().bookPage)
        val links = reading.tracks.mapNotNull { it.bookPage }
        assertEquals(listOf(5, 7, 8, 9, 11, 12), links.map { it.pdfPage })
        assertTrue(links.all(::validAudioBookPage))
        assertFalse(validAudioBookPage(links.first().copy(bookSha256 = "changed")))
        assertFalse(validAudioBookPage(links.first().copy(pdfPage = 1)))
    }
    private fun report(n: Int) = ContentCorrection("report-$n", "book / page / source", "تصحيح موثق $n", n.toLong())
    @Test fun reportValidationPreservesArabicAndRejectsBlankOrOversizedText() {
        assertEquals("وصف\nالمصدر", validCorrectionText(" وصف\nالمصدر "))
        assertNull(validCorrectionText(" "))
        assertNull(validCorrectionText("a".repeat(4001)))
        assertNull(validCorrectionText("bad\u0000text"))
    }
    @Test fun reportUpsertEditsOnlyTheChosenReportAndNeverEvictsAtCapacity() {
        val full = (1..100).map(::report)
        assertNull(upsertCorrection(full, report(101)))
        val changed = report(50).copy(text = "نص مصحح", updatedAt = 500)
        val updated = upsertCorrection(full, changed)!!
        assertEquals(100, updated.size)
        assertEquals(changed, updated.first())
        assertEquals(full.filterNot { it.id == changed.id }.toSet(), updated.filterNot { it.id == changed.id }.toSet())
        assertEquals(100, sanitizeCorrections(full + report(50)).size)
        assertNull(upsertCorrection(full, report(101).copy(target = "")))
    }
}
