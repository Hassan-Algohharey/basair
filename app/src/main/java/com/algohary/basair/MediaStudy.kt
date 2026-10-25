package com.algohary.basair

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal data class MediaUiSeries(
    val id: String, val title: String, val englishTitle: String, val scholarId: String,
    val bookIds: List<String>, val sourceUrl: String, val coverage: String,
    val expectedEpisodes: Int?, val coverageNote: String, val englishCoverageNote: String
)

internal fun mediaTopics(item: MediaUiItem): List<String> = item.topics.ifEmpty { item.topic.split(" · ") }

internal fun orderedSeriesItems(items: List<MediaUiItem>, seriesId: String): List<MediaUiItem> =
    items.filter { it.seriesId == seriesId }.sortedBy { it.episodeNumber ?: Int.MAX_VALUE }

internal fun adjacentSeriesItem(items: List<MediaUiItem>, id: String, delta: Int): MediaUiItem? {
    if (delta !in setOf(-1, 1)) return null
    val item = items.find { it.id == id } ?: return null
    val ordered = orderedSeriesItems(items, item.seriesId ?: return null)
    return ordered.getOrNull(ordered.indexOfFirst { it.id == id } + delta)
}

// Opening a profile or an unreviewed URL never replaces the user's last lesson.
internal fun openedMediaId(previous: String?, url: String): String? =
    MediaCatalog.items.find { it.sourceUrl == url }?.id ?: previous

@Composable
internal fun MediaStudyControls(state: MediaUiState, onAction: (MediaAction) -> Unit) {
    var chooser by rememberSaveable { mutableStateOf<String?>(null) }
    val english = LocalConfiguration.current.locales[0].language == "en"
    val selectedSeries = state.series.find { it.id == state.seriesFilter }
    val topics = state.items.flatMap { item -> mediaTopics(item).mapIndexed { index, key ->
        key to if (english) item.englishTopics.getOrNull(index).orEmpty().ifBlank { key } else key
    } }.distinctBy { it.first }.sortedBy { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.items.find { it.id == state.lastOpenedId }?.let { last ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(R.string.study_last_opened), style = MaterialTheme.typography.labelLarge)
                    Text(last.title)
                    Row {
                        TextButton(onClick = { onAction(MediaAction.ResumeLast) }) { Text(stringResource(R.string.study_resume)) }
                        TextButton(onClick = { onAction(MediaAction.ClearLast) }) { Text(stringResource(R.string.study_clear_last)) }
                    }
                    Text(stringResource(R.string.study_external_progress), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { chooser = "series" }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.study_choose_series))
            }
            OutlinedButton(onClick = { chooser = "topic" }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.study_choose_topic))
            }
        }
        state.topicFilter?.let { key ->
            TextButton(onClick = { onAction(MediaAction.TopicFilter(null)) }) {
                Text(stringResource(R.string.study_clear_topic, topics.find { it.first == key }?.second ?: key))
            }
        }
        selectedSeries?.let { series ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (english) series.englishTitle else series.title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (series.coverage == "complete") R.string.study_complete else R.string.study_partial))
                    Text(stringResource(R.string.study_indexed_episodes, orderedSeriesItems(state.items, series.id).size))
                    Text(if (english) series.englishCoverageNote else series.coverageNote, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.book_study_progress,
                        orderedSeriesItems(state.items, series.id).count { it.id in state.completedIds },
                        orderedSeriesItems(state.items, series.id).size))
                    state.seriesPositions[series.id]?.let { id -> state.items.find { it.id == id && it.seriesId == series.id }?.let { lesson ->
                        Text(lesson.title)
                        TextButton(onClick = { onAction(MediaAction.SelectItem(id)) }) { Text(stringResource(R.string.book_study_resume_series)) }
                        TextButton(onClick = { onAction(MediaAction.ClearSeriesPosition(series.id)) }) { Text(stringResource(R.string.book_study_clear_resume)) }
                    } }
                    TextButton(onClick = { onAction(MediaAction.OpenSource(series.sourceUrl)) }) { Text(stringResource(R.string.study_series_source)) }
                    TextButton(onClick = { onAction(MediaAction.SeriesFilter(null)) }) { Text(stringResource(R.string.study_clear_series)) }
                }
            }
        }
    }
    chooser?.let { kind ->
        AlertDialog(onDismissRequest = { chooser = null }, title = {
            Text(stringResource(if (kind == "series") R.string.study_choose_series else R.string.study_choose_topic))
        }, text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick = {
                    if (kind == "series") onAction(MediaAction.SeriesFilter(null)) else onAction(MediaAction.TopicFilter(null))
                    chooser = null
                }) { Text(stringResource(R.string.media_all_types)) }
                if (kind == "series") state.series.filter { series -> state.bookFilter == null || state.bookFilter in series.bookIds }.forEach { series ->
                    TextButton(onClick = { onAction(MediaAction.SeriesFilter(series.id)); chooser = null }) {
                        Column {
                            Text(if (english) series.englishTitle else series.title)
                            state.scholars.find { it.id == series.scholarId }?.let { Text(it.name, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                } else topics.forEach { (key, label) ->
                    TextButton(onClick = { onAction(MediaAction.TopicFilter(key)); chooser = null }) { Text(label) }
                }
            }
        }, confirmButton = { TextButton(onClick = { chooser = null }) { Text(stringResource(R.string.media_close)) } })
    }
}

@Composable
internal fun MediaEpisodeNavigation(item: MediaUiItem, state: MediaUiState, onAction: (MediaAction) -> Unit) {
    if (item.seriesId == null) return
    Column {
        Text(stringResource(R.string.study_indexed_navigation), style = MaterialTheme.typography.bodySmall, color = Muted)
        Row(Modifier.fillMaxWidth()) {
            listOf(-1, 1).forEach { delta ->
                val adjacent = adjacentSeriesItem(state.items, item.id, delta)
                TextButton(onClick = { adjacent?.let { onAction(MediaAction.SelectItem(it.id)) } },
                    enabled = adjacent != null, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (delta < 0) R.string.study_previous else R.string.study_next))
                }
            }
        }
    }
}
