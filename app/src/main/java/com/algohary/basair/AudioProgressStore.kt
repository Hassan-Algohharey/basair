package com.algohary.basair

import android.content.Context

/** Listening positions are device-local and independent of downloaded files. */
internal class AudioProgressStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("basair-audio", Context.MODE_PRIVATE)

    fun position(trackId: String): Long = preferences.getLong("position:$trackId", 0).coerceAtLeast(0)

    fun duration(trackId: String): Long? = preferences.getLong("duration:$trackId", 0).takeIf { it > 0 }

    fun lastTrack(recordingId: String? = null): String? =
        preferences.getString(if (recordingId == null) "lastTrack" else "lastTrack:$recordingId", null)
            ?.takeIf { id -> audioTrack(id) != null && (recordingId == null || audioRecording(id)?.id == recordingId) }

    fun speed(): Float = audioPlaybackSpeed(preferences.getFloat("speed", 1f))

    fun clearPosition(trackId: String) {
        preferences.edit().remove("position:$trackId").apply()
    }

    internal fun saveSpeed(speed: Float) {
        preferences.edit().putFloat("speed", audioPlaybackSpeed(speed)).apply()
    }

    internal fun save(trackId: String, position: Long, duration: Long?, speed: Float, rememberTrack: Boolean = true) {
        val recording = audioRecording(trackId) ?: return
        val editor = preferences.edit()
            .putLong("position:$trackId", audioSeekPosition(position, duration))
            .putFloat("speed", audioPlaybackSpeed(speed))
        if (duration != null && duration > 0) editor.putLong("duration:$trackId", duration)
        if (rememberTrack) editor.putString("lastTrack", trackId).putString("lastTrack:${recording.id}", trackId)
        editor.apply()
    }
}
