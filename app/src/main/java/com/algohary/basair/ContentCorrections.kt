package com.algohary.basair

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ContentCorrection(val id: String, val target: String, val text: String, val updatedAt: Long)
internal fun validCorrectionText(text: String): String? = text.trim().takeIf { it.isNotBlank() && it.length <= 4000 && '\u0000' !in it }
internal fun sanitizeCorrections(items: List<ContentCorrection>): List<ContentCorrection> = items
    .filter { it.id.isNotBlank() && it.id.length <= 80 && it.target.isNotBlank() && it.target.length <= 2000 && validCorrectionText(it.text) != null && it.updatedAt > 0 }
    .distinctBy { it.id }.sortedByDescending { it.updatedAt }.take(100)

internal fun upsertCorrection(items: List<ContentCorrection>, item: ContentCorrection): List<ContentCorrection>? {
    val clean = sanitizeCorrections(items)
    if (sanitizeCorrections(listOf(item)).isEmpty() || (clean.size >= 100 && clean.none { it.id == item.id })) return null
    return sanitizeCorrections(listOf(item) + clean.filterNot { it.id == item.id })
}

internal class CorrectionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("basair-corrections", Context.MODE_PRIVATE)
    fun list(): List<ContentCorrection> = runCatching {
        val a = JSONArray(prefs.getString("items", "[]"))
        sanitizeCorrections((0 until minOf(a.length(), 100)).mapNotNull { n -> runCatching {
            val o = a.getJSONObject(n)
            ContentCorrection(o.getString("id"), o.getString("target"), o.getString("text"), o.getLong("updatedAt"))
        }.getOrNull() })
    }.getOrDefault(emptyList())
    fun save(item: ContentCorrection): Boolean {
        val updated = upsertCorrection(list(), item) ?: return false
        return write(updated)
    }
    fun delete(id: String) = write(list().filterNot { it.id == id })
    private fun write(items: List<ContentCorrection>): Boolean {
        val a = JSONArray()
        sanitizeCorrections(items).forEach { i -> a.put(JSONObject().put("id", i.id).put("target", i.target).put("text", i.text).put("updatedAt", i.updatedAt)) }
        return prefs.edit().putString("items", a.toString()).commit()
    }
}

@Composable
internal fun ReportCorrectionButton(target: String, onChanged: () -> Unit = {}) {
    var show by rememberSaveable(target) { mutableStateOf(false) }
    TextButton(onClick = { show = true }) { Text(stringResource(R.string.batch_report)) }
    if (show) CorrectionEditor(target, null) { show = false; onChanged() }
}

@Composable
private fun CorrectionEditor(target: String, original: ContentCorrection?, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CorrectionStore(context) }
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(original?.id, target) { mutableStateOf(original?.text.orEmpty()) }
    var discard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val close = { if (!busy) { if (text != original?.text.orEmpty()) discard = true else onClose() } }
    BackHandler(onBack = close)
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.batch_report)) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(target, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.batch_report_local), style = MaterialTheme.typography.bodySmall)
            if (error) Text(stringResource(R.string.batch_report_limit), color = MaterialTheme.colorScheme.error)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(text, { if (it.length <= 4000) text = it }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 7, enabled = !busy,
                label = { Text(stringResource(R.string.batch_report_body)) }, supportingText = { Text("${text.length} / 4000") })
        }
    }, confirmButton = { TextButton(onClick = {
        validCorrectionText(text)?.let { clean ->
            busy = true
            scope.launch {
                val saved = withContext(Dispatchers.IO) { store.save(ContentCorrection(original?.id ?: java.util.UUID.randomUUID().toString(), target, clean, System.currentTimeMillis())) }
                busy = false
                if (saved) onClose() else error = true
            }
        }
    }, enabled = validCorrectionText(text) != null && !busy) { Text(stringResource(R.string.batch_save_report)) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.media_close)) } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.batch_discard)) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.batch_discard_yes)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.batch_continue_edit)) } })
}

@Composable
internal fun CorrectionsScreen() {
    val context = LocalContext.current
    val store = remember { CorrectionStore(context) }
    var reports by remember { mutableStateOf(store.list()) }
    var editing by remember { mutableStateOf<ContentCorrection?>(null) }
    var deleting by remember { mutableStateOf<ContentCorrection?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.batch_reports), style = MaterialTheme.typography.headlineSmall) }
        item { Text(stringResource(R.string.batch_report_local)) }
        item { ReportCorrectionButton("بصائر — Basair") { reports = store.list() } }
        if (reports.isEmpty()) item { Text(stringResource(R.string.batch_no_reports)) }
        items(reports, key = { it.id }) { report ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(report.target, style = MaterialTheme.typography.bodySmall)
                    Text(report.text)
                    Text(stringResource(R.string.batch_report_draft), style = MaterialTheme.typography.labelSmall)
                    Row {
                        TextButton(onClick = { editing = report }) { Text(stringResource(R.string.notes_edit)) }
                        TextButton(onClick = { deleting = report }) { Text(stringResource(R.string.batch_remove_report)) }
                    }
                    TextButton(onClick = {
                        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
                        val message = "Basair $version\n${report.target}\n${report.text}"
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, message), context.getString(R.string.batch_share_report)))
                    }) { Text(stringResource(R.string.batch_share_report)) }
                }
            }
        }
    }
    editing?.let { report -> CorrectionEditor(report.target, report) { editing = null; reports = store.list() } }
    deleting?.let { report -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.batch_delete_report)) },
        confirmButton = { TextButton(onClick = { store.delete(report.id); deleting = null; reports = store.list() }) { Text(stringResource(R.string.batch_remove_report)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.media_close)) } }) }
}
