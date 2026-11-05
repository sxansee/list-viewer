package app.fileviewer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.atomic.AtomicBoolean

enum class Route { PDF, IMAGE, MEDIA, HTML, WEB }

enum class Cat(val title: String, val color: Color) {
    PDF("PDF-документ", Color(0xFFC62828)),
    DOC("Документ", Color(0xFF1565C0)),
    SHEET("Таблица", Color(0xFF2E7D32)),
    SLIDE("Презентация", Color(0xFFE65100)),
    IMG("Изображение", Color(0xFF6A1B9A)),
    MEDIA("Аудио или видео", Color(0xFFAD1457)),
    WEB("Веб-страница", Color(0xFF00838F)),
    TEXT("Текст или код", Color(0xFF546E7A)),
    BOOK("Книга", Color(0xFF6D4C41)),
    ZIP("Архив", Color(0xFFF9A825)),
    OTHER("Файл", Color(0xFF607D8B))
}

class Info(val name: String, val ext: String, val mime: String?, val route: Route, val cat: Cat)

private val TABLE = HashMap<String, Pair<Route, Cat>>().apply {
    fun add(r: Route, c: Cat, vararg exts: String) { for (e in exts) put(e, Pair(r, c)) }
    add(Route.PDF, Cat.PDF, "pdf")
    add(Route.IMAGE, Cat.IMG, "jpg", "jpeg", "png", "webp", "bmp", "heic", "heif", "avif")
    add(Route.WEB, Cat.IMG, "gif", "svg")
    add(Route.MEDIA, Cat.MEDIA, "mp4", "m4v", "mkv", "webm", "3gp", "mov", "mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav")
    add(Route.HTML, Cat.WEB, "html", "htm", "xhtml")
    add(Route.WEB, Cat.DOC, "docx", "doc", "dot", "dotx", "docm", "dotm", "odt", "rtf")
    add(Route.WEB, Cat.SHEET, "xlsx", "xlsm", "xlsb", "xls", "ods", "csv", "tsv")
    add(Route.WEB, Cat.SLIDE, "pptx", "ppt", "pps", "ppsx", "pptm", "odp")
    add(Route.WEB, Cat.BOOK, "epub", "fb2")
    add(Route.WEB, Cat.ZIP, "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")
    add(
        Route.WEB, Cat.TEXT, "txt", "md", "markdown", "json", "xml", "yml", "yaml", "ini", "cfg", "conf", "toml",
        "log", "srt", "vtt", "css", "js", "ts", "kt", "java", "py", "c", "cpp", "h", "cs", "go", "rs", "php",
        "rb", "sh", "bat", "sql", "tex", "gradle", "properties"
    )
}

fun nameOf(c: Context, u: Uri): String {
    val n = try {
        c.contentResolver.query(u, null, null, null, null)?.use {
            if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }
    } catch (e: Exception) { null }
    return n ?: u.lastPathSegment ?: "file"
}

fun mimeOf(ext: String): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

fun infoOf(c: Context, u: Uri): Info {
    val name = nameOf(c, u)
    val mime = try { c.contentResolver.getType(u) } catch (e: Exception) { null }
    var ext = name.substringAfterLast('.', "").lowercase()
    if (!TABLE.containsKey(ext)) {
        val m = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if (m != null && TABLE.containsKey(m)) ext = m
    }
    val t = TABLE[ext]
    return Info(name, ext, mime, t?.first ?: Route.WEB, t?.second ?: Cat.OTHER)
}

fun toast(c: Context, s: String) { Toast.makeText(c, s, Toast.LENGTH_LONG).show() }

fun openExternal(c: Context, u: Uri, mime: String) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(u, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val ch = Intent.createChooser(i, "Открыть в приложении")
        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(c, MainActivity::class.java)))
    try { c.startActivity(ch) } catch (e: Exception) { toast(c, "Не нашлось приложения, которое открывает этот файл") }
}

fun shareFile(c: Context, u: Uri, mime: String) {
    val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { c.startActivity(Intent.createChooser(i, null)) } catch (e: Exception) { toast(c, "Не удалось поделиться файлом") }
}

class Recents(c: Context) {
    private val p = c.getSharedPreferences("recents", Context.MODE_PRIVATE)
    fun list(): List<String> = (p.getString("l", "") ?: "").split("\n").filter { it.isNotBlank() }
    private fun save(l: List<String>) { p.edit().putString("l", l.joinToString("\n")).apply() }
    fun add(u: Uri) { val s = u.toString(); save((listOf(s) + list().filter { it != s }).take(40)) }
    fun remove(s: String) { save(list().filter { it != s }) }
}

