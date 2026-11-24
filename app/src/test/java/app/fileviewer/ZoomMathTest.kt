package app.fileviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {
    // An area of 1000 by 2000 pixels with a picture that fills its width and is 1500 pixels tall.
    private val w = 1000f
    private val h = 2000f
    private val fw = 1000f
    private val fh = 1500f

    private fun zoom(from: Zoom, factor: Float, cx: Float, cy: Float, dx: Float = 0f, dy: Float = 0f) =
        zoomed(from, factor, cx, cy, dx, dy, w, h, fw, fh, 8f)

    /** The point of the picture (from its centre, at scale 1) that is at the place ([px], [py]) of the area (from its centre). */
    private fun pictureAt(z: Zoom, px: Float, py: Float) = Pair((px - z.x) / z.scale, (py - z.y) / z.scale)

    @Test
    fun theFingersKeepHoldOfTheSamePointOfThePicture() {
        val start = Zoom(1f, 0f, 0f)
        val before = pictureAt(start, 300f, -400f)
        val after = zoom(start, 2f, 300f, -400f)
        val now = pictureAt(after, 300f, -400f)
        assertEquals(2f, after.scale, 1e-4f)
        assertEquals(before.first, now.first, 1e-3f)
        assertEquals(before.second, now.second, 1e-3f)
    }

    @Test
    fun theFingersCarryThePictureWhenTheyMove() {
        val start = Zoom(2f, 0f, 0f)
        val moved = zoom(start, 1f, 0f, 0f, dx = -120f, dy = 80f)
        assertEquals(-120f, moved.x, 1e-3f)
        assertEquals(80f, moved.y, 1e-3f)
    }

    @Test
    fun thePictureCannotBeDraggedPastItsEdges() {
        val start = Zoom(2f, 0f, 0f)
        val far = zoom(start, 1f, 0f, 0f, dx = 5000f, dy = -5000f)
        // Shown 2000 by 3000 in an area of 1000 by 2000: it can move 500 sideways and 500 up or down.
        assertEquals(500f, far.x, 1e-3f)
        assertEquals(-500f, far.y, 1e-3f)
    }

    @Test
    fun aSideSmallerThanTheAreaStaysCentred() {
        val small = zoomed(Zoom(1f, 0f, 0f), 1.5f, 200f, 300f, 40f, 40f, w, h, 500f, 500f, 8f)
        assertEquals(1.5f, small.scale, 1e-4f)
        assertEquals(0f, small.x, 1e-3f)
        assertEquals(0f, small.y, 1e-3f)
    }

    @Test
    fun theScaleStaysBetweenOneAndTheMaximum() {
        assertEquals(8f, zoom(Zoom(1f, 0f, 0f), 100f, 0f, 0f).scale, 1e-4f)
        assertEquals(1f, zoom(Zoom(2f, 0f, 0f), 0.01f, 0f, 0f).scale, 1e-4f)
    }

    @Test
    fun zoomingBackOutPutsThePictureBackInTheMiddle() {
        val zoomedIn = zoom(Zoom(1f, 0f, 0f), 3f, 400f, 600f)
        val out = zoom(zoomedIn, 1f / 3f, 400f, 600f)
        assertEquals(1f, out.scale, 1e-4f)
        assertEquals(0f, out.x, 1e-3f)
        assertEquals(0f, out.y, 1e-3f)
    }

    @Test
    fun aChangeThatIsNotANumberChangesNothing() {
        val start = Zoom(2f, 100f, -50f)
        val same = zoom(start, Float.NaN, 0f, 0f)
        assertEquals(2f, same.scale, 1e-4f)
        assertEquals(100f, same.x, 1e-3f)
        val kept = zoom(start, 2f, Float.NaN, 0f)
        assertEquals(2f, kept.scale, 1e-4f)
        assertEquals(100f, kept.x, 1e-3f)
        assertEquals(-50f, kept.y, 1e-3f)
    }

    // ---- pages anchored at the top left corner, on a screen 1000 pixels wide ----

    private fun pages(scale: Float, shift: Float, factor: Float, cx: Float, cy: Float, dx: Float = 0f, dy: Float = 0f) =
        pageZoomed(scale, shift, factor, cx, cy, dx, dy, 1000f, 3f)

    @Test
    fun thePageUnderTheFingersStaysThereHorizontally() {
        val z = pages(1f, 0f, 2f, 400f, 0f)
        assertEquals(2f, z.scale, 1e-4f)
        // The point 400 pixels from the left edge was at 400 and is still at 400: 2 * 400 + shift = 400.
        assertEquals(-400f, z.shift, 1e-3f)
        assertEquals(400f, 2f * 400f + z.shift, 1e-3f)
    }

    @Test
    fun theListIsScrolledSoThatThePageUnderTheFingersStaysThereVertically() {
        // At the bottom of a screen 2000 pixels tall the list gets half as tall: the bottom edge has to stay where it is.
        val z = pages(1f, 0f, 2f, 0f, 2000f)
        assertEquals(1000f, z.scroll, 1e-3f)
    }

    @Test
    fun movingFingersScrollTheListByWhatTheyMovedInItsOwnPixels() {
        val z = pages(2f, -300f, 1f, 0f, 0f, dx = -50f, dy = 100f)
        assertEquals(-350f, z.shift, 1e-3f)
        // Fingers moving down show what is above: the list scrolls back by 100 / 2.
        assertEquals(-50f, z.scroll, 1e-3f)
    }

    @Test
    fun theShiftStaysInsideTheEnlargedPages() {
        assertEquals(0f, pages(2f, 0f, 1f, 0f, 0f, dx = 700f).shift, 1e-3f)
        assertEquals(-1000f, pages(2f, -900f, 1f, 0f, 0f, dx = -700f).shift, 1e-3f)
    }

    @Test
    fun pagesAtScaleOneAreNotShifted() {
        val z = pages(2f, -500f, 0.25f, 300f, 800f)
        assertEquals(1f, z.scale, 1e-4f)
        assertEquals(0f, z.shift, 1e-3f)
    }

    @Test
    fun pagesStopAtTheMaximumScale() {
        assertEquals(3f, pages(1f, 0f, 50f, 0f, 0f).scale, 1e-4f)
    }

    @Test
    fun aChangeThatIsNotANumberLeavesThePagesAlone() {
        val z = pages(2f, -300f, 1.5f, Float.NaN, 0f)
        assertEquals(2f, z.scale, 1e-4f)
        assertEquals(-300f, z.shift, 1e-3f)
        assertEquals(0f, z.scroll, 1e-6f)
    }
}
