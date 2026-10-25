package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class SearchHistoryTest {
    @Test fun newestSearchMovesToFrontAndArabicVariantsDeduplicateWithinTheirSection() {
        var rows = recordSearchHistory(emptyList(), SearchScope.BOOKS, "  أَحْمَد   عامر  ", true)
        rows = recordSearchHistory(rows, SearchScope.MEDIA, "أحمد عامر", true)
        rows = recordSearchHistory(rows, SearchScope.BOOKS, "احمد عامر", true)
        assertEquals(2, rows.size)
        assertEquals(SearchHistoryEntry(SearchScope.BOOKS, "احمد عامر"), rows.first())
        assertEquals(SearchScope.MEDIA, rows.last().scope)
        rows = recordSearchHistory(rows, SearchScope.MEDIA, "AHMED Amer", true)
        rows = recordSearchHistory(rows, SearchScope.MEDIA, "ahmed amer", true)
        assertEquals(3, rows.size)
        assertEquals("ahmed amer", rows.first().query)
    }

    @Test fun disabledRecordingDoesNotChangeOrResurrectDeletedHistory() {
        val row = SearchHistoryEntry(SearchScope.MEDIA, "Tuhfat")
        assertEquals(listOf(row), recordSearchHistory(listOf(row), SearchScope.BOOKS, "new query", false))
        assertTrue(recordSearchHistory(emptyList(), row.scope, row.query, false).isEmpty())
        assertEquals(listOf(row), recordSearchHistory(emptyList(), row.scope, row.query, true))
    }

    @Test fun newestFiftyAreKeptAndBlankOverlongOrMalformedDataCannotEnterHistory() {
        var rows = emptyList<SearchHistoryEntry>()
        repeat(75) { rows = recordSearchHistory(rows, SearchScope.BOOKS, "query $it", true) }
        assertEquals(50, rows.size)
        assertEquals("query 74", rows.first().query)
        assertEquals("query 25", rows.last().query)
        assertEquals(rows, recordSearchHistory(rows, SearchScope.BOOKS, " \n\t ", true))
        assertEquals(rows, recordSearchHistory(rows, SearchScope.BOOKS, "x".repeat(201), true))
        val decoded = decodeSearchHistory(mapOf("scope:0" to "OTHER", "query:0" to "bad",
            "scope:1" to "MEDIA", "query:1" to "  valid\nquery ",
            "scope:2" to "BOOKS", "query:2" to 5, "scope:3" to "MEDIA", "query:3" to "VALID QUERY"))
        assertEquals(listOf(SearchHistoryEntry(SearchScope.MEDIA, "valid query")), decoded)
    }
}
