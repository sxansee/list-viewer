package app.fileviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMathTest {
    @Test
    fun speedButtonGoesRoundTheList() {
        assertEquals(1f, nextSpeed(0.75f))
        assertEquals(1.25f, nextSpeed(1f))
        assertEquals(1.5f, nextSpeed(1.25f))
        assertEquals(2f, nextSpeed(1.5f))
        // After the last speed the first one comes again.
        assertEquals(0.75f, nextSpeed(2f))
    }

    @Test
    fun aSpeedThatIsNotInTheListBecomesNormal() {
        assertEquals(1f, nextSpeed(1.1f))
        assertEquals(1f, nextSpeed(3f))
        assertEquals(1f, nextSpeed(0f))
    }

    @Test
    fun aSpeedThatIsAHairOffStillCounts() {
        // Players report the speed they were given, but a float can come back a little different.
        assertEquals(1.25f, nextSpeed(1.0000001f))
        assertEquals(0.75f, nextSpeed(1.9999999f))
    }

    @Test
    fun everySpeedHasAShortLabel() {
        assertEquals("0.75×", speedLabel(0.75f))
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.25×", speedLabel(1.25f))
        assertEquals("1.5×", speedLabel(1.5f))
        assertEquals("2×", speedLabel(2f))
        assertEquals("0.5×", speedLabel(0.5f))
        assertEquals("10×", speedLabel(10f))
    }

    @Test
    fun theListOfSpeedsHasNormalSpeedAndNoDuplicates() {
        assertTrue(SPEEDS.contains(1f))
        assertEquals(SPEEDS.size, SPEEDS.toSet().size)
        assertEquals(SPEEDS.sorted(), SPEEDS)
        // Going round the whole list comes back to where it started.
        var s = SPEEDS.first()
        repeat(SPEEDS.size) { s = nextSpeed(s) }
        assertEquals(SPEEDS.first(), s)
    }

    @Test
    fun clockShowsMinutesAndSeconds() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:00", formatClock(999))
        assertEquals("0:01", formatClock(1000))
        assertEquals("0:59", formatClock(59_999))
        assertEquals("1:00", formatClock(60_000))
        assertEquals("3:07", formatClock(187_000))
        assertEquals("59:59", formatClock(3_599_999))
    }

    @Test
    fun clockShowsHoursOnlyWhenThereAreSome() {
        assertEquals("1:00:00", formatClock(3_600_000))
        assertEquals("1:02:09", formatClock(3_729_000))
        assertEquals("10:00:00", formatClock(36_000_000))
    }

    @Test
    fun clockNeverGoesNegative() {
        assertEquals("0:00", formatClock(-1))
        assertEquals("0:00", formatClock(-5_000_000))
        assertEquals("0:00", formatClock(Long.MIN_VALUE))
    }

    @Test
    fun jumpsStayInsideTheRecording() {
        assertEquals(15_000L, seekBy(5_000, 10_000, 60_000))
        assertEquals(0L, seekBy(5_000, -10_000, 60_000))
        assertEquals(0L, seekBy(0, -10_000, 60_000))
        assertEquals(60_000L, seekBy(55_000, 10_000, 60_000))
        assertEquals(60_000L, seekBy(60_000, 10_000, 60_000))
        assertEquals(50_000L, seekBy(60_000, -10_000, 60_000))
    }

    @Test
    fun jumpsWithoutAKnownLengthHaveNoEnd() {
        assertEquals(15_000L, seekBy(5_000, 10_000, 0))
        assertEquals(15_000L, seekBy(5_000, 10_000, -1))
        // The player's "unknown" is a huge negative number.
        assertEquals(15_000L, seekBy(5_000, 10_000, Long.MIN_VALUE + 1))
        assertEquals(0L, seekBy(5_000, -10_000, 0))
    }

    @Test
    fun aRecordingHeardToTheEndStartsOverNextTime() {
        assertEquals(10_000L, restingPlace(10_000, 60_000))
        assertEquals(56_999L, restingPlace(56_999, 60_000))
        assertEquals(0L, restingPlace(57_000, 60_000))
        assertEquals(0L, restingPlace(60_000, 60_000))
        assertEquals(0L, restingPlace(65_000, 60_000))
    }

    @Test
    fun restingPlaceWithoutAKnownLength() {
        assertEquals(10_000L, restingPlace(10_000, 0))
        assertEquals(10_000L, restingPlace(10_000, -1))
        assertEquals(10_000L, restingPlace(10_000, Long.MIN_VALUE + 1))
        assertEquals(0L, restingPlace(-5, 0))
        assertEquals(0L, restingPlace(-5, 60_000))
    }

    @Test
    fun theSkipStepIsTenSeconds() {
        assertEquals(10_000L, SKIP_MS)
    }
}
