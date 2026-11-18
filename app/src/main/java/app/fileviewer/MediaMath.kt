package app.fileviewer

import java.util.Locale
import kotlin.math.abs

/*
 * The small calculations behind the audio screen: how a moment is written, which speed comes next, where a jump lands.
 * They know nothing about Android, so the unit tests (app/src/test) run them directly.
 */

/** The speeds the audio screen goes through when its speed button is pressed. */
val SPEEDS: List<Float> = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/** How far the two skip buttons jump, in milliseconds. */
const val SKIP_MS = 10_000L

/** The speed after [current] in [SPEEDS], and the first one again after the last. A speed that is not in the list becomes normal speed. */
fun nextSpeed(current: Float): Float {
    val i = SPEEDS.indexOfFirst { abs(it - current) < 0.01f }
    return if (i < 0) 1f else SPEEDS[(i + 1) % SPEEDS.size]
}

/** A speed as it is written on the button: "1×", "1.25×". */
fun speedLabel(speed: Float): String {
    val whole = speed.toInt()
    val text = if (abs(speed - whole) < 0.005f) {
        whole.toString()
    } else {
        String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    }
    return text + "×"
}

/** A moment of a recording as "3:07", or "1:02:09" when it is an hour or more into it. Negative values count as zero. */
fun formatClock(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/**
 * Where a jump of [delta] milliseconds from [position] lands: never before the start, and never after the end when
 * the length ([duration]) is known (zero or less means it is not).
 */
fun seekBy(position: Long, delta: Long, duration: Long): Long {
    val top = if (duration > 0L) duration else Long.MAX_VALUE
    return (position + delta).coerceIn(0L, top)
}

/**
 * Where playback is to be remembered: the moment it is at, or the start when it is within the last three seconds
 * (a recording that was heard to the end starts over next time). [total] is the length, zero or less when unknown.
 */
fun restingPlace(at: Long, total: Long): Long =
    if (total > 0L && at >= total - 3000L) 0L else at.coerceAtLeast(0L)
