package app.fileviewer

import android.content.Context
import android.net.Uri
import android.os.Process
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.abs

/** The link between the PDF viewer and its search bar: the bar asks the viewer to scroll and where it is. */
class PdfHolder {
    /** Scrolls to a page (0 is the first one). The viewer sets it while a document is shown. */
    var jump: (Int) -> Unit = {}

    /**
     * Scrolls to a page and then down it to a place on it (a fraction of the height of the page), so that a match low on a
     * page that is taller than the screen is on the screen. The viewer sets it while a document is shown.
     */
    var reveal: suspend (Int, Float) -> Unit = { _, _ -> }

    /** The page at the top of the screen (0 is the first one). */
    var page: () -> Int = { 0 }
}

/** A page never gets more than this many characters into the search; more than that is a page of noise, not of text. */
private const val MAX_PAGE_CHARS = 200_000

/** Runs [block] at the priority of background work, so that reading the text never slows down scrolling or drawing of pages. */
private inline fun <T> atBackgroundPriority(block: () -> T): T {
    val old = Process.getThreadPriority(Process.myTid())
    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
    try {
        return block()
    } finally {
        Process.setThreadPriority(old)
    }
}

/**
 * Search inside one PDF file. The pages themselves are drawn by the system's renderer, which cannot tell what is written
 * on them, so their text is read separately by PdfBox, one page after another in the background. The pages read so far
 * are searched at once; [read] of [pages] tells how far the reading has got. Where the query stands on a page that is
 * shown is worked out separately, with the place of every letter, so that it can be marked on the page ([mark]).
 *
 * The state is changed from the main thread only (the reading itself runs on another thread and hands each page over).
 */
@Stable
class PdfSearch(context: Context, private val uri: Uri) {
    private val app = context.applicationContext
    private val lock = Mutex()

    @Volatile
    private var doc: PDDocument? = null
    private var stripper: PDFTextStripper? = null

    @Volatile
    private var closed = false
    private var texts: Array<String> = emptyArray()

    /** What is typed in the search field. */
    var query by mutableStateOf("")

    /** The number of pages of the file (zero until it has been opened) and how many of them have been read. */
    var pages by mutableIntStateOf(0)
        private set
    var read by mutableIntStateOf(0)
        private set

    /** True when the file cannot be searched at all (damaged, protected by a password, no memory for it). */
    var failed by mutableStateOf(false)
        private set

    /** True when at least one of the pages that were read has text on it. */
    var hasText by mutableStateOf(false)
        private set

    /** The pages that have the query, in order, and the one that is selected (an index into [hits], -1 for none). */
    var hits by mutableStateOf<List<PageHit>>(emptyList())
        private set
    var at by mutableIntStateOf(-1)
        private set

    /**
     * The query that the rectangles on the pages are for (empty when there is nothing to mark), and the rectangles worked
     * out so far, by page. They are worked out in the background for the pages that are on the screen ([mark]).
     */
    var marked by mutableStateOf("")
        private set
    private var marks by mutableStateOf<Map<Int, List<Mark>>>(emptyMap())

    /** The page that the bar has just gone to: the screen is scrolled to the first rectangle on it as soon as that is known (-1: none). */
    private var revealing = -1

    /** Scrolls the screen to a place on a page (the page, a fraction of its height). The search bar sets it. */
    var reveal: suspend (Int, Float) -> Unit = { _, _ -> }

