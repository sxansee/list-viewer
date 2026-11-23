package app.fileviewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean

/** The web view of a document. Besides showing it, it can tell how far the page is scrolled. */
class DocView(c: Context) : WebView(c) {
    /** The scroll position in thousandths of what can be scrolled: 0 at the top, 1000 at the bottom. */
    fun place(): Int {
        val room = computeVerticalScrollRange() - computeVerticalScrollExtent()
        return if (room > 24) (scrollY.toLong() * 1000L / room).toInt().coerceIn(0, 1000) else 0
    }
}

class WebHolder { var view: DocView? = null }

/**
 * The first web view of a process takes a few hundred milliseconds, because the system has to load its browser engine.
 * One is made once, a moment after the app has started and while it is idle, and thrown away: the engine stays loaded, so
 * the first document does not make the screen stall while it slides in.
 */
fun warmUpWebView(c: Context) {
    Handler(Looper.getMainLooper()).postDelayed({
        Looper.myQueue().addIdleHandler {
            try {
                WebView(c).destroy()
            } catch (e: Throwable) {
                // No web view on this device: documents that need one say so when they are opened.
            }
            false
        }
    }, 1500L)
}

private const val HOST = "appassets.androidplatform.net"

/* ---------- PDF ---------- */

/**
 * Inverts the lightness of a page and turns the hues back by half a circle, so that white paper goes dark and black
 * text goes light, while photographs and colored charts stay close to their own colors.
 */
private val DARK_PAGE: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.574f, -1.430f, -0.144f, 0f, 255f,
            -0.426f, -0.430f, -0.144f, 0f, 255f,
            -0.426f, -1.430f, 0.856f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

/** Opens a document for drawing, or returns null when it cannot be read (damaged, protected by a password, gone). */
private fun openPdf(ctx: Context, uri: Uri): PdfRenderer? {
    var fd: ParcelFileDescriptor? = null
    return try {
        fd = ctx.contentResolver.openFileDescriptor(uri, "r")
        PdfRenderer(fd!!)
    } catch (e: Exception) {
        // The renderer owns the descriptor only once it has been created.
        try { fd?.close() } catch (x: Exception) { }
        null
    } catch (e: OutOfMemoryError) {
        try { fd?.close() } catch (x: Exception) { }
        null
    }
}

/**
 * A PDF as a column of pages. [start] is the page to open at (where reading stopped), [onPage] is told the page at the
 * top of the screen when scrolling stops and when the document is closed, and [dark] shows the pages with inverted colors.
 * [holder] lets the search bar scroll the document to a page, and [search] (null when there is none) gives the places of the
 * words that are found on the pages, which are marked.
 */
