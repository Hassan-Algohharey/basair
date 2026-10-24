package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class MediaStudyTest {
    private fun episode(number: Int) = MediaCatalog.items.first().copy(id = "episode-$number", seriesId = "test-series",
        episodeNumber = number, kind = "lesson", topics = listOf("الصلاة"), englishTopics = listOf("Prayer"))

    @Test fun episodeOrderPreservesGapsAndStopsAtBoundaries() {
        val episodes = listOf(episode(4), episode(1), episode(3))
        assertEquals(listOf(1, 3, 4), orderedSeriesItems(episodes, "test-series").map { it.episodeNumber })
        assertEquals("episode-3", adjacentSeriesItem(episodes, "episode-1", 1)?.id)
        assertNull(adjacentSeriesItem(episodes, "episode-1", -1))
        assertNull(adjacentSeriesItem(episodes, "episode-4", 1))
        assertNull(adjacentSeriesItem(episodes, "missing", 1))
        assertNull(adjacentSeriesItem(episodes, "episode-3", 2))
    }

    @Test fun unorderedSingleMaterialDoesNotInventEpisodeNumbers() {
        val item = episode(1).copy(episodeNumber = null)
        assertEquals(listOf(item), orderedSeriesItems(listOf(item), "test-series"))
        assertNull(adjacentSeriesItem(listOf(item), item.id, 1))
        assertNull(adjacentSeriesItem(listOf(item.copy(seriesId = null)), item.id, 1))
    }

    @Test fun seriesTopicQueryFavoritesAndScholarFiltersCombine() {
        val one = episode(1).copy(title = "الصلاة والخشوع", scholarId = "speaker", bookIds = listOf("book"))
        val unrelated = one.copy(id = "other", seriesId = "other-series")
        val state = MediaUiState(items = listOf(episode(4), unrelated, one), query = "prayer",
            seriesFilter = "test-series", topicFilter = "الصلاة", kindFilter = "lesson", scholarFilter = "speaker",
            favoriteIds = setOf(one.id), favoritesOnly = true, bookFilter = "book")
        assertEquals(listOf(one), filteredMediaItems(state))
        assertTrue(filteredMediaItems(state.copy(topicFilter = "الزكاة")).isEmpty())
        assertTrue(filteredMediaItems(state.copy(query = "prayer unmatched")).isEmpty())
    }

    @Test fun numericQueriesAcceptArabicAndPersianDigits() {
        assertEquals(mediaSearchKey("الدرس 12"), mediaSearchKey("الدَّرْس ١٢"))
        assertEquals(mediaSearchKey("episode 12"), mediaSearchKey("EPISODE ۱۲"))
    }

    @Test fun sourcePreviewAndProfileUrlsDoNotChangeLastMaterial() {
        val first = MediaCatalog.items.first()
        assertEquals(first.id, openedMediaId(null, first.sourceUrl))
        assertEquals(first.id, openedMediaId(first.id, MediaCatalog.scholars.first().sourceUrl))
        assertEquals(first.id, openedMediaId(first.id, "javascript:alert(1)"))
        MediaCatalog.series.forEach { series -> assertTrue(allowedMediaUrl(series.sourceUrl)) }
    }
}
