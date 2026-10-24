package com.algohary.basair

import android.text.Html
import android.text.style.TypefaceSpan
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class QuranUiSurah(val id: Int, val name: String, val englishName: String,
    val ayahCount: Int, val firstPage: Int)
internal data class QuranUiAyah(val surahId: Int, val number: Int, val surahName: String,
    val text: String, val tafsir: String?, val juz: Int? = null)
internal data class QuranUiBookmark(val page: Int, val label: String)
internal data class QuranUiSearchResult(val surahId: Int, val ayah: Int, val label: String, val preview: String)
internal data class QuranUiState(
    val surahs: List<QuranUiSurah>, val page: Int = 1, val totalPages: Int = 604, val juz: Int = 1,
    val ayahs: List<QuranUiAyah> = emptyList(), val loading: Boolean = false, val error: String? = null,
    val savedPage: Int? = null, val bookmarks: List<QuranUiBookmark> = emptyList(),
    val cachedPages: Int = 0, val offlineOnly: Boolean = false,
    val sourceTitle: String = "", val sourceDetails: String = "",
    val searchResults: List<QuranUiSearchResult> = emptyList(), val searching: Boolean = false,
    val fontFamily: FontFamily? = null, val wifiOnly: Boolean = true, val downloading: Boolean = false,
    val downloadedPages: Int = 0, val downloadedBytes: Long = 0, val totalDownloadBytes: Long = 0,
    val focusSurah: Int? = null, val focusAyah: Int? = null,
    val tafsirFontFamily: FontFamily? = null,
    val sourceUrl: String = "", val navigationSourceUrl: String = ""
)
internal sealed interface QuranAction {
    data class OpenPage(val page: Int) : QuranAction
    data class OpenAyah(val surah: Int, val ayah: Int) : QuranAction
    data class OpenJuz(val juz: Int) : QuranAction
    data class Search(val query: String) : QuranAction
    data class SetOfflineOnly(val enabled: Boolean) : QuranAction
    data class SetWifiOnly(val enabled: Boolean) : QuranAction
    data object ToggleBookmark : QuranAction
    data object DownloadPage : QuranAction
    data object DownloadAll : QuranAction
    data object CancelDownload : QuranAction
    data object ClearDownloaded : QuranAction
    data object Retry : QuranAction
}

