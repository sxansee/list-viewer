package app.fileviewer

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps the music going when the screen is off or another app is in front, and gives it the controls in the notification
 * and on the lock screen. It owns the player and its media session; the audio screen of the app uses that very player
 * through [PlaybackLink] (the two are in one process), and ends the service when the person closes the screen.
 * It also remembers where each piece was left, since the screen may be gone by then.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var saver: PositionSaver? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            saver?.tick()
            handler.postDelayed(this, SAVE_EVERY_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val p = newAudioPlayer(this)
        val built = createSession(p)
        if (built == null) {
            // Not expected, but a phone may be stricter than the rest: the service ends, and the screen, which hears nothing
            // from it, plays the music by itself after a few seconds.
            p.release()
            stopSelf()
            return
        }
        player = p
        session = built
        val recents = Recents(applicationContext)
        val s = PositionSaver(p) { uri, place -> recents.setPos(uri, place) }
        saver = s
        p.addListener(s)
        handler.postDelayed(tick, SAVE_EVERY_MS)
        PlaybackLink.publish(p)
    }

    /** The media session of [p], added to this service; null when it cannot be made. */
    private fun createSession(p: ExoPlayer): MediaSession? {
        var made: MediaSession? = null
        return try {
            val builder = MediaSession.Builder(this, p)
            // Touching the notification brings the app to the front, the way its icon in the launcher does.
            packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
                builder.setSessionActivity(
                    PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
            }
            val built = builder.build()
            made = built
            // The service looks after the notification of the sessions that were added to it. A session is added by itself when a
            // controller connects through onGetSession; here the screen uses the player directly, so it is added by hand.
            addSession(built)
            built
        } catch (e: Exception) {
            try {
                made?.release()
            } catch (again: Exception) {
                // Nothing more can be done with it.
            }
            null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** The app was swiped away from the list of recent apps: the music ends with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        val p = player
        val s = session
        player = null
        session = null
        saver?.saveNow()
        saver = null
        if (p != null) {
            PlaybackLink.gone(p)
            p.release()
        }
        s?.release()
        super.onDestroy()
    }

    private companion object {
        const val SAVE_EVERY_MS = 5000L
    }
}
