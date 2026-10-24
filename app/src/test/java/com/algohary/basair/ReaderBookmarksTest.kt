package com.algohary.basair

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderBookmarksTest {
    @Test fun loadsOnlyValidPagesInReadingOrder() {
        assertEquals(listOf(1, 9, 16), parseBookmarkedPages(setOf("16", "٩", "1", "0", "17", "oops", "-2"), 16))
    }

    @Test fun equivalentDigitsDoNotCreateDuplicateBookmarks() {
        assertEquals(listOf(3), parseBookmarkedPages(setOf("3", "٣", "۳", "03"), 61))
        assertEquals(emptyList<Int>(), parseBookmarkedPages(emptySet(), 61))
    }
}
