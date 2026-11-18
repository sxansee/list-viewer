package app.fileviewer

import java.text.Normalizer

/*
 * The pure part of the search inside a PDF: how the text of a page is prepared, how a page is checked for a query, which
 * page the "next" and "previous" buttons choose, and what the search bar says. It knows nothing about Android, so the unit
 * tests (app/src/test) run it directly. The part that reads the file and draws the bar is in PdfSearch.kt.
 */

/** Queries shorter than this are not searched: a single letter is on nearly every page. */
const val MIN_QUERY = 2

/**
 * A page that has the query: its number (0 is the first), how many times the query occurs on it, and a piece of the text
 * around the first occurrence, cut in three so that the query itself can be marked.
 */
class PageHit(val page: Int, val count: Int, val before: String, val match: String, val after: String)

private val SPACES = Regex("\\s+")

/**
 * A text in the form in which it is searched, for a page and for the query alike: ligatures and width variants are
 * unfolded (the "fi" ligature becomes two letters), soft hyphens are dropped, and every run of spaces and line breaks
 * becomes one space, so that a phrase that runs over the end of a line is still found.
 */
fun squash(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC).replace("\u00AD", "").replace(SPACES, " ").trim()

/**
 * Checks one page. [text] is the page as [squash] made it, [needle] is the query made the same way, and [around] is how
 * many characters of text are kept on each side of the first occurrence. Capital and small letters are the same.
 * Returns null when the page does not have the query.
 */
fun pageHit(page: Int, text: String, needle: String, around: Int = 40): PageHit? {
    if (needle.isEmpty() || text.length < needle.length) return null
    val first = text.indexOf(needle, 0, ignoreCase = true)
    if (first < 0) return null
    var count = 1
    var from = first + needle.length
    while (true) {
        val i = text.indexOf(needle, from, ignoreCase = true)
        if (i < 0) break
        count++
        from = i + needle.length
    }
    val end = first + needle.length
    // A half of a word is not shown: where the piece before starts (or the piece after ends) in the middle of a word,
    // that word is left out. Where the cut falls between two words, everything is kept.
    val a = maxOf(0, first - around)
    var before = text.substring(a, first)
    if (a > 0) {
        if (text[a - 1] != ' ') {
            val space = before.indexOf(' ')
            if (space >= 0) before = before.substring(space + 1)
        }
        before = "…$before"
    }
    val b = minOf(text.length, end + around)
    var after = text.substring(end, b)
    if (b < text.length) {
        if (text[b] != ' ') {
            val space = after.lastIndexOf(' ')
            if (space >= 0) after = after.substring(0, space)
        }
        after = "$after…"
    }
    return PageHit(page, count, before, text.substring(first, end), after)
}

/**
 * The hit that the "next" button ([forward]) or the "previous" one goes to, counted from the page that is on the screen
 * ([current]): the first page after it, or the last page before it. After the last page the first one comes (and the
 * other way round). [pages] are the pages that have the query, in order. The result is an index into [pages], or -1 when
 * there are none.
 */
fun stepHit(pages: List<Int>, current: Int, forward: Boolean): Int {
    if (pages.isEmpty()) return -1
    return if (forward) {
        val i = pages.indexOfFirst { it > current }
        if (i < 0) 0 else i
    } else {
        val i = pages.indexOfLast { it < current }
        if (i < 0) pages.lastIndex else i
    }
}

/** What the search bar has to say under the field. */
enum class FindState {
    /** The file cannot be searched at all. */
    FAILED,

    /** There are pages with the query: the piece of text around the selected one is shown. */
    HITS,

    /** One letter was typed. */
    TOO_SHORT,

    /** The file is being opened. */
    OPENING,

    /** The text of the pages is being read and no query is typed yet. */
    READING,

    /** The text of the pages is being read, nothing was found so far. */
    READING_NONE,

    /** Every page was read and none of them has any text (scans). */
    NO_TEXT,

    /** Every page was read and none has the query. */
    NONE,

    /** Nothing to say. */
    IDLE
}

/**
 * Chooses what the bar says. [hits] is the number of pages with the query so far, [pages] the number of pages of the file
 * (zero until it has been opened), [read] how many of them have been read, and [hasText] tells whether any page had text.
 */
fun findState(failed: Boolean, query: String, hits: Int, pages: Int, read: Int, hasText: Boolean): FindState {
    val typed = squash(query)
    return when {
        failed -> FindState.FAILED
        hits > 0 -> FindState.HITS
        typed.isNotEmpty() && typed.length < MIN_QUERY -> FindState.TOO_SHORT
        pages == 0 -> FindState.OPENING
        read < pages -> if (typed.isEmpty()) FindState.READING else FindState.READING_NONE
        !hasText -> FindState.NO_TEXT
        typed.isNotEmpty() -> FindState.NONE
        else -> FindState.IDLE
    }
}

/** The counter in the search field: "3/12", and "3/12+" while pages are still being read and more may come. */
fun hitCounter(at: Int, hits: Int, more: Boolean): String =
    if (hits <= 0) "" else "${at.coerceIn(0, hits - 1) + 1}/$hits" + (if (more) "+" else "")
