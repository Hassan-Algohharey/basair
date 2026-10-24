package com.algohary.basair

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun LanguageSettings() {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(context.getString(R.string.language), fontSize = 14.sp)
            Row {
                listOf("ar" to "العربية", "en" to "English").forEach { (tag, label) ->
                    FilterChip(selected = language == tag, onClick = {
                        if (tag != language) {
                            context.getSharedPreferences("basair", Context.MODE_PRIVATE).edit().putString("language", tag).apply()
                            generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                                .filterIsInstance<Activity>().firstOrNull()?.recreate()
                        }
                    }, label = { Text(label) })
                }
            }
        }
        if (language == "en") Notice(context.getString(R.string.arabic_content_notice))
    }
}

@Composable
internal fun SchoolSelector(selected: String?, onSelect: (String?) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(context.getString(R.string.four_schools))
        fiqhSchools.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { id ->
                    val count = Catalog.search("", "fiqh", id).size
                    FilterChip(selected = selected == id, onClick = { onSelect(id) }, modifier = Modifier.weight(1f),
                        label = { Text(localizedLabel(context, schoolNames.getValue(id)) + " ($count)") })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(context.getString(R.string.all_fiqh)) })
            FilterChip(selected = selected == "shared", onClick = { onSelect("shared") }, label = { Text(context.getString(R.string.shared_references)) })
        }
        Notice(context.getString(R.string.school_catalog_notice))
    }
}

@Composable
internal fun SchoolEmptyState(onShared: () -> Unit) {
    val context = LocalContext.current
    EmptyState(context.getString(R.string.school_empty), context.getString(R.string.school_empty_detail),
        context.getString(R.string.shared_references), onShared)
}

@Composable
internal fun SharedReferences(query: String, topic: String?, onReference: (Reference) -> Unit) {
    val context = LocalContext.current
    var reference by remember { mutableStateOf<Reference?>(null) }
    val relevant = remember(query, topic) { matchingReferenceVolumes(query, topic) }
    reference?.let { value -> ReferenceDownloadDialog(value, { reference = null }, { reference = null; onReference(it) }) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(context.getString(R.string.shared_references), context.getString(R.string.reference_volumes_notice, relevant.size))
        relevant.forEach { volume ->
            OutlinedButton(onClick = { reference = volume.reference }, modifier = Modifier.fillMaxWidth()) {
                Text(context.getString(R.string.reference_volume, volume.volume, volume.pages))
            }
        }
    }
}