private val Light = lightColorScheme(
    primary = Color(0xFF0B6E6E), onPrimary = Color.White, primaryContainer = Color(0xFFCFEDED),
    onPrimaryContainer = Color(0xFF002020), background = Color(0xFFF5F8F8), onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF5F8F8), onSurface = Color(0xFF161D1D), surfaceVariant = Color(0xFFDDE5E5),
    onSurfaceVariant = Color(0xFF3F4948), outline = Color(0xFF6F7979)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF5CCFCF), onPrimary = Color(0xFF003737), primaryContainer = Color(0xFF004F4F),
    onPrimaryContainer = Color(0xFFCFEDED), background = Color(0xFF0E1515), onBackground = Color(0xFFDDE4E4),
    surface = Color(0xFF0E1515), onSurface = Color(0xFFDDE4E4), surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC8C8), outline = Color(0xFF899392)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

class MainActivity : ComponentActivity() {
    private var opened by mutableStateOf<Uri?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.action == Intent.ACTION_VIEW) opened = intent.data
        setContent { AppTheme { App(opened) { opened = it } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(uri: Uri?, setUri: (Uri?) -> Unit) {
    val ctx = LocalContext.current
    val recents = remember { Recents(ctx) }
    var list by remember { mutableStateOf(recents.list()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) {
            try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            setUri(u)
        }
    }
    LaunchedEffect(uri) { if (uri != null) { recents.add(uri); list = recents.list() } }
    if (uri != null) Viewer(uri) { setUri(null) } else Scaffold(
        topBar = { LargeTopAppBar(title = { Text("Недавние файлы") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Открыть файл") }
            )
        }
    ) { pad ->
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad).padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Откройте первый файл", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "PDF, Word, Excel, PowerPoint, HTML, книги, архивы, картинки, аудио и видео. Всё открывается прямо в приложении.",
                    color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list, key = { it }) { s ->
                    val u = remember(s) { Uri.parse(s) }
                    RecentRow(u, onOpen = { setUri(u) }, onRemove = { recents.remove(s); list = recents.list() })
                }
            }
        }
    }
}

@Composable
fun RecentRow(u: Uri, onOpen: () -> Unit, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    val info = remember(u) { infoOf(ctx, u) }
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(info.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(info.cat.title) },
        leadingContent = {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(info.cat.color.copy(alpha = 0.14f)),
                Alignment.Center
            ) {
                Text(
                    info.ext.ifEmpty { "?" }.uppercase().take(4),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = info.cat.color
                )
            }
        },
        trailingContent = { IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Убрать из списка") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Viewer(uri: Uri, back: () -> Unit) {
    val ctx = LocalContext.current
    val info = remember(uri) { infoOf(ctx, uri) }
    val holder = remember(uri) { WebHolder() }
    val net = remember(uri) { AtomicBoolean(false) }
    var netOn by remember(uri) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf("") }
    val webLike = info.route == Route.WEB || info.route == Route.HTML
    val mime = info.mime ?: mimeOf(info.ext)
    BackHandler {
        val w = holder.view
        if (webLike && w != null && w.canGoBack()) w.goBack() else back()
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(info.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            actions = {
                if (webLike) {
                    IconButton(onClick = { finding = !finding }) { Icon(Icons.Default.Search, "Поиск по документу") }
                }
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Ещё") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Открыть в другом приложении") },
                        onClick = { menu = false; openExternal(ctx, uri, mime) }
                    )
                    DropdownMenuItem(
                        text = { Text("Поделиться") },
                        onClick = { menu = false; shareFile(ctx, uri, mime) }
                    )
                    if (info.route == Route.HTML) {
                        DropdownMenuItem(
                            text = { Text(if (netOn) "Не загружать из интернета" else "Загружать из интернета") },
                            onClick = { menu = false; netOn = !netOn; net.set(netOn); holder.view?.reload() }
                        )
                    }
                }
            }
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (finding && webLike) FindBar(holder, found) { finding = false; found = "" }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (info.route) {
                    Route.PDF -> PdfView(uri)
                    Route.IMAGE -> ZoomImage(uri)
                    Route.MEDIA -> MediaView(uri)
                    Route.HTML, Route.WEB -> WebDoc(uri, info, net, holder) { found = it }
                }
            }
        }
    }
}
