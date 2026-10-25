package com.algohary.basair

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal val Ink = Color(0xFF173E35)
internal val Green = Color(0xFF195444)
internal val Gold = Color(0xFFB18B47)
internal val Paper = Color(0xFFF7F5EF)
internal val Muted = Color(0xFF65746B)
internal val Line = Color(0xFFE2E6DC)
internal val SoftGreen = Color(0xFFE8EFE7)

class MainActivity : ComponentActivity() {
    private var audioRequest by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_audio", false)) { audioRequest++; intent.removeExtra("open_audio") }
    }

    override fun attachBaseContext(newBase: Context) {
        val stored = newBase.getSharedPreferences("basair", Context.MODE_PRIVATE).getString("language", null)
        val language = stored?.takeIf { it in setOf("ar", "en") }
            ?: newBase.resources.configuration.locales[0].language.takeIf { it in setOf("ar", "en") } ?: "ar"
        val locale = Locale.forLanguageTag(language)
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("open_audio", false)) { audioRequest++; intent.removeExtra("open_audio") }
        enableEdgeToEdge()
        setContent {
            val direction = if (resources.configuration.locales[0].language == "en") LayoutDirection.Ltr else LayoutDirection.Rtl
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme(colorScheme = lightColorScheme(
                    primary = Green, onPrimary = Color.White, secondary = Gold,
                    background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
                    secondaryContainer = SoftGreen, onSecondaryContainer = Ink,
                    surfaceVariant = SoftGreen, onSurfaceVariant = Ink, outline = Muted, outlineVariant = Line
                )) { BasairApp(audioRequest) }
            }
        }
    }
}

private data class Destination(val id: String, val title: String, val icon: String)
private fun destinations(context: Context) = listOf(
    Destination("home", context.getString(R.string.ui_home), "home"), Destination("library", context.getString(R.string.ui_library), "book"),
    Destination("search", context.getString(R.string.ui_search), "search"), Destination("compare", context.getString(R.string.ui_compare), "scales"),
    Destination("saved", context.getString(R.string.ui_saved), "bookmark")
)

