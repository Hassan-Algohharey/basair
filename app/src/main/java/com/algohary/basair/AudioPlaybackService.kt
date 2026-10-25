package com.algohary.basair

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Canonical IDs and URLs only; callers cannot supply their own stream location or metadata. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun audioMediaItem(track: AudioTrack, recording: Audiobook): MediaItem = MediaItem.Builder()
    .setMediaId(track.id)
    .setUri(track.url)
    .setMediaMetadata(MediaMetadata.Builder()
        .setTitle(recording.title + " — " + track.title)
        .setAlbumTitle(recording.title)
        .setArtist(recording.reader)
        .setAuthor(recording.author)
        .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
        .build())
    .build()

@androidx.annotation.OptIn(UnstableApi::class)
class AudioPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var progress: AudioProgressStore
    private val handler = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) persistCurrent()
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        progress = AudioProgressStore(this)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(15_000)
            .build()
        player.playbackParameters = PlaybackParameters(progress.speed())
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION)) persistCurrent()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                val speed = audioPlaybackSpeed(playbackParameters.speed)
                if (speed != playbackParameters.speed) player.setPlaybackSpeed(speed)
                progress.saveSpeed(speed)
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                oldPosition.mediaItem?.mediaId?.let { id ->
                    // Capture the departing item's position before the new current item replaces it.
                    progress.save(id, oldPosition.positionMs, progress.duration(id), player.playbackParameters.speed, false)
                }
                persistCurrent()
            }
        })
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("open_audio", true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player)
            .setSessionActivity(activity)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> =
                    resolveItems(mediaItems)

                override fun onSetMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val items = canonicalItems(mediaItems)
                        ?: return Futures.immediateFailedFuture(IllegalArgumentException("Unknown audiobook track"))
                    if (items.isEmpty()) return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, C.INDEX_UNSET, C.TIME_UNSET))
                    val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, items.lastIndex)
                    val duration = audioTrack(items[index].mediaId)?.durationMs ?: progress.duration(items[index].mediaId)
                    val position = if (startPositionMs == C.TIME_UNSET) C.TIME_UNSET else audioSeekPosition(startPositionMs, duration)
                    return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, index, position))
                }
            })
            .build()
        // Connecting a screen never prepares a stream or starts playback.
        handler.postDelayed(progressTick, 2_000)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.uid == Process.myUid() || controllerInfo.isTrusted) session else null

    private fun canonicalItems(items: List<MediaItem>): List<MediaItem>? {
        if (items.size > allAudioRecordings.sumOf { it.tracks.size }) return null
        return items.map { item ->
            val track = audioTrack(item.mediaId) ?: return null
            val recording = audioRecording(track.id) ?: return null
            val canonical = audioMediaItem(track, recording)
            AudioDownloads.localFile(this, track)?.let { file ->
                canonical.buildUpon().setUri(android.net.Uri.fromFile(file)).build()
            } ?: canonical
        }
    }

    private fun resolveItems(items: List<MediaItem>): ListenableFuture<List<MediaItem>> =
        canonicalItems(items)?.let { Futures.immediateFuture(it) }
            ?: Futures.immediateFailedFuture(IllegalArgumentException("Unknown audiobook track"))

    private fun persistCurrent() {
        if (!::player.isInitialized || !::progress.isInitialized) return
        val id = player.currentMediaItem?.mediaId ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: progress.duration(id)
        progress.save(id, player.currentPosition, duration, player.playbackParameters.speed)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistCurrent()
        // Keep an active listening session alive; an idle service can be discarded.
        if (!isPlaybackOngoing()) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressTick)
        persistCurrent()
        session?.release()
        session = null
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