    /** Opens the file and reads the text of the pages that were not read yet. Returns when all are read, or when it is cancelled. */
    suspend fun load() {
        if (failed || (pages > 0 && read >= pages)) return
        try {
            if (pages == 0) {
                val n = open()
                if (n <= 0) {
                    failed = true
                    return
                }
                texts = Array(n) { "" }
                pages = n
            }
            while (read < pages) {
                val i = read
                val t = pageText(i)
                texts[i] = t
                if (t.isNotEmpty()) hasText = true
                read = i + 1
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            failed = true
        }
    }

    /**
     * Looks for [q] on the pages as their text becomes available, and returns when every page has been looked at (or when
     * the query changes and this call is cancelled). The first page that has it is shown at once through [jump].
     */
    suspend fun match(q: String, jump: (Int) -> Unit) {
        hits = emptyList()
        at = -1
        marks = emptyMap()
        revealing = -1
        val needle = squash(q)
        marked = if (needle.length >= MIN_QUERY) needle else ""
        if (needle.length < MIN_QUERY) return
        val found = ArrayList<PageHit>()
        var next = 0
        var announced = false
        while (true) {
            if (failed) return
            val avail = read
            if (next < avail) {
                // A few dozen pages at a time, so that a long document never holds the main thread for long.
                val stop = minOf(avail, next + 40)
                while (next < stop) {
                    val hit = pageHit(next, texts[next], needle)
                    if (hit != null) found.add(hit)
                    next++
                }
                hits = found.toList()
                if (!announced && found.isNotEmpty()) {
                    announced = true
                    at = 0
                    revealing = found[0].page
                    jump(found[0].page)
                }
                yield()
            } else if (pages > 0 && next >= pages) {
                return
            } else {
                snapshotFlow { Pair(read, failed) }.first { it.first > next || it.second }
            }
        }
    }

    /** Goes to the next or the previous page that has the query, counted from the page [current] that is on the screen. */
    fun step(forward: Boolean, current: Int, jump: (Int) -> Unit) {
        val list = hits
        val i = stepHit(list.map { it.page }, current, forward)
        if (i >= 0) {
            at = i
            revealing = list[i].page
            jump(list[i].page)
        }
    }

    /** The rectangles to paint on page [page] for the query (none until they have been worked out). */
    fun marksOn(page: Int): List<Mark> = marks[page] ?: emptyList()

    /**
     * Works out where [forQuery] stands on page [page] (unless that is known already) so that it can be painted there,
     * and, when the bar has just gone to this page, scrolls the screen to the first rectangle on it. It runs in the
     * background and gives up when the query has changed in the meantime; a page that cannot be read gets no rectangles.
     */
    suspend fun mark(page: Int, forQuery: String) {
        if (forQuery.isEmpty() || forQuery != marked || closed) return
        val known = marks[page]
        val list: List<Mark> = if (known != null) {
            known
        } else {
            val read = withContext(Dispatchers.IO) {
                lock.withLock {
                    val d = doc
                    if (d == null || closed) null else atBackgroundPriority { findMarks(d, page, forQuery) }
                }
            }
            if (read == null || forQuery != marked || closed) return
            marks = marks + (page to read)
            read
        }
        if (revealing == page) {
            revealing = -1
            val top = list.firstOrNull()?.top
            if (top != null) reveal(page, top)
        }
    }

    /** Forgets the query and what was found (the bar was closed). The text that was read stays, in case the bar is opened again. */
    fun reset() {
        query = ""
        hits = emptyList()
        at = -1
        marked = ""
        marks = emptyMap()
        revealing = -1
    }

    /** Lets go of the file. Called when the document is closed. */
    fun close() {
        closed = true
        val d = doc
        doc = null
        stripper = null
        if (d != null) {
            // A page that is being read holds the lock, so the file is closed after it lets go.
            CoroutineScope(Dispatchers.IO).launch {
                lock.withLock { try { d.close() } catch (e: Throwable) { } }
            }
        }
    }

    private suspend fun open(): Int = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed) -1 else atBackgroundPriority { openNow() }
        }
    }

    /** Opens the file (or returns the page count of the file that is open already). Runs off the main thread, under the lock. */
    private fun openNow(): Int = try {
        val existing = doc
        if (existing != null) {
            existing.numberOfPages
        } else {
            PDFBoxResourceLoader.init(app)
            // A big file goes to a temporary file after the first few megabytes instead of filling the memory.
            val d = app.contentResolver.openInputStream(uri)!!.use {
                PDDocument.load(it, MemoryUsageSetting.setupMixed(24L * 1024L * 1024L))
            }
            if (closed) {
                d.close()
                -1
            } else {
                doc = d
                stripper = PDFTextStripper()
                d.numberOfPages
            }
        }
    } catch (e: Throwable) {
        -1
    }

    private suspend fun pageText(i: Int): String = withContext(Dispatchers.IO) {
        lock.withLock {
            val d = doc
            val s = stripper
            if (d == null || s == null || closed) "" else atBackgroundPriority { extract(s, d, i) }
        }
    }

    /** The text of one page in the searched form. A page that cannot be read counts as a page without text. */
    private fun extract(s: PDFTextStripper, d: PDDocument, i: Int): String = try {
        s.startPage = i + 1
        s.endPage = i + 1
        squash(s.getText(d)).take(MAX_PAGE_CHARS)
    } catch (e: Throwable) {
        ""
    }

    /**
     * Where [needle] stands on page [i], read again from the file with the place of every letter. Runs off the main
     * thread, under the lock. Whatever goes wrong, the page just gets no rectangles.
     */
    private fun findMarks(d: PDDocument, i: Int, needle: String): List<Mark> = try {
        val page = d.getPage(i)
        val rotation = page.getRotation()
        val box = page.getCropBox()
        // The page as it is shown: a page that is turned by a quarter has its sides the other way round.
        val turned = rotation == 90 || rotation == 270
        val width = if (turned) box.getHeight() else box.getWidth()
        val height = if (turned) box.getWidth() else box.getHeight()
        val letters = GeometryStripper(rotation)
        letters.startPage = i + 1
        letters.endPage = i + 1
        letters.getText(d)
        marksOf(letters.words, needle, width, height)
    } catch (e: Throwable) {
        emptyList()
    }
}

