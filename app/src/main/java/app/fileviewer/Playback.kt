package app.fileviewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/*
 * How audio is played. The queue (see Playlist.kt) is given to one player, which moves on from piece to piece by itself.
 * The player lives either in PlaybackService, which keeps the music going with the screen off and has the controls in the
 * notification and on the lock screen, or, when that is switched off or does not start, inside the app as it always did.
 */

/** A player set up for music: it takes the audio focus, pauses when the headphones are pulled out and keeps the processor awake. */
@OptIn(UnstableApi::class)
fun newAudioPlayer(ctx: Context): ExoPlayer = ExoPlayer.Builder(ctx)
    .setAudioAttributes(
        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
        true
    )
    .setHandleAudioBecomingNoisy(true)
    .setWakeMode(C.WAKE_MODE_LOCAL)
    .build()

/** The queue as the items of a player: the address is also the id, and the name is shown until the tags of the file say better. */
fun mediaItemsOf(list: Playlist): List<MediaItem> = list.tracks.map { track ->
    MediaItem.Builder()
        .setMediaId(track.uri)
        .setUri(track.uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
        .build()
}

/** The activity that a context belongs to, or null (a service, for example). */
fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Remembers where each piece of audio of the queue was left, through [save] (the address of the file, the place in
 * milliseconds): when playback stops, when it moves from one piece to another, and every few seconds through [tick].
 */
class PositionSaver(private val player: Player, private val save: (String, Long) -> Unit) : Player.Listener {
    /** To be called every few seconds: remembers the place of the piece that is playing. */
    fun tick() {
        if (player.isPlaying) saveNow()
    }

    fun saveNow() {
        val id = player.currentMediaItem?.mediaId ?: return
        if (id.isEmpty()) return
        save(id, restingPlace(player.currentPosition, player.duration))
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        val old = oldPosition.mediaItem?.mediaId
        // A jump inside one piece is covered by the ticks; the piece that was left is remembered here.
        if (old.isNullOrEmpty() || old == newPosition.mediaItem?.mediaId) return
        save(old, leftAt(oldPosition.positionMs, reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION))
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) saveNow()
    }
}

/**
 * The way from the audio screen to the player that lives in [PlaybackService]. Both are in the same process, so the
 * screen uses the player itself and nothing is sent between processes. Everything here runs on the main thread.
 */
object PlaybackLink {
    private var current: Player? = null
    private val waiting = ArrayList<(Player) -> Unit>()

    /** Calls [callback] with the player of the service: now when the service is running, else as soon as it has started. */
    fun whenReady(callback: (Player) -> Unit) {
        val p = current
        if (p != null) callback(p) else waiting.add(callback)
    }

    fun cancel(callback: (Player) -> Unit) {
        waiting.remove(callback)
    }

    /** The service has its player. */
    fun publish(player: Player) {
        current = player
        if (waiting.isEmpty()) return
        val calls = ArrayList(waiting)
        waiting.clear()
        for (call in calls) call(player)
    }

    /** The screen is done with the player, which is about to be stopped: nobody else may pick it up. */
    fun detach() {
        current = null
    }

    /** The service is gone with its [player]. */
    fun gone(player: Player) {
        if (current === player) current = null
    }
}

/** How long the screen waits for the service to start before it plays the music inside the app instead. */
private const val SERVICE_WAIT_MS = 4000L

/**
 * The player of an audio screen and what goes with it. [open] starts the playback of [playlist] from [start] (the place
 * in the first piece, in milliseconds); [close] ends it. With [useService] the player is the one in [PlaybackService];
 * if that cannot be started, or does not answer in a few seconds, the music is played by a player of the app's own.
 * [savePlace] remembers where a piece was left (the service does it itself when it has the player), and [onTrack] is told
 * which piece is playing whenever that changes.
 */
@Stable
class AudioSession(
    private val ctx: Context,
    private val playlist: Playlist,
    private val start: Long,
    private val useService: Boolean,
    private val savePlace: (String, Long) -> Unit,
    private val onTrack: (String) -> Unit
) {
    /** The player, once there is one; null while the service is starting. */
    var player by mutableStateOf<Player?>(null)
        private set

    /** True when the piece that was opened cannot be played at all. */
    var failed by mutableStateOf(false)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var own: ExoPlayer? = null
    private var saver: PositionSaver? = null
    private var inService = false
    private var started = false
    private var played = false
    private var closed = false

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId
            if (!id.isNullOrEmpty()) onTrack(id)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) played = true
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player ?: return
            if (played && p.hasNextMediaItem()) {
                // A piece in the middle of the queue that cannot be played is skipped.
                p.seekToNextMediaItem()
                p.prepare()
                p.play()
            } else {
                failed = true
            }
        }
    }

    private val ready: (Player) -> Unit = { p ->
        handler.removeCallbacks(giveUp)
        if (!closed && player == null) attach(p, true)
    }

    private val giveUp = Runnable {
        if (!closed && player == null) {
            PlaybackLink.cancel(ready)
            stopService()
            startOwn()
        }
    }

    fun open() {
        if (useService) {
            handler.postDelayed(giveUp, SERVICE_WAIT_MS)
            PlaybackLink.whenReady(ready)
            try {
                started = true
                ctx.startService(Intent(ctx, PlaybackService::class.java))
                return
            } catch (e: Exception) {
                // The system does not let the service start now: the music is played here.
                started = false
                handler.removeCallbacks(giveUp)
                PlaybackLink.cancel(ready)
                if (player != null) return
            }
        }
        startOwn()
    }

    /** To be called every few seconds. */
    fun tick() {
        saver?.tick()
    }

    /**
     * Ends the playback and lets go of the player. [keepPlaying] is for when the system took the screen away while the
     * music was playing in the service: the music is then left to go on.
     */
    fun close(keepPlaying: Boolean) {
        if (closed) return
        closed = true
        handler.removeCallbacks(giveUp)
        PlaybackLink.cancel(ready)
        val p = player
        p?.removeListener(listener)
        if (inService) {
            if (!keepPlaying) {
                p?.pause()
                PlaybackLink.detach()
                stopService()
            }
        } else {
            saver?.saveNow()
            own?.release()
            own = null
            stopService()
        }
        player = null
    }

    private fun startOwn() {
        if (closed || player != null) return
        val p = newAudioPlayer(ctx)
        own = p
        val s = PositionSaver(p, savePlace)
        saver = s
        p.addListener(s)
        attach(p, false)
    }

    private fun attach(p: Player, service: Boolean) {
        inService = service
        p.addListener(listener)
        val opened = playlist.tracks[playlist.start].uri
        val now = p.currentMediaItem?.mediaId.orEmpty()
        // A service that is already playing a piece of this queue (the system took the screen away while the music went on,
        // and the screen came back) is left alone: it goes on from where it is, and the screen shows that piece.
        val running = service && now.isNotEmpty() && p.mediaItemCount > 0 &&
            p.playbackState != Player.STATE_IDLE && p.playbackState != Player.STATE_ENDED && playlist.positionOf(now) > 0
        if (running) {
            // It has been playing before this screen came back: a piece that fails later is skipped, not reported as the end.
            played = true
            if (now != opened) onTrack(now)
        } else {
            p.setMediaItems(mediaItemsOf(playlist), playlist.start, start)
            p.prepare()
            p.playWhenReady = true
        }
        player = p
    }

    private fun stopService() {
        if (!started) return
        started = false
        try {
            ctx.stopService(Intent(ctx, PlaybackService::class.java))
        } catch (e: Exception) {
            // Nothing more can be done: the service ends itself when the app is closed.
        }
    }
}
