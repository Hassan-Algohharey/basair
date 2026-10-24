package com.algohary.basair

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

private class PdfBookSession(context: Context, book: Book, localFile: File? = null) : Closeable {
    private val descriptor: ParcelFileDescriptor
    private val renderer: PdfRenderer
    private var closed = false

    init {
        val directory = File(context.cacheDir, "books").apply { mkdirs() }
        val file = localFile ?: File(directory, "${book.id}-${book.sha256.take(16)}.pdf")
        if (localFile != null) check(localFile.isFile && sha256(localFile) == book.sha256) { "Reference checksum mismatch" }
        if (localFile == null && !file.exists()) {
            val temporary = File(directory, "${book.id}.part")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                context.assets.open(book.asset).use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                        }
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                check(hash == book.sha256) { "Book checksum mismatch" }
                check(temporary.renameTo(file)) { "Cannot store PDF" }
            } finally { temporary.delete() }
        }
        val opened = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val pdf = PdfRenderer(opened)
            if (pdf.pageCount != book.pages) {
                pdf.close()
                error("Unexpected page count")
            }
            descriptor = opened
            renderer = pdf
        } catch (error: Exception) { opened.close(); throw error }
    }

    @Synchronized fun render(index: Int, requestedWidth: Int): Bitmap {
        check(!closed)
        renderer.openPage(index).use { page ->
            var width = requestedWidth.coerceIn(500, 2400)
            var height = (width.toDouble() * page.height / page.width).roundToInt().coerceAtLeast(1)
            if (width.toLong() * height > 6_000_000) {
                val factor = sqrt(6_000_000.0 / (width.toLong() * height))
                width = (width * factor).toInt().coerceAtLeast(1)
                height = (height * factor).toInt().coerceAtLeast(1)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            try { page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            catch (error: Exception) { bitmap.recycle(); throw error }
            return bitmap
        }
    }

    @Synchronized override fun close() {
        if (!closed) { closed = true; renderer.close(); descriptor.close() }
    }
}

@Composable
internal fun PdfBookReader(book: Book, saved: Boolean, onBack: () -> Unit, onSave: () -> Unit,
    localFile: File? = null, initialPage: Int? = null, allowSave: Boolean = true) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("basair", Context.MODE_PRIVATE) }
    var night by rememberSaveable { mutableStateOf(preferences.getBoolean("readerNight", false)) }
    val view = LocalView.current
    DisposableEffect(night, view) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = !night
        controller?.isAppearanceLightNavigationBars = !night
        onDispose {
            if (lightStatus != null) controller?.isAppearanceLightStatusBars = lightStatus
            if (lightNavigation != null) controller?.isAppearanceLightNavigationBars = lightNavigation
        }
    }
    val readerColors = if (night) darkColorScheme(
        primary = Color(0xFFBDD7C5), onPrimary = Ink, secondary = Color(0xFFE1C48B),
        background = Color(0xFF151C19), surface = Color(0xFF1E2923),
        onSurface = Color(0xFFE4E6DC), onBackground = Color(0xFFE4E6DC),
        onSurfaceVariant = Color(0xFFB5C3B9)
    ) else MaterialTheme.colorScheme
    MaterialTheme(colorScheme = readerColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                PdfReaderContent(book, saved, onBack, onSave, localFile, initialPage, allowSave, night) {
                    night = !night
                    preferences.edit().putBoolean("readerNight", night).apply()
                }
            }
        }
    }
}

private val nightPageFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    -.78f, 0f, 0f, 0f, 228f,
    0f, -.76f, 0f, 0f, 230f,
    0f, 0f, -.74f, 0f, 220f,
    0f, 0f, 0f, 1f, 0f
)))

