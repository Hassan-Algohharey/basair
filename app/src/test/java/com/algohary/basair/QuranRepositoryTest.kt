package com.algohary.basair

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class QuranRepositoryTest {
    private val navigation = listOf(listOf(1, 1, 2), listOf(2, 3))

    @Test fun verseNavigationRespectsSurahAndAyahBoundaries() {
        assertEquals(1, quranPageFor(1, 1, navigation))
        assertEquals(2, quranPageFor(1, 3, navigation))
        assertEquals(2, quranPageFor(2, 1, navigation))
        assertEquals(3, quranPageFor(2, 2, navigation))
        for ((surah, ayah) in listOf(0 to 1, 3 to 1, 1 to 0, 1 to 4, 2 to 3,
            -1 to 1, Int.MIN_VALUE to Int.MIN_VALUE, Int.MAX_VALUE to Int.MAX_VALUE)) {
            assertNull(quranPageFor(surah, ayah, navigation))
        }
        assertNull(quranPageFor(1, 1, emptyList()))
        assertNull(quranPageFor(1, 1, listOf(emptyList())))
    }

    @Test fun savedAnchorsIgnoreMalformedAndOutOfRangeData() {
        assertEquals(1 to 3, quranAnchor("1:3", navigation))
        assertEquals(2 to 2, quranAnchor("2:2", navigation))
        for (value in listOf("", "1", "1:", ":1", "1:2:3", "one:two", "0:1", "1:0",
            "3:1", "2:3", "-1:1", "2147483648:1", "1:2147483648")) {
            assertNull("Accepted invalid anchor: $value", quranAnchor(value, navigation))
        }
    }

    @Test fun searchIgnoresHarakatWaslaAndQuranicAnnotations() {
        assertEquals("الحمد لله", normalizeQuranSearch("ٱلْحَمْدُ لِلَّهِ"))
        assertEquals("الرحمن", normalizeQuranSearch("ٱلرَّحْمَـٰنِ"))
        assertEquals(normalizeQuranSearch("قل هو الله احد"), normalizeQuranSearch("قُلْ هُوَ ٱللَّهُ أَحَدٌ"))
        assertEquals("الحمد لله", normalizeQuranSearch("ٱلْحَمْدُ\u06d6 لِلَّهِ\u06ed"))
        assertEquals("", normalizeQuranSearch(""))
    }

    @Test fun completeArchiveStoresEveryPageWithoutChangingBytes() = fixture { root ->
        val payloads = listOf("{\"text\":\"نص عربي\"}".toByteArray(Charsets.UTF_8), byteArrayOf(0, 1, 13, 10, 127))
        val archive = archive(root, listOf("1.json" to payloads[0], "2.json" to payloads[1]))
        val target = root.resolve("pages")
        val progress = mutableListOf<Int>()
        unpackQuran(archive, target, payloads.map(::spec), progress = { progress += it })
        assertArrayEquals(payloads[0], target.resolve("1.json").readBytes())
        assertArrayEquals(payloads[1], target.resolve("2.json").readBytes())
        assertEquals(listOf(1, 2), progress)
        assertEquals(setOf("1.json", "2.json"), target.list()!!.toSet())
    }

    @Test fun unsafeUnexpectedAndNonCanonicalEntryNamesAreRejected() = fixture { root ->
        val payload = "verified page".toByteArray()
        for (name in listOf("../escape.json", "nested/1.json", "01.json", "3.json", "1.json/")) {
            val archive = archive(root, listOf(name to payload))
            assertThrows(IllegalStateException::class.java) {
                unpackQuran(archive, root.resolve("pages"), listOf(spec(payload), spec(payload)))
            }
            assertFalse("Unsafe file escaped extraction directory", root.resolve("escape.json").exists())
            assertFalse(root.resolve("pages/1.json").exists())
        }
    }

    @Test fun repeatedZipPageIsRejectedEvenWhenItsBytesAreValid() = fixture { root ->
        val payload = "same verified page".toByteArray()
        val archive = archive(root, listOf("1.json" to payload, "2.json" to payload))
        // ZipOutputStream rejects duplicate names. Change equally sized local and
        // central-directory names afterward; entry bytes and CRC remain unchanged.
        val bytes = archive.readBytes()
        val name = "2.json".toByteArray(Charsets.US_ASCII)
        var replacements = 0
        for (i in 0..bytes.size - name.size) {
            if (name.indices.all { bytes[i + it] == name[it] }) {
                bytes[i] = '1'.code.toByte(); replacements++
            }
        }
        assertEquals(2, replacements)
        archive.writeBytes(bytes)
        val target = root.resolve("pages")
        assertThrows(IllegalStateException::class.java) {
            unpackQuran(archive, target, listOf(spec(payload), spec(payload)))
        }
        assertArrayEquals(payload, target.resolve("1.json").readBytes())
        assertFalse(target.resolve("2.json").exists())
    }

    @Test fun badPageHashNeverReplacesPreviouslySavedBytes() = fixture { root ->
        val payload = "downloaded page".toByteArray()
        val archive = archive(root, listOf("1.json" to payload))
        val target = root.resolve("pages").apply { mkdirs() }
        target.resolve("1.json").writeText("keep previous saved page")
        assertThrows(IllegalStateException::class.java) {
            unpackQuran(archive, target, listOf(PageSpec("0".repeat(64), payload.size.toLong())))
        }
        assertEquals("keep previous saved page", target.resolve("1.json").readText())
        assertFalse(target.listFiles()!!.any { it.extension == "part" })
    }

    @Test fun archiveMissingAPageCannotBeReportedComplete() = fixture { root ->
        val payload = "first page".toByteArray()
        val archive = archive(root, listOf("1.json" to payload))
        val target = root.resolve("pages")
        assertThrows(IllegalStateException::class.java) {
            unpackQuran(archive, target, listOf(spec(payload), spec("second page".toByteArray())))
        }
        // Verified completed pages remain useful when retrying an interrupted import.
        assertArrayEquals(payload, target.resolve("1.json").readBytes())
        assertFalse(target.resolve("2.json").exists())
    }

    @Test fun cancellingExtractionKeepsSavedPageAndCleansTemporaryOutput() = fixture { root ->
        val payload = "new page bytes".toByteArray()
        val archive = archive(root, listOf("1.json" to payload))
        val target = root.resolve("pages").apply { mkdirs() }
        target.resolve("1.json").writeText("keep saved page")
        var checks = 0
        assertThrows(CancellationException::class.java) {
            unpackQuran(archive, target, listOf(spec(payload)), active = {
                if (++checks >= 3) throw CancellationException()
            })
        }
        assertEquals("keep saved page", target.resolve("1.json").readText())
        assertFalse(target.listFiles()!!.any { it.extension == "part" })
    }

    private fun spec(bytes: ByteArray) = PageSpec(MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }, bytes.size.toLong())

    private fun archive(root: File, entries: List<Pair<String, ByteArray>>): File = root.resolve("source.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
    }

    private fun fixture(block: (File) -> Unit) {
        val root = Files.createTempDirectory("basair-quran-test").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
}