/** Presentation only: the caller supplies verified scripture, sources and persisted state. */
@Composable
internal fun QuranScreen(state: QuranUiState, onAction: (QuranAction) -> Unit) {
    val context = LocalContext.current
    fun label(id: Int) = context.getString(id)
    val english = LocalConfiguration.current.locales[0].language == "en"
    var index by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var submittedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by rememberSaveable { mutableStateOf("") }
    var indexTab by rememberSaveable { mutableIntStateOf(0) }
    var textSize by rememberSaveable { mutableFloatStateOf(26f) }
    val listState = rememberLazyListState()
    fun open(action: QuranAction) { index = false; onAction(action) }
    LaunchedEffect(index, state.page, state.focusSurah, state.focusAyah, state.ayahs) {
        if (!index) {
            val target = state.ayahs.indexOfFirst { it.surahId == state.focusSurah && it.number == state.focusAyah }
            listState.scrollToItem(if (target >= 0) target + 2 else 0)
        }
    }
    BackHandler(!index && dialog.isEmpty()) { index = true }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(label(R.string.quran_title), style = MaterialTheme.typography.headlineSmall)
            Text(state.sourceTitle.ifBlank { label(R.string.quran_source_pending) }, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { if (index) open(QuranAction.OpenPage(state.page)) else index = true }) { Text(label(if (index) R.string.quran_read else R.string.quran_index)) }
                TextButton(onClick = { dialog = "jump" }) { Text(label(R.string.quran_jump)) }
                TextButton(onClick = { dialog = "bookmarks" }) { Text(label(R.string.quran_bookmarks)) }
                TextButton(onClick = { dialog = "downloads" }) { Text(label(R.string.quran_downloads)) }
                TextButton(onClick = { dialog = "sources" }) { Text(label(R.string.quran_sources)) }
            }
        }
        if (index) {
            state.savedPage?.let { saved -> item {
                OutlinedButton(onClick = { open(QuranAction.OpenPage(saved)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(context.getString(R.string.quran_resume_page, saved))
                }
            } }
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                    label = { Text(label(R.string.quran_search_hint)) }, singleLine = true)
                TextButton(onClick = { submittedQuery = query.trim(); onAction(QuranAction.Search(query.trim())) }, enabled = query.trim().length >= 2 && !state.searching) {
                    Text(label(if (state.searching) R.string.quran_searching else R.string.quran_search_text))
                }
                Text(label(R.string.quran_search_scope), style = MaterialTheme.typography.bodySmall)
                if (submittedQuery == query.trim() && !state.searching && state.searchResults.isEmpty()) {
                    Text(label(R.string.quran_no_results), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(if (submittedQuery == query.trim()) state.searchResults else emptyList(), key = { "result:${it.surahId}:${it.ayah}" }) { result ->
                OutlinedCard(onClick = { open(QuranAction.OpenAyah(result.surahId, result.ayah)) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) { Text(result.label); Text(result.preview, maxLines = 3, fontFamily = state.fontFamily, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl)) }
                }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(indexTab == 0, { indexTab = 0 }, label = { Text(label(R.string.quran_surahs)) })
                FilterChip(indexTab == 1, { indexTab = 1 }, label = { Text(label(R.string.quran_juzs)) })
            } }
            if (indexTab == 0) {
                val visible = state.surahs.filter { query.isBlank() || normalizeArabic("${it.id} ${it.name} ${it.englishName}").contains(normalizeArabic(query.trim())) }
                if (visible.isEmpty()) item { Text(label(R.string.quran_no_surah)) }
                items(visible, key = { "surah:${it.id}" }) { surah ->
                    OutlinedCard(onClick = { open(QuranAction.OpenAyah(surah.id, 1)) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${surah.id}. ${if (english) surah.englishName else surah.name}", style = MaterialTheme.typography.titleMedium)
                            if (english) Text(surah.name, style = MaterialTheme.typography.bodyMedium)
                            Text(context.getString(R.string.quran_surah_metadata, surah.ayahCount, surah.firstPage), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items((1..30).toList(), key = { "juz:$it" }) { juz ->
                    OutlinedButton(onClick = { open(QuranAction.OpenJuz(juz)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(context.getString(R.string.quran_juz_number, juz))
                    }
                }
            }
        } else {
            item {
                Text(context.getString(R.string.quran_page_juz, state.page, state.totalPages, state.juz), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ open(QuranAction.OpenPage(state.page - 1)) }, enabled = state.page > 1) { Text(label(R.string.quran_previous)) }
                    OutlinedButton({ open(QuranAction.OpenPage(state.page + 1)) }, enabled = state.page < state.totalPages) { Text(label(R.string.quran_next)) }
                    TextButton({ onAction(QuranAction.ToggleBookmark) }) {
                        Text(label(if (state.bookmarks.any { it.page == state.page }) R.string.quran_remove_bookmark else R.string.quran_add_bookmark))
                    }
                }
                Text(label(R.string.quran_text_view), style = MaterialTheme.typography.bodySmall)
                if (english) Text(label(R.string.quran_arabic_content), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ textSize = (textSize - 2f).coerceAtLeast(22f) }, enabled = textSize > 22f) { Text(label(R.string.quran_font_smaller)) }
                    TextButton({ textSize = (textSize + 2f).coerceAtMost(36f) }, enabled = textSize < 36f) { Text(label(R.string.quran_font_larger)) }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton({ onAction(QuranAction.Retry) }) { Text(label(R.string.quran_retry)) }
                }
            }
            if (state.ayahs.isEmpty() && !state.loading && state.error == null) item { Text(label(R.string.quran_unloaded)) }
            items(state.ayahs, key = { "ayah:${it.surahId}:${it.number}" }) { ayah ->
                OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(
                    containerColor = if (ayah.surahId == state.focusSurah && ayah.number == state.focusAyah) SoftGreen else Paper
                )) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(context.getString(R.string.quran_ayah_heading, ayah.surahName, ayah.number), style = MaterialTheme.typography.labelLarge)
                    SelectionContainer { Text(ayah.text, Modifier.fillMaxWidth(), fontFamily = state.fontFamily ?: FontFamily.Serif,
                        fontSize = textSize.sp, lineHeight = (textSize * 1.7f).sp, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl)) }
                    HorizontalDivider()
                    Text(label(R.string.quran_tafsir), style = MaterialTheme.typography.titleSmall)
                    val tafsir = remember(ayah.tafsir, state.fontFamily) {
                        ayah.tafsir?.let { quranTafsirText(it, state.fontFamily) }
                    }
                    SelectionContainer {
                        if (tafsir != null) Text(tafsir, Modifier.fillMaxWidth(), fontFamily = state.tafsirFontFamily,
                            fontSize = (textSize * 0.75f).sp, lineHeight = (textSize * 1.3f).sp,
                            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
                        else Text(label(R.string.quran_tafsir_unavailable), style = MaterialTheme.typography.bodyLarge)
                    }
                } }
            }
            if (state.ayahs.isNotEmpty()) item {
                OutlinedButton({ open(QuranAction.OpenPage(state.page + 1)) }, enabled = state.page < state.totalPages, modifier = Modifier.fillMaxWidth()) {
                    Text(label(R.string.quran_next))
                }
            }
        }
    }
    if (dialog == "jump") QuranJumpDialog(state, { dialog = "" }) { action -> dialog = ""; open(action) }
    if (dialog == "sources") AlertDialog(onDismissRequest = { dialog = "" },
        title = { Text(label(R.string.quran_sources)) },
        text = { SelectionContainer { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(state.sourceTitle); Spacer(Modifier.height(8.dp))
            Text(state.sourceDetails.ifBlank { label(R.string.quran_source_pending) })
            if (state.sourceUrl.isNotBlank()) TextButton({ openSource(context, state.sourceUrl) }) {
                Text(label(R.string.quran_official_source))
            }
            if (state.navigationSourceUrl.isNotBlank()) TextButton({ openSource(context, state.navigationSourceUrl) }) {
                Text(label(R.string.quran_navigation_source))
            }
        } } }, confirmButton = { TextButton({ dialog = "" }) { Text(label(R.string.quran_close)) } })
    if (dialog == "bookmarks") AlertDialog(onDismissRequest = { dialog = "" },
        title = { Text(label(R.string.quran_bookmarks)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.bookmarks.isEmpty()) Text(label(R.string.quran_no_bookmarks))
                state.bookmarks.forEach { bookmark -> TextButton({ dialog = ""; open(QuranAction.OpenPage(bookmark.page)) }) {
                    Text(context.getString(R.string.quran_bookmark_item, bookmark.page, bookmark.label))
                } }
            }
        }, confirmButton = { TextButton({ dialog = "" }) { Text(label(R.string.quran_close)) } })
    if (dialog in setOf("downloads", "clear", "all")) AlertDialog(onDismissRequest = { dialog = "" },
        title = { Text(label(R.string.quran_downloads)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(context.getString(R.string.quran_cached_pages, state.cachedPages))
                Text(label(R.string.quran_download_policy))
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row { Text(label(R.string.quran_offline_only), Modifier.weight(1f)); Switch(state.offlineOnly, { onAction(QuranAction.SetOfflineOnly(it)) }) }
                Row { Text(label(R.string.quran_wifi_only), Modifier.weight(1f)); Switch(state.wifiOnly, { onAction(QuranAction.SetWifiOnly(it)) }) }
                if (state.downloading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(context.getString(R.string.quran_download_progress, state.downloadedPages, state.totalPages,
                        android.text.format.Formatter.formatShortFileSize(context, state.downloadedBytes),
                        android.text.format.Formatter.formatShortFileSize(context, state.totalDownloadBytes)))
                    TextButton({ onAction(QuranAction.CancelDownload) }) { Text(label(R.string.quran_pause_download)) }
                }
                OutlinedButton({ onAction(QuranAction.DownloadPage) }, enabled = !state.offlineOnly && !state.loading && !state.downloading, modifier = Modifier.fillMaxWidth()) {
                    Text(context.getString(R.string.quran_download_page, state.page))
                }
                if (dialog == "all") {
                    Text(context.getString(R.string.quran_download_all_confirm,
                        android.text.format.Formatter.formatShortFileSize(context, state.totalDownloadBytes)))
                    TextButton({ onAction(QuranAction.DownloadAll); dialog = "downloads" }) { Text(label(R.string.quran_confirm_download)) }
                    TextButton({ dialog = "downloads" }) { Text(label(R.string.quran_cancel)) }
                } else OutlinedButton({ dialog = "all" }, enabled = !state.offlineOnly && !state.downloading, modifier = Modifier.fillMaxWidth()) {
                    Text(label(R.string.quran_download_all))
                }
                if (dialog == "clear") {
                    Text(label(R.string.quran_clear_confirm))
                    TextButton({ onAction(QuranAction.ClearDownloaded); dialog = "downloads" }) { Text(label(R.string.quran_clear)) }
                    TextButton({ dialog = "downloads" }) { Text(label(R.string.quran_cancel)) }
                } else TextButton({ dialog = "clear" }, enabled = state.cachedPages > 0 && !state.downloading) { Text(label(R.string.quran_clear)) }
            }
        }, confirmButton = { TextButton({ dialog = "" }) { Text(label(R.string.quran_close)) } })
}

