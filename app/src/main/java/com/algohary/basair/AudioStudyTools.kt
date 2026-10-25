package com.algohary.basair

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

internal data class AudioSleepPlan(val deadline: Long = 0, val endTrack: String? = null)
internal fun parseSleepMinutes(value: String): Int? {
    val input = value.trim()
    if (input.length !in 1..3) return null
    var result = 0
    for (c in input) { val digit = Character.digit(c, 10); if (digit < 0) return null; result = result * 10 + digit }
    return result.takeIf { it in 1..180 }
}
internal fun sleepDeadline(now: Long, minutes: Int): Long? =
    if (now >= 0 && minutes in 1..180 && now <= Long.MAX_VALUE - minutes * 60_000L) now + minutes * 60_000L else null
internal fun sleepExpired(plan: AudioSleepPlan, now: Long): Boolean = plan.deadline > 0 && now >= plan.deadline
internal data class EndTimerAction(val cancel: Boolean = false, val pause: Boolean = false)
internal fun endTimerTransition(endTrack: String?, nextTrack: String?, automatic: Boolean, repeated: Boolean): EndTimerAction =
    if (endTrack != null && (repeated || nextTrack != endTrack)) EndTimerAction(true, automatic || repeated) else EndTimerAction()

/** Timers use elapsed time and belong to the running playback service, never a future process. */
internal class AudioSleepStore(context: Context) {
    private companion object { val lock = Any() }
    private val prefs = context.applicationContext.getSharedPreferences("basair-audio-sleep", Context.MODE_PRIVATE)
    fun plan(): AudioSleepPlan {
        val snapshot = prefs.all
        return AudioSleepPlan(snapshot["deadline"] as? Long ?: 0, snapshot["endTrack"] as? String)
    }
    fun minutes(value: Int): Boolean {
        val deadline = sleepDeadline(SystemClock.elapsedRealtime(), value) ?: return false
        return synchronized(lock) { prefs.edit().clear().putLong("deadline", deadline).commit() }
    }
    fun endOf(track: String): Boolean = audioTrack(track) != null && synchronized(lock) { prefs.edit().clear().putString("endTrack", track).commit() }
    fun clear() = synchronized(lock) { prefs.edit().clear().commit() }
    fun clearIf(expected: AudioSleepPlan): Boolean = synchronized(lock) {
        if (plan() != expected) false else {
            // Pausing must not depend on a successful disk write; this timer belongs to this service only.
            prefs.edit().clear().apply()
            true
        }
    }
}

internal data class AudioBookmark(val id: String, val trackId: String, val positionMs: Long,
    val title: String, val note: String, val updatedAt: Long)
internal fun validAudioBookmark(item: AudioBookmark): Boolean {
    val track = audioTrack(item.trackId) ?: return false
    return item.id.isNotBlank() && item.id.length <= 80 && item.positionMs >= 0 &&
        (track.durationMs == null || item.positionMs <= track.durationMs) &&
        item.title.isNotBlank() && item.title.length <= 120 && item.note.length <= 2000 &&
        '\u0000' !in item.title && '\u0000' !in item.note && item.updatedAt > 0
}
internal fun upsertAudioBookmark(items: List<AudioBookmark>, item: AudioBookmark): List<AudioBookmark>? {
    if (!validAudioBookmark(item) || (items.size >= 500 && items.none { it.id == item.id })) return null
    return (listOf(item) + items.filterNot { it.id == item.id }).sortedByDescending { it.updatedAt }
}

internal class AudioBookmarkStore(context: Context) {
    private companion object { val lock = Any() }
    private val prefs = context.applicationContext.getSharedPreferences("basair-audio-bookmarks", Context.MODE_PRIVATE)
    fun list(): List<AudioBookmark> = runCatching { read() }.getOrDefault(emptyList())
    private fun read(): List<AudioBookmark> {
        val a = JSONArray(prefs.getString("items", "[]"))
        return (0 until minOf(a.length(), 500)).mapNotNull { n -> runCatching {
            val o = a.getJSONObject(n)
            AudioBookmark(o.getString("id"), o.getString("trackId"), o.getLong("positionMs"), o.getString("title"), o.optString("note"), o.getLong("updatedAt"))
                .takeIf(::validAudioBookmark)
        }.getOrNull() }.distinctBy { it.id }.sortedByDescending { it.updatedAt }
    }
    fun save(item: AudioBookmark): Boolean = runCatching { synchronized(lock) { upsertAudioBookmark(read(), item)?.let(::write) ?: false } }.getOrDefault(false)
    fun delete(id: String): Boolean = runCatching { synchronized(lock) { write(read().filterNot { it.id == id }) } }.getOrDefault(false)
    private fun write(items: List<AudioBookmark>): Boolean {
        val array = JSONArray()
        items.forEach { i -> array.put(JSONObject().put("id", i.id).put("trackId", i.trackId).put("positionMs", i.positionMs)
            .put("title", i.title).put("note", i.note).put("updatedAt", i.updatedAt)) }
        return prefs.edit().putString("items", array.toString()).commit()
    }
}