/**
 * A text extractor that keeps the place of every letter: instead of writing the words of a page out it collects them,
 * with their letters, in the order they are read.
 */
private class GeometryStripper(private val rotation: Int) : PDFTextStripper() {
    val words = ArrayList<Word>()

    override fun writeString(text: String, textPositions: List<TextPosition>) {
        val letters = ArrayList<Glyph>(textPositions.size)
        for (p in textPositions) {
            val g = glyphOf(p, rotation)
            if (g != null) letters.add(g)
        }
        if (letters.isNotEmpty()) words.add(Word(letters))
    }
}

/** How far a letter reaches above the line it stands on, and below it, as parts of its font size. */
private const val ABOVE_LINE = 0.78f
private const val BELOW_LINE = 0.22f

/**
 * The box of one letter on the page as it is shown, or null for a letter that does not run along the page (text that is
 * turned or runs down is not marked) or that has no usable size.
 */
private fun glyphOf(p: TextPosition, rotation: Int): Glyph? {
    val size = p.getFontSizeInPt()
    val advance = p.getWidthDirAdj()
    val x = p.getXDirAdj()
    val line = p.getYDirAdj()
    if (abs(p.getDir() - rotation) > 1f) return null
    if (!(size > 0f) || !(advance >= 0f) || x.isNaN() || line.isNaN()) return null
    return Glyph(p.getUnicode() ?: "", x, line - size * ABOVE_LINE, x + advance, line + size * BELOW_LINE)
}

/** What is painted on one page for the search: the rectangles, and whether this is the page that the search bar is at. */
class PageMarks(val marks: List<Mark>, val current: Boolean)

private val NO_MARKS = PageMarks(emptyList(), false)

/**
 * The rectangles to paint on page [page] of the document that [s] searches ([s] is null when there is no search). While
 * the page has the query, its rectangles are worked out in the background and appear as soon as they are known.
 */
@Composable
fun pdfMarksOf(s: PdfSearch?, page: Int): PageMarks {
    if (s == null) return NO_MARKS
    val query = s.marked
    val has = query.isNotEmpty() && s.hits.any { it.page == page }
    val current = has && s.hits.getOrNull(s.at)?.page == page
    LaunchedEffect(page, query, has, current) {
        if (has) s.mark(page, query)
    }
    val found = if (has) s.marksOn(page) else emptyList()
    return if (found.isEmpty()) NO_MARKS else PageMarks(found, current)
}

private val MARK_COLOR = Color(0x66FFD600)
private val CURRENT_MARK_COLOR = Color(0x99FF9100)

/** Paints the rectangles of [m] over what the element shows; they are fractions of its size. The first one on the page the bar is at stands out. */
fun Modifier.pdfMarks(m: PageMarks): Modifier =
    if (m.marks.isEmpty()) this else this.drawWithContent {
        drawContent()
        for (mark in m.marks) {
            drawRect(
                color = if (m.current && mark.first) CURRENT_MARK_COLOR else MARK_COLOR,
                topLeft = Offset(mark.left * size.width, mark.top * size.height),
                size = Size((mark.right - mark.left) * size.width, (mark.bottom - mark.top) * size.height)
            )
        }
    }

