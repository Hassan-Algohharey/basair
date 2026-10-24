package com.algohary.basair

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal fun toggleMediaFavorite(stored: Set<String>, id: String): Set<String> =
    if (MediaCatalog.items.none { it.id == id }) stored else if (id in stored) stored - id else stored + id

internal fun allowedMediaUrl(url: String): Boolean = url in MediaCatalog.allowedUrls

@Composable
internal fun MediaEntry(onOpen: () -> Unit, count: Int? = null) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SoftGreen)) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.media_title), style = MaterialTheme.typography.titleMedium)
            Text(if (count == null) stringResource(R.string.media_intro) else stringResource(R.string.media_result_count, count))
        }
    }
}

@Composable
internal fun MediaFeature(initialQuery: String, initialBook: String?, initialFavorites: Boolean, onBook: (String) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("basair-media", Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(preferences.getStringSet("favoriteIds", emptySet()).orEmpty().toSet()) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var bookFilter by rememberSaveable { mutableStateOf(initialBook) }
    var profile by rememberSaveable { mutableStateOf<String?>(null) }
    var scholar by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyFavorites by rememberSaveable { mutableStateOf(initialFavorites) }
    var series by rememberSaveable { mutableStateOf<String?>(null) }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var lastOpened by remember { mutableStateOf(preferences.getString("lastOpenedId", null)?.takeIf { id -> MediaCatalog.items.any { it.id == id } }) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val state = MediaUiState(MediaCatalog.scholars, MediaCatalog.items, favorites, query, scholar,
        kind, onlyFavorites, profile, selected, Catalog.books.associate { it.id to it.title }, bookFilter, MediaCatalog.series, series, topic, lastOpened)
    Column {
        if (bookFilter != null) TextButton(onClick = { bookFilter = null }) {
            Text(stringResource(R.string.media_clear_book_filter, state.bookTitles[bookFilter].orEmpty()))
        }
        Box(Modifier.weight(1f)) { MediaScreen(state) { action ->
            when (action) {
                is MediaAction.Query -> query = action.value
                is MediaAction.ScholarFilter -> scholar = action.value
                is MediaAction.KindFilter -> kind = action.value
                is MediaAction.FavoritesOnly -> onlyFavorites = action.value
                is MediaAction.Profile -> { profile = action.id; scholar = null; query = ""; kind = null; onlyFavorites = false; series = null; topic = null }
                is MediaAction.SelectItem -> selected = action.id
                is MediaAction.ToggleFavorite -> {
                    favorites = toggleMediaFavorite(favorites, action.id)
                    preferences.edit().putStringSet("favoriteIds", favorites).apply()
                }
                is MediaAction.OpenSource -> if (allowedMediaUrl(action.url)) {
                    lastOpened = openedMediaId(lastOpened, action.url)
                    lastOpened?.let { preferences.edit().putString("lastOpenedId", it).apply() }
                    openSource(context, action.url)
                }
                is MediaAction.SeriesFilter -> {
                    series = action.value
                    if (action.value != null) { profile = null; scholar = null; kind = "lesson"; topic = null; query = ""; onlyFavorites = false }
                }
                is MediaAction.TopicFilter -> topic = action.value
                MediaAction.ResumeLast -> lastOpened?.let { id ->
                    selected = id
                    query = ""; scholar = null; profile = null; kind = null; onlyFavorites = false; topic = null; bookFilter = null
                    series = MediaCatalog.items.find { it.id == id }?.seriesId
                }
                MediaAction.ClearLast -> { lastOpened = null; preferences.edit().remove("lastOpenedId").apply() }
                is MediaAction.OpenBook -> if (Catalog.books.any { it.id == action.id }) onBook(action.id)
            }
        } }
    }
}
