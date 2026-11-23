package app.fileviewer

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Marking the found words on the pages of a PDF. The search (SearchLogic.kt, PdfSearch.kt) only knows which pages have the
 * query; to paint it on a page the place of every letter is needed. This file works with those places and knows nothing
 * about Android, so the unit tests (app/src/test) run it directly: from the letters of a page and the query it works out
 * the rectangles to paint, as fractions of the width and the height of the page. Where the letters come from (PdfBox) and
 * how the rectangles are painted are in PdfSearch.kt.
 */

/** One letter (or a ligature) of a page with its box, in the units of the page and from its top left corner. */
class Glyph(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A word of a page as the text extractor cut it: its letters, in order. */
class Word(val glyphs: List<Glyph>)

/** A rectangle to paint on a page, in fractions (0 to 1) of its width and height; [first] is true for the first occurrence on the page. */
class Mark(val left: Float, val top: Float, val right: Float, val bottom: Float, val first: Boolean)

/** A page never gets more rectangles than this: a query that is on every line of a long page is not worth painting without end. */
const val MAX_MARKS = 400

/** A letter is taken for the same line when its centre is no further from the centre of the rectangle than this many heights of it. */
private const val SAME_LINE = 0.6f

/**
 * Where [needle] (the query in the form [squash] makes it) is on a page of [pageWidth] by [pageHeight] units whose [words]
 * are given in reading order. Capital and small letters are the same, a phrase may run over the end of a word or of a line
 * (it then gets one rectangle for each line), and ligatures count as the letters they stand for, as in the search itself.
 * The rectangles come in the order of the text; the ones of the first occurrence have [Mark.first] set.
 */
fun marksOf(words: List<Word>, needle: String, pageWidth: Float, pageHeight: Float): List<Mark> {
    if (needle.isEmpty() || !(pageWidth > 0f) || !(pageHeight > 0f)) return emptyList()

    // The text of the page in the searched form, and for every character of it the letter it came from (-1: a space).
    val text = StringBuilder()
    val owner = ArrayList<Int>()
    val glyphs = ArrayList<Glyph>()
    fun space() {
        if (text.isNotEmpty() && text[text.length - 1] != ' ') {
            text.append(' ')
            owner.add(-1)
        }
    }
    for (word in words) {
        space()
        for (g in word.glyphs) {
            val index = glyphs.size
            glyphs.add(g)
            for (c in unfolded(g.text)) {
                if (c.isWhitespace()) {
                    space()
                } else {
                    text.append(c)
                    owner.add(index)
                }
            }
        }
    }
    val flat = text.toString()

    val out = ArrayList<Mark>()
    var from = 0
    var first = true
    while (out.size < MAX_MARKS) {
        val at = flat.indexOf(needle, from, ignoreCase = true)
        if (at < 0) break
        val end = at + needle.length
        // The letters of the occurrence, each once and in order.
        val picked = ArrayList<Glyph>()
        var last = -1
        for (i in at until end) {
            val g = owner[i]
            if (g >= 0 && g != last) {
                picked.add(glyphs[g])
                last = g
            }
        }
        addLineMarks(picked, first, pageWidth, pageHeight, out)
        first = false
        from = end
    }
    return if (out.size > MAX_MARKS) out.subList(0, MAX_MARKS).toList() else out
}

/** The text of a letter as the search sees it: ligatures and width variants unfolded, soft hyphens dropped. */
private fun unfolded(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC).replace("­", "")

/** Puts the letters of one occurrence into rectangles, one for each line, and adds them to [out]. */
private fun addLineMarks(letters: List<Glyph>, first: Boolean, width: Float, height: Float, out: MutableList<Mark>) {
    var open = false
    var l = 0f
    var t = 0f
    var r = 0f
    var b = 0f
    for (g in letters) {
        if (open) {
            val sameLine = abs((g.top + g.bottom) / 2f - (t + b) / 2f) <= (b - t) * SAME_LINE
            val notBack = g.left >= l - (b - t)
            if (sameLine && notBack) {
                l = min(l, g.left)
                t = min(t, g.top)
                r = max(r, g.right)
                b = max(b, g.bottom)
                continue
            }
            addMark(l, t, r, b, first, width, height, out)
        }
        l = g.left
        t = g.top
        r = g.right
        b = g.bottom
        open = true
    }
    if (open) addMark(l, t, r, b, first, width, height, out)
}

/** Adds one rectangle, as fractions of the page and kept inside it; a rectangle with nothing left of it is not added. */
private fun addMark(l: Float, t: Float, r: Float, b: Float, first: Boolean, width: Float, height: Float, out: MutableList<Mark>) {
    val left = (l / width).coerceIn(0f, 1f)
    val top = (t / height).coerceIn(0f, 1f)
    val right = (r / width).coerceIn(0f, 1f)
    val bottom = (b / height).coerceIn(0f, 1f)
    if (!(right - left >= MIN_SIDE) || !(bottom - top >= MIN_SIDE)) return
    out.add(Mark(left, top, right, bottom, first))
}

/** A rectangle thinner than this fraction of the page (or one that is not a number) is not worth painting. */
private const val MIN_SIDE = 0.0001f