@Composable
fun PdfView(uri: Uri, start: Int, dark: Boolean, holder: PdfHolder, search: PdfSearch?, onPage: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val save = rememberUpdatedState(onPage)
    var pdf by remember(uri) { mutableStateOf<PdfRenderer?>(null) }
    var err by remember(uri) { mutableStateOf(false) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var live by remember(uri) { mutableFloatStateOf(1f) }
    var pill by remember(uri) { mutableStateOf(true) }
    val lock = remember(uri) { Mutex() }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val hs = rememberScrollState()
    val cur by remember { derivedStateOf { list.firstVisibleItemIndex + 1 } }
    // The page counter is shown while the document is being scrolled and fades out a moment later.
    LaunchedEffect(list.isScrollInProgress) {
        if (list.isScrollInProgress) {
            pill = true
        } else {
            save.value(list.firstVisibleItemIndex)
            delay(1500)
            pill = false
        }
    }
    DisposableEffect(uri) {
        // A big file takes a while to open, so that happens away from the main thread and the pages appear when it is done.
        val opening = CoroutineScope(Dispatchers.IO).async { openPdf(ctx, uri) }
        val waiting = scope.launch {
            val r = opening.await()
            if (r != null) pdf = r else err = true
        }
        onDispose {
            waiting.cancel()
            pdf = null
            save.value(list.firstVisibleItemIndex)
            // A page that is still being drawn holds the lock, so the renderer is closed after it lets go.
            CoroutineScope(Dispatchers.IO).launch {
                val done = try { opening.await() } catch (e: Throwable) { null }
                lock.withLock { try { done?.close() } catch (e: Exception) { } }
            }
        }
    }
    // The search bar scrolls the document to a page through the holder.
    DisposableEffect(holder) {
        holder.jump = { page -> scope.launch { list.scrollToItem(page.coerceAtLeast(0)) } }
        holder.reveal = { page, top -> list.revealOnPage(page.coerceAtLeast(0), top) }
        holder.page = { list.firstVisibleItemIndex }
        onDispose {
            holder.jump = {}
            holder.reveal = { _, _ -> }
            holder.page = { 0 }
        }
    }
    val p = pdf
    if (err) {
        MessageState(
            iconRes = R.drawable.ic_warning,
            title = stringResource(R.string.pdf_error_title),
            body = stringResource(R.string.pdf_error_body)
        ) {
            OutlinedButton(onClick = { openExternal(ctx, uri, "application/pdf") }) {
                Text(stringResource(R.string.action_open_external))
            }
        }
    } else if (p != null) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            val d = LocalDensity.current
            val base = with(d) { maxWidth.roundToPx() }
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = if (zoom > 1.05f) 1f else 2f }) }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.changes.size > 1) {
                                    live = (live * e.calculateZoom()).coerceIn(1f / zoom, 3f / zoom)
                                    e.changes.forEach { it.consume() }
                                }
                            } while (e.changes.any { it.pressed })
                            if (live != 1f) { zoom = (zoom * live).coerceIn(1f, 3f); live = 1f }
                        }
                    }
            ) {
                // The pinch scale is read inside the layer, so a pinch redraws the page without composing it again.
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX = live; scaleY = live }.horizontalScroll(hs)) {
                    LazyColumn(
                        Modifier.width(with(d) { (base * zoom).toDp() }).fillMaxHeight(),
                        state = list,
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(p.pageCount) { i -> PdfPage(p, i, minOf((base * zoom).toInt(), 2000), lock, dark, pdfMarksOf(search, i)) }
                    }
                }
                AnimatedVisibility(
                    visible = pill,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f)
                    ) {
                        Text(
                            stringResource(R.string.page_counter, cur, p.pageCount),
                            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPage(pdf: PdfRenderer, i: Int, w: Int, lock: Mutex, dark: Boolean, marks: PageMarks) {
    var bmp by remember(i) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(i, w) {
        val r = try {
            withContext(Dispatchers.IO) {
                lock.withLock {
                    val pg = pdf.openPage(i)
                    try {
                        val h = (w.toFloat() * pg.height / pg.width).toInt().coerceAtLeast(1)
                        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        b.eraseColor(android.graphics.Color.WHITE)
                        pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        b
                    } finally { pg.close() }
                }
            }
        } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
        if (r != null) bmp = r
    }
    val b = bmp
    val page = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(3.dp))
    if (b != null) Image(b.asImageBitmap(), null, page.pdfMarks(marks), contentScale = ContentScale.FillWidth, colorFilter = if (dark) DARK_PAGE else null)
    else Box(page.height(420.dp).background(if (dark) Color.Black else Color.White))
}

/* ---------- Images ---------- */

@Composable
fun ZoomImage(uri: Uri, mime: String) {
    val ctx = LocalContext.current
    var s by remember(uri) { mutableFloatStateOf(1f) }
    var o by remember(uri) { mutableStateOf(Offset.Zero) }
    var failed by remember(uri) { mutableStateOf(false) }
    if (failed) {
        MessageState(
            iconRes = R.drawable.ic_warning,
            title = stringResource(R.string.image_error_title),
            body = stringResource(R.string.image_error_body)
        ) {
            OutlinedButton(onClick = { openExternal(ctx, uri, mime) }) {
                Text(stringResource(R.string.action_open_external))
            }
        }
    } else {
        AsyncImage(
            model = uri, contentDescription = null,
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { if (s > 1f) { s = 1f; o = Offset.Zero } else { s = 2.5f } })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val ns = (s * zoom).coerceIn(1f, 8f)
                        // The picture can be moved only as far as its enlarged edges.
                        val mx = size.width * (ns - 1f) / 2f
                        val my = size.height * (ns - 1f) / 2f
                        s = ns
                        o = if (ns == 1f) Offset.Zero
                        else Offset((o.x + pan.x).coerceIn(-mx, mx), (o.y + pan.y).coerceIn(-my, my))
                    }
                }
                // Scale and offset are read inside the layer: moving a finger redraws the picture without composing it again.
                .graphicsLayer {
                    scaleX = s
                    scaleY = s
                    translationX = o.x
                    translationY = o.y
                },
            contentScale = ContentScale.Fit,
            onError = { failed = true }
        )
    }
}

