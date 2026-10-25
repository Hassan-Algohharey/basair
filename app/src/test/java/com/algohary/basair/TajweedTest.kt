package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class TajweedTest {
    @Test fun topicsOpenVerifiedChaptersInTheSelectedEdition() {
        assertEquals(9, tajweedTopics.size)
        assertEquals(9, tajweedTopics.map { it.id }.distinct().size)
        assertEquals(setOf("tuhfat-atfal", "jazariyyah"), Catalog.search("", "tajweed").map { it.id }.toSet())
        tajweedTopics.flatMap { it.readings }.forEach { reading ->
            assertEquals("tajweed", reading.book.subject)
            assertTrue(reading.chapter.pdfPage in 1..reading.book.pages)
            assertTrue(reading.chapter.printedPage.isNotBlank())
            assertEquals("https://d1.islamhouse.com/html/faq.htm", reading.book.licenseUrl)
        }
        assertEquals(5, tajweedTopics.first { it.id == "noon" }.readings.first().chapter.pdfPage)
        assertEquals(32, tajweedTopics.first { it.id == "stopping" }.readings.single().chapter.pdfPage)
        assertEquals(62, Catalog.search("", "tajweed").sumOf { it.pages })
    }

    @Test fun searchCombinesTermsAndKeepsUnrelatedTopicsOut() {
        assertEquals(listOf("articulation"), searchTajweed("articulation").map { it.id })
        assertEquals(listOf("madd"), searchTajweed("الْمُدُود").map { it.id })
        assertEquals(listOf("noon"), searchTajweed("noon tanween").map { it.id })
        assertTrue(searchTajweed("articulation tanween").isEmpty())
        assertEquals(9, searchTajweed("tajweed").size)
        assertTrue(Catalog.search("tajweed", "fiqh").isEmpty())
        assertEquals(2, Catalog.search("tajweed", "tajweed").size)
    }

    @Test fun unknownProgressIdsCannotInflateTheReadingCount() {
        assertEquals(setOf("madd"), validTajweedMarks(setOf("madd", "deleted-topic")))
        assertTrue(validTajweedMarks(emptySet()).isEmpty())
    }
}
