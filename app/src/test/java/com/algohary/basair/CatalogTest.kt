package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class CatalogTest {
    @Test fun arabicSearchIgnoresDiacriticsAndTatweel() {
        assertEquals("الزكاة", normalizeArabic("الزَّكـاة"))
        assertTrue(Catalog.search("الزَّكـاة").any { it.id == "fiqh-muyassar" })
    }
    @Test fun searchCombinesTermsAndSubject() {
        assertTrue(Catalog.search("الحديث القدسي", "hadith").isNotEmpty())
        assertTrue(Catalog.search("الزكاة", "sirah").isEmpty())
        assertTrue(Catalog.search("عبارةغيرموجودة").isEmpty())
    }
    @Test fun everySubjectHasContentAndSources() {
        Catalog.subjects.forEach { subject -> assertTrue(Catalog.books.any { it.subject == subject.id }) }
        Catalog.comparisons.forEach { comparison ->
            assertEquals(setOf("الحنفي", "المالكي", "الشافعي", "الحنبلي"), comparison.opinions.map { it.school }.toSet())
            comparison.opinions.forEach { opinion ->
                assertTrue(opinion.references.isNotEmpty())
                assertTrue(opinion.references.all { it.volume > 0 && it.pages.isNotBlank() && it.edition.isNotBlank() && it.pdfPage > 0 })
            }
        }
        assertTrue(Catalog.books.all { it.source.startsWith("https://") })
    }
    @Test fun comparisonSearchRespectsArabicAndCategory() {
        assertTrue(Catalog.searchComparisons("النِّيَّة الغُسل").any { it.id == "ghusl-intention" })
        assertTrue(Catalog.searchComparisons("الغسل", "tafsir").isEmpty())
        assertTrue(Catalog.searchComparisons("عبارةغيرموجودة").isEmpty())
    }
    @Test fun booksHaveOriginalAssetsAndLicenseProvenance() {
        assertEquals(16, Catalog.books.size)
        assertEquals(3322, Catalog.books.sumOf { it.pages })
        assertTrue(Catalog.search("نافع", "salaf").any { it.id == "qurra-biographies" })
        assertTrue(Catalog.search("تزكية", "tazkiyah").any { it.id == "hearts-remedies" })
        assertTrue(Catalog.books.all { it.asset.startsWith("books/") && it.asset.endsWith(".pdf") && it.sha256.matches(Regex("[a-f0-9]{64}")) })
        assertTrue(Catalog.books.all { it.licenseUrl.startsWith("https://") && it.rightsNote.isNotBlank() })
    }
    @Test fun expandedSectionsCoverRequestedBiographiesAndPurification() {
        listOf("أبو حنيفة", "مالك", "الشافعي", "أحمد بن حنبل").forEach { name ->
            assertTrue(Catalog.search(name, "salaf").any { it.id == "four-imams" })
        }
        assertTrue(Catalog.search("الصحابة", "salaf").any { it.id == "safwa1" })
        assertTrue(Catalog.search("التابعين", "salaf").any { it.id == "safwa2" })
        assertTrue(Catalog.search("الإخلاص", "tazkiyah").any { it.id == "risalat" })
        assertTrue(Catalog.search("التوكل", "tazkiyah").any { it.id == "wasael" })
        Catalog.subjectFilters.forEach { (subject, filters) ->
            filters.forEach { assertTrue(Catalog.search(it, subject).isNotEmpty()) }
        }
        assertEquals(4, Catalog.books.count { it.subject == "salaf" })
        assertEquals(3, Catalog.books.count { it.subject == "tazkiyah" })
    }
    @Test fun pageJumpSupportsArabicDigitsAndRejectsOutOfRange() {
        assertEquals(123, parsePageNumber("١٢٣", 469))
        assertEquals(123, parsePageNumber("۱۲۳", 469))
        assertEquals(469, parsePageNumber(" 469 ", 469))
        listOf("0", "470", "-1", "", "1.5", "9999999999999999999").forEach { assertNull(parsePageNumber(it, 469)) }
    }
}
