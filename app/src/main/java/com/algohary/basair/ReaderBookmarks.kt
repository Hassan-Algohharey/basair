package com.algohary.basair

// Ignore stale or invalid page numbers when loading locally stored bookmarks.
internal fun parseBookmarkedPages(values: Set<String>, pageCount: Int): List<Int> =
    values.mapNotNull { parsePageNumber(it, pageCount) }.distinct().sorted()