/* ---------- Everything else: the built-in web viewer ---------- */

private fun status(code: Int, why: String): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", code, why, emptyMap<String, String>(), ByteArrayInputStream(ByteArray(0)))

private fun sniffCharset(b: ByteArray): String {
    if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return "utf-8"
    if (b.size >= 2 && ((b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) || (b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()))) return "utf-16"
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(b))
        "utf-8"
    } catch (e: Exception) { "windows-1251" }
}

private fun docResponse(c: Context, uri: Uri, info: Info, path: String): WebResourceResponse? {
    if (path != "file") return status(404, "Not Found")
    return try {
        if (info.route == Route.HTML) {
            val b = c.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            WebResourceResponse(
                if (info.ext == "xhtml") "application/xhtml+xml" else "text/html",
                sniffCharset(b), ByteArrayInputStream(b)
            )
        } else {
            WebResourceResponse("application/octet-stream", null, c.contentResolver.openInputStream(uri))
        }
    } catch (e: Exception) { status(500, "Error") }
}

/** Puts a page at a place given in thousandths of what can be scrolled (the place is the argument of the function this makes). */
private const val SCROLL_TO =
    "(function(p){var e=document.scrollingElement||document.documentElement,r=e.scrollHeight-innerHeight;" +
        "if(r>0)scrollTo(0,Math.round(r*p/1000))})"

/**
 * A document shown in a web view: the built-in viewer page for most formats, the file itself for HTML.
 *
 * [start] is the place where reading stopped (thousandths of the page height) and [onPos] is told the place every
 * moment and when the document is closed. [zoom] is the size of text in percent. The place is saved only once the page
 * has been put at [start], so that leaving early never overwrites it with the top of the page.
 */
