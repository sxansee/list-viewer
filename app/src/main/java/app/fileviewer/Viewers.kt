package app.fileviewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.webkit.WebViewAssetLoader
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean

class WebHolder { var view: WebView? = null }

private const val HOST = "appassets.androidplatform.net"

/* ---------- PDF ---------- */

@Composable
fun PdfView(uri: Uri) {
    val ctx = LocalContext.current
    var pdf by remember(uri) { mutableStateOf<PdfRenderer?>(null) }
    var err by remember(uri) { mutableStateOf(false) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var live by remember(uri) { mutableFloatStateOf(1f) }
    val lock = remember(uri) { Mutex() }
    val list = rememberLazyListState()
    val hs = rememberScrollState()
    val cur by remember { derivedStateOf { list.firstVisibleItemIndex + 1 } }
    DisposableEffect(uri) {
        var r: PdfRenderer? = null
        try {
            r = PdfRenderer(ctx.contentResolver.openFileDescriptor(uri, "r")!!)
            pdf = r
        } catch (e: Exception) { err = true }
        onDispose { pdf = null; try { r?.close() } catch (e: Exception) { } }
    }
    val p = pdf
    if (err) {
        Text("Не удалось открыть PDF. Возможно, файл повреждён или защищён паролем.", Modifier.padding(24.dp))
    } else if (p != null) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
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
                Box(Modifier.fillMaxSize().graphicsLayer(scaleX = live, scaleY = live).horizontalScroll(hs)) {
                    LazyColumn(
                        Modifier.width(with(d) { (base * zoom).toDp() }).fillMaxHeight(),
                        state = list,
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(p.pageCount) { i -> PdfPage(p, i, minOf((base * zoom).toInt(), 2000), lock) }
                    }
                }
                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f)
                ) {
                    Text(
                        "$cur / ${p.pageCount}", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun PdfPage(pdf: PdfRenderer, i: Int, w: Int, lock: Mutex) {
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
        } catch (e: Exception) { null }
        if (r != null) bmp = r
    }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    else Box(Modifier.fillMaxWidth().height(420.dp).background(MaterialTheme.colorScheme.surfaceVariant))
}

/* ---------- Картинки ---------- */

@Composable
fun ZoomImage(uri: Uri) {
    var s by remember { mutableFloatStateOf(1f) }
    var o by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = uri, contentDescription = null,
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { if (s > 1f) { s = 1f; o = Offset.Zero } else { s = 2.5f } })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    s = (s * zoom).coerceIn(1f, 8f)
                    o = if (s == 1f) Offset.Zero else o + pan
                }
            }
            .graphicsLayer(scaleX = s, scaleY = s, translationX = o.x, translationY = o.y),
        contentScale = ContentScale.Fit
    )
}

/* ---------- Аудио и видео ---------- */

@OptIn(UnstableApi::class)
@Composable
fun MediaView(uri: Uri) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                controllerShowTimeoutMs = 0
                controllerHideOnTouch = false
            }
        }
    )
}

/* ---------- Всё остальное: встроенный веб-просмотрщик ---------- */

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

@Composable
fun WebDoc(uri: Uri, info: Info, net: AtomicBoolean, holder: WebHolder, onFind: (String) -> Unit) {
    val dark = if (isSystemInDarkTheme()) 1 else 0
    DisposableEffect(uri) { onDispose { holder.view?.destroy(); holder.view = null } }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(c))
                .addPathHandler("/doc/") { p -> docResponse(c, uri, info, p) }
                .build()
            WebView(c).apply {
                holder.view = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = info.route == Route.HTML
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

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val s = request.url.scheme
                        if (request.url.host != HOST && (s == "http" || s == "https" || s == "mailto" || s == "tel")) {
                            try { c.startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (e: Exception) { }
                            return true
                        }
                        return false
                    }
                }
                val url = if (info.route == Route.HTML) "https://$HOST/doc/file"
                else "https://$HOST/assets/viewer.html?ext=${Uri.encode(info.ext)}&d=$dark"
                loadUrl(url)
            }
        }
    )
}

@Composable
fun FindBar(h: WebHolder, info: String, close: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = q,
            onValueChange = {
                q = it
                if (it.isEmpty()) h.view?.clearMatches() else h.view?.findAllAsync(it)
            },
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            placeholder = { Text("Найти в документе") },
            trailingIcon = { Text(info, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelMedium) }
        )
        IconButton(onClick = { h.view?.findNext(false) }) { Icon(Icons.Default.KeyboardArrowUp, "Предыдущее совпадение") }
        IconButton(onClick = { h.view?.findNext(true) }) { Icon(Icons.Default.KeyboardArrowDown, "Следующее совпадение") }
        IconButton(onClick = { h.view?.clearMatches(); close() }) { Icon(Icons.Default.Close, "Закрыть поиск") }
    }
}
