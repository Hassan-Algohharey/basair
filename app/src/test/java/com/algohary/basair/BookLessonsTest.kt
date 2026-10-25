package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class BookLessonsTest {
    @Test fun mappingsBelongToTheReviewedEditionAndOfficialLesson() {
        val links = bookLessonLinks()
        assertEquals(7, links.size)
        assertEquals(7, links.map { it.id }.distinct().size)
        links.forEach { link ->
            val book = Catalog.books.first { it.id == link.bookId }
            val item = MediaCatalog.items.first { it.id == link.itemId }
            assertEquals(book.sha256, link.bookSha256)
            assertEquals(item.sourceUrl, link.sourceUrl)
            assertTrue(link.pdfPage in 1..book.pages)
            assertTrue(link.bookId in item.bookIds)
        }
    }

    @Test fun filteringDoesNotLeakOtherBooksOrUnmappedEpisodes() {
        val link = bookLessonLinks("wasael").first { it.pdfPage == 8 }
        val state = MediaUiState(items = MediaCatalog.items, bookFilter = "wasael", sectionFilter = link.id)
        assertEquals(listOf(link.itemId), filteredMediaItems(state).map { it.id })
        assertTrue(filteredMediaItems(state.copy(bookFilter = "tahawiyyah")).isEmpty())
        assertTrue(filteredMediaItems(state.copy(seriesFilter = "bin-baz-tahawi")).isEmpty())
        assertTrue(filteredMediaItems(state.copy(query = "unmatched")).isEmpty())
        assertTrue(filteredMediaItems(state.copy(favoritesOnly = true)).isEmpty())
        assertEquals(listOf(link.itemId), filteredMediaItems(state.copy(favoritesOnly = true, favoriteIds = setOf(link.itemId))).map { it.id })
    }

    @Test fun twoLessonsCanMapToOnePageWithoutBeingMerged() {
        val links = bookLessonLinks("wasael").filter { it.pdfPage == 3 }
        assertEquals(2, links.size)
        assertEquals(2, links.map { it.itemId }.distinct().size)
        assertEquals(setOf("2"), links.map { it.printedPage }.toSet())
    }

    @Test fun bookWithoutPageMappingRetainsItsWholeCommentaryIndex() {
        assertTrue(bookLessonLinks("nawawi-fifty").isEmpty())
        assertEquals(43, filteredMediaItems(MediaUiState(items = MediaCatalog.items, bookFilter = "nawawi-fifty")).size)
        assertTrue(bookLessonLinks("missing").isEmpty())
    }

    @Test fun resumeRejectsUnknownAndCrossSeriesIds() {
        val item = MediaCatalog.items.first { it.seriesId != null }
        assertEquals(item.id, seriesResumeId(item.seriesId!!, item.id))
        assertNull(seriesResumeId("missing", item.id))
        assertNull(seriesResumeId(item.seriesId!!, "removed-episode"))
        assertNull(seriesResumeId(item.seriesId!!, null))
    }

    @Test fun completionIsManualIndependentAndReversible() {
        val one = MediaCatalog.items.first { it.seriesId != null }
        val other = MediaCatalog.items.first { it.seriesId != null && it.seriesId != one.seriesId }
        val both = toggleLessonCompleted(setOf(other.id), one.id)
        assertEquals(setOf(one.id, other.id), both)
        assertEquals(setOf(other.id), toggleLessonCompleted(both, one.id))
        assertEquals(both, toggleLessonCompleted(both, "unknown"))
        val sermon = MediaCatalog.items.first { it.seriesId == null }
        assertEquals(both, toggleLessonCompleted(both, sermon.id))
    }
}