@Composable
private fun BasairApp(audioRequest: Int) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("basair", Context.MODE_PRIVATE) }
    var saved by remember { mutableStateOf(preferences.getStringSet("saved", emptySet())!!.toSet()) }
    var savedComparisons by remember { mutableStateOf(preferences.getStringSet("savedComparisons", emptySet()).orEmpty().toSet()) }
    var referenceVolume by rememberSaveable { mutableStateOf<Int?>(null) }
    var referencePage by rememberSaveable { mutableIntStateOf(1) }
    var comparisonId by rememberSaveable { mutableStateOf("ghusl-intention") }
    var route by rememberSaveable { mutableStateOf("home") }
    var bookId by rememberSaveable { mutableStateOf<String?>(null) }
    var notePage by rememberSaveable { mutableStateOf<Int?>(null) }
    var noteReference by remember { mutableStateOf<Reference?>(null) }
    val openBook: (String) -> Unit = { notePage = null; bookId = it }
    var subject by rememberSaveable { mutableStateOf<String?>(null) }
    var school by rememberSaveable { mutableStateOf<String?>(null) }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val comparisonState = rememberSaveableStateHolder()
    val mediaState = rememberSaveableStateHolder()
    val audioState = rememberSaveableStateHolder()
    val tajweedState = rememberSaveableStateHolder()
    var tajweedQuery by rememberSaveable { mutableStateOf("") }
    val openTajweed: (String) -> Unit = { search ->
        tajweedState.removeState("tajweed"); tajweedQuery = search; bookId = null; route = "tajweed"
    }
    var audioQuery by rememberSaveable { mutableStateOf("") }
    var audioBook by rememberSaveable { mutableStateOf<String?>(null) }
    val openAudio: (String, String?) -> Unit = { search, book ->
        audioState.removeState("audio"); audioQuery = search; audioBook = book; bookId = null; route = "audio"
    }
    LaunchedEffect(audioRequest) {
        if (audioRequest > 0) {
            bookId = null; referenceVolume = null; noteReference = null
            audioState.removeState("audio"); audioQuery = ""; audioBook = null; route = "audio"
        }
    }
    var mediaQuery by rememberSaveable { mutableStateOf("") }
    var mediaBook by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaFavorites by rememberSaveable { mutableStateOf(false) }
    val openMedia: (String, String?, Boolean) -> Unit = { search, book, favorites ->
        mediaState.removeState("media")
        mediaQuery = search; mediaBook = book; mediaFavorites = favorites; bookId = null; route = "media"
    }
    val toggleSave: (String) -> Unit = { id ->
        saved = if (id in saved) saved - id else saved + id
        preferences.edit().putStringSet("saved", saved).apply()
    }
    val visitSubject: (String) -> Unit = { subject = it; school = null; topic = null; query = ""; route = "library"; bookId = null }
    val toggleComparison: (String) -> Unit = { id ->
        savedComparisons = if (id in savedComparisons) savedComparisons - id else savedComparisons + id
        preferences.edit().putStringSet("savedComparisons", savedComparisons).apply()
    }
    val navigate: (String) -> Unit = { route = it; bookId = null }
    val openComparison: (String) -> Unit = { id ->
        comparisonState.removeState("comparison")
        comparisonId = id
        navigate("compare")
    }
    BackHandler(enabled = referenceVolume != null || bookId != null || route != "home") {
        if (referenceVolume != null) referenceVolume = null else if (bookId != null) bookId = null else route = "home"
    }
    noteReference?.let { ref -> ReferenceDownloadDialog(ref, { noteReference = null }, {
        referencePage = it.pdfPage; referenceVolume = it.volume; noteReference = null
    }) }
    if (route == "quran") {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TextButton(onClick = { route = "home" }) { Text(context.getString(R.string.ui_back)) }
            Box(Modifier.weight(1f)) { QuranFeature() }
        }
        return
    }
    referenceVolumes[referenceVolume]?.let { volume ->
        key(volume.volume, referencePage) {
            PdfBookReader(volume.asBook(), false, { referenceVolume = null }, {},
                localFile = java.io.File(referenceDirectory(context), volume.fileName), initialPage = referencePage, allowSave = false)
        }
        return
    }
    val readingBook = Catalog.books.find { it.id == bookId }
    if (readingBook != null) {
        key(readingBook.id) { PdfBookReader(readingBook, readingBook.id in saved,
            { bookId = null }, { toggleSave(readingBook.id) }, initialPage = notePage,
            onExplanations = if (MediaCatalog.items.any { readingBook.id in it.bookIds }) ({ openMedia("", readingBook.id, false) }) else null,
            onAudio = if (AudiobookCatalog.recordings.any { readingBook.id in it.bookIds }) ({ openAudio("", readingBook.id) }) else null) }
        return
    }
    Scaffold(containerColor = Paper, bottomBar = {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            destinations(context).forEach { destination ->
                NavigationBarItem(selected = route == destination.id,
                    onClick = { navigate(destination.id) },
                    icon = { LineIcon(destination.icon, if (route == destination.id) Green else Muted) },
                    label = { Text(destination.title, fontSize = 11.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = SoftGreen,
                        selectedTextColor = Green, unselectedTextColor = Muted))
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (route) {
                "home" -> HomeScreen(visitSubject, { navigate("search") }, { navigate("compare") }, openBook, { navigate("notes") }, { navigate("quran") }, { openMedia("", null, false) }, { openAudio("", null) }, { openTajweed("") })
                "library", "search" -> CatalogScreen(route == "search", query, { query = it }, subject,
                    { subject = it; school = null; topic = null }, openBook, saved, toggleSave, openComparison,
                    school, { school = it }, topic, { topic = it },
                    { referencePage = it.pdfPage; referenceVolume = it.volume }, { navigate("quran") }, { openMedia(query, null, false) }, { openAudio(query, it) }, { openTajweed(query) })
                "tajweed" -> tajweedState.SaveableStateProvider("tajweed") { TajweedFeature(tajweedQuery) { id, page -> notePage = page; bookId = id } }
                "audio" -> audioState.SaveableStateProvider("audio") { key(audioRequest) { AudiobookFeature(audioQuery, audioBook, openBook) } }
                "media" -> mediaState.SaveableStateProvider("media") { MediaFeature(mediaQuery, mediaBook, mediaFavorites, openBook) }
                "compare" -> comparisonState.SaveableStateProvider("comparison") {
                    ComparisonScreen(comparisonId, { comparisonId = it }, savedComparisons, toggleComparison,
                        { referencePage = it.pdfPage; referenceVolume = it.volume })
                }
                "notes" -> StudyNotesScreen { note ->
                    noteBook(note)?.let { book ->
                        if (book.id.startsWith("reference-")) {
                            noteReference = kuwaitReference(book.id.removePrefix("reference-").toInt(), "", note.page).copy(filePageOnly = true)
                        } else { notePage = note.page; bookId = book.id }
                    }
                }
                "saved" -> SavedScreen(Catalog.books.filter { it.id in saved }, openBook, toggleSave,
                    { subject = null; school = null; topic = null; query = ""; navigate("library") }, Catalog.comparisons.filter { it.id in savedComparisons },
                    openComparison, toggleComparison, { navigate("notes") }, { openMedia("", null, true) })
            }
        }
    }
}

@Composable
private fun HomeScreen(onSubject: (String) -> Unit, onSearch: () -> Unit, onCompare: () -> Unit, onBook: (String) -> Unit, onNotes: () -> Unit, onQuran: () -> Unit, onMedia: () -> Unit, onAudio: () -> Unit, onTajweed: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { LanguageSettings() }
        item { QuranEntry(onQuran) }
        item { AudiobookEntry(onAudio) }
        item { TajweedEntry(onTajweed) }
        item { MediaEntry(onMedia) }
        item { OutlinedButton(onClick = onNotes) { Text(context.getString(R.string.notes_title)) } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Green), contentAlignment = Alignment.Center) {
                    LineIcon("open", Color(0xFFE9D7AD), 27)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(context.getString(R.string.ui_basair), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(context.getString(R.string.ui_read_knowledge_know_its_source), fontSize = 12.sp, color = Muted)
                }
                Tag(context.getString(R.string.ui_1_books_and_volumes, Catalog.books.size))
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF123E34), Color(0xFF256B52))))) {
                Canvas(Modifier.matchParentSize()) {
                    val center = Offset(size.width * .12f, size.height * .48f)
                    for (i in 1..5) drawCircle(Color.White.copy(alpha = .055f), i * 34.dp.toPx(), center, style = Stroke(1.dp.toPx()))
                }
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(context.getString(R.string.ui_your_library_of_islamic_studies), color = Color(0xFFDDC58F), fontSize = 12.sp)
                    Text(context.getString(R.string.ui_a_journey_of_knowledge_begins_with_a_question), fontSize = 32.sp, lineHeight = 45.sp,
                        color = Color.White, fontWeight = FontWeight.Bold)
                    Text(context.getString(R.string.ui_fiqh_creed_hadith_biography_and_tafsir_early_muslim_biographies_and_spir), color = Color(0xFFD6E6DB), fontSize = 14.sp, lineHeight = 24.sp)
                    Button(onClick = onSearch, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8D5A7), contentColor = Ink),
                        shape = RoundedCornerShape(12.dp)) {
                        LineIcon("search", Ink, 18); Spacer(Modifier.width(8.dp)); Text(context.getString(R.string.ui_search_the_library))
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(context.getString(R.string.ui_fields_of_knowledge), context.getString(R.string.ui_1_subjects_in_one_place, Catalog.subjects.size))
                Catalog.subjects.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { item ->
                            Surface(onClick = { onSubject(item.id) }, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    LineIcon(item.symbol, Gold, 27)
                                    Text(localizedLabel(context, item.title), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    Text(localizedLabel(context, item.description), fontSize = 11.sp, color = Muted)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Surface(onClick = onCompare, color = SoftGreen, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    LineIcon("scales", Green, 32); Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(context.getString(R.string.ui_one_question_four_schools), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(context.getString(R.string.ui_explore_opinions_and_their_sources), fontSize = 12.sp, color = Muted)
                    }
                    Text("‹", fontSize = 28.sp, color = Green)
                }
            }
        }
        item { SectionTitle(context.getString(R.string.ui_start_reading), context.getString(R.string.ui_original_books_available_offline)) }
        item { BookCard(Catalog.books.first(), false, { onBook(Catalog.books.first().id) }, null) }
        item { Notice(context.getString(R.string.ui_1_books_and_volumes_across_2_subjects_with_sources_and_usage_details_sea, Catalog.books.size, Catalog.subjects.size)) }
        item { TextButton(onClick = { openSource(context, "https://github.com/Hassan-Algohharey/basair/blob/main/publishing/privacy-policy.txt") }) { Text(context.getString(R.string.ui_privacy_and_support)) } }
    }
}

