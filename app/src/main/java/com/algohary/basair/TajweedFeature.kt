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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal data class TajweedReading(val bookId: String, val chapterTitle: String) {
    val book: Book get() = Catalog.books.first { it.id == bookId }
    val chapter: BookChapter get() = book.chapters.first { it.title == chapterTitle }
}
internal data class TajweedTopic(val id: String, val title: String, val readings: List<TajweedReading>)
internal data class TajweedExample(val id: String, val topicId: String, val word: String, val englishTitle: String,
    val surah: Int, val ayah: Int, val recordingId: String) {
    val topic: TajweedTopic get() = tajweedTopics.first { it.id == topicId }
    val recording: Audiobook get() = TajweedExamples.recordings.first { it.id == recordingId }
}

internal fun searchTajweedExamples(query: String): List<TajweedExample> {
    val terms = mediaSearchKey(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    return TajweedExamples.examples.filter { e ->
        val text = mediaSearchKey("${e.word} ${e.englishTitle} ${e.topic.title} ${e.recording.title} ${e.recording.englishTitle} علي الحذيفي Ali al-Hudhaify Hafs حفص تجويد tajweed")
        terms.all { it in text }
    }
}
internal data class TajweedAudioLesson(val id: String, val title: String, val englishTitle: String,
    val speaker: String, val englishSpeaker: String, val topicIds: List<String>, val bookIds: List<String>,
    val surah: Int? = null, val ayah: Int? = null) {
    val readings: List<TajweedReading> get() = if (topicIds.isEmpty()) listOf(TajweedReading("jazariyyah", "التجويد"))
        else topicIds.flatMap { id -> tajweedTopics.first { it.id == id }.readings }.distinct()
}

internal fun searchTajweedAudio(query: String): List<TajweedAudioLesson> {
    val terms = mediaSearchKey(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    return TajweedAudioCatalog.lessons.filter { lesson ->
        val text = mediaSearchKey("${lesson.title} ${lesson.englishTitle} ${lesson.speaker} ${lesson.englishSpeaker} tajweed tajwid التجويد " +
            lesson.topicIds.joinToString(" ") { id -> tajweedTopics.first { it.id == id }.let { it.title + " " + topicSearchAliases[it.title].orEmpty() } })
        terms.all { it in text }
    }
}

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
internal fun TajweedFeature(initialQuery: String, onRead: (String, Int) -> Unit,
    onAudio: (String) -> Unit, onCommentaries: (String) -> Unit, onQuran: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val english = LocalConfiguration.current.locales[0].language == "en"
    val preferences = remember { context.getSharedPreferences("basair-tajweed", Context.MODE_PRIVATE) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var read by remember { mutableStateOf(validTajweedMarks(preferences.getStringSet("read", emptySet()).orEmpty())) }
    var last by remember { mutableStateOf(preferences.getString("last", null)) }
    var lastLesson by remember { mutableStateOf(preferences.getString("lastLesson", null)) }
    val results = remember(query) { searchTajweed(query) }
    val audioResults = remember(query) { searchTajweedAudio(query) }
    val examples = remember(query) { searchTajweedExamples(query) }
    fun lessonPlayer(lesson: TajweedAudioLesson) {
        lastLesson = lesson.id
        preferences.edit().putString("lastLesson", lesson.id).apply()
        onAudio(lesson.id)
    }
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
            TajweedExamples.examples.find { "example-" + it.id == lastLesson }?.let { example ->
                TextButton(onClick = { onAudio(example.recordingId) }) {
                    Text(stringResource(R.string.tja_resume) + ": " + if (english) example.englishTitle else example.word)
                }
            }
            TajweedAudioCatalog.lessons.find { it.id == lastLesson }?.let { lesson ->
                TextButton(onClick = { lessonPlayer(lesson) }) {
                    Text(stringResource(R.string.tja_resume) + ": " + if (english) lesson.englishTitle else lesson.title)
                }
            }
        }
        item {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tj_search)) }, singleLine = true,
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(stringResource(R.string.ui_clear)) } })
        }
        item {
            Text(stringResource(R.string.tje_title, examples.size), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tje_notice), style = MaterialTheme.typography.bodySmall)
        }
        items(examples, key = { "example-" + it.id }) { example ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (english) example.englishTitle else example.word, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.tje_reader))
                    Text(example.recording.title + " · 00:00–" + audioTime(example.recording.tracks.single().durationMs!!))
                    Button(onClick = {
                        lastLesson = "example-" + example.id
                        preferences.edit().putString("lastLesson", lastLesson).apply()
                        onAudio(example.recordingId)
                    }) { Text(stringResource(R.string.tje_play)) }
                    TextButton(onClick = { onQuran(example.surah, example.ayah) }) {
                        Text(stringResource(R.string.tje_verse, example.surah, example.ayah))
                    }
                    example.topic.readings.forEach { reading ->
                        TextButton(onClick = { onRead(reading.bookId, reading.chapter.pdfPage) }) {
                            Text(stringResource(R.string.tj_read_page, reading.book.title, reading.chapter.pdfPage, reading.chapter.printedPage))
                        }
                    }
                    TextButton(onClick = { openSource(context, example.recording.sourceUrl) }) { Text(stringResource(R.string.audio_source)) }
                }
            }
        }
        item { Text(stringResource(R.string.tj_books), style = MaterialTheme.typography.titleMedium) }
        items(Catalog.search(query, "tajweed"), key = { "book-" + it.id }) { book ->
            Column {
                OutlinedButton(onClick = { onRead(book.id, 1) }, modifier = Modifier.fillMaxWidth()) { Text(book.title) }
                val count = MediaCatalog.items.count { book.id in it.bookIds }
                if (count > 0) TextButton(onClick = { onCommentaries(book.id) }) {
                    Text(stringResource(R.string.tja_commentaries, book.title, count))
                }
            }
        }
        item {
            Text(stringResource(R.string.tja_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tja_count, audioResults.size))
            Text(stringResource(R.string.tja_notice), style = MaterialTheme.typography.bodySmall)
        }
        items(audioResults, key = { "audio-" + it.id }) { lesson ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (english) lesson.englishTitle else lesson.title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.tja_speaker, if (english) lesson.englishSpeaker else lesson.speaker))
                    Button(onClick = { lessonPlayer(lesson) }) { Text(stringResource(R.string.tja_open)) }
                    if (lesson.surah != null && lesson.ayah != null) TextButton(onClick = { onQuran(lesson.surah, lesson.ayah) }) {
                        Text(stringResource(R.string.tja_verses, lesson.surah, lesson.ayah))
                    }
                    lesson.readings.forEach { reading ->
                        TextButton(onClick = { onRead(reading.bookId, reading.chapter.pdfPage) }) {
                            Text(stringResource(R.string.tj_read_page, reading.book.title, reading.chapter.pdfPage, reading.chapter.printedPage))
                        }
                    }
                    TextButton(onClick = { openSource(context, "https://islamhouse.com/ar/audios/174077/") }) {
                        Text(stringResource(R.string.tja_source))
                    }
                }
            }
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
                    TajweedAudioCatalog.lessons.filter { topic.id in it.topicIds }.forEach { lesson ->
                        TextButton(onClick = { lessonPlayer(lesson) }) {
                            Text(stringResource(R.string.tja_open) + ": " + if (english) lesson.englishTitle else lesson.title)
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
