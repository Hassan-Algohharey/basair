package com.algohary.basair

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

internal const val MADINAH_ARCHIVE_URL = "https://dm.qurancomplex.gov.sa/Download/1441-AI-hafs.zip"
internal const val MADINAH_ARCHIVE_SHA = "280c5d71ca16aaeeb3a343be1b92c76fa6df71d0671e202c026fde12402d9eef"
internal const val MADINAH_ARCHIVE_BYTES = 466430736L
internal val madinahBook = Book(
    id = "madinah-hafs-1441", title = "مصحف المدينة النبوية — حفص، النسخة المصورة", author = "",
    subject = "tafsir", description = "صفحات النسخة الرقمية الرسمية 1441هـ؛ تُعرض بيانات PDF المتوافقة داخل ملفات المتجهات الأصلية دون تعديل. التفسير الميسر متاح بزر التفسير لكل صفحة.",
    topics = listOf("القرآن الكريم"), source = "https://dm.qurancomplex.gov.sa/", asset = "madinah-hafs-1441.pdf",
    pages = 604, publisher = "مجمع الملك فهد لطباعة المصحف الشريف", edition = "النسخة الرقمية حفص 1441هـ، ملفات Hafs39 DM",
    licenseName = "إتاحة رسمية مجانية للاستخدام في البرامج الحاسوبية", licenseUrl = "https://dm.qurancomplex.gov.sa/rights/",
    rightsNote = "الصفحات من الأرشيف الرسمي دون تغيير؛ لا يُنزّل الأرشيف الكبير على الهاتف، بل الجزء المضغوط للصفحة المطلوبة فقط مع التحقق من الحجم والبصمة.",
    sha256 = MADINAH_ARCHIVE_SHA, pageOnly = true
)

internal fun validMadinahRange(offset: Long, compressedBytes: Long, rawBytes: Long): Boolean =
    offset >= 0 && compressedBytes in 1..8_000_000 && rawBytes in 1..12_000_000 && offset <= MADINAH_ARCHIVE_BYTES - compressedBytes

internal object MadinahPages {
    fun manifest(context: Context): JSONObject = JSONObject(context.assets.open("madinah-pages.json").bufferedReader().use { it.readText() })
    suspend fun fetch(context: Context, page: Int, target: File, spec: PageSpec): File {
        require(page in 1..604)
        val row = manifest(context).getJSONArray("pages").getJSONObject(page - 1)
        val offset = row.getLong("compressedOffset")
        val size = row.getLong("compressedBytes")
        check(validMadinahRange(offset, size, spec.bytes))
        val connection = URL(MADINAH_ARCHIVE_URL).openConnection() as HttpsURLConnection
        connection.connectTimeout = 20_000; connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Range", "bytes=$offset-${offset + size - 1}")
        connection.setRequestProperty("Accept-Encoding", "identity")
        try {
            check(connection.responseCode == 206)
            check(connection.getHeaderField("Content-Range") == "bytes $offset-${offset + size - 1}/$MADINAH_ARCHIVE_BYTES")
            val task = currentCoroutineContext()
            val compressed = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(size.toInt())
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    task.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size().toLong() + count <= size)
                    output.write(buffer, 0, count)
                }
                check(output.size().toLong() == size)
                output.toByteArray()
            }
            val inflater = Inflater(true)
            try {
                InflaterInputStream(ByteArrayInputStream(compressed), inflater).use { input ->
                    return storePage(input, target, spec) { task.ensureActive() }
                }
            } finally { inflater.end() }
        } finally { connection.disconnect() }
    }
}
