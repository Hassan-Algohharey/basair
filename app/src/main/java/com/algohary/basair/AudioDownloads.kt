package com.algohary.basair

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal class AudioDownloads(private val context: Context) {
    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()
        private fun directory(context: Context) = File(context.filesDir, "audiobooks").apply { mkdirs() }
        private fun key(track: AudioTrack) = MessageDigest.getInstance("SHA-256").digest(track.url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        private fun file(context: Context, track: AudioTrack) = File(directory(context), key(track) + ".mp3")
        fun localFile(context: Context, track: AudioTrack): File? {
            val target = file(context, track)
            val marker = File(target.path + ".complete")
            return target.takeIf { it.isFile && it.length() == track.byteSize && marker.isFile && marker.length() == 64L }
        }
        fun partialBytes(context: Context, track: AudioTrack): Long =
            File(file(context, track).path + ".part").length().coerceIn(0, track.byteSize)
    }

    suspend fun download(track: AudioTrack, onProgress: (Long) -> Unit): File = withContext(Dispatchers.IO) {
        check(audioRecording(track.id)?.downloadAllowed == true)
        check(audioTrack(track.id) == track && track.byteSize in 1..500_000_000)
        locks.getOrPut(key(track)) { Mutex() }.withLock {
            localFile(context, track)?.let { return@withLock it }
            val preferences = context.getSharedPreferences("basair-audio", Context.MODE_PRIVATE)
            val network = context.getSystemService(ConnectivityManager::class.java)
            if (preferences.getBoolean("wifiOnly", true) &&
                (network.getNetworkCapabilities(network.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true ||
                    network.isActiveNetworkMetered)) throw IOException("wifi required")
            val target = file(context, track)
            val partial = File(target.path + ".part")
            val versionFile = File(target.path + ".part.version")
            if (partial.length() > track.byteSize) partial.delete()
            var offset = partial.length()
            val previousVersion = versionFile.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
            if (offset in 1 until track.byteSize && previousVersion == null) { partial.delete(); offset = 0 }
            if (offset < track.byteSize) {
                val connection = URL(track.url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000; connection.readTimeout = 20_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Range", "bytes=$offset-")
                if (offset > 0 && previousVersion != null) connection.setRequestProperty("If-Range", previousVersion)
                try {
                    val code = connection.responseCode
                    if (code == 200) offset = 0
                    else if (code != 206 || !validAudioContentRange(connection.getHeaderField("Content-Range"), offset, track.byteSize))
                        throw IOException("Invalid audio response")
                    if (connection.contentType?.substringBefore(';') != track.mimeType) throw IOException("Unexpected audio type")
                    val version = connection.getHeaderField("ETag")?.takeUnless { it.startsWith("W/") }
                        ?: connection.getHeaderField("Last-Modified")
                    if (offset > 0 && version != previousVersion) {
                        partial.delete(); versionFile.delete(); throw IOException("Audio source changed")
                    }
                    versionFile.writeText(version.orEmpty())
                    connection.inputStream.use { input ->
                        java.io.FileOutputStream(partial, offset > 0).use { output ->
                            val buffer = ByteArray(32_768)
                            var total = offset
                            var lastUpdate = 0L
                            withContext(Dispatchers.Main) { onProgress(total) }
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > track.byteSize) throw IOException("Audio exceeds expected size")
                                output.write(buffer, 0, count)
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastUpdate > 200 || total == track.byteSize) {
                                    withContext(Dispatchers.Main) { onProgress(total) }; lastUpdate = now
                                }
                            }
                        }
                    }
                } finally { connection.disconnect() }
            }
            currentCoroutineContext().ensureActive()
            if (partial.length() != track.byteSize) throw IOException("Incomplete audio")
            val digest = MessageDigest.getInstance("SHA-256")
            partial.inputStream().use { input ->
                val buffer = ByteArray(32_768)
                while (true) { currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            val localHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (track.sha256 != null && localHash != track.sha256) {
                partial.delete(); versionFile.delete(); throw IOException("Audio checksum mismatch")
            }
            if (!partial.renameTo(target)) throw IOException("Cannot finalize audio")
            File(target.path + ".complete").writeText(localHash, Charsets.US_ASCII)
            versionFile.delete()
            target
        }
    }

    suspend fun remove(track: AudioTrack) = withContext(Dispatchers.IO) {
        locks.getOrPut(key(track)) { Mutex() }.withLock {
            val target = file(context, track)
            File(target.path + ".complete").delete(); target.delete(); File(target.path + ".part").delete(); File(target.path + ".part.version").delete()
        }
    }
}

internal fun validAudioContentRange(value: String?, offset: Long, total: Long): Boolean {
    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value.orEmpty()) ?: return false
    val start = match.groupValues[1].toLongOrNull() ?: return false
    val end = match.groupValues[2].toLongOrNull() ?: return false
    return start == offset && end >= start && end < total && match.groupValues[3].toLongOrNull() == total
}