@Composable
private fun CatalogScreen(searchMode: Boolean, query: String, onQuery: (String) -> Unit, subject: String?,
    onSubject: (String?) -> Unit, onBook: (String) -> Unit, saved: Set<String>, onSave: (String) -> Unit, onCompare: (String) -> Unit,
    school: String?, onSchool: (String?) -> Unit, topic: String?, onTopic: (String?) -> Unit, onReference: (Reference) -> Unit, onQuran: () -> Unit, onMedia: () -> Unit, onAudio: (String?) -> Unit, onTajweed: () -> Unit) {
    val context = LocalContext.current
    val results = remember(query, subject, school, topic) { Catalog.search(query, subject, school, topic) }
    val comparisons = remember(query, subject, school, topic, searchMode) {
        if (searchMode && school == null && topic == null) Catalog.searchComparisons(query, subject) else emptyList()
    }
    val audioResults = searchAudiobooks(query).filter { recording ->
        recording.bookIds.any { id -> Catalog.books.any { book -> book.id == id && book.matchesOrganization(subject, school, topic) } }
    }
    val mediaMatches = if (subject == null && school == null && topic == null)
        filteredMediaItems(MediaUiState(scholars = MediaCatalog.scholars, items = MediaCatalog.items, query = query, series = MediaCatalog.series,
            bookTitles = Catalog.books.associate { it.id to it.title })).size else 0
    val showQuran = subject in listOf(null, "tafsir") && school == null && topic == null &&
        normalizeArabic(query).split(Regex("\\s+")).all { it in normalizeArabic("القرآن الكريم المصحف التفسير الميسر Quran Koran Madinah Mushaf tafsir muyassar") }
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { if (LocalConfiguration.current.locales[0].language == "en") Notice(context.getString(R.string.arabic_content_notice)) }
        item { SectionTitle(if (searchMode) context.getString(R.string.ui_find_knowledge) else context.getString(R.string.ui_library), context.getString(R.string.ui_original_books_and_volumes_by_subject)) }
        item {
            OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(context.getString(R.string.ui_title_author_or_topic), fontSize = 14.sp) },
                leadingIcon = { LineIcon("search", Muted) },
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text(context.getString(R.string.ui_clear)) } },
                singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line,
                    focusedBorderColor = Green, unfocusedContainerColor = Color.White))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = subject == null, onClick = { onSubject(null) }, label = { Text(context.getString(R.string.ui_all)) })
                Catalog.subjects.forEach {
                    FilterChip(selected = subject == it.id, onClick = { onSubject(it.id) }, label = { Text(localizedLabel(context, it.title)) })
                }
            }
        }
        if (subject in listOf(null, "tajweed") && school == null && topic == null && searchTajweed(query).isNotEmpty()) item { TajweedEntry(onTajweed) }
        if (showQuran) item { QuranEntry(onQuran) }
        if (audioResults.isNotEmpty()) item { AudiobookEntry({ onAudio(audioResults.singleOrNull()?.bookIds?.firstOrNull()) }, audioResults.size) }
        if (mediaMatches > 0) item { MediaEntry(onMedia, mediaMatches) }
        if (subject == "fiqh") item { SchoolSelector(school, onSchool) }
        subject?.let { selectedSubject -> Catalog.books.filter { it.subject == selectedSubject }.flatMap { it.topics }.distinct() }?.let { filters ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(context.getString(R.string.ui_browse_a_topic), color = Muted, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        filters.forEach { filter ->
                            FilterChip(selected = topic == filter, onClick = { onTopic(if (topic == filter) null else filter) }, label = { Text(localizedLabel(context, filter)) })
                        }
                    }
                }
            }
        }
        if (subject == "salaf") item {
            Notice(context.getString(R.string.ui_biographies_of_the_four_imams_companions_and_successors_only_volumes_1_a))
        }
        item { Text(context.getString(R.string.ui_results_1, results.size) + if (searchMode) context.getString(R.string.ui_1_comparisons, comparisons.size) else context.getString(R.string.ui_books_and_volumes), color = Muted, fontSize = 12.sp) }
        if (results.isEmpty() && comparisons.isEmpty() && !showQuran && mediaMatches == 0 && audioResults.isEmpty() && !(subject == "fiqh" && (school == null || school == "shared") && matchingReferenceVolumes(query, topic).isNotEmpty())) item {
            if (school != null && school != "shared") SchoolEmptyState { onSchool("shared") }
            else EmptyState(context.getString(R.string.ui_no_results), context.getString(R.string.ui_try_a_shorter_query_or_select_all_subjects), context.getString(R.string.ui_reset_search)) { onQuery(""); onSubject(null); onSchool(null); onTopic(null) }
        }
        items(comparisons, key = { "comparison:" + it.title }) { comparison ->
            Surface(onClick = { onCompare(comparison.id) }, color = SoftGreen, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(R.string.ui_compare_the_four_schools), fontSize = 11.sp, color = Green)
                    Text(comparison.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(context.getString(R.string.ui_view_opinions_and_sources), fontSize = 12.sp, color = Green)
                }
            }
        }
        items(results, key = { it.id }) { book -> BookCard(book, book.id in saved, { onBook(book.id) }, { onSave(book.id) }) }
        if (subject == "fiqh" && (school == null || school == "shared")) {
            item { SharedReferences(query, topic, onReference) }
        }
    }
}

