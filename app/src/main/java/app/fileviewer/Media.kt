package app.fileviewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/* ---------- Audio and video ---------- */

/** Where playback is to be remembered: the current moment, or the start when the end was reached. */
private fun restingPlace(p: Player): Long = restingPlace(p.currentPosition, p.duration)

/**
 * Audio and video. Playback starts at [start] (milliseconds, where it stopped last time); [onPos] is told the moment
 * every few seconds while playing and when the player is closed.
 *
 * Video gets the player with its own controls. Audio gets a screen of its own: the cover, the title, a seek bar and the
 * buttons ([name] stands in for a title the file does not carry). A file that is called video but has only sound is shown
 * as audio once the player knows that, and a file that cannot be played gets an explanation and a way out ([mime] is for
 * handing it to another app).
 */
@OptIn(UnstableApi::class)
@Composable
fun MediaView(uri: Uri, video: Boolean, start: Long, name: String, mime: String, onPos: (Long) -> Unit) {
    val ctx = LocalContext.current
    val save = rememberUpdatedState(onPos)
    val player = remember(uri) {
        ExoPlayer.Builder(ctx)
            // The player takes the audio focus (a call or another player pauses it) and pauses when the headphones are pulled out.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(if (video) C.AUDIO_CONTENT_TYPE_MOVIE else C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            // Music goes on with the screen off: the processor is not put to sleep while a file is playing.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(uri), start)
                prepare()
                playWhenReady = true
            }
    }
    var failed by remember(player) { mutableStateOf(false) }
    // A video file is shown with a picture until the player says that there is none to show.
    var picture by remember(player) { mutableStateOf(video) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (video && !tracks.isEmpty()) picture = tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            if (player.isPlaying) save.value(restingPlace(player))
        }
    }
    DisposableEffect(player) {
        onDispose {
            save.value(restingPlace(player))
            player.release()
        }
    }
    when {
        failed -> MessageState(
            iconRes = R.drawable.ic_warning,
            title = stringResource(R.string.media_error_title),
            body = stringResource(R.string.media_error_body)
        ) {
            OutlinedButton(onClick = { openExternal(ctx, uri, mime) }) {
                Text(stringResource(R.string.action_open_external))
            }
        }
        video && picture -> VideoSurface(player)
        else -> AudioPanel(player, name.substringBeforeLast('.', name))
    }
}

/** The player with its own controls, which go away after a few seconds and come back on touch. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(player: Player) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                controllerShowTimeoutMs = 3000
                controllerHideOnTouch = true
            }
        }
    )
}

/* ---------- The audio screen ---------- */

/** What the audio screen shows, read from the player whenever it reports a change and a few times a second for the moment. */
@Stable
private class PlayerUi(private val p: Player) {
    /** True when the main button is to show "play" (paused, not started or finished), false when it is to show "pause". */
    var showPlay by mutableStateOf(true)
    var duration by mutableLongStateOf(0L)
    var position by mutableLongStateOf(0L)
    var speed by mutableFloatStateOf(1f)
    var title by mutableStateOf<String?>(null)
    var artist by mutableStateOf<String?>(null)
    var album by mutableStateOf<String?>(null)
    var art by mutableStateOf<ByteArray?>(null)

    fun sync() {
        val state = p.playbackState
        showPlay = !p.playWhenReady || state == Player.STATE_IDLE || state == Player.STATE_ENDED
        val total = p.duration
        duration = if (total > 0L) total else 0L
        position = p.currentPosition.coerceAtLeast(0L)
        speed = p.playbackParameters.speed
        // The tags of the file (ID3, MP4, Vorbis comments) arrive through the player.
        val m = p.mediaMetadata
        title = clean(m.title)
        artist = clean(m.artist) ?: clean(m.albumArtist)
        album = clean(m.albumTitle)
        art = m.artworkData
    }

    private fun clean(c: CharSequence?): String? = c?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

/** The picture embedded in the file, scaled down so that a huge cover does not eat the memory. Null when it cannot be read. */
private fun decodeCover(data: ByteArray, limit: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    var step = 1
    while (bounds.outWidth / (step * 2) >= limit && bounds.outHeight / (step * 2) >= limit) step *= 2
    BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = step })
} catch (e: Exception) {
    null
} catch (e: OutOfMemoryError) {
    null
}

/** The main button: starts, pauses, or starts again from the beginning when the end was reached. */
private fun pressPlay(p: Player) {
    val state = p.playbackState
    val showsPlay = !p.playWhenReady || state == Player.STATE_IDLE || state == Player.STATE_ENDED
    if (!showsPlay) {
        p.pause()
        return
    }
    if (state == Player.STATE_IDLE) p.prepare()
    if (state == Player.STATE_ENDED) p.seekTo(0L)
    p.play()
}

