package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class LibraryOrganizationTest {
    @Test fun generalFiqhIsNotAssignedToAnySchool() {
        assertEquals(4, fiqhSchools.distinct().size)
        val assigned = mapOf("hanafi" to "tahtawi1290", "maliki" to "risalah-maliki", "shafii" to "fath-qarib", "hanbali" to "umdat-fiqh")
        assigned.forEach { (school, id) ->
            assertEquals(listOf(id), Catalog.search("", "fiqh", school).map { it.id })
            assertTrue(Catalog.search(school, "fiqh", school).any { it.id == id })
        }
        assertEquals(listOf("fiqh-muyassar"), Catalog.search("", "fiqh", "shared").map { it.id })
        assertTrue(Catalog.search("", "aqidah", "hanafi").isEmpty())
    }

    @Test fun classificationRequiresBothSchoolAndTopicAndDoesNotDependOnAuthor() {
        val book = Catalog.books.first().copy(schools = setOf("hanafi", "maliki"))
        assertTrue(book.matchesOrganization("fiqh", "hanafi", "الصلاة"))
        assertTrue(book.matchesOrganization("fiqh", "maliki", "الصلاة"))
        assertFalse(book.matchesOrganization("fiqh", "shafii", "الصلاة"))
        assertFalse(book.matchesOrganization("fiqh", "hanafi", "غيرموجود"))
        assertFalse(book.matchesOrganization("fiqh", "shared", "الصلاة"))
        assertFalse(book.matchesOrganization("aqidah", "hanafi", null))
    }

    @Test fun topicsCombineWithQueryAndSubjectWithoutChangingExistingIds() {
        assertEquals(listOf("fiqh-muyassar"), Catalog.search("zakat", "fiqh", "shared", "الزكاة").map { it.id })
        assertTrue(Catalog.search("zakat", "fiqh", "shared", "غيرموجود").isEmpty())
        assertTrue(Catalog.search("companions", "salaf").any { it.id == "safwa1" })
        assertTrue(Catalog.search("sincerity", "tazkiyah", topic = "الإخلاص").isNotEmpty())
        assertEquals(Catalog.books.map { it.id }, Catalog.search("").map { it.id })
    }

    @Test fun sharedVolumeSearchHonorsTopicAndDoesNotLeakUnrelatedVolumes() {
        assertEquals(setOf(23, 27, 31, 43), matchingReferenceVolumes("encyclopedia", null).map { it.volume }.toSet())
        assertEquals(listOf(31), matchingReferenceVolumes("31", null).map { it.volume })
        assertTrue(matchingReferenceVolumes("unknown-query", null).isEmpty())
        assertTrue(matchingReferenceVolumes("", "غيرموجود").isEmpty())
    }

    @Test fun chapterLinksBelongToTheExactEditionAndCoveredTopics() {
        val books=Catalog.books.filter { it.schools.isNotEmpty() }
        assertEquals(4, books.size)
        assertEquals(18, books.sumOf { it.chapters.size })
        books.forEach { book ->
            assertEquals(1, book.schools.size)
            assertTrue(book.chapters.isNotEmpty())
            assertTrue(book.chapters.all { it.pdfPage in 1..book.pages && it.printedPage.isNotBlank() && it.title in book.topics })
            book.chapters.forEach { assertTrue(Catalog.search("", "fiqh", book.schools.single(), it.title).contains(book)) }
        }
        assertTrue(Catalog.search("", "fiqh", "hanafi", "المعاملات").isEmpty())
        assertTrue(Catalog.search("", "fiqh", "hanafi", "الحج").isEmpty())
        assertEquals(23, books.first { it.id=="fath-qarib" }.chapters.first { it.title=="الزكاة" }.pdfPage)
    }
}
