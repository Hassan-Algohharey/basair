package com.algohary.basair

import android.content.Context
import android.net.ConnectivityManager
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

// Pinned to the reviewed data commit before distribution.
internal const val QURAN_HOST = "https://raw.githubusercontent.com/Hassan-Algohharey/basair/550e6fbf9714601273b86020f44111aff10b2e21/"
internal data class QuranSurah(val id: Int, val name: String, val englishName: String, val ayahPages: List<Int>)
internal data class QuranVerse(val surah: Int, val ayah: Int, val text: String, val tafsir: String, val juz: Int, val originalPage: Int = 0)
internal data class QuranPage(val page: Int, val verses: List<QuranVerse>)
internal data class QuranIndex(val source: JSONObject, val surahs: List<QuranSurah>, val pages: List<PageSpec>,
    val juzPages: List<Int>, val archive: PageSpec) {
    fun pageFor(surah: Int, ayah: Int) = quranPageFor(surah, ayah, surahs.map { it.ayahPages })
    fun firstVerse(page: Int): Pair<Int, Int> {
        require(page in 1..pages.size)
        surahs.forEach { s -> s.ayahPages.indexOf(page).takeIf { it >= 0 }?.let { return s.id to it + 1 } }
        error("Missing Quran page")
    }
    fun expected(page: Int) = surahs.flatMap { s -> s.ayahPages.mapIndexedNotNull { i, p -> if (p == page) s.id to i + 1 else null } }
}

internal fun quranPageFor(surah: Int, ayah: Int, pages: List<List<Int>>): Int? =
    pages.getOrNull(surah - 1)?.getOrNull(ayah - 1)

internal fun quranAnchor(value: String, pages: List<List<Int>>): Pair<Int, Int>? {
    val parts = value.split(':')
    if (parts.size != 2) return null
    val s = parts[0].toIntOrNull() ?: return null
    val a = parts[1].toIntOrNull() ?: return null
    return (s to a).takeIf { quranPageFor(s, a, pages) != null }
}

internal fun unpackQuran(archive: File, directory: File, specs: List<PageSpec>, active: () -> Unit = {}, progress: (Int) -> Unit = {}) {
    val seen = mutableSetOf<Int>()
    ZipInputStream(archive.inputStream().buffered()).use { zip ->
        while (true) {
            active()
            val entry = zip.nextEntry ?: break
            val page = entry.name.removeSuffix(".json").toIntOrNull()
            check(!entry.isDirectory && page != null && page in 1..specs.size && entry.name == "$page.json" && seen.add(page))
            storePage(zip, File(directory, "$page.json"), specs[page - 1], active)
            zip.closeEntry()
            progress(seen.size)
        }
    }
    check(seen.size == specs.size)
}

internal class QuranOfflineException : Exception()
internal class QuranWifiException : Exception()

