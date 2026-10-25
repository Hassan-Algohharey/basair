package com.algohary.basair

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal const val SEARCH_HISTORY_LIMIT = 50
internal const val SEARCH_QUERY_LIMIT = 200
internal enum class SearchScope { BOOKS, MEDIA }
internal data class SearchHistoryEntry(val scope: SearchScope, val query: String) {
    val key: String get() = scope.name + ":" + mediaSearchKey(query)
}

internal fun cleanHistoryQuery(query: String): String? = query.map { if (it.isISOControl()) ' ' else it }
    .joinToString("").trim().replace(Regex("\\s+"), " ")
    .takeIf { it.isNotEmpty() && it.length <= SEARCH_QUERY_LIMIT }

internal fun sanitizeSearchHistory(entries: List<SearchHistoryEntry>): List<SearchHistoryEntry> = entries
    .mapNotNull { entry -> cleanHistoryQuery(entry.query)?.let { entry.copy(query = it) } }
    .distinctBy { it.key }.take(SEARCH_HISTORY_LIMIT)

internal fun recordSearchHistory(entries: List<SearchHistoryEntry>, scope: SearchScope, query: String,
    enabled: Boolean): List<SearchHistoryEntry> {
    val clean = cleanHistoryQuery(query)
    if (!enabled || clean == null) return entries
    return sanitizeSearchHistory(listOf(SearchHistoryEntry(scope, clean)) + entries)
}

internal fun decodeSearchHistory(values: Map<String, *>): List<SearchHistoryEntry> = sanitizeSearchHistory(
    (0 until SEARCH_HISTORY_LIMIT).mapNotNull { index ->
        val scope = SearchScope.entries.find { it.name == values["scope:$index"] } ?: return@mapNotNull null
        val query = values["query:$index"] as? String ?: return@mapNotNull null
        SearchHistoryEntry(scope, query)
    })

/** Dedicated device-private preferences. No timers, automatic input capture or network operations. */
internal class SearchHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("basair-search-history", Context.MODE_PRIVATE)
    fun enabled(): Boolean = preferences.getBoolean("enabled", true)
    fun list(): List<SearchHistoryEntry> = decodeSearchHistory(preferences.all)
    fun setEnabled(enabled: Boolean) { preferences.edit().putBoolean("enabled", enabled).apply() }
    fun record(scope: SearchScope, query: String) {
        if (!enabled() || cleanHistoryQuery(query) == null) return
        write(recordSearchHistory(list(), scope, query, true))
    }
    fun delete(entry: SearchHistoryEntry) = write(list().filterNot { it.key == entry.key })
    fun clear() = write(emptyList())
    private fun write(entries: List<SearchHistoryEntry>) {
        val editor = preferences.edit().clear().putBoolean("enabled", enabled())
        sanitizeSearchHistory(entries).forEachIndexed { index, entry ->
            editor.putString("scope:$index", entry.scope.name).putString("query:$index", entry.query)
        }
        editor.apply()
    }
    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) = preferences.registerOnSharedPreferenceChangeListener(listener)
    fun unobserve(listener: SharedPreferences.OnSharedPreferenceChangeListener) = preferences.unregisterOnSharedPreferenceChangeListener(listener)
}

@Composable
internal fun SearchHistoryActions(query: String, onSubmit: () -> Unit, onHistory: () -> Unit) {
    val focus = LocalFocusManager.current
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { focus.clearFocus(); onSubmit() }, enabled = query.isNotBlank()) { Text(stringResource(R.string.history_search)) }
            TextButton(onClick = { focus.clearFocus(); onHistory() }) { Text(stringResource(R.string.history_title)) }
        }
        Text(stringResource(R.string.history_capture_notice), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
internal fun SearchHistoryScreen(onBack: () -> Unit, onSearch: (SearchHistoryEntry) -> Unit) {
    val context = LocalContext.current
    val store = remember { SearchHistoryStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        store.observe(listener)
        onDispose { store.unobserve(listener) }
    }
    val enabled = remember(revision) { store.enabled() }
    val entries = remember(revision) { store.list() }
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.ui_back)) }
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.history_private_notice, SEARCH_HISTORY_LIMIT, SEARCH_QUERY_LIMIT))
        }
        item {
            Row {
                Switch(checked = enabled, onCheckedChange = store::setEnabled,
                    modifier = Modifier.semantics { contentDescription = context.getString(R.string.history_enabled) })
                Text(stringResource(R.string.history_enabled), Modifier.padding(start = 8.dp, top = 12.dp))
            }
            Text(stringResource(if (enabled) R.string.history_on else R.string.history_off), color = Muted)
            Text(stringResource(R.string.history_disable_notice), style = MaterialTheme.typography.bodySmall)
        }
        item {
            OutlinedButton(onClick = store::clear, enabled = entries.isNotEmpty()) { Text(stringResource(R.string.history_clear)) }
            Text(stringResource(R.string.history_count, entries.size))
            if (entries.isEmpty()) Text(stringResource(R.string.history_empty))
        }
        items(entries, key = { it.key }) { entry ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(if (entry.scope == SearchScope.BOOKS) R.string.history_books else R.string.history_media), color = Muted)
                    TextButton(onClick = { onSearch(entry) }) { Text(entry.query) }
                    TextButton(onClick = { store.delete(entry) }) { Text(stringResource(R.string.history_delete, entry.query)) }
                }
            }
        }
    }
}
