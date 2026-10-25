package com.algohary.basair

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal fun mediaDurationLabel(seconds: Int): String {
    val value = seconds.coerceAtLeast(0)
    return if (value >= 3600) java.lang.String.format(java.util.Locale.ROOT, "%d:%02d:%02d", value / 3600, value / 60 % 60, value % 60)
    else java.lang.String.format(java.util.Locale.ROOT, "%d:%02d", value / 60, value % 60)
}

internal fun matchesMediaDuration(seconds: Int?, filter: String?): Boolean {
    val known = seconds?.takeIf { it > 0 }
    return when (filter) {
        null -> true
        "unknown" -> known == null
        "short" -> known != null && known <= 180
        "medium" -> known != null && known in 181..1800
        "long" -> known != null && known > 1800
        else -> false
    }
}

@Composable
private fun durationLabel(key: String): String = stringResource(when (key) {
    "short" -> R.string.batch_duration_short
    "medium" -> R.string.batch_duration_medium
    "long" -> R.string.batch_duration_long
    else -> R.string.batch_duration_unknown
})

@Composable
internal fun MediaDateControls(state: MediaUiState, onAction: (MediaAction) -> Unit) {
    var show by rememberSaveable { mutableStateOf(false) }
    var durationChooser by rememberSaveable { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { show = true }) { Text(stringResource(R.string.batch_year_filter)) }
        OutlinedButton(onClick = { durationChooser = true }) { Text(stringResource(R.string.batch_duration_filter)) }
        state.durationFilter?.let { key ->
            Text(durationLabel(key))
            TextButton(onClick = { onAction(MediaAction.DurationFilter(null)) }) { Text(stringResource(R.string.batch_clear_duration)) }
        }
        state.yearFilter?.let { year ->
            Text(if (year == 0) stringResource(R.string.batch_unknown_date) else stringResource(R.string.batch_hijri_year, year))
            TextButton(onClick = { onAction(MediaAction.YearFilter(null)) }) { Text(stringResource(R.string.batch_clear_year)) }
        }
    }
    if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text(stringResource(R.string.batch_year_filter)) }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.batch_date_scope), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onAction(MediaAction.YearFilter(null)); show = false }) { Text(stringResource(R.string.media_all_types)) }
            state.items.mapNotNull { it.publishedHijriYear }.distinct().sortedDescending().forEach { year ->
                TextButton(onClick = { onAction(MediaAction.YearFilter(year)); show = false }) { Text(stringResource(R.string.batch_hijri_year, year)) }
            }
            TextButton(onClick = { onAction(MediaAction.YearFilter(0)); show = false }) { Text(stringResource(R.string.batch_unknown_date)) }
        }
    }, confirmButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.media_close)) } })
    if (durationChooser) AlertDialog(onDismissRequest = { durationChooser = false }, title = { Text(stringResource(R.string.batch_duration_filter)) }, text = {
        Column {
            Text(stringResource(R.string.batch_duration_scope), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onAction(MediaAction.DurationFilter(null)); durationChooser = false }) { Text(stringResource(R.string.media_all_types)) }
            listOf("short", "medium", "long", "unknown").forEach { key ->
                TextButton(onClick = { onAction(MediaAction.DurationFilter(key)); durationChooser = false }) { Text(durationLabel(key)) }
            }
        }
    }, confirmButton = { TextButton(onClick = { durationChooser = false }) { Text(stringResource(R.string.media_close)) } })
}
