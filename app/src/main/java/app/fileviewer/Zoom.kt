package app.fileviewer

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min

/*
 * The gestures that zoom: two fingers spread (the point between them stays where it is), a double tap (zooms in on the
 * place that was tapped, or back out), and dragging a zoomed picture or page around. The numbers are worked out in
 * ZoomMath.kt; this file holds the state that the screens draw from and reads the fingers.
 */

/** The biggest enlargement of the pages of a document: the pages are drawn sharp up to this, as far as their bitmaps go. */
private const val MAX_PAGE_ZOOM = 3f

/** The biggest enlargement of a picture. */
private const val MAX_IMAGE_ZOOM = 8f

/** How much a double tap enlarges. */
private const val DOUBLE_TAP_PAGE_ZOOM = 2f
private const val DOUBLE_TAP_IMAGE_ZOOM = 2.5f

/** How long the zoom of a double tap takes, in milliseconds. */
private const val DOUBLE_TAP_MS = 240

/** A zoom a little above 1 counts as not zoomed: a double tap then zooms in, from further on it zooms out. */
private const val ZOOMED = 1.05f

/**
 * The zoom of the pages of a PDF. The pages are laid out as they are without zoom, in a list that is as tall as the screen
 * divided by [scale]; the layer that draws the list enlarges it from its top left corner, so it fills the screen. Moving the
 * fingers up and down scrolls the list as it always does, [shift] moves the pages sideways, and a pinch scrolls the list
 * and changes [scale] together so that the place between the fingers does not move. [sharp] is the scale the bitmaps of the
 * pages are drawn for: it follows [scale] once the fingers are lifted, so that the pages are not drawn again all the time
 * during a pinch.
 */
@Stable
class PagesZoom(private val list: LazyListState) {
    var scale by mutableFloatStateOf(1f)
        private set
    var shift by mutableFloatStateOf(0f)
        private set
    var sharp by mutableFloatStateOf(1f)
        private set
    private var running: Job? = null

    /** The fingers that were at ([cx], [cy]) spread by [factor] and moved by ([dx], [dy]); [width] is the width of the screen. */
    fun pinch(factor: Float, cx: Float, cy: Float, dx: Float, dy: Float, width: Float) {
        val z = pageZoomed(scale, shift, factor, cx, cy, dx, dy, width, MAX_PAGE_ZOOM)
        scale = z.scale
        shift = z.shift
        if (z.scroll != 0f) list.dispatchRawDelta(z.scroll)
    }

    /** The fingers are lifted: the pages are drawn again for the scale they have now. */
    fun settle() {
        sharp = scale
    }

    /** Stops the zoom of a double tap that is still going on. */
    fun stop() {
        running?.cancel()
        running = null
    }

    /** Zooms to [target] in a smooth movement, the place ([cx], [cy]) of the screen staying where it is. */
    fun animateTo(scope: CoroutineScope, target: Float, cx: Float, cy: Float, width: Float) {
        stop()
        running = scope.launch {
            val start = scale
            animate(0f, 1f, animationSpec = tween(DOUBLE_TAP_MS)) { t, _ ->
                pinch((start + (target - start) * t) / scale, cx, cy, 0f, 0f, width)
            }
            settle()
        }
    }
}

/**
 * Reads the fingers on the pages: two fingers zoom (and are not passed on, so the list does not scroll under them), one
 * finger moves a zoomed page sideways (the list takes the up and down part of the same movement), a double tap zooms.
 */
fun Modifier.pageGestures(zoom: PagesZoom, width: Float, scope: CoroutineScope): Modifier = this
    .pointerInput(width) {
        detectTapGestures(onDoubleTap = { at ->
            zoom.animateTo(scope, if (zoom.scale > ZOOMED) 1f else DOUBLE_TAP_PAGE_ZOOM, at.x, at.y, width)
        })
    }
    .pointerInput(width) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            zoom.stop()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) {
                    val before = event.calculateCentroid(useCurrent = false)
                    val pan = event.calculatePan()
                    if (before.isSpecified) zoom.pinch(event.calculateZoom(), before.x, before.y, pan.x, pan.y, width)
                    event.changes.forEach { it.consume() }
                } else if (zoom.scale > 1f) {
                    zoom.pinch(1f, 0f, 0f, event.calculatePan().x, 0f, width)
                }
            } while (event.changes.any { it.pressed })
            zoom.settle()
        }
    }

/** A picture bigger than this is not loaded in full: what is shown is sharp up to this many pixels on its longer side. */
const val IMAGE_DECODE_PX = 2560

/**
 * The zoom of a picture that is shown fitted into its area ([area], measured by the screen). The picture is enlarged from
 * its centre by [scale] and moved by ([x], [y]). [natural] is the size of the picture itself: with it the picture is stopped
 * at its own edges and not at the edges of the area.
 */
@Stable
class ImageZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set
    var area by mutableStateOf(IntSize.Zero)
    var natural by mutableStateOf(Size.Unspecified)
    private var running: Job? = null

    /** The size of the picture at scale 1: as it is fitted into the area. */
    private fun fitted(): Size {
        val w = area.width.toFloat()
        val h = area.height.toFloat()
        val n = natural
        if (!n.isSpecified || n.width <= 0f || n.height <= 0f) return Size(w, h)
        val k = min(w / n.width, h / n.height)
        return Size(n.width * k, n.height * k)
    }

    /** The fingers that were at ([cx], [cy]) (from the centre of the area) spread by [factor] and moved by ([dx], [dy]). */
    fun pinch(factor: Float, cx: Float, cy: Float, dx: Float, dy: Float) {
        val f = fitted()
        val z = zoomed(
            Zoom(scale, x, y), factor, cx, cy, dx, dy,
            area.width.toFloat(), area.height.toFloat(), f.width, f.height, MAX_IMAGE_ZOOM
        )
        scale = z.scale
        x = z.x
        y = z.y
    }

    fun stop() {
        running?.cancel()
        running = null
    }

    /** Zooms to [target] in a smooth movement, the place ([cx], [cy]) (from the centre of the area) staying where it is. */
    fun animateTo(scope: CoroutineScope, target: Float, cx: Float, cy: Float) {
        stop()
        running = scope.launch {
            val start = scale
            animate(0f, 1f, animationSpec = tween(DOUBLE_TAP_MS)) { t, _ ->
                pinch((start + (target - start) * t) / scale, cx, cy, 0f, 0f)
            }
        }
    }
}

/** Reads the fingers on a picture and draws it as zoomed: pinch, drag and double tap. */
fun Modifier.imageGestures(zoom: ImageZoom, scope: CoroutineScope): Modifier = this
    .onSizeChanged { zoom.area = it }
    .pointerInput(Unit) {
        detectTapGestures(onDoubleTap = { at ->
            zoom.animateTo(
                scope,
                if (zoom.scale > ZOOMED) 1f else DOUBLE_TAP_IMAGE_ZOOM,
                at.x - zoom.area.width / 2f,
                at.y - zoom.area.height / 2f
            )
        })
    }
    .pointerInput(Unit) {
        detectTransformGestures { centroid, pan, factor, _ ->
            zoom.stop()
            // The centroid is the place of the fingers before they moved by [pan]: the point they took hold of.
            zoom.pinch(
                factor,
                centroid.x - zoom.area.width / 2f,
                centroid.y - zoom.area.height / 2f,
                pan.x,
                pan.y
            )
        }
    }
    // Scale and offset are read inside the layer: moving a finger redraws the picture without composing it again.
    .graphicsLayer {
        scaleX = zoom.scale
        scaleY = zoom.scale
        translationX = zoom.x
        translationY = zoom.y
    }