@Composable
private fun BookCard(book: Book, saved: Boolean, onOpen: () -> Unit, onSave: (() -> Unit)?) {
    val context = LocalContext.current
    Surface(onClick = onOpen, color = Color.White, shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(61.dp).height(82.dp).clip(RoundedCornerShape(6.dp, 12.dp, 12.dp, 6.dp))
                .background(if (book.subject == "fiqh") Green else Color(0xFF646C50))
                .border(1.dp, Gold.copy(alpha = .45f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                LineIcon(Catalog.subjects.first { it.id == book.subject }.symbol, Color(0xFFE8D5A7), 28)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(localizedLabel(context, Catalog.subjects.first { it.id == book.subject }.title), fontSize = 11.sp, color = Gold)
                Text(book.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, fontSize = 11.sp, color = Muted)
                Text(context.getString(R.string.ui_read_book_1_pages, book.pages), fontSize = 11.sp, color = Green)
            }
            if (onSave != null) IconButton(onClick = onSave) {
                LineIcon("bookmark", if (saved) Gold else Muted, description = if (saved) context.getString(R.string.ui_remove_saved_book) else context.getString(R.string.ui_save_book))
            }
        }
    }
}

@Composable
private fun SavedScreen(books: List<Book>, onBook: (String) -> Unit, onSave: (String) -> Unit, onLibrary: () -> Unit,
    comparisons: List<Comparison>, onComparison: (String) -> Unit, onRemoveComparison: (String) -> Unit, onNotes: () -> Unit, onMedia: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle(context.getString(R.string.ui_saved), context.getString(R.string.ui_your_selections_always_nearby)) }
        item { OutlinedButton(onClick = onNotes) { Text(context.getString(R.string.notes_title)) } }
        item { OutlinedButton(onClick = onMedia) { Text(context.getString(R.string.media_saved_entry)) } }
        item { Text(context.getString(R.string.ui_books_1_issues_2, books.size, comparisons.size), color = Muted, fontSize = 13.sp) }
        if (books.isEmpty() && comparisons.isEmpty()) item { EmptyState(context.getString(R.string.ui_your_next_reading_starts_here), context.getString(R.string.ui_save_a_book_or_comparison_to_find_it_here_on_this_device), context.getString(R.string.ui_explore_the_library), onLibrary) }
        if (comparisons.isNotEmpty()) item { Text(context.getString(R.string.ui_saved_issues), fontWeight = FontWeight.Bold) }
        items(comparisons, key = { "comparison:${it.id}" }) { comparison ->
            Surface(color = SoftGreen, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(comparison.topic, fontSize = 12.sp, color = Muted)
                    Text(comparison.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = { onComparison(comparison.id) }) { Text(context.getString(R.string.ui_open_comparison)) }
                        TextButton(onClick = { onRemoveComparison(comparison.id) }) { Text(context.getString(R.string.ui_remove_issue)) }
                    }
                }
            }
        }
        if (books.isNotEmpty()) item { Text(context.getString(R.string.ui_saved_books), fontWeight = FontWeight.Bold) }
        items(books, key = { it.id }) { book -> BookCard(book, true, { onBook(book.id) }, { onSave(book.id) }) }
    }
}

