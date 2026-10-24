package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class LibraryOrganizationTest {
    @Test fun generalFiqhIsNotAssignedToAnySchool() {
        assertEquals(4, fiqhSchools.distinct().size)
        fiqhSchools.forEach { assertTrue(Catalog.search("", "fiqh", it).isEmpty()) }
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
}
