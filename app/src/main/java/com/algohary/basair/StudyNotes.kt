package com.algohary.basair

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val NOTE_LIMIT = 10000
internal data class StudyNote(val bookId: String, val bookSha: String, val page: Int,
    val title: String, val edition: String, val text: String, val updatedAt: Long) {
    val key get() = "n1:$bookId:$bookSha:$page"
    fun valid() = bookId.matches(Regex("[a-z0-9-]{1,64}")) &&
        bookSha.matches(Regex("[a-f0-9]{64}")) && page > 0 && title.isNotBlank() &&
        text.isNotBlank() && text.length <= NOTE_LIMIT && updatedAt >= 0
}

internal fun decodeNotes(values: Map<String, *>): List<StudyNote> = values.mapNotNull { (key, value) ->
    val parts = key.split(':')
    if (parts.size != 4 || parts[0] != "n1") return@mapNotNull null
    val note = StudyNote(parts[1], parts[2], parts[3].toIntOrNull() ?: return@mapNotNull null,
        values["$key:title"] as? String ?: return@mapNotNull null,
        values["$key:edition"] as? String ?: "", value as? String ?: return@mapNotNull null,
        values["$key:time"] as? Long ?: return@mapNotNull null)
    note.takeIf { it.valid() && it.key == key }
}.sortedWith(compareByDescending<StudyNote> { it.updatedAt }.thenBy { it.key })

internal class StudyNoteStore(context: Context) {
    private val prefs = context.getSharedPreferences("basair-notes", Context.MODE_PRIVATE)
    fun list() = decodeNotes(prefs.all)
    suspend fun save(note: StudyNote) = withContext(Dispatchers.IO) {
        require(note.valid())
        check(prefs.edit().putString(note.key, note.text).putString("${note.key}:title", note.title)
            .putString("${note.key}:edition", note.edition).putLong("${note.key}:time", note.updatedAt).commit())
    }
    suspend fun delete(note: StudyNote) = withContext(Dispatchers.IO) {
        check(prefs.edit().remove(note.key).remove("${note.key}:title")
            .remove("${note.key}:edition").remove("${note.key}:time").commit())
    }
}

internal fun noteBook(note: StudyNote): Book? =
    (Catalog.books + referenceVolumes.values.map { it.asBook() }).find {
        it.id == note.bookId && it.sha256 == note.bookSha && note.page in 1..it.pages
    }

/** One note per edition and file page. Downloads never own or erase this store. */
@Composable
internal fun StudyNotesScreen(book: Book? = null, page: Int? = null, onOpen: (StudyNote) -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { StudyNoteStore(context) }
    var notes by remember { mutableStateOf(store.list()) }
    var query by rememberSaveable { mutableStateOf("") }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val current = if (book != null && page != null) StudyNote(book.id, book.sha256, page,
        book.title, book.edition, "", 0) else null
    val visible = notes.filter { (book == null || (it.bookId == book.id && it.bookSha == book.sha256)) &&
        normalizeArabic(it.title + " " + it.edition + " " + it.text + " " + it.page)
            .contains(normalizeArabic(query.trim())) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(context.getString(R.string.notes_title), style = MaterialTheme.typography.titleLarge) }
        item { Text(context.getString(R.string.notes_local)) }
        if (current != null) item {
            Button(onClick = { editingKey = current.key }) { Text(context.getString(R.string.notes_current_page, current.page)) }
        }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(context.getString(R.string.notes_search)) }) }
        if (visible.isEmpty()) item { Text(context.getString(R.string.notes_empty)) }
        items(visible, key = { it.key }) { note ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium)
                    Text(note.edition, style = MaterialTheme.typography.bodySmall)
                    Text(context.getString(R.string.notes_file_page, note.page))
                    Text(note.text, maxLines = 4)
                    Row {
                        TextButton(onClick = { editingKey = note.key }) { Text(context.getString(R.string.notes_edit)) }
                        TextButton(onClick = { onOpen(note) }, enabled = noteBook(note) != null) { Text(context.getString(R.string.notes_open)) }
                    }
                    if (noteBook(note) == null) Text(context.getString(R.string.notes_old_edition))
                }
            }
        }
    }
    editingKey?.let { key ->
        val existing = notes.find { it.key == key }
        (existing ?: current?.takeIf { it.key == key })?.let { draft ->
            key(key) { NoteEditor(draft, existing != null, store) { notes = store.list(); editingKey = null } }
        }
    }
}

@Composable
private fun NoteEditor(note: StudyNote, exists: Boolean, store: StudyNoteStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(note.key) { mutableStateOf(note.text) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val close = { if (!busy) { if (text != note.text) confirmDiscard = true else onClose() } }
    BackHandler { close() }
    AlertDialog(onDismissRequest = close, title = { Text(context.getString(R.string.notes_file_page, note.page)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(note.title)
            OutlinedTextField(text, { if (it.length <= NOTE_LIMIT) text = it }, Modifier.fillMaxWidth(),
                enabled = !busy, minLines = 4, maxLines = 10, label = { Text(context.getString(R.string.notes_text)) },
                supportingText = { Text("${text.length} / $NOTE_LIMIT") })
            if (error) Text(context.getString(R.string.notes_error), color = MaterialTheme.colorScheme.error)
            if (exists) TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text(context.getString(R.string.notes_delete)) }
        } }, confirmButton = { TextButton(enabled = !busy && text.isNotBlank(), onClick = {
            busy = true; error = false
            scope.launch { try { store.save(note.copy(text = text.trim(), updatedAt = System.currentTimeMillis())); onClose() }
                catch (_: Exception) { error = true } finally { busy = false } }
        }) { Text(context.getString(R.string.notes_save)) } },
        dismissButton = { TextButton(onClick = close, enabled = !busy) { Text(context.getString(R.string.ui_cancel)) } })
    if (confirmDelete || confirmDiscard) AlertDialog(onDismissRequest = { confirmDelete = false; confirmDiscard = false },
        title = { Text(context.getString(if (confirmDelete) R.string.notes_delete_confirm else R.string.notes_discard_confirm)) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            if (confirmDelete) {
                busy = true
                scope.launch { try { store.delete(note); onClose() } catch (_: Exception) { error = true; confirmDelete = false }
                    finally { busy = false } }
            } else onClose()
        }) { Text(context.getString(if (confirmDelete) R.string.notes_delete else R.string.notes_discard)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false; confirmDiscard = false }) { Text(context.getString(R.string.ui_cancel)) } })
}