/**
 * Scrolls the list of pages to page [page] and then down it to the place [top] (a fraction of the height of the page), so
 * that the place shows in the upper part of the screen even when the page is taller than the screen. A list that the
 * person is scrolling by hand at the moment is left alone.
 */
suspend fun LazyListState.revealOnPage(page: Int, top: Float) {
    if (isScrollInProgress) return
    scrollToItem(page)
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == page } ?: return
    val offset = (top * item.size - layoutInfo.viewportSize.height * 0.3f).toInt()
    if (offset > 0) scrollToItem(page, offset)
}

/**
 * The search row under the top bar for a PDF: the query with the counter ("3/12"), previous / next / close, and below it
 * the piece of text around the match on the page the bar is at (tap it to go back to that page) or a message about
 * how the search is going. [h] scrolls the document.
 */
@Composable
fun PdfFindBar(s: PdfSearch, h: PdfHolder, close: () -> Unit) {
    val res = LocalContext.current.resources
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // The text of the pages is read while the bar is open. Closing the bar pauses that, opening it again carries on.
    LaunchedEffect(s) { s.load() }
    // The search starts a moment after the last key, and starts again when the query changes.
    LaunchedEffect(s.query) {
        delay(250)
        s.match(s.query) { page -> h.jump(page) }
    }
    DisposableEffect(s) { onDispose { s.reset() } }
    // After a jump to a page, the screen is scrolled to the first match on it as soon as the place of the match is known.
    DisposableEffect(s, h) {
        s.reveal = { page, top -> h.reveal(page, top) }
        onDispose { s.reveal = { _, _ -> } }
    }

    val more = s.pages == 0 || s.read < s.pages
    val state = findState(s.failed, s.query, s.hits.size, s.pages, s.read, s.hasText)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = s.query,
                onValueChange = { s.query = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text(stringResource(R.string.find_hint)) },
                trailingIcon = {
                    val counter = hitCounter(s.at, s.hits.size, more)
                    if (counter.isNotEmpty()) {
                        Text(counter, Modifier.padding(end = 14.dp), style = MaterialTheme.typography.labelMedium)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { s.step(true, h.page()) { p -> h.jump(p) } }),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent
                )
            )
            IconButton(onClick = { s.step(false, h.page()) { p -> h.jump(p) } }) {
                Ico(R.drawable.ic_chevron_up, stringResource(R.string.find_prev))
            }
            IconButton(onClick = { s.step(true, h.page()) { p -> h.jump(p) } }) {
                Ico(R.drawable.ic_chevron_down, stringResource(R.string.find_next))
            }
            IconButton(onClick = close) {
                Ico(R.drawable.ic_close, stringResource(R.string.find_close))
            }
        }
        if (state == FindState.HITS) {
            val hit = s.hits.getOrNull(s.at) ?: s.hits.firstOrNull()
            if (hit != null) {
                val markBackground = MaterialTheme.colorScheme.tertiaryContainer
                val markColor = MaterialTheme.colorScheme.onTertiaryContainer
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { h.jump(hit.page) }
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.pdf_find_where, hit.page + 1) + "  ·  " +
                            res.getQuantityString(R.plurals.pdf_find_matches, hit.count, hit.count),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        buildAnnotatedString {
                            append(hit.before)
                            withStyle(SpanStyle(background = markBackground, color = markColor, fontWeight = FontWeight.SemiBold)) {
                                append(hit.match)
                            }
                            append(hit.after)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            val message: String? = when (state) {
                FindState.FAILED -> stringResource(R.string.pdf_find_failed)
                FindState.TOO_SHORT -> stringResource(R.string.pdf_find_short)
                FindState.OPENING -> stringResource(R.string.pdf_find_opening)
                FindState.READING -> stringResource(R.string.pdf_find_reading, s.read, s.pages)
                FindState.READING_NONE -> stringResource(R.string.pdf_find_none_yet, s.read, s.pages)
                FindState.NO_TEXT -> stringResource(R.string.pdf_find_no_text)
                FindState.NONE -> stringResource(R.string.pdf_find_none)
                else -> null
            }
            if (message != null) {
                Text(
                    message,
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