/** Decode HTML without normalizing source text; Quran quotations retain their supplied font. */
internal fun quranTafsirText(html: String, quranFont: FontFamily?): AnnotatedString {
    // Html.fromHtml does not expose CSS classes. Re-tag only class="aya" spans as a
    // named font, preserving their contents, entities and all other HTML for its parser.
    val spans = ArrayDeque<Boolean>()
    val tagged = Regex("</?span\\b[^>]*>", RegexOption.IGNORE_CASE).replace(html) { tag ->
        if (tag.value.startsWith("</")) {
            if (spans.removeLastOrNull() == true) "</font>" else tag.value
        } else {
            val classes = Regex("\\bclass\\s*=\\s*(['\"])(.*?)\\1", RegexOption.IGNORE_CASE)
                .find(tag.value)?.groupValues?.get(2)?.split(Regex("\\s+")) ?: emptyList()
            val isAyah = "aya" in classes
            spans.addLast(isAyah)
            if (isAyah) "<font face=\"BasairQuranHafs\">" else tag.value
        }
    }
    val parsed = Html.fromHtml(tagged, Html.FROM_HTML_MODE_LEGACY)
    return buildAnnotatedString {
        append(parsed.toString())
        if (quranFont != null) parsed.getSpans(0, parsed.length, TypefaceSpan::class.java).forEach { span ->
            if (span.family == "BasairQuranHafs") {
                val start = parsed.getSpanStart(span)
                val end = parsed.getSpanEnd(span)
                if (start >= 0 && end > start && end <= length) addStyle(SpanStyle(fontFamily = quranFont), start, end)
            }
        }
    }
}

