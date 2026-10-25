package com.algohary.basair

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

// The parent owns navigation, filters, favorites and external navigation; this screen never downloads media.
internal data class MediaUiScholar(
    val id: String, val name: String, val description: String, val sourceName: String, val sourceUrl: String,
    val englishName: String = "", val itemCount: Int = 0
)
internal data class MediaUiItem(
    val id: String, val scholarId: String, val title: String, val kind: String,
    val topic: String, val series: String = "", val sourceName: String, val sourceUrl: String,
    val bookIds: List<String> = emptyList(), val searchAliases: String = "", val sourceNote: String = "",
    val englishSourceNote: String = "", val format: String = "",
    val seriesId: String? = null, val episodeNumber: Int? = null,
    val topics: List<String> = emptyList(), val englishTopics: List<String> = emptyList(),
    val publishedHijriYear: Int? = null, val publishedDateLabel: String = "", val durationSeconds: Int? = null
)
internal data class MediaUiState(
    val scholars: List<MediaUiScholar> = emptyList(), val items: List<MediaUiItem> = emptyList(),
    val favoriteIds: Set<String> = emptySet(), val query: String = "", val scholarFilter: String? = null,
    val kindFilter: String? = null, val favoritesOnly: Boolean = false,
    val profileId: String? = null, val selectedItemId: String? = null,
    val bookTitles: Map<String, String> = emptyMap(), val bookFilter: String? = null,
    val series: List<MediaUiSeries> = emptyList(), val seriesFilter: String? = null,
    val topicFilter: String? = null, val lastOpenedId: String? = null,
    val sectionFilter: String? = null, val seriesPositions: Map<String, String> = emptyMap(),
    val completedIds: Set<String> = emptySet(), val yearFilter: Int? = null, val durationFilter: String? = null
)
internal sealed interface MediaAction {
    data object SubmitSearch : MediaAction
    data class Query(val value: String) : MediaAction
    data class ScholarFilter(val value: String?) : MediaAction
    data class KindFilter(val value: String?) : MediaAction
    data class FavoritesOnly(val value: Boolean) : MediaAction
    data class Profile(val id: String?) : MediaAction
    data class SelectItem(val id: String?) : MediaAction
    data class ToggleFavorite(val id: String) : MediaAction
    data class OpenSource(val url: String) : MediaAction
    data class OpenBook(val id: String) : MediaAction
    data class SeriesFilter(val value: String?) : MediaAction
    data class TopicFilter(val value: String?) : MediaAction
    data class YearFilter(val value: Int?) : MediaAction
    data class DurationFilter(val value: String?) : MediaAction
    data class SectionFilter(val value: String?) : MediaAction
    data class OpenBookPage(val linkId: String) : MediaAction
    data class SetSeriesPosition(val itemId: String) : MediaAction
    data class ClearSeriesPosition(val seriesId: String) : MediaAction
    data class ToggleCompleted(val itemId: String) : MediaAction
    data object ResumeLast : MediaAction
    data object ClearLast : MediaAction
}

internal fun mediaSearchKey(value: String): String = value.lowercase(java.util.Locale.ROOT)
    .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
    .replace(Regex("[أإآٱ]"), "ا").replace('ى', 'ي')
    .map { when (it) { in '٠'..'٩' -> '0' + (it - '٠'); in '۰'..'۹' -> '0' + (it - '۰'); else -> it } }.joinToString("")

internal fun filteredMediaItems(state: MediaUiState): List<MediaUiItem> {
    val terms = mediaSearchKey(state.query).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return state.items.filter { item ->
        val scholar = state.scholars.find { it.id == item.scholarId }
        val searchable = mediaSearchKey(listOf(item.title, item.topic, item.series, item.searchAliases, item.englishTopics.joinToString(" "),
            state.series.find { it.id == item.seriesId }?.englishTitle.orEmpty(),
            scholar?.name.orEmpty(), scholar?.englishName.orEmpty(), scholar?.description.orEmpty(),
            item.bookIds.mapNotNull { state.bookTitles[it] }.joinToString(" ")).joinToString(" "))
        matchesMediaDuration(item.durationSeconds, state.durationFilter) &&
        (state.yearFilter == null || (if (state.yearFilter == 0) item.publishedHijriYear == null else item.publishedHijriYear == state.yearFilter)) &&
        (state.sectionFilter == null || bookLessonLinks(state.bookFilter, item.id).any { it.id == state.sectionFilter }) &&
        (state.seriesFilter == null || item.seriesId == state.seriesFilter) &&
        (state.topicFilter == null || state.topicFilter in mediaTopics(item)) &&
        (state.bookFilter == null || state.bookFilter in item.bookIds) &&
        (state.profileId == null || item.scholarId == state.profileId) &&
            (state.scholarFilter == null || item.scholarId == state.scholarFilter) &&
            (state.kindFilter == null || item.kind == state.kindFilter) &&
            (!state.favoritesOnly || item.id in state.favoriteIds) && terms.all { it in searchable }
    }.let { results -> if (state.seriesFilter == null) results else results.sortedBy { it.episodeNumber ?: Int.MAX_VALUE } }
}

