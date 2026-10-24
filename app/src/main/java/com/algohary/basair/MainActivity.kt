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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
    override fun attachBaseContext(newBase: Context) {
        val locale = Locale.forLanguageTag("ar")
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = lightColorScheme(
                    primary = Green, onPrimary = Color.White, secondary = Gold,
                    background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
                    secondaryContainer = SoftGreen, onSecondaryContainer = Ink,
                    surfaceVariant = SoftGreen, onSurfaceVariant = Ink, outline = Muted, outlineVariant = Line
                )) { BasairApp() }
            }
        }
    }
}

private data class Destination(val id: String, val title: String, val icon: String)
private val destinations = listOf(
    Destination("home", "الرئيسية", "home"), Destination("library", "المكتبة", "book"),
    Destination("search", "البحث", "search"), Destination("compare", "المقارنة", "scales"),
    Destination("saved", "محفوظاتي", "bookmark")
)

@Composable
private fun BasairApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("basair", Context.MODE_PRIVATE) }
    var saved by remember { mutableStateOf(preferences.getStringSet("saved", emptySet())!!.toSet()) }
    var comparisonId by rememberSaveable { mutableStateOf("ghusl-intention") }
    var route by rememberSaveable { mutableStateOf("home") }
    var bookId by rememberSaveable { mutableStateOf<String?>(null) }
    var subject by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val toggleSave: (String) -> Unit = { id ->
        saved = if (id in saved) saved - id else saved + id
        preferences.edit().putStringSet("saved", saved).apply()
    }
    val visitSubject: (String) -> Unit = { subject = it; route = "library"; bookId = null }
    val navigate: (String) -> Unit = { route = it; bookId = null }
    BackHandler(enabled = bookId != null || route != "home") {
        if (bookId != null) bookId = null else route = "home"
    }
    val readingBook = Catalog.books.find { it.id == bookId }
    if (readingBook != null) {
        key(readingBook.id) { PdfBookReader(readingBook, readingBook.id in saved,
            { bookId = null }, { toggleSave(readingBook.id) }) }
        return
    }
    Scaffold(containerColor = Paper, bottomBar = {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            destinations.forEach { destination ->
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
                "home" -> HomeScreen(visitSubject, { navigate("search") }, { navigate("compare") }, { bookId = it })
                "library", "search" -> CatalogScreen(route == "search", query, { query = it }, subject,
                    { subject = it }, { bookId = it }, saved, toggleSave, { comparisonId = it; navigate("compare") })
                "compare" -> ComparisonScreen(comparisonId, { comparisonId = it })
                "saved" -> SavedScreen(Catalog.books.filter { it.id in saved }, { bookId = it }, toggleSave,
                    { subject = null; navigate("library") })
            }
        }
    }
}

@Composable
private fun HomeScreen(onSubject: (String) -> Unit, onSearch: () -> Unit, onCompare: () -> Unit, onBook: (String) -> Unit) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Green), contentAlignment = Alignment.Center) {
                    LineIcon("open", Color(0xFFE9D7AD), 27)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("بصائر", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("علمٌ تقرؤه، ومصدرٌ تعرفه", fontSize = 12.sp, color = Muted)
                }
                Tag("${Catalog.books.size} كتابًا ومجلدًا")
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
                    Text("مكتبتك للعلوم الإسلامية", color = Color(0xFFDDC58F), fontSize = 12.sp)
                    Text("رحلة العلم تبدأ\nبسؤال", fontSize = 32.sp, lineHeight = 45.sp,
                        color = Color.White, fontWeight = FontWeight.Bold)
                    Text("فقه، عقيدة، حديث، سيرة وتفسير\nوتراجم السلف والتزكية", color = Color(0xFFD6E6DB), fontSize = 14.sp, lineHeight = 24.sp)
                    Button(onClick = onSearch, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8D5A7), contentColor = Ink),
                        shape = RoundedCornerShape(12.dp)) {
                        LineIcon("search", Ink, 18); Spacer(Modifier.width(8.dp)); Text("ابحث في المكتبة")
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("أبواب المعرفة", "${Catalog.subjects.size} أقسام، نافذة واحدة")
                Catalog.subjects.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { item ->
                            Surface(onClick = { onSubject(item.id) }, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    LineIcon(item.symbol, Gold, 27)
                                    Text(item.title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    Text(item.description, fontSize = 11.sp, color = Muted)
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
                        Text("مسألة واحدة، أربعة مذاهب", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("تعرّف على الأقوال مع مصدرها", fontSize = 12.sp, color = Muted)
                    }
                    Text("‹", fontSize = 28.sp, color = Green)
                }
            }
        }
        item { SectionTitle("ابدأ القراءة", "كتب أصلية متاحة دون اتصال") }
        item { BookCard(Catalog.books.first(), false, { onBook(Catalog.books.first().id) }, null) }
        item { Notice("${Catalog.books.size} كتابًا ومجلدًا في ${Catalog.subjects.size} أقسام، مع بيانات المصدر والإتاحة لكل نسخة. البحث الحالي في عناوين الكتب وموضوعاتها والمقارنات؛ الصفحات المصورة تُقرأ كما في الأصل.") }
        item { TextButton(onClick = { openSource(context, "https://sites.google.com/view/basair-privacy-algohary") }) { Text("سياسة الخصوصية والدعم") } }
    }
}

