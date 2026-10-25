package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class MediaCatalogTest {
    private fun state(query: String = "") = MediaUiState(scholars = MediaCatalog.scholars,
        items = MediaCatalog.items, query = query, bookTitles = Catalog.books.associate { it.id to it.title })

    @Test fun expandedCollectionHasRealLinksAndValidBookReferences() {
        assertEquals(4, MediaCatalog.scholars.size)
        assertEquals(52, MediaCatalog.items.size)
        assertEquals(mapOf("lesson" to 37, "sermon" to 9, "short" to 6), MediaCatalog.items.groupingBy { it.kind }.eachCount())
        assertEquals(52, MediaCatalog.items.map { it.id }.toSet().size)
        MediaCatalog.items.forEach { item ->
            assertTrue(item.sourceNote.isNotBlank() && item.englishSourceNote.isNotBlank())
            assertTrue(item.format in setOf("audio", "video"))
            assertTrue(item.bookIds.all { id -> Catalog.books.any { it.id == id } })
            assertTrue(MediaCatalog.scholars.any { it.id == item.scholarId })
            assertTrue(java.net.URI(item.sourceUrl).scheme == "https")
        }
    }
    @Test fun searchNormalizesArabicAndRequiresEveryWord() {
        assertTrue(filteredMediaItems(state("التوحيد")).isNotEmpty())
        assertEquals(filteredMediaItems(state("التوحيد")), filteredMediaItems(state("التَّوْحِيد")))
        assertTrue(filteredMediaItems(state("ibn Baz tahawiyyah")).isNotEmpty())
        assertTrue(filteredMediaItems(state("ibn Baz nonexistent")).isEmpty())
        assertTrue(filteredMediaItems(state("الأربعون النووية")).isNotEmpty())
        assertTrue(filteredMediaItems(state("الأربعون النووية")).all { "nawawi-fifty" in it.bookIds })
    }
    @Test fun filtersCombineAndNeverClaimOtherScholarsLessons() {
        val favorites = MediaCatalog.items.take(5).map { it.id }.toSet()
        val input = state().copy(kindFilter = "lesson", profileId = "bin-baz", favoritesOnly = true,
            favoriteIds = favorites, bookFilter = "tahawiyyah")
        val result = filteredMediaItems(input)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.kind == "lesson" && it.scholarId == "bin-baz" && it.id in favorites && "tahawiyyah" in it.bookIds })
        assertTrue(filteredMediaItems(input.copy(scholarFilter = "almunajjid")).isEmpty())
        assertTrue(filteredMediaItems(input.copy(bookFilter = "missing")).isEmpty())
    }
    @Test fun favoritesPreserveUnknownStoredIdsAndRejectUnknownToggles() {
        val id = MediaCatalog.items.first().id
        assertEquals(setOf("legacy", id), toggleMediaFavorite(setOf("legacy"), id))
        assertEquals(setOf("legacy"), toggleMediaFavorite(setOf("legacy", id), id))
        assertEquals(setOf("legacy"), toggleMediaFavorite(setOf("legacy"), "unknown"))
    }
    @Test fun externalNavigationOnlyAcceptsExactReviewedUrls() {
        assertTrue(allowedMediaUrl(MediaCatalog.items.first().sourceUrl))
        assertTrue(allowedMediaUrl(MediaCatalog.scholars.first().sourceUrl))
        listOf("javascript:alert(1)", "https://binbaz.org.sa.evil.test/", "https://binbaz.org.sa/unknown",
            "https://binbaz.org.sa@evil.test/", MediaCatalog.items.first().sourceUrl + "?tracking=1").forEach {
            assertFalse(it, allowedMediaUrl(it))
        }
    }

    @Test fun seriesCoverageAndBookLinksMatchTheIndexedEpisodes() {
        assertEquals(5, MediaCatalog.series.size)
        assertEquals(4, MediaCatalog.series.count { it.coverage == "partial" })
        assertEquals(1, MediaCatalog.series.count { it.coverage == "complete" })
        MediaCatalog.series.forEach { series ->
            val episodes = orderedSeriesItems(MediaCatalog.items, series.id)
            assertTrue(episodes.isNotEmpty())
            assertTrue(allowedMediaUrl(series.sourceUrl))
            assertTrue(episodes.all { it.scholarId == series.scholarId && it.bookIds.toSet() == series.bookIds.toSet() })
            assertEquals(episodes.size, episodes.map { it.episodeNumber }.toSet().size)
            if (series.coverage == "complete") {
                assertEquals(series.expectedEpisodes, episodes.size)
                assertEquals((1..episodes.size).toList(), episodes.map { it.episodeNumber })
            }
        }
        assertTrue(MediaCatalog.items.filter { it.kind == "lesson" }.all { item -> MediaCatalog.series.any { it.id == item.seriesId } })
        val useful = MediaCatalog.series.single { "wasael" in it.bookIds }
        val results = filteredMediaItems(state("beneficial").copy(series = MediaCatalog.series, seriesFilter = useful.id))
        assertEquals(7, results.size)
    }
}