@Composable
private fun PdfReaderContent(book: Book, saved: Boolean, onBack: () -> Unit, onSave: () -> Unit,
    localFile: File?, initialPage: Int?, allowSave: Boolean, night: Boolean, onToggleNight: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("basair", Context.MODE_PRIVATE) }
    var page by rememberSaveable(book.id, initialPage) { mutableIntStateOf((initialPage ?: preferences.getInt("page:${book.id}", 1)).coerceIn(1, book.pages)) }
    var zoom by rememberSaveable(book.id) { mutableFloatStateOf(preferences.getFloat("pdfZoom", 1f).coerceIn(1f, 3f)) }
    var showPageDialog by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    var showChapters by rememberSaveable { mutableStateOf(false) }
    var bookmarks by remember(book.id) {
        mutableStateOf(parseBookmarkedPages(preferences.getStringSet("bookmarks:${book.id}", emptySet()).orEmpty(), book.pages))
    }
    val updateBookmarks: (List<Int>) -> Unit = { pages ->
        bookmarks = pages.distinct().sorted()
        preferences.edit().putStringSet("bookmarks:${book.id}", bookmarks.map(Int::toString).toSet()).apply()
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var retry by remember { mutableIntStateOf(0) }
    var error by remember(book.id, retry) { mutableStateOf<String?>(null) }
    val session by produceState<PdfBookSession?>(null, book.id, retry) {
        var owned: PdfBookSession? = null
        try {
            withContext(Dispatchers.IO) { owned = PdfBookSession(context, book, localFile) }
            value = owned
            awaitCancellation()
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { error = context.getString(R.string.ui_unable_to_open_the_book_please_try_again) }
        finally { withContext(NonCancellable + Dispatchers.IO) { owned?.close() } }
    }
    LaunchedEffect(page) { preferences.edit().putInt("page:${book.id}", page).apply() }
    LaunchedEffect(zoom) { preferences.edit().putFloat("pdfZoom", zoom).apply() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(context.getString(R.string.ui_back)) }
            Text(book.title, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { showDetails = true }) { Text(context.getString(R.string.ui_details)) }
            if (allowSave) IconButton(onClick = onSave) { LineIcon("bookmark", if (saved) MaterialTheme.colorScheme.secondary else muted,
                description = if (saved) context.getString(R.string.ui_remove_saved_book) else context.getString(R.string.ui_save_book)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (localFile == null) context.getString(R.string.ui_original_edition_offline) else context.getString(R.string.ui_downloaded_reference_offline), Modifier.weight(1f), fontSize = 11.sp, color = muted)
            TextButton(onClick = { zoom = (zoom - .5f).coerceAtLeast(1f) }, enabled = zoom > 1f) { Text("−") }
            Text(context.getString(R.string.ui_1, (zoom * 100).roundToInt()), fontSize = 12.sp)
            TextButton(onClick = { zoom = (zoom + .5f).coerceAtMost(3f) }, enabled = zoom < 3f) { Text("+") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (book.chapters.isNotEmpty()) TextButton(onClick = { showChapters = true }) { Text(context.getString(R.string.book_contents)) }
            TextButton(onClick = onToggleNight) { Text(if (night) context.getString(R.string.ui_day_mode) else context.getString(R.string.ui_night_mode)) }
            TextButton(onClick = {
                updateBookmarks(if (page in bookmarks) bookmarks - page else bookmarks + page)
            }) { Text(if (page in bookmarks) context.getString(R.string.ui_remove_page_bookmark) else context.getString(R.string.ui_bookmark_this_page)) }
            TextButton(onClick = { showBookmarks = true }) { Text(context.getString(R.string.ui_page_bookmarks_1, bookmarks.size)) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(if (night) Color(0xFF101612) else Color(0xFFE7E5DE))) {
            val density = LocalDensity.current
            val viewportWidth = maxWidth
            val renderWidth = with(density) { (viewportWidth * zoom).roundToPx() }
            var renderError by remember(session, page, zoom) { mutableStateOf(false) }
            val bitmap by produceState<Bitmap?>(null, session, page, renderWidth) {
                value = null
                try { value = withContext(Dispatchers.IO) { session?.render(page - 1, renderWidth) } }
                catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: Exception) { renderError = true }
            }
            when {
                error != null || renderError -> Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: context.getString(R.string.ui_unable_to_display_this_page_try_reducing_the_zoom))
                    Button(onClick = { retry++; renderError = false }) { Text(context.getString(R.string.ui_try_again)) }
                }
                bitmap == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> {
                    key(page) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                            Image(bitmap!!.asImageBitmap(), context.getString(R.string.ui_file_page_1_of_2_3, page, book.pages, book.title),
                                Modifier.width(viewportWidth * zoom), alignment = Alignment.TopCenter,
                                colorFilter = if (night) nightPageFilter else null)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { page-- }, enabled = page > 1) { Text(context.getString(R.string.ui_previous)) }
            TextButton(onClick = { showPageDialog = true }) { Text(context.getString(R.string.ui_file_page_1_2, page, book.pages), fontSize = 13.sp) }
            TextButton(onClick = { page++ }, enabled = page < book.pages) { Text(context.getString(R.string.ui_next)) }
        }
    }
    if (showChapters) AlertDialog(onDismissRequest = { showChapters = false },
        title = { Text(context.getString(R.string.book_contents)) }, text = {
            Column {
                Text(context.getString(R.string.chapter_index_notice), fontSize = 12.sp)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(book.chapters, key = { it.title }) { chapter ->
                        TextButton(onClick = { page = chapter.pdfPage; showChapters = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(context.getString(R.string.chapter_location, localizedLabel(context, chapter.title), chapter.printedPage, chapter.pdfPage))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showChapters = false }) { Text(context.getString(R.string.ui_close)) } })
    if (showDetails) AlertDialog(onDismissRequest = { showDetails = false }, title = { Text(context.getString(R.string.ui_book_and_usage_details)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(book.title, fontSize = 19.sp)
                Text(context.getString(R.string.ui_author_1_publisher_2_edition_3_file_pages_4, book.author, book.publisher, book.edition, book.pages), fontSize = 14.sp)
                Text(book.description, fontSize = 14.sp)
                Text(book.licenseName, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Text(book.rightsNote, fontSize = 13.sp)
                Text(context.getString(R.string.ui_file_page_numbers_may_differ_from_printed_numbers_scanned_files_do_not_s), fontSize = 12.sp, color = muted)
                TextButton(onClick = { openSource(context, book.source) }) { Text(context.getString(R.string.ui_source_page)) }
                TextButton(onClick = { openSource(context, book.licenseUrl) }) { Text(context.getString(R.string.ui_usage_terms)) }
            }
        }, confirmButton = { TextButton(onClick = { showDetails = false }) { Text(context.getString(R.string.ui_close)) } })
    if (showBookmarks) AlertDialog(onDismissRequest = { showBookmarks = false },
        title = { Text(context.getString(R.string.ui_book_page_bookmarks)) }, text = {
            Column {
                Text(context.getString(R.string.ui_1_numbers_refer_to_file_pages, book.title), fontSize = 13.sp, color = muted)
                if (bookmarks.isEmpty()) {
                    Text(context.getString(R.string.ui_no_bookmarks_yet_select_bookmark_this_page_while_reading), Modifier.padding(vertical = 20.dp))
                } else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(bookmarks, key = { it }) { markedPage ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { page = markedPage; showBookmarks = false }, modifier = Modifier.weight(1f)) {
                                Text(context.getString(R.string.ui_go_to_page_1, markedPage))
                            }
                            TextButton(onClick = { updateBookmarks(bookmarks - markedPage) }) { Text(context.getString(R.string.ui_delete_bookmark_1, markedPage)) }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showBookmarks = false }) { Text(context.getString(R.string.ui_close)) } })
    if (showPageDialog) {
        var input by remember { mutableStateOf(page.toString()) }
        val target = parsePageNumber(input, book.pages)
        AlertDialog(onDismissRequest = { showPageDialog = false }, title = { Text(context.getString(R.string.ui_go_to_a_page)) },
            text = { OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true,
                label = { Text(context.getString(R.string.ui_file_page_number_from_1_to_1, book.pages)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = target == null, supportingText = { if (target == null) Text(context.getString(R.string.ui_enter_a_number_within_the_file_page_range)) }) },
            confirmButton = { TextButton(onClick = { target?.let { page = it; showPageDialog = false } }, enabled = target != null) { Text(context.getString(R.string.ui_go)) } },
            dismissButton = { TextButton(onClick = { showPageDialog = false }) { Text(context.getString(R.string.ui_cancel)) } })
    }
}