@Composable
fun WebDoc(
    uri: Uri,
    info: Info,
    net: AtomicBoolean,
    holder: WebHolder,
    start: Long,
    zoom: Int,
    onPos: (Long) -> Unit,
    onFind: (String) -> Unit
) {
    val dark = if (isSystemInDarkTheme()) 1 else 0
    val html = info.route == Route.HTML
    val save = rememberUpdatedState(onPos)
    val ready = remember(uri) { AtomicBoolean(false) }
    // The place that was saved last: writing it again when nothing has moved would only make the list do needless work.
    val last = remember(uri) { longArrayOf(start) }
    // "en" or "ru": the language of the messages inside the viewer page, taken from the same resources as the app UI.
    val lang = stringResource(R.string.web_lang)
    // Seen only until the page paints, so a dark theme does not flash white.
    val bg = MaterialTheme.colorScheme.background.toArgb()
    // The page colours are CSS variables switched by the class "d" on <html>, so a theme change needs no reload.
    LaunchedEffect(dark) {
        if (info.route == Route.WEB) holder.view?.evaluateJavascript("document.documentElement.classList.toggle('d', $dark === 1)", null)
    }
    // The size of text follows the reading options at once: the viewer page scales its own text, a web page the browser does.
    LaunchedEffect(zoom) {
        val w = holder.view ?: return@LaunchedEffect
        if (html) {
            w.settings.textZoom = zoom
        } else {
            w.evaluateJavascript("window.__zoom&&window.__zoom($zoom)", null)
        }
    }
    // While the document is open its place is saved now and then (and once more when it is closed, below).
    LaunchedEffect(uri) {
        while (true) {
            delay(1500)
            val w = holder.view ?: continue
            if (ready.get()) {
                val at = w.place().toLong()
                if (at != last[0]) {
                    last[0] = at
                    save.value(at)
                }
            } else if (!html) {
                // The viewer page says when it has put the document at the saved place.
                w.evaluateJavascript("window.__posOk===true") { r -> if (r == "true") ready.set(true) }
            }
        }
    }
    DisposableEffect(uri) {
        onDispose {
            val w = holder.view
            if (w != null && ready.get()) {
                val at = w.place().toLong()
                if (at != last[0]) save.value(at)
            }
            w?.destroy()
            holder.view = null
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(c))
                .addPathHandler("/doc/") { p -> docResponse(c, uri, info, p) }
                .build()
            DocView(c).apply {
                holder.view = this
                setBackgroundColor(bg)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = html
                if (html) settings.textZoom = zoom
                setFindListener { i, n, _ -> onFind(if (n == 0) "0" else "${i + 1}/$n") }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val u = request.url
                        if (u.host == HOST) {
                            val r = loader.shouldInterceptRequest(u)
                            if (r != null && u.path?.endsWith(".wasm") == true) r.mimeType = "application/wasm"
                            return r
                        }
                        if ((u.scheme == "http" || u.scheme == "https") && !net.get()) {
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        // A web page is put at the saved place as soon as it has loaded; the viewer page does that itself.
                        if (html && !ready.get()) {
                            if (start > 0L) view.evaluateJavascript(SCROLL_TO + "(" + start + ")", null)
                            view.postDelayed({ ready.set(true) }, 600)
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val s = request.url.scheme
                        if (request.url.host != HOST && (s == "http" || s == "https" || s == "mailto" || s == "tel")) {
                            try { c.startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (e: Exception) { }
                            return true
                        }
                        return false
                    }
                }
                val url = if (html) "https://$HOST/doc/file"
                else "https://$HOST/assets/viewer.html?ext=${Uri.encode(info.ext)}&d=$dark&l=${Uri.encode(lang)}&z=$zoom&p=$start"
                loadUrl(url)
            }
        }
    )
}

/** The search row under the top bar: the query, the match counter ("3/12"), previous / next and close. */
@Composable
fun FindBar(h: WebHolder, info: String, close: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = q,
            onValueChange = {
                q = it
                if (it.isEmpty()) h.view?.clearMatches() else h.view?.findAllAsync(it)
            },
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text(stringResource(R.string.find_hint)) },
            trailingIcon = {
                if (q.isNotEmpty() && info.isNotEmpty()) {
                    Text(info, Modifier.padding(end = 14.dp), style = MaterialTheme.typography.labelMedium)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { h.view?.findNext(true) }),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent
            )
        )
        IconButton(onClick = { h.view?.findNext(false) }) {
            Ico(R.drawable.ic_chevron_up, stringResource(R.string.find_prev))
        }
        IconButton(onClick = { h.view?.findNext(true) }) {
            Ico(R.drawable.ic_chevron_down, stringResource(R.string.find_next))
        }
        IconButton(onClick = { h.view?.clearMatches(); close() }) {
            Ico(R.drawable.ic_close, stringResource(R.string.find_close))
        }
    }
}
