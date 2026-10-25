package com.algohary.basair

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
internal fun BookStorageDialog(book: Book, page: Int, downloads: BookDownloads, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var full by remember { mutableStateOf(false) }
    var cached by remember { mutableIntStateOf(0) }
    var ranges by remember { mutableStateOf("") }
    var bytes by remember { mutableLongStateOf(0) }
    var cacheBytes by remember { mutableLongStateOf(0) }
    var partial by remember { mutableLongStateOf(0) }
    var busy by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var progress by remember { mutableLongStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var wifiOnly by remember { mutableStateOf(downloads.wifiOnly()) }
    var fullWifiOnly by remember { mutableStateOf(downloads.fullWifiOnly()) }
    var offlineOnly by remember { mutableStateOf(downloads.offlineOnly()) }
    var limit by remember { mutableIntStateOf(downloads.limitMb()) }
    LaunchedEffect(revision) {
        busy = true
        withContext(Dispatchers.IO) {
            full = downloads.fullFile(book) != null
            val pages = downloads.cachedPages(book)
            cached = pages.size
            ranges = pageRanges(pages)
            bytes = downloads.bookBytes(book)
            cacheBytes = downloads.pageBytes()
            partial = downloads.partialBytes(book)
        }
        busy = false
    }
    val close = { job?.cancel(); onClose() }
    AlertDialog(onDismissRequest = close, title = { Text(context.getString(R.string.storage_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(book.title)
            Text(context.getString(R.string.storage_counts, cached, book.pages, (bytes + 999_999) / 1_000_000))
            if (!full) Text(context.getString(R.string.storage_page_ranges, ranges))
            Text(context.getString(if (full) R.string.storage_full_ready else R.string.storage_partial_notice))
            if (!book.pageOnly) Text(context.getString(R.string.storage_full_size, (book.bytes + 999_999) / 1_000_000))
            else Text(context.getString(R.string.batch_madinah_page_only))
            Row {
                Switch(checked = offlineOnly, enabled = !running, onCheckedChange = { offlineOnly = it; downloads.setOfflineOnly(it) })
                Text(context.getString(R.string.storage_offline_only), Modifier.padding(start = 8.dp))
            }
            if (partial > 0 && !running) Text(context.getString(R.string.storage_resume_size, (partial + 999_999) / 1_000_000))
            if (running) {
                LinearProgressIndicator(progress = { (progress.toFloat() / book.bytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(context.getString(R.string.storage_progress, progress * 100 / book.bytes))
                TextButton(onClick = { job?.cancel() }) { Text(context.getString(R.string.storage_pause)) }
            } else if (!book.pageOnly) Button(enabled = !busy && !full && !offlineOnly, onClick = {
                running = true; failed = false
                job = scope.launch {
                    try { downloads.downloadFull(book) { progress = it } }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { failed = true }
                    finally { running = false; revision++ }
                }
            }) { Text(context.getString(if (partial > 0) R.string.storage_resume else R.string.storage_download)) }
            if (failed) Text(context.getString(R.string.storage_failure), color = MaterialTheme.colorScheme.error)
            TextButton(enabled = !busy && !running, onClick = {
                busy = true
                scope.launch { withContext(Dispatchers.IO) { downloads.clearBook(book) }; revision++ }
            }) { Text(context.getString(R.string.storage_delete_book)) }
            Text(context.getString(R.string.storage_cache_size, (cacheBytes + 999_999) / 1_000_000))
            Row {
                Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it; downloads.setWifiOnly(it) })
                Text(context.getString(R.string.storage_wifi), Modifier.padding(start = 8.dp))
            }
            if (!book.pageOnly) Row {
                Switch(checked = fullWifiOnly, onCheckedChange = { fullWifiOnly = it; downloads.setFullWifiOnly(it) })
                Text(context.getString(R.string.storage_full_wifi), Modifier.padding(start = 8.dp))
            }
            Text(context.getString(R.string.storage_limit))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(50, 100, 250).forEach { size ->
                    FilterChip(selected = limit == size, onClick = {
                        limit = size; downloads.setLimitMb(size)
                        scope.launch { withContext(Dispatchers.IO) { downloads.trim(book, page) }; revision++ }
                    },
                        label = { Text(context.getString(R.string.storage_mb, size)) })
                }
            }
            TextButton(enabled = !busy && !running, onClick = {
                busy = true
                scope.launch { withContext(Dispatchers.IO) { downloads.clearPages() }; revision++ }
            }) { Text(context.getString(R.string.storage_clear_cache)) }
            Text(context.getString(R.string.storage_preserve_data))
        }
    }, confirmButton = { TextButton(onClick = close) { Text(context.getString(R.string.ui_close)) } })
}
