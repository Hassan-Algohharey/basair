package com.algohary.basair

internal data class AudioBookPage(val bookId: String, val bookSha256: String, val pdfPage: Int, val printedPage: String)
internal fun validAudioBookPage(page: AudioBookPage): Boolean = Catalog.books.any {
    it.id == page.bookId && it.sha256 == page.bookSha256 && it.chapters.any { c -> c.pdfPage == page.pdfPage && c.printedPage == page.printedPage }
}
internal data class AudioTrack(
    val id: String, val title: String, val englishTitle: String, val url: String,
    val sequence: Int, val durationMs: Long? = null, val byteSize: Long = 0,
    val mimeType: String = "audio/mpeg", val sha256: String? = null,
    val clipStartMs: Long = 0, val clipEndMs: Long? = null, val bookPage: AudioBookPage? = null
)

internal data class Audiobook(
    val id: String, val title: String, val englishTitle: String,
    val author: String, val reader: String, val englishReader: String,
    val bookIds: List<String>, val coverage: String, val coverageNote: String,
    val englishCoverageNote: String, val sourceUrl: String, val licenseName: String,
    val licenseUrl: String, val rightsNote: String, val englishRightsNote: String,
    val tracks: List<AudioTrack>, val downloadAllowed: Boolean = false, val kind: String = "reading"
)

internal val allAudioRecordings: List<Audiobook> get() = AudiobookCatalog.recordings + TajweedAudioCatalog.recordings + TajweedExamples.recordings + TajweedExamples.sourceRecordings

internal fun audioTrack(id: String): AudioTrack? = allAudioRecordings.flatMap { it.tracks }.find { it.id == id }
internal fun audioRecording(trackId: String): Audiobook? = allAudioRecordings.find { recording -> recording.tracks.any { it.id == trackId } }
internal fun audioSourceSearchText(recording: Audiobook): String =
    TajweedExamples.sourceRecordings.filter { source -> source.tracks.any { original -> recording.tracks.any { it.url == original.url } } }
        .joinToString(" ") { "${it.title} ${it.englishTitle}" }
internal fun audioSeekPosition(position: Long, duration: Long?): Long =
    position.coerceAtLeast(0).let { if (duration != null && duration > 0) it.coerceAtMost(duration) else it }

internal fun audioPlaybackSpeed(speed: Float): Float = if (speed.isFinite()) speed.coerceIn(0.5f, 2f) else 1f

internal fun audioResumePosition(position: Long, duration: Long?): Long =
    if (duration != null && duration > 0 && position >= duration - 1_000) 0 else audioSeekPosition(position, duration)

internal fun searchAudiobooks(query: String, bookId: String? = null): List<Audiobook> {
    val terms = mediaSearchKey(query).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return allAudioRecordings.filterNot { r -> TajweedExamples.sourceRecordings.any { it.id == r.id } }.filter { recording ->
        val aliases = when (recording.kind) {
            "reading" -> "كتب مسموعة قراءة متن تسجيل صوتي audiobook audiobooks audio reading IslamHouse دار الإسلام"
            "recitation" -> "تلاوة حفص تجويد مثال سورة قرآن مجمع الملك فهد recitation Hafs example Quran tajweed audio"
            else -> "درس تطبيق تجويد صوتي lesson practice tajweed tajwid audio IslamHouse دار الإسلام"
        }
        val value = mediaSearchKey(listOf(aliases, recording.title, recording.englishTitle, recording.author, audioSourceSearchText(recording),
            recording.reader, recording.englishReader, recording.englishReader.filter { it.isLetterOrDigit() }, recording.tracks.joinToString(" ") { "${it.title} ${it.englishTitle}" },
            recording.bookIds.mapNotNull { id -> Catalog.books.find { it.id == id }?.title }.joinToString(" ")).joinToString(" "))
        (bookId == null || bookId in recording.bookIds) && terms.all { it in value }
    }
}
