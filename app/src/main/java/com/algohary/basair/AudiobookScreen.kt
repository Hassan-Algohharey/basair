package com.algohary.basair

import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

internal fun audioTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}

@Composable
internal fun AudiobookEntry(onOpen: () -> Unit, count: Int? = null) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SoftGreen)) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.audio_title), style = MaterialTheme.typography.titleMedium)
            Text(if (count == null) stringResource(R.string.audio_intro) else stringResource(R.string.audio_results, count))
        }
    }
}

@Composable
internal fun AudiobookFeature(initialQuery: String, initialBook: String?, onBook: (String) -> Unit) {
    val context = LocalContext.current
    val english = LocalConfiguration.current.locales[0].language == "en"
    val store = remember { AudioProgressStore(context) }
    val preferences = remember { context.getSharedPreferences("basair-audio", Context.MODE_PRIVATE) }
    val downloads = remember { AudioDownloads(context) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var bookFilter by rememberSaveable { mutableStateOf(initialBook) }
    var selectedId by rememberSaveable { mutableStateOf(
        searchAudiobooks(initialQuery, initialBook).find { it.id == audioRecording(store.lastTrack().orEmpty())?.id }?.id
            ?: searchAudiobooks(initialQuery, initialBook).firstOrNull()?.id ?: allAudioRecordings.first().id) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var connectionError by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var wifiOnly by remember { mutableStateOf(preferences.getBoolean("wifiOnly", true)) }
    var downloading by remember { mutableStateOf<String?>(null) }
    var downloadedBytes by remember { mutableLongStateOf(0) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadError by remember { mutableStateOf(false) }
    var storageVersion by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    DisposableEffect(context) {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))).buildAsync()
        var disposed = false
        future.addListener({ if (!disposed) {
            try { controller = future.get() } catch (_: Exception) { connectionError = true }
        } }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; controller = null; MediaController.releaseFuture(future) }
    }
    LaunchedEffect(controller) { while (true) { tick++; delay(500) } }
    val player = controller
    val selected = allAudioRecordings.find { it.id == selectedId } ?: allAudioRecordings.first()
    val track = selected.tracks.find { it.id == store.lastTrack(selected.id) } ?: selected.tracks.first()
    @Suppress("UNUSED_VARIABLE") val observedTick = tick
    val currentId = player?.currentMediaItem?.mediaId
    val active = currentId == track.id
    val duration = if (active) player?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: store.duration(track.id) else store.duration(track.id)
    val position = if (active) player?.currentPosition ?: store.position(track.id) else store.position(track.id)
    val playing = active && player?.isPlaying == true
    val results = searchAudiobooks(query, bookFilter)
    val local = remember(track.id, storageVersion) { AudioDownloads.localFile(context, track) != null }
    val partialBytes = remember(track.id, storageVersion) { AudioDownloads.partialBytes(context, track) }
    val play: () -> Unit = {
        player?.let { control ->
            if (control.currentMediaItem?.mediaId == track.id && !local) {
                if (control.playbackState == Player.STATE_ENDED) control.seekTo(0)
                if (control.playbackState == Player.STATE_IDLE || control.playerError != null) control.prepare()
            } else {
                val ordered = selected.tracks.sortedBy { it.sequence }
                val sameTrack = control.currentMediaItem?.mediaId == track.id
                val resume = if (sameTrack) control.currentPosition else store.position(track.id)
                val resumeDuration = if (sameTrack) control.duration.takeIf { it > 0 } ?: store.duration(track.id) else store.duration(track.id)
                val resumeSpeed = if (sameTrack) control.playbackParameters.speed else store.speed()
                control.setMediaItems(ordered.map { audioMediaItem(it, selected) }, ordered.indexOfFirst { it.id == track.id },
                    audioResumePosition(resume, resumeDuration))
                control.setPlaybackSpeed(resumeSpeed); control.prepare()
            }
            control.play()
        }
    }
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.audio_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text(stringResource(R.string.audio_intro)); Text(stringResource(R.string.audio_reading_notice), color = Muted) }
        item { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.audio_search)) }, trailingIcon = {
                if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(stringResource(R.string.media_clear)) }
            }) }
        bookFilter?.let { id -> item { TextButton(onClick = { bookFilter = null }) {
            Text(stringResource(R.string.audio_clear_book, Catalog.books.find { it.id == id }?.title.orEmpty()))
        } } }
        item { Text(stringResource(R.string.audio_results, results.size), color = Muted) }
        items(results, key = { it.id }) { recording ->
            OutlinedCard(onClick = { selectedId = recording.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(recording.title, fontWeight = FontWeight.Bold)
                    if (english) Text(recording.englishTitle, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(if (recording.kind == "lesson") R.string.tja_speaker else R.string.audio_reader, if (english) recording.englishReader else recording.reader))
                    Text(stringResource(R.string.audio_select), color = Green)
                }
            }
        }
        if (results.isEmpty()) item { Text(stringResource(R.string.media_no_results)) }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SoftGreen)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selected.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(if (selected.kind == "lesson") R.string.tja_speaker else R.string.audio_reader, if (english) selected.englishReader else selected.reader))
                    if (selected.author.isNotBlank()) Text(stringResource(R.string.audio_author, selected.author))
                    Text(if (english) selected.englishCoverageNote else selected.coverageNote, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(if (local) R.string.audio_local else R.string.audio_online), color = Green)
                    if (connectionError) Text(stringResource(R.string.audio_connection_error))
                    if (active && player?.playerError != null) Text(stringResource(R.string.audio_play_error))
                    if (active && player?.playbackState == Player.STATE_BUFFERING) {
                        LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.audio_buffering))
                    }
                    Text(stringResource(R.string.audio_position, audioTime(position), duration?.let(::audioTime) ?: "--:--:--"))
                    var seeking by remember(track.id) { mutableStateOf<Float?>(null) }
                    Slider(value = seeking ?: position.toFloat().coerceIn(0f, (duration ?: 1).toFloat().coerceAtLeast(1f)),
                        onValueChange = { seeking = it }, enabled = active && duration != null && player?.isCurrentMediaItemSeekable == true,
                        valueRange = 0f..(duration ?: 1).toFloat().coerceAtLeast(1f), onValueChangeFinished = {
                            seeking?.let { player?.seekTo(it.toLong()) }; seeking = null
                        })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { player?.seekBack() }, enabled = active && player?.isCurrentMediaItemSeekable == true, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.audio_back15)) }
                        Button(onClick = { if (playing) player?.pause() else play() }, enabled = player != null, modifier = Modifier.weight(1f)) {
                            Text(stringResource(if (playing) R.string.audio_pause else R.string.audio_play))
                        }
                        TextButton(onClick = { player?.seekForward() }, enabled = active && player?.isCurrentMediaItemSeekable == true, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.audio_forward15)) }
                    }
                    TextButton(onClick = { player?.pause(); player?.stop(); player?.clearMediaItems() }, enabled = currentId != null) { Text(stringResource(R.string.audio_stop)) }
                    if (currentId != null && !active) Text(stringResource(R.string.audio_other_playing, audioRecording(currentId)?.title.orEmpty()))
                    Text(stringResource(R.string.audio_speed), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            FilterChip(selected = (player?.playbackParameters?.speed ?: store.speed()) == speed,
                                onClick = { player?.setPlaybackSpeed(speed) }, enabled = player != null, label = { Text("${speed}×") })
                        }
                    }
                    selected.bookIds.filter { id -> Catalog.books.any { it.id == id } }.forEach { id ->
                        TextButton(onClick = { onBook(id) }) { Text(stringResource(R.string.media_read_book_value, Catalog.books.first { it.id == id }.title)) }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.audio_source_credit), fontWeight = FontWeight.Bold)
                Text(if (english) selected.englishRightsNote else selected.rightsNote, style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { openSource(context, selected.sourceUrl) }) { Text(stringResource(R.string.audio_source)) }
                    TextButton(onClick = { openSource(context, selected.licenseUrl) }) { Text(stringResource(R.string.audio_rights)) }
                }
                Text(stringResource(R.string.audio_download_size, String.format(Locale.ROOT, "%.1f", track.byteSize / 1_000_000.0)), color = Muted)
                Row {
                    Checkbox(checked = wifiOnly, onCheckedChange = { wifiOnly = it; preferences.edit().putBoolean("wifiOnly", it).apply() })
                    Text(stringResource(R.string.audio_wifi_only), Modifier.padding(top = 12.dp))
                }
                if (downloadError) Text(stringResource(R.string.audio_download_error))
                if (local) Text(stringResource(R.string.audio_local), color = Green)
                if (downloading == track.id) {
                    LinearProgressIndicator(progress = { (downloadedBytes.toFloat() / track.byteSize).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.audio_download_progress, downloadedBytes * 100 / track.byteSize))
                    TextButton(onClick = { downloadJob?.cancel() }) { Text(stringResource(R.string.audio_cancel_download)) }
                } else {
                    Button(onClick = {
                        downloadJob = scope.launch {
                            downloading = track.id; downloadedBytes = 0; downloadError = false
                            try { downloads.download(track) { downloadedBytes = it } }
                            catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { downloadError = true }
                            finally { downloading = null; storageVersion++ }
                        }
                    }, enabled = selected.downloadAllowed && !local && downloading == null) {
                        Text(stringResource(if (partialBytes > 0) R.string.audio_resume_download else R.string.audio_download))
                    }
                    TextButton(onClick = { scope.launch { downloads.remove(track); storageVersion++ } }, enabled = currentId != track.id && downloading == null) {
                        Text(stringResource(R.string.audio_delete_download))
                    }
                }
                Text(stringResource(R.string.audio_download_note), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}