@Composable
internal fun MediaScreen(state: MediaUiState, onHistory: () -> Unit = {}, onAction: (MediaAction) -> Unit) {
    val focus = LocalFocusManager.current
    val englishInterface = LocalConfiguration.current.locales[0].language == "en"
    val profile = state.scholars.find { it.id == state.profileId }
    val results = filteredMediaItems(state)
    BackHandler(enabled = state.profileId != null && state.selectedItemId == null) {
        onAction(MediaAction.Profile(null))
    }
    state.items.find { it.id == state.selectedItemId }?.let { item ->
        MediaSourceDialog(item, state, onAction)
    }
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(stringResource(R.string.media_title), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.media_intro), style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        if (englishInterface) item {
            MediaNotice(stringResource(R.string.media_original_language))
        }
        item { MediaNotice(stringResource(R.string.media_external_notice)) }
        item { MediaNotice(stringResource(R.string.media_initial_collection)) }
        item { MediaStudyControls(state, onAction) }
        item { MediaDateControls(state, onAction) }
        if (state.bookFilter != null) item { BookStudyControls(state, onAction) }
        if (profile != null) item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SoftGreen)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onAction(MediaAction.Profile(null)) }) {
                        Text(stringResource(R.string.media_back_directory))
                    }
                    Text(profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(profile.description)
                    Text(stringResource(R.string.media_profile_count,
                        state.items.count { it.scholarId == profile.id }), color = Muted)
                    Text(stringResource(R.string.media_source_value, profile.sourceName), style = MaterialTheme.typography.bodySmall)
                    SelectionContainer { Text(profile.sourceUrl, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { onAction(MediaAction.OpenSource(profile.sourceUrl)) }) {
                        Text(stringResource(R.string.media_open_profile_source))
                    }
                }
            }
        }
        item {
            OutlinedTextField(value = state.query, onValueChange = { onAction(MediaAction.Query(it)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.media_search)) },
                placeholder = { Text(stringResource(R.string.media_search_hint)) },
                trailingIcon = { if (state.query.isNotEmpty()) TextButton(onClick = { onAction(MediaAction.Query("")) }) {
                    Text(stringResource(R.string.media_clear))
                } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); onAction(MediaAction.SubmitSearch) }), shape = RoundedCornerShape(14.dp))
        }
        item { SearchHistoryActions(state.query, { onAction(MediaAction.SubmitSearch) }, onHistory) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.kindFilter == null, onClick = { onAction(MediaAction.KindFilter(null)); focus.clearFocus() },
                    label = { Text(stringResource(R.string.media_all_types)) })
                listOf("lesson", "sermon", "short").forEach { kind ->
                    FilterChip(selected = state.kindFilter == kind,
                        onClick = { onAction(MediaAction.KindFilter(kind)); focus.clearFocus() }, label = { Text(mediaKindLabel(kind)) })
                }
                FilterChip(selected = state.favoritesOnly, onClick = { onAction(MediaAction.FavoritesOnly(!state.favoritesOnly)) },
                    label = { Text(stringResource(R.string.media_favorites)) })
            }
        }
        if (profile == null && state.scholars.isNotEmpty()) item {
            Text(stringResource(R.string.media_scholar_filter), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.scholarFilter == null, onClick = { onAction(MediaAction.ScholarFilter(null)) },
                    label = { Text(stringResource(R.string.media_all_scholars)) })
                state.scholars.forEach { scholar ->
                    FilterChip(selected = state.scholarFilter == scholar.id,
                        onClick = { onAction(MediaAction.ScholarFilter(scholar.id)) }, label = { Text(scholar.name) })
                }
            }
        }
        if (profile == null && state.query.isBlank() && state.scholarFilter == null &&
            state.kindFilter == null && state.seriesFilter == null && state.topicFilter == null && state.yearFilter == null && state.durationFilter == null && !state.favoritesOnly && state.scholars.isNotEmpty()) {
            item { Text(stringResource(R.string.media_directory), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(state.scholars, key = { "scholar:${it.id}" }) { scholar ->
                OutlinedCard(onClick = { onAction(MediaAction.Profile(scholar.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(scholar.name, fontWeight = FontWeight.Bold)
                        Text(scholar.description, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.media_profile_count, state.items.count { it.scholarId == scholar.id }), color = Muted)
                        Text(stringResource(R.string.media_view_profile), color = Green)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.media_result_count, results.size), modifier = Modifier.weight(1f), color = Muted)
                if (state.query.isNotBlank() || state.scholarFilter != null || state.kindFilter != null || state.seriesFilter != null || state.topicFilter != null || state.sectionFilter != null || state.yearFilter != null || state.durationFilter != null || state.favoritesOnly) {
                    TextButton(onClick = {
                        onAction(MediaAction.Query("")); onAction(MediaAction.ScholarFilter(null))
                        onAction(MediaAction.KindFilter(null)); onAction(MediaAction.FavoritesOnly(false))
                        onAction(MediaAction.SeriesFilter(null)); onAction(MediaAction.TopicFilter(null)); onAction(MediaAction.SectionFilter(null)); focus.clearFocus()
                        onAction(MediaAction.YearFilter(null))
                        onAction(MediaAction.DurationFilter(null))
                    }) { Text(stringResource(R.string.media_reset)) }
                }
            }
        }
        if (results.isEmpty()) item {
            MediaNotice(stringResource(if (state.items.isEmpty()) R.string.media_catalog_empty else R.string.media_no_results))
        }
        items(results, key = { "item:${it.id}" }) { item ->
            MediaItemCard(item, state, onAction)
        }
    }
}

@Composable
private fun MediaItemCard(item: MediaUiItem, state: MediaUiState, onAction: (MediaAction) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(mediaKindLabel(item.kind) + " · " + stringResource(if (item.format == "audio") R.string.media_audio else R.string.media_video), style = MaterialTheme.typography.labelMedium, color = Green)
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            item.episodeNumber?.let { Text(stringResource(R.string.study_episode, it), color = Muted) }
            if (item.id in state.completedIds) Text(stringResource(R.string.book_study_completed), color = Green)
            item.seriesId?.let { id -> TextButton(onClick = { onAction(MediaAction.SeriesFilter(id)) }) {
                Text(stringResource(R.string.study_open_series))
            } }
            state.scholars.find { it.id == item.scholarId }?.let { scholar ->
                TextButton(onClick = { onAction(MediaAction.Profile(scholar.id)) }, contentPadding = PaddingValues(0.dp)) { Text(scholar.name) }
            }
            Text(item.topic, style = MaterialTheme.typography.bodyMedium)
            if (item.publishedDateLabel.isNotBlank()) Text(stringResource(R.string.batch_source_date, item.publishedDateLabel), style = MaterialTheme.typography.bodySmall)
            item.durationSeconds?.let { Text(stringResource(R.string.batch_seconds, it), style = MaterialTheme.typography.bodySmall) }
            if (item.series.isNotBlank()) Text(stringResource(R.string.media_series_value, item.series), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.media_source_value, item.sourceName), color = Muted, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onAction(MediaAction.SelectItem(item.id)) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.media_view_source))
                }
                TextButton(onClick = { onAction(MediaAction.ToggleFavorite(item.id)) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (item.id in state.favoriteIds) R.string.media_remove_favorite else R.string.media_add_favorite))
                }
            }
            item.bookIds.filter { it in state.bookTitles }.forEach { id ->
                TextButton(onClick = { onAction(MediaAction.OpenBook(id)) }) {
                    Text(stringResource(R.string.media_read_book_value, state.bookTitles.getValue(id)))
                }
            }
        }
    }
}

