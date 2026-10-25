package com.algohary.basair

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val bookmarkEditorSaver = listSaver<AudioBookmark?, Any>(
    save = { value -> value?.let { listOf(it.id, it.trackId, it.positionMs, it.title, it.note, it.updatedAt) } ?: emptyList() },
    restore = { values -> if (values.isEmpty()) null else AudioBookmark(values[0] as String, values[1] as String,
        values[2] as Long, values[3] as String, values[4] as String, values[5] as Long) }
)

@Composable
internal fun AudioStudyControls(recording: Audiobook, track: AudioTrack, position: Long, connected: Boolean,
    active: Boolean, tick: Int, onSeek: (AudioBookmark) -> Unit) {
    val context = LocalContext.current
    val sleep = remember { AudioSleepStore(context) }
    val bookmarks = remember { AudioBookmarkStore(context) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val plan = remember(tick, revision) { sleep.plan() }
    var timerDialog by rememberSaveable { mutableStateOf(false) }
    var minutes by rememberSaveable { mutableStateOf("15") }
    var timerError by remember { mutableStateOf(false) }
    var timerBusy by remember { mutableStateOf(false) }
    var marksDialog by rememberSaveable { mutableStateOf(false) }
    var editor by rememberSaveable(stateSaver = bookmarkEditorSaver) { mutableStateOf<AudioBookmark?>(null) }
    var deleting by remember { mutableStateOf<AudioBookmark?>(null) }
    var error by remember { mutableStateOf(false) }
    val marks = remember(recording.id, revision) { bookmarks.list().filter { audioRecording(it.trackId)?.id == recording.id } }
    val updateTimer: (suspend () -> Boolean) -> Unit = { action ->
        timerBusy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { action() }
            timerBusy = false; timerError = !ok; revision++
            if (ok) timerDialog = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(when {
            plan.endTrack != null -> stringResource(R.string.listen_timer_end)
            plan.deadline > 0 -> stringResource(R.string.listen_timer_left, audioTime((plan.deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)))
            else -> stringResource(R.string.listen_timer_off)
        })
        OutlinedButton(onClick = { timerDialog = true }, enabled = connected) { Text(stringResource(R.string.listen_timer)) }
        OutlinedButton(onClick = { editor = AudioBookmark(java.util.UUID.randomUUID().toString(), track.id, position.coerceAtLeast(0), "", "", System.currentTimeMillis()) }) {
            Text(stringResource(R.string.listen_add_mark))
        }
        TextButton(onClick = { marksDialog = true; revision++ }) { Text(stringResource(R.string.listen_marks)) }
    }
    if (timerDialog) AlertDialog(onDismissRequest = { if (!timerBusy) timerDialog = false }, title = { Text(stringResource(R.string.listen_timer)) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.listen_timer_scope))
            OutlinedTextField(value = minutes, onValueChange = { minutes = it.take(3) }, enabled = !timerBusy, singleLine = true,
                label = { Text(stringResource(R.string.listen_timer_minutes)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(onClick = { val value = parseSleepMinutes(minutes); if (value == null) timerError = true else updateTimer { sleep.minutes(value) } }, enabled = !timerBusy) {
                Text(stringResource(R.string.listen_timer_start))
            }
            OutlinedButton(onClick = { updateTimer { sleep.endOf(track.id) } }, enabled = active && !timerBusy) { Text(stringResource(R.string.listen_timer_end)) }
            TextButton(onClick = { updateTimer { sleep.clear() } }, enabled = !timerBusy) { Text(stringResource(R.string.listen_timer_cancel)) }
            if (timerError) Text(stringResource(R.string.listen_timer_error), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { timerDialog = false }, enabled = !timerBusy) { Text(stringResource(R.string.media_close)) } })
    if (marksDialog) AlertDialog(onDismissRequest = { marksDialog = false }, title = { Text(stringResource(R.string.listen_marks)) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.listen_mark_scope))
            if (marks.isEmpty()) Text(stringResource(R.string.listen_no_marks))
            marks.forEach { mark ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(mark.title, style = MaterialTheme.typography.titleMedium)
                Text(audioTrack(mark.trackId)?.title.orEmpty())
                Text(stringResource(R.string.listen_saved_at, audioTime(mark.positionMs)))
                if (mark.note.isNotBlank()) Text(mark.note)
                TextButton(onClick = { marksDialog = false; onSeek(mark) }) { Text(stringResource(R.string.listen_mark_jump)) }
                TextButton(onClick = { editor = mark }) { Text(stringResource(R.string.listen_mark_edit)) }
                TextButton(onClick = { deleting = mark }) { Text(stringResource(R.string.listen_mark_delete)) }
                } }
            }
            if (error) Text(stringResource(R.string.listen_mark_error), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { marksDialog = false }) { Text(stringResource(R.string.media_close)) } })
    editor?.let { mark -> AudioBookmarkEditor(mark, { editor = null }) { revision++ } }
    deleting?.let { mark -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.listen_mark_delete_confirm)) },
        text = { Text(mark.title) }, confirmButton = { TextButton(onClick = {
            deleting = null
            scope.launch { error = !withContext(Dispatchers.IO) { bookmarks.delete(mark.id) }; revision++ }
        }) { Text(stringResource(R.string.listen_mark_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.media_close)) } }) }
}

@Composable
private fun AudioBookmarkEditor(original: AudioBookmark, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(original.id) { mutableStateOf(original.title) }
    var note by rememberSaveable(original.id) { mutableStateOf(original.note) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val close = { if (!busy) { if (title != original.title || note != original.note) discard = true else onClose() } }
    BackHandler(onBack = close)
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.listen_mark_editor_title)) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.listen_saved_at, audioTime(original.positionMs)))
            OutlinedTextField(title, { title = it.take(120) }, enabled = !busy, singleLine = true, label = { Text(stringResource(R.string.listen_mark_title)) })
            OutlinedTextField(note, { note = it.take(2000) }, enabled = !busy, label = { Text(stringResource(R.string.listen_mark_note)) })
            if (error) Text(stringResource(R.string.listen_mark_error), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        busy = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) { AudioBookmarkStore(context).save(original.copy(title = title.trim(), note = note.trim(), updatedAt = System.currentTimeMillis())) }
            busy = false; error = !saved
            if (saved) { onSaved(); onClose() }
        }
    }, enabled = !busy && title.isNotBlank()) { Text(stringResource(R.string.listen_mark_save)) } },
        dismissButton = { TextButton(onClick = close, enabled = !busy) { Text(stringResource(R.string.media_close)) } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.listen_mark_discard)) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.listen_mark_discard_yes)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.listen_mark_continue)) } })
}
