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

internal data class BookTextEntry(val bookId: String, val sha256: String, val number: Int,
    val pdfPage: Int, val printedPage: String, val text: String)

internal fun availableBookText(bookId: String? = null): List<BookTextEntry> = reviewedTextEntries.filter { e ->
    (bookId == null || e.bookId == bookId) && Catalog.books.any { it.id == e.bookId && it.sha256 == e.sha256 && e.pdfPage in 1..it.pages }
}

internal fun searchBookText(query: String, bookId: String? = null): List<BookTextEntry> {
    val terms = mediaSearchKey(query.take(200)).trim().split(Regex("\\s+")).filter(String::isNotBlank)
    if (terms.isEmpty()) return emptyList()
    return availableBookText(bookId).filter { e ->
        val key = mediaSearchKey("${e.number} ${e.text}")
        terms.all { term -> if (term.all(Char::isDigit)) term == e.number.toString() else term in key }
    }
}

@Composable
internal fun TextSearchEntry(onOpen: () -> Unit) {
    OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.batch_text_search)) }
}

@Composable
internal fun BookTextSearchDialog(bookId: String? = null, onPage: (String, Int) -> Unit, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, bookId) { searchBookText(query, bookId) }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.batch_text_search)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.batch_text_scope), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, { if (it.length <= 200) query = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.batch_text_query)) })
            Text(stringResource(R.string.batch_text_count, results.size))
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (query.isBlank()) Text(stringResource(R.string.batch_text_hint))
                else if (results.isEmpty()) Text(stringResource(R.string.media_no_results))
                results.take(40).forEach { result ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(stringResource(R.string.batch_verse, result.number))
                            Text(result.text)
                            TextButton(onClick = { onPage(result.bookId, result.pdfPage); onClose() }) {
                                Text(stringResource(R.string.book_study_read_page, result.printedPage, result.pdfPage))
                            }
                        }
                    }
                }
                if (results.size > 40) Text(stringResource(R.string.batch_text_limit))
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.media_close)) } })
}
