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

internal data class BookLessonLink(
    val id: String, val itemId: String, val bookId: String, val title: String,
    val englishTitle: String, val pdfPage: Int, val printedPage: String,
    val bookSha256: String, val sourceUrl: String
)

// A link belongs to one reviewed edition; changing the PDF invalidates its page mapping.
internal fun bookLessonLinks(bookId: String? = null, itemId: String? = null): List<BookLessonLink> =
    reviewedBookLessons.filter { link ->
        (bookId == null || link.bookId == bookId) && (itemId == null || link.itemId == itemId) &&
            Catalog.books.any { it.id == link.bookId && it.sha256 == link.bookSha256 && link.pdfPage in 1..it.pages } &&
            MediaCatalog.items.any { it.id == link.itemId && link.bookId in it.bookIds && it.sourceUrl == link.sourceUrl }
    }

internal fun seriesResumeId(seriesId: String, storedId: String?): String? =
    storedId?.takeIf { id -> MediaCatalog.items.any { it.id == id && it.seriesId == seriesId } }

internal fun toggleLessonCompleted(stored: Set<String>, itemId: String): Set<String> =
    if (MediaCatalog.items.none { it.id == itemId && it.seriesId != null }) stored
    else if (itemId in stored) stored - itemId else stored + itemId

@Composable
internal fun BookStudyControls(state: MediaUiState, onAction: (MediaAction) -> Unit) {
    val bookId = state.bookFilter ?: return
    val english = LocalConfiguration.current.locales[0].language == "en"
    val links = bookLessonLinks(bookId).filter { link ->
        state.seriesFilter == null || state.items.any { it.id == link.itemId && it.seriesId == state.seriesFilter }
    }
    var chooser by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.bookTitles[bookId].orEmpty(), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.book_study_scope), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.book_study_link_count, links.size))
            if (links.isEmpty()) Text(stringResource(R.string.book_study_unmapped), style = MaterialTheme.typography.bodySmall)
            else OutlinedButton(onClick = { chooser = true }) { Text(stringResource(R.string.book_study_choose)) }
            links.find { it.id == state.sectionFilter }?.let { link ->
                Text(if (english) link.englishTitle else link.title)
                TextButton(onClick = { onAction(MediaAction.OpenBookPage(link.id)) }) {
                    Text(stringResource(R.string.book_study_read_page, link.printedPage, link.pdfPage))
                }
                TextButton(onClick = { onAction(MediaAction.SectionFilter(null)) }) { Text(stringResource(R.string.book_study_all)) }
            }
        }
    }
    if (chooser) AlertDialog(onDismissRequest = { chooser = false }, title = { Text(stringResource(R.string.book_study_choose)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                links.forEach { link ->
                    TextButton(onClick = { onAction(MediaAction.SectionFilter(link.id)); chooser = false }) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(if (english) link.englishTitle else link.title)
                            Text(stringResource(R.string.book_study_page, link.printedPage, link.pdfPage), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { chooser = false }) { Text(stringResource(R.string.media_close)) } })
}

@Composable
internal fun LessonStudyDetails(item: MediaUiItem, state: MediaUiState, onAction: (MediaAction) -> Unit) {
    val english = LocalConfiguration.current.locales[0].language == "en"
    bookLessonLinks(itemId = item.id).forEach { link ->
        HorizontalDivider()
        Text(stringResource(R.string.book_study_matched), style = MaterialTheme.typography.labelLarge)
        Text(if (english) link.englishTitle else link.title)
        Text(state.bookTitles[link.bookId].orEmpty())
        TextButton(onClick = { onAction(MediaAction.OpenBookPage(link.id)) }) {
            Text(stringResource(R.string.book_study_read_page, link.printedPage, link.pdfPage))
        }
        Text(stringResource(R.string.book_study_scope), style = MaterialTheme.typography.bodySmall)
    }
    if (item.seriesId != null) {
        HorizontalDivider()
        TextButton(onClick = { onAction(MediaAction.SetSeriesPosition(item.id)) }) { Text(stringResource(R.string.book_study_set_resume)) }
        if (state.seriesPositions[item.seriesId] == item.id) Text(stringResource(R.string.book_study_resume_saved))
        TextButton(onClick = { onAction(MediaAction.ToggleCompleted(item.id)) }) {
            Text(stringResource(if (item.id in state.completedIds) R.string.book_study_uncomplete else R.string.book_study_complete))
        }
        Text(stringResource(R.string.book_study_manual_progress), style = MaterialTheme.typography.bodySmall)
    }
}