@Composable
private fun AudioPanel(p: Player, fallbackTitle: String) {
    val ui = remember(p) { PlayerUi(p) }
    DisposableEffect(p) {
        ui.sync()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                ui.sync()
            }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }
    LaunchedEffect(ui) {
        while (true) {
            ui.sync()
            delay(250)
        }
    }
    val art = ui.art
    val cover by produceState<Bitmap?>(null, art) {
        value = if (art == null) null else withContext(Dispatchers.IO) { decodeCover(art, 1024) }
    }
    val title = ui.title ?: fallbackTitle
    val sub = listOfNotNull(ui.artist, ui.album).joinToString("  ·  ")

    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), MaterialTheme.colorScheme.background)
            )
        )
    ) {
        // A phone held sideways has the cover on the left and everything else on the right.
        val wide = maxWidth > maxHeight
        // Held upright the cover is as wide as the screen allows, but not so tall that the buttons are pushed off a small screen.
        val side = if (wide) maxHeight * 0.6f else minOf(maxWidth - 64.dp, 320.dp, maxHeight * 0.45f)
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (wide) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    AudioCover(cover, side)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        AudioTitles(title, sub)
                        Spacer(Modifier.height(12.dp))
                        AudioSeek(ui, p)
                        AudioButtons(ui, p)
                    }
                }
            } else {
                AudioCover(cover, side)
                Spacer(Modifier.height(28.dp))
                AudioTitles(title, sub)
                Spacer(Modifier.height(16.dp))
                AudioSeek(ui, p)
                Spacer(Modifier.height(8.dp))
                AudioButtons(ui, p)
            }
        }
    }
}

/** The cover of the recording, or the app's own tile with a note when the file has none. */
@Composable
private fun AudioCover(bitmap: Bitmap?, side: Dp) {
    val shape = RoundedCornerShape(side * 0.09f)
    Box(
        Modifier
            .size(side)
            .shadow(10.dp, shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0E7C7B), Color(0xFF063F44)))),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Ico(R.drawable.ic_music_note, null, Modifier.size(side * 0.38f), Color.White.copy(alpha = 0.92f))
        }
    }
}

@Composable
private fun AudioTitles(title: String, sub: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    if (sub.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(
            sub,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The seek bar with the moment on the left and the length on the right. Dragging shows where it would land; letting go jumps there. */
@Composable
private fun AudioSeek(ui: PlayerUi, p: Player) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val total = ui.duration
    val shown = drag ?: if (total > 0L) (ui.position.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown,
            onValueChange = { drag = it },
            onValueChangeFinished = {
                val f = drag
                if (f != null && total > 0L) {
                    p.seekTo((f * total).toLong())
                    ui.sync()
                }
                drag = null
            },
            enabled = total > 0L
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatClock(if (drag != null) (shown * total).toLong() else ui.position),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (total > 0L) formatClock(total) else "–:––",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Speed on the left, ten seconds back, play / pause, ten seconds forward; the right side is left empty to keep the middle in the middle. */
@Composable
private fun AudioButtons(ui: PlayerUi, p: Player) {
    val playText = stringResource(R.string.audio_play)
    val pauseText = stringResource(R.string.audio_pause)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            TextButton(onClick = {
                p.setPlaybackSpeed(nextSpeed(p.playbackParameters.speed))
                ui.sync()
            }) {
                Text(speedLabel(ui.speed), fontWeight = FontWeight.SemiBold)
            }
        }
        SkipButton(R.drawable.ic_replay, stringResource(R.string.audio_back)) {
            p.seekTo(seekBy(p.currentPosition, -SKIP_MS, p.duration))
            ui.sync()
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button, onClickLabel = if (ui.showPlay) playText else pauseText) {
                    pressPlay(p)
                    ui.sync()
                },
            contentAlignment = Alignment.Center
        ) {
            Ico(
                if (ui.showPlay) R.drawable.ic_play else R.drawable.ic_pause,
                if (ui.showPlay) playText else pauseText,
                Modifier.size(36.dp),
                MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(Modifier.width(8.dp))
        SkipButton(R.drawable.ic_forward, stringResource(R.string.audio_forward)) {
            p.seekTo(seekBy(p.currentPosition, SKIP_MS, p.duration))
            ui.sync()
        }
        Box(Modifier.weight(1f))
    }
}

/** A round arrow with "10" in the middle. */
@Composable
private fun SkipButton(icon: Int, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Ico(icon, desc, Modifier.size(32.dp))
            Text("10", fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
