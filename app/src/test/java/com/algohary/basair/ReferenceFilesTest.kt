package com.algohary.basair

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class ReferenceFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val sample = "%PDF-1.4\nverified reference fixture".toByteArray()
    private fun fixture() = ReferenceVolume(31, 399, sample.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(sample).joinToString("") { "%02x".format(it) })

    @Test fun downloadedBytesAreVerifiedAndReusedWithoutReplacingTheFile() {
        val directory = temporary.newFolder()
        val volume = fixture()
        val stored = storeReference(ByteArrayInputStream(sample), directory, volume)
        assertArrayEquals(sample, stored.readBytes())
        assertEquals(stored, verifiedReferenceFile(directory, volume))
        assertEquals(listOf(stored.name), directory.list()!!.toList())
        assertEquals(stored, storeReference(ByteArrayInputStream(sample), directory, volume))
    }

    @Test fun corruptAndOversizedDownloadsLeaveNoPartialOrTrustedFile() {
        listOf(sample.copyOf(sample.size - 1), sample + byteArrayOf(1), sample.reversedArray()).forEach { bytes ->
            val directory = temporary.newFolder()
            try { storeReference(ByteArrayInputStream(bytes), directory, fixture()); fail("Must reject changed bytes") }
            catch (_: IllegalStateException) { }
            assertNull(verifiedReferenceFile(directory, fixture()))
            assertTrue(directory.list()!!.isEmpty())
        }
    }

    @Test fun cancellationRemovesPartialAndPreservesExistingVerifiedFile() {
        val directory = temporary.newFolder()
        val volume = fixture()
        val stored = storeReference(ByteArrayInputStream(sample), directory, volume)
        try { storeReference(ByteArrayInputStream(sample), directory, volume, checkActive = { throw CancellationException() }); fail("Must cancel") }
        catch (_: CancellationException) { }
        assertEquals(stored, verifiedReferenceFile(directory, volume))
        assertEquals(listOf(stored.name), directory.list()!!.toList())
    }

    @Test fun corruptStoredFileIsNotTrustedAndCanBeReplaced() {
        val directory = temporary.newFolder()
        val volume = fixture()
        File(directory, volume.fileName).writeBytes(sample.reversedArray())
        assertNull(verifiedReferenceFile(directory, volume))
        assertArrayEquals(sample, storeReference(ByteArrayInputStream(sample), directory, volume).readBytes())
    }

    @Test fun everyComparisonReferenceHasMatchingDownloadMetadataAndValidTargetPage() {
        assertEquals(setOf(23, 27, 31, 43), referenceVolumes.keys)
        Catalog.comparisons.flatMap { it.opinions }.flatMap { it.references }.forEach { reference ->
            val volume = referenceVolumes.getValue(reference.volume)
            assertTrue(reference.pdfPage in 1..volume.pages)
            assertEquals(reference.pdfUrl, volume.reference.pdfUrl)
            assertEquals(reference.edition, volume.asBook().edition)
            assertTrue(volume.sha256.matches(Regex("[a-f0-9]{64}")))
        }
    }

    @Test fun sharedComparisonKeepsScopeEditionsAndPageLinksAndRespectsSchoolFilter() {
        val comparison = Catalog.comparisons.first { it.id == "travel-qasr" }
        val all = comparison.shareText()
        assertTrue(all.contains(comparison.scope))
        comparison.opinions.forEach { opinion ->
            assertTrue(all.contains("المذهب ${opinion.school}"))
            opinion.references.forEach { assertTrue(all.contains(it.source)); assertTrue(all.contains(it.edition)) }
        }
        val hanafi = comparison.shareText("الحنفي")
        assertTrue(hanafi.contains("المذهب الحنفي"))
        assertFalse(hanafi.contains("المذهب المالكي"))
        assertTrue(hanafi.contains("ليس فتوى لحالة شخصية"))
    }
}
