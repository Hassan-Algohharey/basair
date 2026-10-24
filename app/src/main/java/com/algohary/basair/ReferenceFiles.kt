package com.algohary.basair

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal data class ReferenceVolume(val volume: Int, val pages: Int, val bytes: Long, val sha256: String) {
    val reference get() = kuwaitReference(volume, "1", 1)
    val fileName get() = "kuwait-$volume-${sha256.take(16)}.pdf"
    fun asBook() = Book(
        id = "reference-$volume", title = "الموسوعة الفقهية الكويتية — الجزء $volume",
        author = "وزارة الأوقاف والشؤون الإسلامية — الكويت", subject = "fiqh",
        description = "مجلد مرجعي للمقارنات، منزّل من المصدر المحدد دون تغيير. صفحات الإحالات المطبوعة تطابق صفحات الملف في المواضع الموثقة.",
        topics = emptyList(), source = reference.pdfUrl, asset = "", pages = pages,
        publisher = reference.publisher, edition = reference.edition,
        licenseName = "إتاحة التنزيل من المصدر؛ حقوق الطبعة محفوظة للوزارة",
        licenseUrl = "https://islamhouse.com/ar/books/191979/",
        rightsNote = "لا يُضمّن هذا المجلد في حزمة التطبيق. يُنزّل بطلبك للقراءة على جهازك، ولا تعني إتاحته ترخيصًا لإعادة نشر الطبعة أو استخدامها تجاريًا.",
        sha256 = sha256
    )
}

internal val referenceVolumes = listOf(
    ReferenceVolume(23, 383, 8896496, "9ad75ff0a215db16aebf9dc468462d3f2c5991bf0373094b110be70c9c222e3c"),
    ReferenceVolume(27, 406, 10479245, "43b61b9d3700e85b1c9d81808e48863adb43819817b6f0a8b143f669698a1d8f"),
    ReferenceVolume(31, 399, 9648943, "9ee4f68d5447d2a8070c7dcf2bcdeea789a7fb73b51dd80580417407ba20c491"),
    ReferenceVolume(43, 450, 10779599, "aace4bd070b455e23f2d6eae57c4ade1d45aa058c07d20a09453be18639169ed")
).associateBy { it.volume }

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun verifiedReferenceFile(directory: File, volume: ReferenceVolume): File? =
    File(directory, volume.fileName).takeIf { it.isFile && it.length() == volume.bytes && sha256(it) == volume.sha256 }

// A unique partial file prevents a cancelled attempt from corrupting another attempt.
internal fun storeReference(input: InputStream, directory: File, volume: ReferenceVolume,
    onProgress: (Long) -> Unit = {}, checkActive: () -> Unit = {}): File {
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create references directory" }
    val partial = File.createTempFile("kuwait-${volume.volume}-", ".part", directory)
    try {
        var total = 0L
        partial.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                check(total <= volume.bytes) { "Unexpected reference size" }
                output.write(buffer, 0, count)
                onProgress(total)
            }
        }
        checkActive()
        check(total == volume.bytes && sha256(partial) == volume.sha256) { "Reference checksum mismatch" }
        verifiedReferenceFile(directory, volume)?.let { return it }
        val target = File(directory, volume.fileName)
        check(!target.exists() || target.delete()) { "Cannot replace invalid reference" }
        check(partial.renameTo(target)) { "Cannot store reference" }
        return target
    } finally { partial.delete() }
}

internal fun Comparison.shareText(school: String? = null): String = buildString {
    appendLine("بصائر · مقارنة فقهية")
    appendLine(title)
    appendLine(topic)
    appendLine(scope)
    opinions.filter { school == null || it.school == school }.forEach { opinion ->
        appendLine()
        appendLine("المذهب ${opinion.school}")
        appendLine(opinion.summary)
        opinion.references.forEach { reference ->
            appendLine(reference.label)
            appendLine(reference.publisher)
            appendLine(reference.edition)
            appendLine(reference.source)
        }
    }
    appendLine()
    append("تلخيص تحريري من مرجع مقارن؛ راجع حواشي المصدر للمراجع المذهبية الأصلية. ليس فتوى لحالة شخصية.")
}