@Composable
private fun MediaSourceDialog(item: MediaUiItem, state: MediaUiState, onAction: (MediaAction) -> Unit) {
    AlertDialog(onDismissRequest = { onAction(MediaAction.SelectItem(null)) },
        title = { Text(item.title) }, text = {
            Column {
                MediaEpisodeNavigation(item, state, onAction)
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.scholars.find { it.id == item.scholarId }?.let { Text(it.name, fontWeight = FontWeight.Bold) }
                Text(stringResource(R.string.media_source_value, item.sourceName))
                if (item.sourceNote.isNotBlank()) Text(if (LocalConfiguration.current.locales[0].language == "en") item.englishSourceNote else item.sourceNote, style = MaterialTheme.typography.bodySmall)
                LessonStudyDetails(item, state, onAction)
                ReportCorrectionButton("${item.id}\n${item.title}\n${item.sourceUrl}")
                SelectionContainer { Text(item.sourceUrl, style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.media_external_notice), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAction(MediaAction.OpenSource(item.sourceUrl)) }) {
            Text(stringResource(R.string.media_open_external))
        } }, dismissButton = { TextButton(onClick = { onAction(MediaAction.SelectItem(null)) }) { Text(stringResource(R.string.media_close)) } })
}

@Composable
private fun mediaKindLabel(kind: String): String = stringResource(when (kind) {
    "sermon" -> R.string.media_sermons
    "short" -> R.string.media_shorts
    else -> R.string.media_lessons
})

@Composable
private fun MediaNotice(text: String) {
    Surface(color = SoftGreen, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}
