package app.fileviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchLogicTest {
    @Test
    fun spacesAndLineBreaksBecomeOneSpace() {
        assertEquals("a b c", squash("a\n  b\tc"))
        assertEquals("a b", squash("  a \r\n\r\n b  "))
        assertEquals("", squash("   \n\t "))
        assertEquals("", squash(""))
    }

    @Test
    fun ligaturesAndWidthVariantsAreUnfolded() {
        assertEquals("find", squash("ﬁnd"))
        assertEquals("office", squash("oﬃce"))
        assertEquals("ABC 123", squash("ＡＢＣ １２３"))
        // A no-break space is an ordinary space once the text is unfolded.
        assertEquals("a b", squash("a b"))
    }

    @Test
    fun softHyphensAreDropped() {
        assertEquals("cooperate", squash("co­operate"))
        assertEquals("", squash("­"))
    }

    @Test
    fun cyrillicTextIsKeptAsItIs() {
        assertEquals("Договор поставки", squash("Договор \n поставки"))
        // "й" and "ё" are the same letters whether they are one character or two.
        assertEquals(squash("й"), squash("й"))
        assertEquals(squash("ё"), squash("ё"))
    }

    @Test
    fun noPageIsReportedWhenThereIsNothingToFind() {
        assertNull(pageHit(0, "some text", ""))
        assertNull(pageHit(0, "", "text"))
        assertNull(pageHit(0, "ab", "abc"))
        assertNull(pageHit(0, "some text", "other"))
    }

    @Test
    fun capitalAndSmallLettersAreTheSame() {
        val hit = pageHit(3, "The Quick Brown Fox", "quick")
        assertNotNull(hit)
        hit!!
        assertEquals(3, hit.page)
        assertEquals(1, hit.count)
        // The piece that is shown has the letters the page has, not the ones that were typed.
        assertEquals("Quick", hit.match)
        assertEquals("The ", hit.before)
        assertEquals(" Brown Fox", hit.after)
        assertNotNull(pageHit(0, "ПРИВЕТ, мир", "привет"))
    }

    @Test
    fun everyOccurrenceIsCounted() {
        val hit = pageHit(0, "to be or not to be, to BE", "be")!!
        assertEquals(3, hit.count)
        // The piece is cut around the first one.
        assertEquals("to ", hit.before)
        assertEquals("be", hit.match)
    }

    @Test
    fun occurrencesDoNotOverlap() {
        assertEquals(2, pageHit(0, "aaaa", "aa")!!.count)
        assertEquals(1, pageHit(0, "aaa", "aa")!!.count)
    }

    @Test
    fun theWholeQueryCanBeTheWholePage() {
        val hit = pageHit(7, "needle", "needle")!!
        assertEquals(7, hit.page)
        assertEquals(1, hit.count)
        assertEquals("", hit.before)
        assertEquals("needle", hit.match)
        assertEquals("", hit.after)
    }

    @Test
    fun aLongTextIsCutAtWordsWithDots() {
        val text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau upsilon"
        // 22 characters back would start in the middle of "epsilon" and 22 forward would end in the middle of "omicron":
        // the halves of words are left out.
        val hit = pageHit(0, text, "kappa", around = 22)!!
        assertEquals("kappa", hit.match)
        assertEquals("…zeta eta theta iota ", hit.before)
        assertEquals(" lambda mu nu xi…", hit.after)
    }

    @Test
    fun aCutBetweenTwoWordsKeepsBothOfThem() {
        val text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau upsilon"
        // 20 back starts exactly where "zeta" starts, and 16 forward ends exactly where "xi" ends.
        val back = pageHit(0, text, "kappa", around = 20)!!
        assertEquals("…zeta eta theta iota ", back.before)
        val forward = pageHit(0, text, "kappa", around = 16)!!
        assertEquals(" lambda mu nu xi…", forward.after)
        // 16 back ends in a space of the cut word "zeta", so the whole word is left out.
        assertEquals("…eta theta iota ", forward.before)
    }

    @Test
    fun theTextAtTheStartAndEndOfThePageIsNotMarkedAsCut() {
        val text = "alpha beta gamma delta epsilon zeta eta"
        val first = pageHit(0, text, "beta", around = 100)!!
        assertEquals("alpha ", first.before)
        assertEquals(" gamma delta epsilon zeta eta", first.after)
        // Exactly as much text as is wanted on each side: nothing is cut, so there are no dots.
        val exact = pageHit(0, text, "gamma", around = 23)!!
        assertEquals("alpha beta ", exact.before)
        assertEquals(" delta epsilon zeta eta", exact.after)
        // One character less, and the last word is cut in the middle: it goes, and the dots show that there was more.
        val short = pageHit(0, text, "gamma", around = 22)!!
        assertEquals(" delta epsilon zeta…", short.after)
    }

    @Test
    fun nothingIsCutWhenTheTextIsShort() {
        val hit = pageHit(0, "one two three", "two", around = 40)!!
        assertEquals("one ", hit.before)
        assertEquals(" three", hit.after)
    }

    @Test
    fun aCutWithoutAnySpaceKeepsWhatItHas() {
        val text = "x".repeat(100) + "needle" + "y".repeat(100)
        val hit = pageHit(0, text, "needle", around = 10)!!
        assertEquals("…" + "x".repeat(10), hit.before)
        assertEquals("y".repeat(10) + "…", hit.after)
    }

    @Test
    fun nextAndPreviousGoThroughThePagesThatHaveTheQuery() {
        val pages = listOf(2, 5, 9)
        // From the page on the screen: the first page after it.
        assertEquals(0, stepHit(pages, 0, true))
        assertEquals(1, stepHit(pages, 2, true))
        assertEquals(2, stepHit(pages, 5, true))
        assertEquals(1, stepHit(pages, 3, true))
        // After the last one the first comes again.
        assertEquals(0, stepHit(pages, 9, true))
        assertEquals(0, stepHit(pages, 20, true))
        // Backwards: the last page before it.
        assertEquals(1, stepHit(pages, 9, false))
        assertEquals(0, stepHit(pages, 5, false))
        assertEquals(1, stepHit(pages, 8, false))
        assertEquals(2, stepHit(pages, 20, false))
        // Before the first one the last comes.
        assertEquals(2, stepHit(pages, 2, false))
        assertEquals(2, stepHit(pages, 0, false))
    }

    @Test
    fun aSingleHitIsAlwaysTheOneToGoTo() {
        val pages = listOf(4)
        assertEquals(0, stepHit(pages, 0, true))
        assertEquals(0, stepHit(pages, 4, true))
        assertEquals(0, stepHit(pages, 9, true))
        assertEquals(0, stepHit(pages, 0, false))
        assertEquals(0, stepHit(pages, 4, false))
        assertEquals(0, stepHit(pages, 9, false))
    }

    @Test
    fun noHitsMeansNoStep() {
        assertEquals(-1, stepHit(emptyList(), 3, true))
        assertEquals(-1, stepHit(emptyList(), 3, false))
    }

    @Test
    fun aFailedFileSaysSo() {
        assertEquals(FindState.FAILED, findState(true, "word", 0, 0, 0, false))
        assertEquals(FindState.FAILED, findState(true, "", 0, 10, 10, true))
        // Even pages that were found before the failure do not hide it.
        assertEquals(FindState.FAILED, findState(true, "word", 2, 10, 5, true))
    }

    @Test
    fun pagesThatWereFoundAreShownWhileTheRestIsStillRead() {
        assertEquals(FindState.HITS, findState(false, "word", 1, 10, 3, true))
        assertEquals(FindState.HITS, findState(false, "word", 1, 10, 10, true))
    }

    @Test
    fun oneLetterIsTooShort() {
        assertEquals(FindState.TOO_SHORT, findState(false, "a", 0, 10, 10, true))
        // That is said even while the file is still being opened.
        assertEquals(FindState.TOO_SHORT, findState(false, "a", 0, 0, 0, false))
        assertEquals(FindState.TOO_SHORT, findState(false, " a ", 0, 10, 3, true))
        // Spaces alone are not a query at all.
        assertEquals(FindState.OPENING, findState(false, "   ", 0, 0, 0, false))
    }

    @Test
    fun theBarSaysWhatIsGoingOn() {
        assertEquals(FindState.OPENING, findState(false, "", 0, 0, 0, false))
        assertEquals(FindState.OPENING, findState(false, "word", 0, 0, 0, false))
        assertEquals(FindState.READING, findState(false, "", 0, 10, 4, true))
        assertEquals(FindState.READING_NONE, findState(false, "word", 0, 10, 4, true))
        assertEquals(FindState.NONE, findState(false, "word", 0, 10, 10, true))
        assertEquals(FindState.IDLE, findState(false, "", 0, 10, 10, true))
    }

    @Test
    fun aScanHasNoTextToSearch() {
        assertEquals(FindState.NO_TEXT, findState(false, "word", 0, 10, 10, false))
        assertEquals(FindState.NO_TEXT, findState(false, "", 0, 10, 10, false))
        // While it is read, nobody knows yet.
        assertEquals(FindState.READING_NONE, findState(false, "word", 0, 10, 9, false))
    }

    @Test
    fun theCounterShowsWhereYouAre() {
        assertEquals("", hitCounter(0, 0, false))
        assertEquals("", hitCounter(0, 0, true))
        assertEquals("1/12", hitCounter(0, 12, false))
        assertEquals("3/12", hitCounter(2, 12, false))
        assertEquals("3/12+", hitCounter(2, 12, true))
        assertEquals("1/1", hitCounter(0, 1, false))
    }

    @Test
    fun theCounterNeverLeavesItsRange() {
        assertEquals("12/12", hitCounter(50, 12, false))
        assertEquals("1/12", hitCounter(-3, 12, false))
        assertEquals("", hitCounter(3, -1, true))
    }

    @Test
    fun theShortestQueryIsTwoLetters() {
        assertEquals(2, MIN_QUERY)
    }
}
