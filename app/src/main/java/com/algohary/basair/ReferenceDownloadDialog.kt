package com.algohary.basair

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*

internal fun referenceDirectory(context: Context) = File(context.filesDir, "references")

private suspend fun downloadReference(context: Context, volume: ReferenceVolume, onProgress: (Long) -> Unit): File =
    withContext(Dispatchers.IO) {
        val directory = referenceDirectory(context)
        verifiedReferenceFile(directory, volume)?.let { return@withContext it }
        val url = URL(volume.reference.pdfUrl)
        check(url.protocol == "https" && url.host == "d1.islamhouse.com")
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            check(connection.responseCode == 200) { "Reference download failed" }
            val length = connection.contentLengthLong
            check(length < 0 || length == volume.bytes) { "Unexpected reference size" }
            val taskContext = currentCoroutineContext()
            connection.inputStream.use { input ->
                storeReference(input, directory, volume, onProgress) { taskContext.ensureActive() }
            }
        } finally { connection.disconnect() }
    }

@Composable
internal fun ReferenceDownloadDialog(reference: Reference, onDismiss: () -> Unit, onOpen: (Reference) -> Unit) {
    val context = LocalContext.current
    val volume = referenceVolumes.getValue(reference.volume)
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(true) }
    var file by remember { mutableStateOf<File?>(null) }
    var progress by remember { mutableLongStateOf(0) }
    var downloading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var task by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(volume.volume) {
        file = withContext(Dispatchers.IO) { verifiedReferenceFile(referenceDirectory(context), volume) }
        checking = false
    }
    val cancel = { task?.cancel(); onDismiss() }
    AlertDialog(onDismissRequest = cancel, title = { Text(context.getString(R.string.ui_read_reference_in_app)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(reference.label)
            Text(reference.edition)
            when {
                checking -> { CircularProgressIndicator(); Text(context.getString(R.string.ui_checking_the_saved_edition)) }
                downloading -> {
                    LinearProgressIndicator(progress = { (progress.toFloat() / volume.bytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(context.getString(R.string.ui_downloading_volume_1, progress * 100 / volume.bytes))
                }
                file != null -> Text(context.getString(R.string.ui_the_volume_matches_the_source_and_is_ready_to_read_offline))
                else -> {
                    Text(context.getString(R.string.ui_download_volume_1_in_full_about_2_mb_from_islamhouse_then_open_page_3_it, volume.volume, (volume.bytes + 999_999) / 1_000_000, reference.pdfPage))
                    Text(context.getString(R.string.ui_data_charges_may_apply_download_starts_only_when_you_press_the_button))
                    if (failed) Text(context.getString(R.string.ui_download_failed_or_the_file_did_not_match_the_source_checksum_check_your), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }, confirmButton = {
        TextButton(enabled = !checking && !downloading, onClick = {
            if (file != null) onOpen(reference) else {
                downloading = true; failed = false; progress = 0
                task = scope.launch {
                    try {
                        file = downloadReference(context, volume) { bytes -> progress = bytes }
                        onOpen(reference)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { downloading = false }
                }
            }
        }) { Text(if (file != null) context.getString(R.string.ui_open_page_1, reference.pdfPage) else if (failed) context.getString(R.string.ui_download_again) else context.getString(R.string.ui_download_volume_and_open_page)) }
    }, dismissButton = { TextButton(onClick = cancel) { Text(if (downloading) context.getString(R.string.ui_cancel_download) else context.getString(R.string.ui_close)) } })
}