@Composable
internal fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(subtitle, fontSize = 12.sp, color = Muted)
    }
}

@Composable
internal fun Tag(text: String) {
    Text(text, modifier = Modifier.background(SoftGreen, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp), color = Green, fontSize = 11.sp)
}

@Composable
internal fun Notice(text: String) {
    Surface(color = Color(0xFFF0EBDD), shape = RoundedCornerShape(14.dp)) {
        Text(text, Modifier.fillMaxWidth().padding(16.dp), color = Color(0xFF695B3F), fontSize = 12.sp, lineHeight = 22.sp)
    }
}

@Composable
internal fun EmptyState(title: String, message: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 38.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LineIcon("open", Gold, 48)
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(message, color = Muted, fontSize = 14.sp, lineHeight = 26.sp)
        Button(onClick = onAction) { Text(action) }
    }
}

internal fun openSource(context: Context, source: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source))
    try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, context.getString(R.string.ui_no_browser_is_available_to_open_this_source), android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun LineIcon(kind: String, color: Color, size: Int = 24, description: String? = null) {
    Canvas(Modifier.size(size.dp).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        val scale = this.size.width / 24f
        fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, point(x,y), point(x2,y2), 1.65f * scale)
        fun path(vararg points: Pair<Float,Float>, close: Boolean = false) {
            val p = Path(); p.moveTo(points[0].first * scale, points[0].second * scale)
            points.drop(1).forEach { p.lineTo(it.first * scale, it.second * scale) }; if (close) p.close()
            drawPath(p, color, style = Stroke(1.65f * scale))
        }
        when (kind) {
            "search" -> { drawCircle(color, 7f * scale, point(10f,10f), style = Stroke(1.65f * scale)); line(15f,15f,21f,21f) }
            "bookmark" -> path(6f to 3f, 18f to 3f, 18f to 21f, 12f to 17f, 6f to 21f, close = true)
            "home" -> { path(3f to 10f, 12f to 3f, 21f to 10f); path(5f to 9f, 5f to 21f, 10f to 21f, 10f to 15f, 14f to 15f, 14f to 21f, 19f to 21f, 19f to 9f) }
            "scales" -> { line(12f,3f,12f,21f); line(5f,7f,19f,7f); line(7f,21f,17f,21f)
                path(6f to 7f, 2f to 15f, 10f to 15f, 6f to 7f); path(18f to 7f, 14f to 15f, 22f to 15f, 18f to 7f) }
            "star" -> path(12f to 2f, 15f to 8f, 22f to 10f, 17f to 15f, 18f to 22f, 12f to 18f, 6f to 22f, 7f to 15f, 2f to 10f, 9f to 8f, close = true)
            "sun" -> { drawCircle(color, 5f * scale, point(12f,12f), style = Stroke(1.65f * scale));
                line(12f,1f,12f,4f); line(12f,20f,12f,23f); line(1f,12f,4f,12f); line(20f,12f,23f,12f)
                line(4f,4f,6f,6f); line(18f,18f,20f,20f); line(4f,20f,6f,18f); line(18f,6f,20f,4f) }
            "open" -> { path(12f to 5f, 8f to 3f, 2f to 3f, 2f to 19f, 8f to 19f, 12f to 21f, 16f to 19f, 22f to 19f, 22f to 3f, 16f to 3f, 12f to 5f, 12f to 21f)
                line(5f,8f,9f,8f); line(15f,8f,19f,8f); line(5f,12f,9f,12f); line(15f,12f,19f,12f) }
            else -> { path(5f to 3f, 20f to 3f, 20f to 21f, 5f to 21f, 3f to 19f, 3f to 5f, 5f to 3f); line(7f,3f,7f,21f); line(11f,8f,17f,8f); line(11f,12f,17f,12f) }
        }
    }
}