internal class QuranRepository(private val context: Context, val index: QuranIndex) {
    private val prefs = context.getSharedPreferences("basair-quran", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "quran/${index.source.getString("sourceSha256").take(16)}")
    private val lock = operationLock
    private val network = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    fun offlineOnly() = prefs.getBoolean("offlineOnly", false)
    fun wifiOnly() = prefs.getBoolean("wifiOnly", true)
    fun setOffline(value: Boolean) { prefs.edit().putBoolean("offlineOnly", value).apply() }
    fun setWifi(value: Boolean) { prefs.edit().putBoolean("wifiOnly", value).apply() }
    fun savedAnchor() = quranAnchor(prefs.getString("position", "").orEmpty(), index.surahs.map { it.ayahPages })
    fun savedPage() = savedAnchor()?.let { index.pageFor(it.first, it.second) }
    fun savePosition(s: Int, a: Int) { require(index.pageFor(s, a) != null); prefs.edit().putString("position", "$s:$a").apply() }
    fun bookmarks(): List<Pair<Int, Int>> = prefs.getStringSet("bookmarks", emptySet()).orEmpty()
        .mapNotNull { quranAnchor(it, index.surahs.map { s -> s.ayahPages }) }.sortedWith(compareBy<Pair<Int,Int>> { it.first }.thenBy { it.second })
    fun toggleBookmark(page: Int) {
        val anchors = bookmarks().toMutableSet()
        val existing = anchors.filter { index.pageFor(it.first, it.second) == page }
        if (existing.isEmpty()) anchors.add(index.firstVerse(page)) else anchors.removeAll(existing.toSet())
        prefs.edit().putStringSet("bookmarks", anchors.map { "${it.first}:${it.second}" }.toSet()).apply()
    }
    private fun file(page: Int) = File(directory, "$page.json")
    private fun cached(page: Int): File? = file(page).takeIf { it.isFile && it.length() == index.pages[page - 1].bytes && sha256(it) == index.pages[page - 1].sha256 }
    fun cachedPages() = (1..index.pages.size).filter { cached(it) != null }
    private fun decode(page: Int, file: File): QuranPage {
        val json = JSONObject(file.readText())
        check(json.getInt("page") == page)
        val array = json.getJSONArray("ayahs")
        val verses = (0 until array.length()).map { i -> array.getJSONObject(i).let {
            QuranVerse(it.getInt("surah"), it.getInt("ayah"), it.getString("text"), it.getString("tafsir"), it.getInt("juz"), it.optInt("originalPage", 0))
        } }
        check(verses.map { it.surah to it.ayah } == index.expected(page))
        check(verses.all { it.text.isNotBlank() && it.tafsir.isNotBlank() && it.juz in 1..30 })
        return QuranPage(page, verses)
    }
    suspend fun open(page: Int, foreground: Boolean = true): QuranPage = withContext(Dispatchers.IO) { lock.withLock {
        require(page in 1..index.pages.size)
        var local = cached(page)
        if (local == null) {
            if (offlineOnly()) throw QuranOfflineException()
            if (!foreground && wifiOnly() && network.isActiveNetworkMetered) throw QuranWifiException()
            val conn = connection(index.source.getString("path") + "/$page.json")
            try {
                check(conn.responseCode == 200)
                val task = currentCoroutineContext()
                local = conn.inputStream.use { storePage(it, file(page), index.pages[page - 1]) { task.ensureActive() } }
            } finally { conn.disconnect() }
        }
        decode(page, local!!)
    } }
    suspend fun prefetch(page: Int) {
        if (offlineOnly() || (wifiOnly() && network.isActiveNetworkMetered)) return
        val order = pageWindow(page, index.pages.size).filter { it != page }
            .sortedWith(compareBy<Int> { kotlin.math.abs(it - page) }.thenByDescending { it })
        for (next in order) {
            currentCoroutineContext().ensureActive()
            if (offlineOnly() || (wifiOnly() && network.isActiveNetworkMetered)) break
            try { open(next, false) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { break }
        }
    }
    suspend fun search(query: String): List<QuranVerse> = withContext(Dispatchers.IO) { lock.withLock {
        val terms = normalizeQuranSearch(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return@withLock emptyList()
        val results = mutableListOf<QuranVerse>()
        for (page in cachedPages()) {
            currentCoroutineContext().ensureActive()
            decode(page, file(page)).verses.forEach { verse ->
                val text = normalizeQuranSearch(verse.text)
                if (terms.all { it in text } && results.size < 100) results += verse
            }
        }
        results
    } }
    suspend fun clearDownloads() = withContext(Dispatchers.IO) { lock.withLock {
        directory.listFiles().orEmpty().filter { it.isFile }.forEach { check(it.delete()) }
    } }
    suspend fun downloadAll(progress: (Int, Long) -> Unit) = withContext(Dispatchers.IO) { lock.withLock {
        val existing = cachedPages().size
        if (existing == index.pages.size) {
            File(directory, "all.zip").delete(); File(directory, "all.zip.part").delete()
            progress(existing, index.archive.bytes); return@withLock
        }
        if (offlineOnly()) throw QuranOfflineException()
        if (wifiOnly() && network.isActiveNetworkMetered) throw QuranWifiException()
        check(directory.isDirectory || directory.mkdirs())
        val archive = File(directory, "all.zip")
        val part = File(directory, "all.zip.part")
        if (!archive.isFile || archive.length() != index.archive.bytes || sha256(archive) != index.archive.sha256) {
            if (part.isFile && part.length() == index.archive.bytes && sha256(part) == index.archive.sha256) {
                check(!archive.exists() || archive.delete()); check(part.renameTo(archive))
            } else {
            if (part.isFile && part.length() >= index.archive.bytes) check(part.delete())
            var offset = part.takeIf { it.exists() }?.length() ?: 0
            val conn = connection(index.source.getJSONObject("archive").getString("path"))
            if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")
            try {
                val response = conn.responseCode
                check(response == 200 || response == 206)
                if (response == 206) check(conn.getHeaderField("Content-Range")?.startsWith("bytes $offset-") == true) else offset = 0
                val task = currentCoroutineContext()
                var total = offset
                // Open once after deciding whether the server honored the range.
                conn.inputStream.use { input -> java.io.FileOutputStream(part, offset > 0).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        task.ensureActive()
                        if (offlineOnly()) throw QuranOfflineException()
                        if (wifiOnly() && network.isActiveNetworkMetered) throw QuranWifiException()
                        val count = input.read(buffer)
                        task.ensureActive()
                        if (count < 0) break
                        total += count; check(total <= index.archive.bytes)
                        output.write(buffer, 0, count); progress(existing, total)
                    }
                } }
                task.ensureActive()
                if (total != index.archive.bytes || sha256(part) != index.archive.sha256) { part.delete(); error("Invalid Quran archive") }
                check(!archive.exists() || archive.delete()); check(part.renameTo(archive))
            } finally { conn.disconnect() }
            }
        }
        val task = currentCoroutineContext()
        unpackQuran(archive, directory, index.pages, { task.ensureActive() }) { progress(it, index.archive.bytes) }
        check(archive.delete())
    } }
    private fun connection(path: String): HttpsURLConnection {
        check(Regex("https://raw\\.githubusercontent\\.com/Hassan-Algohharey/basair/[a-f0-9]{40}/").matches(QURAN_HOST))
        check(path.startsWith(index.source.getString("path") + "/") && !path.contains(".."))
        return (URL(QURAN_HOST + path).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000; instanceFollowRedirects = false
            setRequestProperty("Accept-Encoding", "identity")
        }
    }
    companion object {
        private val operationLock = Mutex()
        suspend fun create(context: Context): QuranRepository = withContext(Dispatchers.IO) {
            val json = JSONObject(context.assets.open("quran-index.json").bufferedReader().use { it.readText() })
            val list = json.getJSONArray("surahs")
            val surahs = (0 until list.length()).map { i -> list.getJSONObject(i).let { s ->
                val array = s.getJSONArray("ayahPages")
                val pages = (0 until array.length()).map { array.getInt(it) }
                check(s.getInt("ayahCount") == pages.size)
                QuranSurah(s.getInt("id"), s.getString("name"), s.getString("englishName"), pages)
            } }
            val array = json.getJSONArray("pages")
            val pages = (0 until array.length()).map { i -> array.getJSONObject(i).let { PageSpec(it.getString("sha256"), it.getLong("bytes")) } }
            val parts = json.getJSONArray("juzPages"); val juzPages = (0 until parts.length()).map { parts.getInt(it) }
            val archive = json.getJSONObject("archive").let { PageSpec(it.getString("sha256"), it.getLong("bytes")) }
            check(surahs.map { it.id } == (1..114).toList() && surahs.sumOf { it.ayahPages.size } == 6236)
            check(pages.size == 604 && surahs.flatMap { it.ayahPages }.toSet() == (1..604).toSet())
            check(juzPages.size == 30 && juzPages.first() == 1 && juzPages.zipWithNext().all { it.first < it.second })
            check(pages.all { it.bytes > 0 && it.sha256.matches(Regex("[a-f0-9]{64}")) })
            check(json.getString("path").matches(Regex("content/quran/[a-f0-9]{16}")))
            QuranRepository(context.applicationContext, QuranIndex(json, surahs, pages, juzPages, archive))
        }
    }
}

internal fun normalizeQuranSearch(value: String) = normalizeArabic(value).replace('ٱ', 'ا')
    .replace(Regex("[\\u06d6-\\u06ed]"), "")
