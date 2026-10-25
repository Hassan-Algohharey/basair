package com.algohary.basair

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

internal fun versesForPrintedPage(verses: List<QuranVerse>, page: Int): List<QuranVerse> =
    verses.filter { it.originalPage == page }.distinctBy { it.surah to it.ayah }.sortedWith(compareBy<QuranVerse> { it.surah }.thenBy { it.ayah })

@Composable
internal fun PrintedTafsirDialog(repo: QuranRepository, page: Int, fontFamily: FontFamily, onClose: () -> Unit) {
    val context = LocalContext.current
    var verses by remember(page) { mutableStateOf<List<QuranVerse>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(page, retry) {
        failed = false; verses = null
        try {
            val manifest = MadinahPages.manifest(context)
            check(manifest.getString("textSourceSha256") == repo.index.source.getString("sourceSha256"))
            val row = manifest.getJSONArray("pages").getJSONObject(page - 1)
            val groups = row.getJSONArray("textPages")
            val all = mutableListOf<QuranVerse>()
            for (i in 0 until groups.length()) all += repo.open(groups.getInt(i), foreground = false).verses
            val result = versesForPrintedPage(all, page)
            check(result.size == row.getInt("verseCount"))
            verses = result
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed = true }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.batch_printed_tafsir, page)) }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.batch_printed_tafsir_source), style = MaterialTheme.typography.bodySmall)
            if (failed) {
                Text(stringResource(R.string.quran_network_error))
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.quran_retry)) }
            } else if (verses == null) CircularProgressIndicator()
            verses?.forEach { verse ->
                Text("${repo.index.surahs[verse.surah - 1].name} ${verse.surah}:${verse.ayah}", style = MaterialTheme.typography.labelLarge)
                Text(android.text.Html.fromHtml(verse.tafsir, android.text.Html.FROM_HTML_MODE_LEGACY).toString(), fontFamily = fontFamily)
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.media_close)) } })
}
