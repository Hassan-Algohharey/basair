package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class ComparisonTest {
    @Test fun twentyFiveIssuesCoverThreeTopicsAndOneHundredSchoolCards() {
        assertEquals(25, Catalog.comparisons.size)
        assertEquals(25, Catalog.comparisons.map { it.id }.toSet().size)
        assertEquals(100, Catalog.comparisons.sumOf { it.opinions.size })
        assertEquals(listOf("الطهارة", "الصلاة", "الزكاة"), Catalog.comparisonTopics)
        assertEquals(listOf(17, 4, 4), Catalog.comparisonTopics.map { Catalog.searchComparisons("", topic = it).size })
        Catalog.comparisons.forEach {
            assertEquals(Catalog.comparisonSchools, it.opinions.map { opinion -> opinion.school })
            assertTrue(it.scope.isNotBlank())
        }
    }

    @Test fun searchCombinesTopicSchoolAndNormalizedTerms() {
        assertEquals(listOf("travel-qasr"), Catalog.searchComparisons("قَصـر ٢٧ ٢٧٤", topic = "الصلاة", school = "المالكي").map { it.id })
        assertEquals(listOf("zakat-child"), Catalog.searchComparisons("الصغير عشر", topic = "الزكاة", school = "الحنفي").map { it.id })
        assertTrue(Catalog.searchComparisons("الصغير عشر", topic = "الزكاة", school = "الشافعي").isEmpty())
        assertTrue(Catalog.searchComparisons("الغسل", topic = "الصلاة").isEmpty())
        assertTrue(Catalog.searchComparisons("", school = "غير موجود").isEmpty())
        assertTrue(Catalog.searchComparisons("", topic = "غير موجود").isEmpty())
        assertEquals(25, Catalog.searchComparisons("  ").size)
        assertTrue(Catalog.searchComparisons("زكاة", subject = "tafsir").isEmpty())
    }

    @Test fun filteredSelectionNeverShowsAnExcludedOrMissingIssue() {
        val prayer = Catalog.searchComparisons("", topic = "الصلاة")
        assertEquals("travel-qasr", selectComparison(prayer, "ghusl-intention")?.id)
        assertEquals("travel-residence", selectComparison(prayer, "travel-residence")?.id)
        assertEquals("travel-qasr", selectComparison(prayer, "missing")?.id)
        assertNull(selectComparison(emptyList(), "travel-qasr"))
    }

    @Test fun referencesUseTheReviewedEditionAndPageAnchor() {
        val reviewedPages = mapOf(
            23 to setOf(232, 294, 295, 296, 298, 299),
            27 to setOf(274, 280, 281, 282, 283, 284, 285),
            31 to setOf(207, 208, 209, 211, 212),
            43 to setOf(348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 360, 362, 363, 364, 365)
        )
        Catalog.comparisons.flatMap { it.opinions }.flatMap { it.references }.forEach { reference ->
            assertTrue(reviewedPages.getValue(reference.volume).contains(reference.pdfPage))
            reference.pages.split("–").map(String::toInt).let { range ->
                assertTrue(reference.pdfPage in range.first()..range.last())
                (range.first()..range.last()).forEach { assertTrue(reviewedPages.getValue(reference.volume).contains(it)) }
            }
            assertTrue(reference.source.endsWith(".pdf#page=${reference.pdfPage}"))
        }
        assertTrue(kuwaitReference(23, "232", 232).edition.contains("الثانية"))
        assertTrue(kuwaitReference(27, "274", 274).edition.contains("1992"))
        assertTrue(kuwaitReference(31, "207", 207).edition.contains("1994"))
        assertTrue(kuwaitReference(43, "360", 360).edition.contains("2005"))
    }
}