@Composable
private fun CatalogScreen(searchMode: Boolean, query: String, onQuery: (String) -> Unit, subject: String?,
    onSubject: (String?) -> Unit, onBook: (String) -> Unit, saved: Set<String>, onSave: (String) -> Unit, onCompare: (String) -> Unit) {
    val results = remember(query, subject) { Catalog.search(query, subject) }
    val comparisons = remember(query, subject, searchMode) {
        if (searchMode) Catalog.searchComparisons(query, subject) else emptyList()
    }
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle(if (searchMode) "ابحث عن معرفة" else "المكتبة", "كتب ومجلدات أصلية مرتبة حسب العلوم") }
        item {
            OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("عنوان، مؤلف أو موضوع…", fontSize = 14.sp) },
                leadingIcon = { LineIcon("search", Muted) },
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("مسح") } },
                singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line,
                    focusedBorderColor = Green, unfocusedContainerColor = Color.White))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = subject == null, onClick = { onSubject(null) }, label = { Text("الكل") })
                Catalog.subjects.forEach {
                    FilterChip(selected = subject == it.id, onClick = { onSubject(it.id) }, label = { Text(it.title) })
                }
            }
        }
        Catalog.subjectFilters[subject]?.let { filters ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("تصفح موضوعًا", color = Muted, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        filters.forEach { filter ->
                            FilterChip(selected = query == filter, onClick = { onQuery(if (query == filter) "" else filter) }, label = { Text(filter) })
                        }
                    }
                }
            }
        }
        if (subject == "salaf") item {
            Notice("سير الأئمة الأربعة، وتراجم الصحابة والتابعين ومن بعدهم. المتاح من صفة الصفوة هو المجلدان الأول والثاني؛ بيانات كل نسخة ومصدرها في «البيانات» داخل القارئ.")
        }
        item { Text("النتائج: ${results.size}" + if (searchMode) " · ${comparisons.size} مقارنة" else " · كتب ومجلدات", color = Muted, fontSize = 12.sp) }
        if (results.isEmpty() && comparisons.isEmpty()) item {
            EmptyState("لم نجد نتائج", "جرّب كلمة أقصر أو اختر جميع الأقسام.", "إعادة ضبط البحث") { onQuery(""); onSubject(null) }
        }
        items(comparisons, key = { "comparison:" + it.title }) { comparison ->
            Surface(onClick = { onCompare(comparison.id) }, color = SoftGreen, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مقارنة المذاهب الأربعة", fontSize = 11.sp, color = Green)
                    Text(comparison.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("عرض الأقوال والمصدر ←", fontSize = 12.sp, color = Green)
                }
            }
        }
        items(results, key = { it.id }) { book -> BookCard(book, book.id in saved, { onBook(book.id) }, { onSave(book.id) }) }
    }
}

@Composable
private fun BookCard(book: Book, saved: Boolean, onOpen: () -> Unit, onSave: (() -> Unit)?) {
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
                Text(Catalog.subjects.first { it.id == book.subject }.title, fontSize = 11.sp, color = Gold)
                Text(book.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, fontSize = 11.sp, color = Muted)
                Text("قراءة الكتاب · ${book.pages} صفحة", fontSize = 11.sp, color = Green)
            }
            if (onSave != null) IconButton(onClick = onSave) {
                LineIcon("bookmark", if (saved) Gold else Muted, description = if (saved) "إزالة الكتاب من المحفوظات" else "حفظ الكتاب")
            }
        }
    }
}

@Composable
private fun SavedScreen(books: List<Book>, onBook: (String) -> Unit, onSave: (String) -> Unit, onLibrary: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("محفوظاتي", "ما اخترته، قريب منك دائمًا") }
        if (books.isEmpty()) item { EmptyState("مساحة لقراءتك القادمة", "احفظ كتابًا من المكتبة لتجده هنا، مع استئناف آخر صفحة قرأتها على هذا الجهاز.", "استكشف المكتبة", onLibrary) }
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
        android.widget.Toast.makeText(context, "لا يوجد متصفح لفتح المصدر", android.widget.Toast.LENGTH_SHORT).show()
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
