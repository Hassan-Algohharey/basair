package com.algohary.basair

internal data class AudioTrack(
    val id: String, val title: String, val englishTitle: String, val url: String,
    val sequence: Int, val durationMs: Long? = null, val byteSize: Long = 0,
    val mimeType: String = "audio/mpeg"
)

internal data class Audiobook(
    val id: String, val title: String, val englishTitle: String,
    val author: String, val reader: String, val englishReader: String,
    val bookIds: List<String>, val coverage: String, val coverageNote: String,
    val englishCoverageNote: String, val sourceUrl: String, val licenseName: String,
    val licenseUrl: String, val rightsNote: String, val englishRightsNote: String,
    val tracks: List<AudioTrack>, val downloadAllowed: Boolean = false
)

internal fun audioTrack(id: String): AudioTrack? = AudiobookCatalog.recordings.flatMap { it.tracks }.find { it.id == id }
internal fun audioRecording(trackId: String): Audiobook? = AudiobookCatalog.recordings.find { recording -> recording.tracks.any { it.id == trackId } }
internal fun audioSeekPosition(position: Long, duration: Long?): Long =
    position.coerceAtLeast(0).let { if (duration != null && duration > 0) it.coerceAtMost(duration) else it }

internal fun audioPlaybackSpeed(speed: Float): Float = if (speed.isFinite()) speed.coerceIn(0.5f, 2f) else 1f

internal fun audioResumePosition(position: Long, duration: Long?): Long =
    if (duration != null && duration > 0 && position >= duration - 1_000) 0 else audioSeekPosition(position, duration)

internal fun searchAudiobooks(query: String, bookId: String? = null): List<Audiobook> {
    val terms = mediaSearchKey(query).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return AudiobookCatalog.recordings.filter { recording ->
        val value = mediaSearchKey(listOf("كتب مسموعة قراءة متن تسجيل صوتي audiobook audiobooks audio reading IslamHouse دار الإسلام", recording.title, recording.englishTitle, recording.author,
            recording.reader, recording.englishReader, recording.englishReader.filter { it.isLetterOrDigit() }, recording.tracks.joinToString(" ") { "${it.title} ${it.englishTitle}" },
            recording.bookIds.mapNotNull { id -> Catalog.books.find { it.id == id }?.title }.joinToString(" ")).joinToString(" "))
        (bookId == null || bookId in recording.bookIds) && terms.all { it in value }
    }
}
