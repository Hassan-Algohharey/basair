package com.algohary.basair

import android.graphics.Typeface
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
internal fun QuranEntry(onOpen: () -> Unit) {
    val context = LocalContext.current
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(context.getString(R.string.quran_entry), style = MaterialTheme.typography.titleLarge)
            Text(context.getString(R.string.quran_entry_description), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun QuranFeature() {
    val context = LocalContext.current
    var repository by remember { mutableStateOf<QuranRepository?>(null) }
    var fonts by remember { mutableStateOf<Pair<FontFamily, FontFamily>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        failed = false
        try {
            val repo = QuranRepository.create(context)
            val pair = withContext(Dispatchers.IO) {
                FontFamily(Typeface.createFromAsset(context.assets, repo.index.source.getString("fontAsset"))) to
                    FontFamily(Typeface.createFromAsset(context.assets, repo.index.source.getString("tafsirFontAsset")))
            }
            fonts = pair; repository = repo
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { failed = true }
    }
    val repo = repository
    val readyFonts = fonts
    if (repo == null || readyFonts == null) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(context.getString(R.string.quran_title), style = MaterialTheme.typography.titleLarge)
            if (failed) {
                Text(context.getString(R.string.quran_data_error))
                Button(onClick = { retry++ }) { Text(context.getString(R.string.quran_retry)) }
            } else CircularProgressIndicator()
        }
    } else key(repo) { QuranReady(repo, readyFonts.first, readyFonts.second) }
}

@Composable
private fun QuranReady(repo: QuranRepository, quranFont: FontFamily, tafsirFont: FontFamily) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data = repo.index
    fun sourceDetails() = data.source.getString("sourceDetails") + "\n\n" +
        context.getString(R.string.quran_source_note) + "\n\n" +
        data.source.getString("sourceUrl") + "\n" + data.source.getString("navigationSourceUrl")
    fun bookmarkRows() = repo.bookmarks().map { (s, a) ->
        QuranUiBookmark(data.pageFor(s, a)!!, "${data.surahs[s - 1].name} · $a")
    }.distinctBy { it.page }
    var ui by remember(repo) { mutableStateOf(QuranUiState(
        surahs = data.surahs.map { QuranUiSurah(it.id, it.name, it.englishName, it.ayahPages.size, it.ayahPages.first()) },
        page = repo.savedPage() ?: 1, savedPage = repo.savedPage(), bookmarks = bookmarkRows(),
        offlineOnly = repo.offlineOnly(), wifiOnly = repo.wifiOnly(), sourceTitle = data.source.getString("sourceTitle"),
        sourceDetails = sourceDetails(), sourceUrl = data.source.getString("sourceUrl"), navigationSourceUrl = data.source.getString("navigationSourceUrl"),
        totalDownloadBytes = data.archive.bytes, fontFamily = quranFont, tafsirFontFamily = tafsirFont
    )) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var prefetchJob by remember { mutableStateOf<Job?>(null) }
    var fullJob by remember { mutableStateOf<Job?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    fun message(error: Exception) = context.getString(when (error) {
        is QuranOfflineException -> R.string.quran_missing_offline
        is QuranWifiException -> R.string.quran_wait_wifi
        else -> R.string.quran_network_error
    })
    suspend fun countCache() = withContext(Dispatchers.IO) { repo.cachedPages().size }
    fun open(page: Int, focus: Pair<Int, Int>? = null) {
        if (page !in 1..data.pages.size) return
        loadJob?.cancel(); prefetchJob?.cancel()
        loadJob = scope.launch {
            ui = ui.copy(page = page, juz = data.source.getJSONArray("pages").getJSONObject(page - 1).getInt("juz"),
                loading = true, error = null, ayahs = emptyList(), focusSurah = focus?.first, focusAyah = focus?.second)
            try {
                val result = repo.open(page)
                val anchor = focus ?: data.firstVerse(page)
                repo.savePosition(anchor.first, anchor.second)
                ui = ui.copy(loading = false, savedPage = page, cachedPages = countCache(),
                    ayahs = result.verses.map { QuranUiAyah(it.surah, it.ayah, data.surahs[it.surah - 1].name, it.text, it.tafsir, it.juz) })
                prefetchJob = scope.launch {
                    try { repo.prefetch(page); ui = ui.copy(cachedPages = countCache()) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { /* A failed background request leaves the requested page readable. */ }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { ui = ui.copy(loading = false, error = message(error)) }
        }
    }
    LaunchedEffect(repo) { ui = ui.copy(cachedPages = countCache()) }
    val onAction: (QuranAction) -> Unit = { action -> when (action) {
        is QuranAction.OpenPage -> open(action.page)
        is QuranAction.OpenAyah -> data.pageFor(action.surah, action.ayah)?.let { open(it, action.surah to action.ayah) }
        is QuranAction.OpenJuz -> data.juzPages.getOrNull(action.juz - 1)?.let { open(it) }
        is QuranAction.ToggleBookmark -> { repo.toggleBookmark(ui.page); ui = ui.copy(bookmarks = bookmarkRows()) }
        is QuranAction.DownloadPage, QuranAction.Retry -> open(ui.page, ui.focusSurah?.let { s -> ui.focusAyah?.let { s to it } })
        is QuranAction.SetWifiOnly -> { repo.setWifi(action.enabled); ui = ui.copy(wifiOnly = action.enabled) }
        is QuranAction.SetOfflineOnly -> {
            repo.setOffline(action.enabled); ui = ui.copy(offlineOnly = action.enabled)
            if (action.enabled) { loadJob?.cancel(); prefetchJob?.cancel(); fullJob?.cancel(); ui = ui.copy(loading = false) }
        }
        is QuranAction.CancelDownload -> fullJob?.cancel()
        is QuranAction.DownloadAll -> if (fullJob?.isActive != true) {
            fullJob = scope.launch {
                prefetchJob?.cancelAndJoin()
                ui = ui.copy(downloading = true, error = null, downloadedPages = 0, downloadedBytes = 0)
                try {
                    repo.downloadAll { pages, bytes -> scope.launch { ui = ui.copy(downloadedPages = pages, downloadedBytes = bytes) } }
                    ui = ui.copy(cachedPages = countCache(), downloadedPages = data.pages.size)
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { ui = ui.copy(error = message(error)) }
                finally { ui = ui.copy(downloading = false) }
            }
        }
        is QuranAction.ClearDownloaded -> scope.launch {
            loadJob?.cancelAndJoin(); prefetchJob?.cancelAndJoin(); fullJob?.cancelAndJoin(); searchJob?.cancelAndJoin()
            try {
                repo.clearDownloads()
                ui = ui.copy(cachedPages = 0, downloadedPages = 0, downloadedBytes = 0, loading = false,
                    ayahs = emptyList(), searchResults = emptyList(), searching = false, error = context.getString(R.string.quran_downloads_cleared))
            } catch (error: Exception) { ui = ui.copy(error = message(error)) }
        }
        is QuranAction.Search -> {
            searchJob?.cancel()
            searchJob = scope.launch {
                ui = ui.copy(searching = true, searchResults = emptyList(), error = null)
                try {
                    val results = repo.search(action.query)
                    ui = ui.copy(searching = false, searchResults = results.map {
                        QuranUiSearchResult(it.surah, it.ayah, context.getString(R.string.quran_ayah_heading, data.surahs[it.surah - 1].name, it.ayah), it.text)
                    })
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { ui = ui.copy(searching = false, error = message(error)) }
            }
        }
    } }
    QuranScreen(ui, onAction)
}
