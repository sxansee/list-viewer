package app.fileviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfMarksTest {
    /** A page 200 units wide and 100 high. */
    private val w = 200f
    private val h = 100f

    /** A word whose letters are 10 by 10 units, side by side from ([x], [y]). */
    private fun word(text: String, x: Float, y: Float): Word =
        Word(text.mapIndexed { i, c -> Glyph(c.toString(), x + i * 10f, y, x + (i + 1) * 10f, y + 10f) })

    @Test
    fun aWordIsMarkedWhereItStands() {
        val marks = marksOf(listOf(word("hello", 0f, 0f), word("world", 60f, 0f)), "world", w, h)
        assertEquals(1, marks.size)
        assertEquals(0.3f, marks[0].left, 1e-6f)
        assertEquals(0.55f, marks[0].right, 1e-6f)
        assertEquals(0f, marks[0].top, 1e-6f)
        assertEquals(0.1f, marks[0].bottom, 1e-6f)
        assertTrue(marks[0].first)
    }

    @Test
    fun capitalAndSmallLettersAreTheSame() {
        assertEquals(1, marksOf(listOf(word("World", 0f, 0f)), "wORLD", w, h).size)
    }

    @Test
    fun aPhraseOverSeveralWordsOfOneLineIsOneRectangle() {
        val marks = marksOf(listOf(word("hello", 0f, 0f), word("world", 60f, 0f)), "hello world", w, h)
        assertEquals(1, marks.size)
        assertEquals(0f, marks[0].left, 1e-6f)
        assertEquals(0.55f, marks[0].right, 1e-6f)
    }

    @Test
    fun aPhraseOverTheEndOfALineGetsOneRectangleForEachLine() {
        val marks = marksOf(listOf(word("hello", 0f, 0f), word("world", 0f, 20f)), "hello world", w, h)
        assertEquals(2, marks.size)
        assertEquals(0f, marks[0].top, 1e-6f)
        assertEquals(0.2f, marks[1].top, 1e-6f)
        assertEquals(0.25f, marks[0].right, 1e-6f)
        // Both belong to the same occurrence.
        assertTrue(marks[0].first && marks[1].first)
    }

    @Test
    fun aLigatureStandsForTheLettersItIs() {
        val fish = Word(listOf(Glyph("ﬁ", 0f, 0f, 12f, 10f), Glyph("s", 12f, 0f, 20f, 10f), Glyph("h", 20f, 0f, 28f, 10f)))
        val whole = marksOf(listOf(fish), "fish", w, h)
        assertEquals(1, whole.size)
        assertEquals(0.14f, whole[0].right, 1e-6f)
        // The query "fi" is the first glyph alone.
        val part = marksOf(listOf(fish), "fi", w, h)
        assertEquals(1, part.size)
        assertEquals(0.06f, part[0].right, 1e-6f)
    }

    @Test
    fun onlyTheFirstOccurrenceIsMarkedFirst() {
        val words = listOf(word("ab", 0f, 0f), word("ab", 40f, 0f), word("ab", 0f, 30f))
        val marks = marksOf(words, "ab", w, h)
        assertEquals(3, marks.size)
        assertTrue(marks[0].first)
        assertFalse(marks[1].first)
        assertFalse(marks[2].first)
    }

    @Test
    fun nothingIsMarkedWhenThereIsNothingToFind() {
        val words = listOf(word("hello", 0f, 0f))
        assertTrue(marksOf(words, "xyz", w, h).isEmpty())
        assertTrue(marksOf(words, "", w, h).isEmpty())
        assertTrue(marksOf(emptyList(), "hello", w, h).isEmpty())
    }

    @Test
    fun aSoftHyphenInTheMiddleOfAWordDoesNotStopTheMatch() {
        val letters = listOf(
            Glyph("c", 0f, 0f, 10f, 10f),
            Glyph("o", 10f, 0f, 20f, 10f),
            Glyph("­", 20f, 0f, 25f, 10f),
            Glyph("o", 25f, 0f, 35f, 10f),
            Glyph("p", 35f, 0f, 45f, 10f)
        )
        val marks = marksOf(listOf(Word(letters)), "coop", w, h)
        assertEquals(1, marks.size)
        assertEquals(0.225f, marks[0].right, 1e-6f)
    }

    @Test
    fun aSpaceInsideAWordIsASpace() {
        val letters = listOf(Glyph("a", 0f, 0f, 10f, 10f), Glyph(" ", 10f, 0f, 15f, 10f), Glyph("b", 15f, 0f, 25f, 10f))
        assertEquals(1, marksOf(listOf(Word(letters)), "a b", w, h).size)
        assertTrue(marksOf(listOf(Word(letters)), "ab", w, h).isEmpty())
    }

    @Test
    fun rectanglesStayInsideThePage() {
        val marks = marksOf(listOf(Word(listOf(Glyph("a", -5f, -5f, 5f, 5f), Glyph("b", 195f, 95f, 215f, 120f)))), "a", w, h)
        assertEquals(1, marks.size)
        assertEquals(0f, marks[0].left, 1e-6f)
        assertEquals(0f, marks[0].top, 1e-6f)
        val edge = marksOf(listOf(Word(listOf(Glyph("b", 195f, 95f, 215f, 120f)))), "b", w, h)
        assertEquals(1f, edge[0].right, 1e-6f)
        assertEquals(1f, edge[0].bottom, 1e-6f)
    }

    @Test
    fun aLetterWithNoSizeIsNotMarked() {
        assertTrue(marksOf(listOf(Word(listOf(Glyph("a", 10f, 10f, 10f, 20f)))), "a", w, h).isEmpty())
        assertTrue(marksOf(listOf(Word(listOf(Glyph("a", Float.NaN, 10f, 20f, 20f)))), "a", w, h).isEmpty())
    }

    @Test
    fun aPageWithoutASizeHasNoMarks() {
        assertTrue(marksOf(listOf(word("a", 0f, 0f)), "a", 0f, h).isEmpty())
        assertTrue(marksOf(listOf(word("a", 0f, 0f)), "a", w, Float.NaN).isEmpty())
    }

    @Test
    fun wordsThatRunBackOnTheLineAreSeparateRectangles() {
        // The second word of the phrase stands to the left of the first (another column).
        val marks = marksOf(listOf(word("ab", 100f, 0f), word("cd", 0f, 0f)), "ab cd", w, h)
        assertEquals(2, marks.size)
    }

    @Test
    fun aPageGetsNoMoreThanTheLimit() {
        val words = (0 until 1000).map { i -> word("aa", (i % 10) * 20f, (i / 10) * 12f) }
        val marks = marksOf(words, "aa", 400f, 2000f)
        assertEquals(MAX_MARKS, marks.size)
    }
}
