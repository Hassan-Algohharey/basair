package com.algohary.basair

import org.junit.Assert.*
import org.junit.Test

class StudyNotesTest {
    private val note = StudyNote("test-book", "a".repeat(64), 7, "كتاب", "طبعة ١", "ملاحظة\nArabic and English", 123L)
    private fun encoded(n: StudyNote) = mapOf(n.key to n.text, "${n.key}:title" to n.title,
        "${n.key}:edition" to n.edition, "${n.key}:time" to n.updatedAt)

    @Test fun textAndEditionSurviveDecodingWithoutLosingArabicOrLineBreaks() {
        assertEquals(listOf(note), decodeNotes(encoded(note)))
    }
    @Test fun editionsAndPagesNeverShareIdentityAndLatestComesFirst() {
        val other = note.copy(bookSha = "b".repeat(64), updatedAt = 999L)
        val page = note.copy(page = 8)
        assertEquals(3, setOf(note.key, other.key, page.key).size)
        assertEquals(other, decodeNotes(encoded(note) + encoded(other) + encoded(page)).first())
    }
    @Test fun malformedEntriesCannotCrashTheListOrBecomeNotes() {
        assertTrue(decodeNotes(mapOf("n1:x:sha:no-page" to "text", "other" to 1)).isEmpty())
        assertTrue(decodeNotes(encoded(note) + ("${note.key}:time" to "bad")).isEmpty())
        assertFalse(note.copy(page = 0).valid())
        assertFalse(note.copy(bookId = "../book").valid())
        assertFalse(note.copy(bookSha = "bad").valid())
        assertFalse(note.copy(text = "  \n").valid())
        assertTrue(note.copy(text = "x".repeat(NOTE_LIMIT)).valid())
        assertFalse(note.copy(text = "x".repeat(NOTE_LIMIT + 1)).valid())
    }
    @Test fun openingRequiresTheExactAvailableEditionAndValidPage() {
        val book = Catalog.books.first()
        val matching = note.copy(bookId = book.id, bookSha = book.sha256, page = book.pages)
        assertEquals(book, noteBook(matching))
        assertNull(noteBook(matching.copy(bookSha = "b".repeat(64))))
        assertNull(noteBook(matching.copy(page = book.pages + 1)))
        val ref = referenceVolumes.getValue(43).asBook()
        assertEquals(ref, noteBook(note.copy(bookId = ref.id, bookSha = ref.sha256)))
    }
}
