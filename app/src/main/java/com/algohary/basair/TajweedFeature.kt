package com.algohary.basair

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal data class TajweedReading(val bookId: String, val chapterTitle: String) {
    val book: Book get() = Catalog.books.first { it.id == bookId }
    val chapter: BookChapter get() = book.chapters.first { it.title == chapterTitle }
}
internal data class TajweedTopic(val id: String, val title: String, val readings: List<TajweedReading>)

internal val tajweedTopics = listOf(
    TajweedTopic("articulation", "مخارج الحروف", listOf(TajweedReading("jazariyyah", "مخارج الحروف"))),
    TajweedTopic("attributes", "صفات الحروف", listOf(TajweedReading("jazariyyah", "صفات الحروف"))),
    TajweedTopic("noon", "أحكام النون الساكنة والتنوين", listOf(TajweedReading("tuhfat-atfal", "أحكام النون الساكنة والتنوين"), TajweedReading("jazariyyah", "أحكام النون الساكنة والتنوين"))),
    TajweedTopic("ghunnah", "أحكام الميم والنون المشددتين", listOf(TajweedReading("tuhfat-atfal", "أحكام الميم والنون المشددتين"))),
    TajweedTopic("meem", "أحكام الميم الساكنة", listOf(TajweedReading("tuhfat-atfal", "أحكام الميم الساكنة"), TajweedReading("jazariyyah", "أحكام الميم الساكنة"))),
    TajweedTopic("lam", "أحكام اللام", listOf(TajweedReading("tuhfat-atfal", "أحكام اللام"), TajweedReading("jazariyyah", "أحكام اللام"))),
    TajweedTopic("pairs", "المتماثلان والمتقاربان والمتجانسان", listOf(TajweedReading("tuhfat-atfal", "المتماثلان والمتقاربان والمتجانسان"))),
    TajweedTopic("madd", "المدود", listOf(TajweedReading("tuhfat-atfal", "المدود"), TajweedReading("jazariyyah", "المدود"))),
    TajweedTopic("stopping", "الوقف والابتداء", listOf(TajweedReading("jazariyyah", "الوقف والابتداء")))
)

internal fun searchTajweed(query: String): List<TajweedTopic> {
    val terms = normalizeArabic(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    return tajweedTopics.filter { topic ->
        val text = normalizeArabic(topic.title + " " + topicSearchAliases[topic.title].orEmpty() + " tajweed tajwid التجويد " +
            topic.readings.joinToString(" ") { "${it.book.title} ${it.book.author} ${it.bookId}" })
        terms.all { it in text }
    }
}

internal fun validTajweedMarks(stored: Set<String>): Set<String> = stored.intersect(tajweedTopics.map { it.id }.toSet())

@Composable
internal fun TajweedEntry(onOpen: () -> Unit) {
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tj_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.tj_intro))
        }
    }
}

@Composable
internal fun TajweedFeature(initialQuery: String, onRead: (String, Int) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("basair-tajweed", Context.MODE_PRIVATE) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var read by remember { mutableStateOf(validTajweedMarks(preferences.getStringSet("read", emptySet()).orEmpty())) }
    var last by remember { mutableStateOf(preferences.getString("last", null)) }
    val results = remember(query) { searchTajweed(query) }
    fun open(topic: TajweedTopic, reading: TajweedReading) {
        last = topic.id
        preferences.edit().putString("last", topic.id).apply()
        onRead(reading.bookId, reading.chapter.pdfPage)
    }
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionTitle(stringResource(R.string.tj_title), stringResource(R.string.tj_intro))
            Notice(stringResource(R.string.tj_notice))
        }
        item {
            Text(stringResource(R.string.tj_progress, read.size, tajweedTopics.size))
            Text(stringResource(R.string.tj_local), style = MaterialTheme.typography.bodySmall)
            tajweedTopics.find { it.id == last }?.let { topic ->
                TextButton(onClick = { open(topic, topic.readings.first()) }) {
                    Text(stringResource(R.string.tj_continue) + ": " + localizedLabel(context, topic.title))
                }
            }
        }
        item {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tj_search)) }, singleLine = true,
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(stringResource(R.string.ui_clear)) } })
        }
        item { Text(stringResource(R.string.tj_books), style = MaterialTheme.typography.titleMedium) }
        items(Catalog.search(query, "tajweed"), key = { "book-" + it.id }) { book ->
            OutlinedButton(onClick = { onRead(book.id, 1) }, modifier = Modifier.fillMaxWidth()) { Text(book.title) }
        }
        item {
            Text(stringResource(R.string.tj_plan), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tj_results, results.size))
            if (results.isEmpty()) Text(stringResource(R.string.tj_empty))
        }
        items(results, key = { it.id }) { topic ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedLabel(context, topic.title), style = MaterialTheme.typography.titleMedium)
                    topic.readings.forEach { reading ->
                        TextButton(onClick = { open(topic, reading) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.tj_read_page, reading.book.title, reading.chapter.pdfPage, reading.chapter.printedPage))
                        }
                    }
                    FilterChip(selected = topic.id in read, onClick = {
                        read = if (topic.id in read) read - topic.id else read + topic.id
                        preferences.edit().putStringSet("read", read).apply()
                    }, label = { Text(stringResource(if (topic.id in read) R.string.tj_unmark else R.string.tj_mark)) })
                }
            }
        }
    }
}
