package com.algohary.basair

import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class BookDownloadsTest {
    @Test fun cachedPageRangesShowActualOfflineCoverage() {
        assertEquals("93–107", pageRanges((93..107).toList()))
        assertEquals("1–3, 8, 10–11", pageRanges(listOf(11, 2, 1, 8, 3, 10, 2)))
        assertEquals("—", pageRanges(emptyList()))
    }
    @Test fun windowFollowsExactPageAndBookBoundaries() {
        assertEquals(93..107, pageWindow(100, 469))
        assertEquals(1..8, pageWindow(1, 469))
        assertEquals(462..469, pageWindow(469, 469))
        assertEquals(1..3, pageWindow(2, 3))
        assertEquals(1..1, pageWindow(1, 1))
        assertEquals(15, pageWindow(100, 469).count())
    }
    @Test fun outOfRangePageCannotTriggerNetworkRequests() {
        for ((page, total) in listOf(0 to 10, 11 to 10, 1 to 0)) {
            try { pageWindow(page, total); fail("Accepted invalid page") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun verifiedPageIsStoredAndPartRemoved() {
        val dir = Files.createTempDirectory("basair-page").toFile()
        try {
            val original = dir.resolve("original").apply { writeText("source page") }
            val target = dir.resolve("pages/100.pdf")
            original.inputStream().use { storePage(it, target, PageSpec(sha256(original), original.length())) }
            assertArrayEquals(original.readBytes(), target.readBytes())
            assertEquals(listOf("100.pdf"), target.parentFile!!.list()!!.toList())
        } finally { dir.deleteRecursively() }
    }
    @Test fun incompleteOversizedAndCorruptPagesNeverReplaceGoodCache() {
        val dir = Files.createTempDirectory("basair-page").toFile()
        try {
            val original = dir.resolve("original").apply { writeText("correct") }
            val target = dir.resolve("100.pdf").apply { writeText("keep cached page") }
            val spec = PageSpec(sha256(original), original.length())
            for (bad in listOf("short", "much too large", "corrupt")) {
                try { storePage(ByteArrayInputStream(bad.toByteArray()), target, spec); fail("Accepted invalid bytes") }
                catch (_: IllegalStateException) { }
                assertEquals("keep cached page", target.readText())
                assertFalse(dir.listFiles()!!.any { it.extension == "part" })
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun cancellationKeepsPreviousCacheAndCleansPartialPage() {
        val dir = Files.createTempDirectory("basair-page").toFile()
        try {
            val original = dir.resolve("original").apply { writeText("correct") }
            val target = dir.resolve("1.pdf").apply { writeText("keep") }
            try {
                original.inputStream().use { input ->
                    storePage(input, target, PageSpec(sha256(original), original.length())) { throw java.util.concurrent.CancellationException() }
                }
                fail("Cancellation ignored")
            } catch (_: java.util.concurrent.CancellationException) { }
            assertEquals("keep", target.readText())
            assertFalse(dir.listFiles()!!.any { it.extension == "part" })
        } finally { dir.deleteRecursively() }
    }
}
