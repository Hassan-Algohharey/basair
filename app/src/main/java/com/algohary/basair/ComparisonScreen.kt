package com.algohary.basair

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComparisonScreen(selectedId: String, onSelect: (String) -> Unit, saved: Set<String>,
    onSave: (String) -> Unit, onReference: (Reference) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var school by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var readingReference by remember { mutableStateOf<Reference?>(null) }
    val results = remember(query, topic, school) { Catalog.searchComparisons(query, topic = topic, school = school) }
    val comparison = selectComparison(results, selectedId)
    LaunchedEffect(comparison?.id) { comparison?.let { if (it.id != selectedId) onSelect(it.id) } }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    readingReference?.let { reference ->
        key(reference.volume, reference.pdfPage) {
            ReferenceDownloadDialog(reference, { readingReference = null }, { readingReference = null; onReference(it) })
        }
    }
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { if (LocalConfiguration.current.locales[0].language == "en") Notice(context.getString(R.string.arabic_content_notice)) }
        item { SectionTitle(context.getString(R.string.ui_fiqh_comparison), context.getString(R.string.ui_1_issues_four_schools_page_references, Catalog.comparisons.size)) }
        item {
            OutlinedTextField(value = query, onValueChange = { query = it; expanded = false },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(context.getString(R.string.ui_search_comparisons)) }, placeholder = { Text(context.getString(R.string.ui_issue_opinion_volume_or_page)) },
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(context.getString(R.string.ui_clear)) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }), shape = RoundedCornerShape(14.dp))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(context.getString(R.string.ui_topic), color = Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = topic == null, onClick = { topic = null; focus.clearFocus() }, label = { Text(context.getString(R.string.ui_all_topics)) })
                    Catalog.comparisonTopics.forEach { name ->
                        FilterChip(selected = topic == name, onClick = { topic = name; focus.clearFocus() }, label = { Text(localizedLabel(context, name)) })
                    }
                }
                Text(context.getString(R.string.ui_school_search_covers_displayed_opinions), color = Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = school == null, onClick = { school = null; focus.clearFocus() }, label = { Text(context.getString(R.string.ui_all_four)) })
                    Catalog.comparisonSchools.forEach { name ->
                        FilterChip(selected = school == name, onClick = { school = name; focus.clearFocus() }, label = { Text(localizedLabel(context, name)) })
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(context.getString(R.string.ui_results_1_of_2_issues, results.size, Catalog.comparisons.size), Modifier.weight(1f), color = Muted, fontSize = 13.sp)
                if (query.isNotBlank() || topic != null || school != null) TextButton(onClick = {
                    query = ""; topic = null; school = null; expanded = false; focus.clearFocus()
                }) { Text(context.getString(R.string.ui_reset)) }
            }
        }
        if (comparison == null) item {
            Notice(context.getString(R.string.ui_no_matching_issues_try_a_shorter_query_change_the_topic_or_school_or_res))
        }
        if (comparison != null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onSave(comparison.id) }) {
                        Text(if (comparison.id in saved) context.getString(R.string.ui_remove_saved_issue) else context.getString(R.string.ui_save_issue))
                    }
                    TextButton(onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, comparison.title)
                            putExtra(android.content.Intent.EXTRA_TEXT, comparison.shareText(school))
                        }
                        try { context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.ui_share_comparison))) }
                        catch (_: android.content.ActivityNotFoundException) {
                            android.widget.Toast.makeText(context, context.getString(R.string.ui_no_app_is_available_for_sharing), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(context.getString(R.string.ui_share_comparison)) }
                }
            }
            item {
                Box {
                    OutlinedButton(onClick = { expanded = true; focus.clearFocus() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(comparison.title, Modifier.weight(1f), fontSize = 15.sp)
                        Text("⌄")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                        results.forEach { entry ->
                            DropdownMenuItem(text = { Text(entry.title) }, onClick = { onSelect(entry.id); expanded = false })
                        }
                    }
                }
            }
            item {
                Surface(color = Green, shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(comparison.topic, fontSize = 12.sp, color = Color(0xFFE8D5A7))
                        Text(comparison.title, fontSize = 25.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(comparison.scope, fontSize = 13.sp, lineHeight = 23.sp, color = Color(0xFFD6E6DB))
                    }
                }
            }
            item {
                val index = results.indexOf(comparison)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = index > 0, onClick = { onSelect(results[index - 1].id) }) { Text(context.getString(R.string.ui_previous_issue)) }
                    Text(context.getString(R.string.ui_1_of_2, index + 1, results.size), color = Muted, fontSize = 12.sp)
                    TextButton(enabled = index < results.lastIndex, onClick = { onSelect(results[index + 1].id) }) { Text(context.getString(R.string.ui_next_issue)) }
                }
            }
            items(comparison.opinions.filter { school == null || it.school == school }, key = { comparison.id + it.school }) { opinion ->
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(4.dp).height(24.dp).background(Gold, CircleShape))
                            Spacer(Modifier.width(9.dp)); Text(context.getString(R.string.ui_school_1, localizedLabel(context, opinion.school)), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Text(opinion.summary, fontSize = 16.sp, lineHeight = 28.sp)
                        HorizontalDivider(color = Line)
                        opinion.references.forEach { reference ->
                            Text(reference.label, fontSize = 13.sp, color = Green, lineHeight = 23.sp)
                            Text("${reference.publisher}\n${reference.edition}", fontSize = 11.sp, lineHeight = 20.sp, color = Muted)
                            TextButton(onClick = { readingReference = reference }) { Text(context.getString(R.string.ui_read_reference_in_app_page_1, reference.pdfPage), fontSize = 12.sp) }
                            TextButton(onClick = { openSource(context, reference.source) }) { Text(context.getString(R.string.ui_open_scanned_source_page_1, reference.pdfPage), fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        item { Notice(context.getString(R.string.ui_these_editorial_summaries_cite_pages_in_the_specified_scanned_editions_t)) }
    }
}
