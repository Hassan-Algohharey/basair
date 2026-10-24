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
    AlertDialog(onDismissRequest = cancel, title = { Text("قراءة المرجع داخل التطبيق") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(reference.label)
            Text(reference.edition)
            when {
                checking -> { CircularProgressIndicator(); Text("جارٍ التحقق من النسخة المحفوظة…") }
                downloading -> {
                    LinearProgressIndicator(progress = { (progress.toFloat() / volume.bytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("جارٍ تنزيل المجلد: ${progress * 100 / volume.bytes}٪")
                }
                file != null -> Text("المجلد محفوظ ومطابق للمصدر، وجاهز للقراءة دون اتصال.")
                else -> {
                    Text("تنزيل الجزء ${volume.volume} كاملًا (نحو ${(volume.bytes + 999_999) / 1_000_000} ميغابايت) من دار الإسلام، ثم فتح صفحة ${reference.pdfPage}. يُحفظ داخل التطبيق ويعمل دون اتصال بعد التنزيل.")
                    Text("قد تُحسب تكلفة بيانات حسب اتصالك. لا يبدأ التنزيل إلا عند الضغط على الزر.")
                    if (failed) Text("تعذر التنزيل أو لم تطابق النسخة بصمة المصدر. تحقق من الاتصال والمساحة ثم أعد المحاولة.", color = MaterialTheme.colorScheme.error)
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
        }) { Text(if (file != null) "فتح صفحة ${reference.pdfPage}" else if (failed) "إعادة التنزيل" else "تنزيل المجلد وفتح الصفحة") }
    }, dismissButton = { TextButton(onClick = cancel) { Text(if (downloading) "إلغاء التنزيل" else "إغلاق") } })
}
