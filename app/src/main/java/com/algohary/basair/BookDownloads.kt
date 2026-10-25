package com.algohary.basair

import android.content.Context
import android.net.ConnectivityManager
import java.io.File
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import androidx.core.content.edit

internal fun pageWindow(page: Int, total: Int): IntRange {
    require(total > 0 && page in 1..total)
    return maxOf(1, page - 7)..minOf(total, page + 7)
}

internal fun pageRanges(pages: List<Int>): String {
    val sorted = pages.distinct().sorted()
    if (sorted.isEmpty()) return "—"
    val ranges = mutableListOf<String>()
    var start = sorted.first()
    var end = start
    fun append() { ranges += if (start == end) "$start" else "$start–$end" }
    for (page in sorted.drop(1)) {
        if (page == end + 1) end = page else { append(); start = page; end = page }
    }
    append()
    return ranges.joinToString(", ")
}

internal data class PageSpec(val sha256: String, val bytes: Long)
internal data class PageFile(val file: File, val index: Int, val count: Int)
internal const val BOOK_HOST = "https://raw.githubusercontent.com/Hassan-Algohharey/basair/9ca0530e333f3eb337caf6f71912e739b30d33a9/"

internal fun storePage(input: InputStream, target: File, spec: PageSpec, active: () -> Unit = {}): File {
    target.parentFile!!.mkdirs()
    val part = File.createTempFile("page-", ".part", target.parentFile)
    try {
        var size = 0L
        part.outputStream().use { output ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                active()
                val count = input.read(buffer)
                if (count < 0) break
                size += count
                check(size <= spec.bytes)
                output.write(buffer, 0, count)
            }
        }
        active()
        check(size == spec.bytes && sha256(part) == spec.sha256)
        check(!target.exists() || target.delete())
        check(part.renameTo(target))
        return target
    } finally { part.delete() }
}

internal class BookDownloads(private val context: Context) {
    private companion object { val fullDownloadLock = Mutex() }
    private val preferences = context.getSharedPreferences("basair", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "library")
    private val pagesRoot = File(root, "pages")
    private val fullRoot = File(root, "full")
    @Volatile private var retainedPaths: Set<String> = emptySet()
    private val manifest by lazy { JSONObject(context.assets.open("page-manifest.json").bufferedReader().use { it.readText() }) }
    private fun entry(book: Book): JSONObject = manifest.getJSONObject(book.id).also {
        check(it.getString("originalSha256") == book.sha256 && it.getJSONArray("pages").length() == book.pages)
    }
    private fun directory(book: Book) = File(pagesRoot, "${book.id}-${book.sha256.take(16)}")
    private fun fullTarget(book: Book) = File(fullRoot, "${book.id}-${book.sha256.take(16)}.pdf")
    private val verifiedFull = mutableMapOf<String, Pair<Long, Long>>()