@Composable
private fun QuranJumpDialog(state: QuranUiState, onDismiss: () -> Unit, onOpen: (QuranAction) -> Unit) {
    val context = LocalContext.current
    var surah by rememberSaveable { mutableStateOf("") }
    var ayah by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf("") }
    var juz by rememberSaveable { mutableStateOf("") }
    val surahInfo = state.surahs.find { it.id == parsePageNumber(surah, 114) }
    val validAyah = surahInfo?.let { parsePageNumber(ayah, it.ayahCount) }
    val validPage = parsePageNumber(page, state.totalPages)
    val validJuz = parsePageNumber(juz, 30)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(context.getString(R.string.quran_jump)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(context.getString(R.string.quran_jump_instructions))
            OutlinedTextField(surah, { surah = it.take(3) }, Modifier.fillMaxWidth(), label = { Text(context.getString(R.string.quran_surah_number)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(ayah, { ayah = it.take(3) }, Modifier.fillMaxWidth(), label = { Text(context.getString(R.string.quran_ayah_number)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            TextButton({ onOpen(QuranAction.OpenAyah(surahInfo!!.id, validAyah!!)) }, enabled = validAyah != null) { Text(context.getString(R.string.quran_open_ayah)) }
            HorizontalDivider()
            OutlinedTextField(page, { page = it.take(3) }, Modifier.fillMaxWidth(), label = { Text(context.getString(R.string.quran_page_range, state.totalPages)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            TextButton({ onOpen(QuranAction.OpenPage(validPage!!)) }, enabled = validPage != null) { Text(context.getString(R.string.quran_open_page)) }
            HorizontalDivider()
            OutlinedTextField(juz, { juz = it.take(2) }, Modifier.fillMaxWidth(), label = { Text(context.getString(R.string.quran_juz_range)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            TextButton({ onOpen(QuranAction.OpenJuz(validJuz!!)) }, enabled = validJuz != null) { Text(context.getString(R.string.quran_open_juz)) }
        }
    }, confirmButton = { TextButton(onDismiss) { Text(context.getString(R.string.quran_close)) } })
}
