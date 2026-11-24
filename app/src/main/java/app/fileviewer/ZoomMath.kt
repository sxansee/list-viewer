package app.fileviewer

import kotlin.math.max

/*
 * Zooming a picture or the pages of a document with two fingers. The point that is under the fingers when they take hold
 * stays under them while they spread or move, and nothing can be dragged away from the edges of the area it is shown in.
 * This file works with numbers only, so the unit tests (app/src/test) run it directly; the gestures that feed it are in Zoom.kt.
 */

/** A picture enlarged [scale] times and moved by ([x], [y]) pixels from where it sits centred in its area. */
class Zoom(val scale: Float, val x: Float, val y: Float)

/** A change of the zoom that is not a number (a finger lifted at the wrong moment, say) is no change. */
private fun usable(factor: Float): Float = if (factor.isFinite() && factor > 0f) factor else 1f

private fun finite(vararg values: Float): Boolean = values.all { it.isFinite() }

/**
 * The zoom of a picture after the fingers at ([cx], [cy]) (measured from the centre of the area) have spread by [factor] and
 * moved by ([dx], [dy]). The picture is [fw] by [fh] pixels at scale 1 inside an area of [w] by [h]; the scale stays between
 * 1 and [max], and a side of the picture that is smaller than the area stays centred.
 */
fun zoomed(
    from: Zoom, factor: Float, cx: Float, cy: Float, dx: Float, dy: Float,
    w: Float, h: Float, fw: Float, fh: Float, max: Float
): Zoom {
    if (!finite(cx, cy, dx, dy)) return from
    val scale = (from.scale * usable(factor)).coerceIn(1f, max)
    val z = scale / from.scale
    return Zoom(
        scale,
        keepInside(cx * (1f - z) + from.x * z + dx, w, fw * scale),
        keepInside(cy * (1f - z) + from.y * z + dy, h, fh * scale)
    )
}

/** [offset] kept so that a picture [shown] pixels wide in an area of [area] pixels does not leave a gap at its edges. */
private fun keepInside(offset: Float, area: Float, shown: Float): Float {
    val room = max(0f, (shown - area) / 2f)
    return offset.coerceIn(-room, room)
}

/**
 * The zoom of pages that are anchored at their top left corner: the new [scale], the horizontal [shift] (never positive: the
 * pages are only moved to the left) and how far the list of pages is to be scrolled, in the pixels of the list (positive is
 * towards the end). The list is as tall as the screen divided by the scale, so that it fills the screen once it is enlarged.
 */
class PageZoom(val scale: Float, val shift: Float, val scroll: Float)

/**
 * The zoom of pages after the fingers at ([cx], [cy]) (from the top left corner of the screen, before they moved) have spread
 * by [factor] and moved by ([dx], [dy]), on a screen [width] pixels wide. [scale] and [shift] are what they were.
 */
fun pageZoomed(
    scale: Float, shift: Float, factor: Float, cx: Float, cy: Float, dx: Float, dy: Float, width: Float, max: Float
): PageZoom {
    if (!finite(cx, cy, dx, dy)) return PageZoom(scale, shift, 0f)
    val s = (scale * usable(factor)).coerceIn(1f, max)
    val x = cx - (cx - shift) * (s / scale) + dx
    return PageZoom(s, x.coerceIn(width * (1f - s), 0f), cy / scale - (cy + dy) / s)
}