    @Synchronized fun fullFile(book: Book): File? {
        val target = fullTarget(book)
        if (!target.exists()) {
            val legacy = File(context.cacheDir, "books/${target.name}")
            if (legacy.isFile && legacy.length() == book.bytes && sha256(legacy) == book.sha256) {
                fullRoot.mkdirs()
                legacy.renameTo(target)
            }
        }
        if (!target.isFile || target.length() != book.bytes) return null
        val stamp = target.length() to target.lastModified()
        if (verifiedFull[book.id] != stamp) {
            if (sha256(target) != book.sha256) return null
            verifiedFull[book.id] = stamp
        }
        return target
    }
    private fun spec(book: Book, page: Int): PageSpec {
        val row = entry(book).getJSONArray("pages").getJSONObject(page - 1)
        return PageSpec(row.getString("sha256"), row.getLong("bytes"))
    }
    fun pageFile(book: Book, page: Int): File? {
        val file = File(directory(book), "$page.pdf")
        if (!file.isFile) return null
        val expected = spec(book, page)
        if (file.length() != expected.bytes || sha256(file) != expected.sha256) { file.delete(); return null }
        file.setLastModified(System.currentTimeMillis())
        return file
    }
    suspend fun open(book: Book, page: Int, foreground: Boolean = true): PageFile = withContext(Dispatchers.IO) {
        require(page in 1..book.pages)
        if (foreground) retainedPaths = pageWindow(page, book.pages).map { File(directory(book), "$it.pdf").absolutePath }.toSet()
        fullFile(book)?.let { return@withContext PageFile(it, page - 1, book.pages) }
        val file = pageFile(book, page) ?: run {
            check(!offlineOnly()) { "Page not cached" }
            val url = BOOK_HOST + entry(book).getString("path") + "/$page.pdf"
            connection(url).let { connection ->
                try {
                    check(connection.responseCode == 200)
                    val task = currentCoroutineContext()
                    connection.inputStream.use { storePage(it, File(directory(book), "$page.pdf"), spec(book, page)) { task.ensureActive() } }
                } finally { connection.disconnect() }
            }
        }
        PageFile(file, 0, 1)
    }
    fun wifiOnly() = preferences.getBoolean("prefetchWifiOnly", true)
    fun offlineOnly() = preferences.getBoolean("readerOfflineOnly", false)
    fun setOfflineOnly(value: Boolean) { preferences.edit { putBoolean("readerOfflineOnly", value) } }
    fun setWifiOnly(value: Boolean) { preferences.edit { putBoolean("prefetchWifiOnly", value) } }
    fun fullWifiOnly() = preferences.getBoolean("fullDownloadWifiOnly", false)
    fun setFullWifiOnly(value: Boolean) { preferences.edit { putBoolean("fullDownloadWifiOnly", value) } }
    fun limitMb() = preferences.getInt("pageCacheLimitMb", 100)
    fun setLimitMb(value: Int) { require(value in listOf(50, 100, 250)); preferences.edit { putInt("pageCacheLimitMb", value) } }
    suspend fun prefetch(book: Book, page: Int) = withContext(Dispatchers.IO) {
        if (fullFile(book) != null || offlineOnly()) return@withContext
        val network = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (wifiOnly() && network.isActiveNetworkMetered) return@withContext
        // The opened page has already rendered. Nearest upcoming pages have priority.
        val order = pageWindow(page, book.pages).filter { it != page }.sortedWith(compareBy<Int> { kotlin.math.abs(it - page) }.thenByDescending { it })
        try {
            for (next in order) {
                currentCoroutineContext().ensureActive()
                if (offlineOnly() || (wifiOnly() && network.isActiveNetworkMetered)) break
                try { open(book, next, foreground = false) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { break } // Cached pages remain readable offline.
            }
        } finally { trim(book, page) }
    }
    fun cachedPages(book: Book): List<Int> = if (fullFile(book) != null) (1..book.pages).toList() else
        directory(book).listFiles().orEmpty().mapNotNull { file ->
            val number = file.nameWithoutExtension.toIntOrNull()
            if (file.extension != "pdf" || number == null || number !in 1..book.pages) null else {
                val expected = spec(book, number)
                number.takeIf { file.length() == expected.bytes && sha256(file) == expected.sha256 }
            }
        }.sorted()
    fun cachedCount(book: Book): Int = cachedPages(book).size
    fun pageBytes() = pagesRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun bookBytes(book: Book) = (fullTarget(book).takeIf { it.exists() }?.length() ?: 0) + (directory(book).listFiles()?.sumOf { it.length() } ?: 0)
    fun partialBytes(book: Book) = File(fullRoot, fullTarget(book).name + ".part").takeIf { it.isFile }?.length() ?: 0
    @Synchronized fun clearBook(book: Book) {
        File(context.cacheDir, "books/${fullTarget(book).name}").delete()
        fullTarget(book).delete()
        File(fullRoot, fullTarget(book).name + ".part").delete()
        directory(book).listFiles()?.forEach { it.delete() }
        verifiedFull.remove(book.id)
    }
    fun clearPages() { pagesRoot.walkTopDown().filter { it.isFile }.forEach { it.delete() } }
    fun trim(book: Book, page: Int) {
        val protected = retainedPaths.ifEmpty { pageWindow(page, book.pages).map { File(directory(book), "$it.pdf").absolutePath }.toSet() }
        var bytes = pageBytes()
        val limit = limitMb() * 1_000_000L
        pagesRoot.walkTopDown().filter { it.isFile && it.extension == "pdf" && it.absolutePath !in protected }
            .sortedBy { it.lastModified() }.forEach { if (bytes > limit) { val size = it.length(); if (it.delete()) bytes -= size } }
    }
    suspend fun downloadFull(book: Book, progress: (Long) -> Unit): File = fullDownloadLock.withLock { withContext(Dispatchers.IO) {
        fullFile(book)?.let { return@withContext it }
        check(!offlineOnly()) { "Offline reading only" }
        val network = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        check(!fullWifiOnly() || !network.isActiveNetworkMetered) { "Unmetered connection required" }
        fullRoot.mkdirs()
        val target = fullTarget(book)
        val partial = File(fullRoot, target.name + ".part")
        var offset = partial.takeIf { it.isFile }?.length() ?: 0
        if (offset >= book.bytes) { partial.delete(); offset = 0 }
        val connection = connection(BOOK_HOST + "content/originals/" + book.asset.substringAfterLast('/'))
        try {
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val response = connection.responseCode
            check(response == 200 || response == 206)
            if (response == 206) check(connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-") == true)
            if (response == 200) offset = 0
            val task = currentCoroutineContext()
            var total = offset
            progress(total)
            connection.inputStream.use { input ->
                java.io.FileOutputStream(partial, offset > 0).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        task.ensureActive()
                        check(!fullWifiOnly() || !network.isActiveNetworkMetered) { "Unmetered connection required" }
                        val count = input.read(buffer)
                        if (count < 0) break
                        task.ensureActive()
                        total += count
                        check(total <= book.bytes)
                        output.write(buffer, 0, count)
                        progress(total)
                    }
                }
            }
            task.ensureActive()
            if (total != book.bytes || sha256(partial) != book.sha256) { partial.delete(); error("Book checksum mismatch") }
            check(!target.exists() || target.delete())
            check(partial.renameTo(target))
            target
        } finally { connection.disconnect() }
    } }
    private fun connection(url: String): HttpsURLConnection {
        val parsed = URL(url)
        check(parsed.protocol == "https" && parsed.host == "raw.githubusercontent.com")
        return (parsed.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000; instanceFollowRedirects = false
            setRequestProperty("Accept-Encoding", "identity")
        }
    }
}
