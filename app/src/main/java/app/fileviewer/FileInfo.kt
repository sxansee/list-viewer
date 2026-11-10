package app.fileviewer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import java.io.File

/** How a file is shown: natively (PDF, image, media) or in the built-in web viewer. */
enum class Route { PDF, IMAGE, MEDIA, HTML, WEB }

/** File family: drives the label, the colour of the file tile and the filter chips on the home screen. */
enum class Cat(val titleRes: Int, val chipRes: Int, val color: Color) {
    PDF(R.string.cat_pdf, R.string.chip_pdf, Color(0xFFC62828)),
    DOC(R.string.cat_doc, R.string.chip_doc, Color(0xFF1565C0)),
    SHEET(R.string.cat_sheet, R.string.chip_sheet, Color(0xFF2E7D32)),
    SLIDE(R.string.cat_slide, R.string.chip_slide, Color(0xFFE65100)),
    IMG(R.string.cat_img, R.string.chip_img, Color(0xFF6A1B9A)),
    MEDIA(R.string.cat_media, R.string.chip_media, Color(0xFFAD1457)),
    WEB(R.string.cat_web, R.string.chip_web, Color(0xFF00838F)),
    TEXT(R.string.cat_text, R.string.chip_text, Color(0xFF546E7A)),
    BOOK(R.string.cat_book, R.string.chip_book, Color(0xFF6D4C41)),
    ZIP(R.string.cat_zip, R.string.chip_zip, Color(0xFFF9A825)),
    OTHER(R.string.cat_other, R.string.chip_other, Color(0xFF607D8B))
}

/**
 * Everything the UI needs to know about a file.
 * [size] is -1 when the provider does not report it; [available] is false when the URI can no longer be read
 * (the file was deleted or the access grant is gone).
 */
class Info(
    val name: String,
    val ext: String,
    val mime: String?,
    val size: Long,
    val available: Boolean,
    val route: Route,
    val cat: Cat
)

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

/** Some providers answer a metadata query badly but still serve the content, so "missing" is confirmed by opening it. */
private fun canOpen(c: Context, u: Uri): Boolean = try {
    val s = c.contentResolver.openInputStream(u)
    if (s != null) { s.close(); true } else false
} catch (e: Exception) {
    false
}

private val VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "3gp", "mov")

/** True for video files (the player hides its controls after a moment); other media is audio. */
fun Info.isVideo(): Boolean = ext in VIDEO_EXT

fun mimeOf(ext: String): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

/** Reads name, size and availability with a single metadata query (no file content is opened). */
fun infoOf(c: Context, u: Uri): Info {
    var name: String? = null
    var size = -1L
    var available = true
    if (u.scheme == "file") {
        val f = File(u.path ?: "")
        name = f.name
        size = f.length()
        available = f.exists()
    } else {
        try {
            val cur = c.contentResolver.query(u, null, null, null, null)
            // A provider without query() support returns null: treat that as "unknown", i.e. available.
            if (cur != null) {
                cur.use {
                    if (it.moveToFirst()) {
                        val ni = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (ni >= 0 && !it.isNull(ni)) name = it.getString(ni)
                        val si = it.getColumnIndex(OpenableColumns.SIZE)
                        if (si >= 0 && !it.isNull(si)) size = it.getLong(si)
                    } else {
                        available = canOpen(c, u)
                    }
                }
            }
        } catch (e: Exception) {
            available = canOpen(c, u)
        }
    }
    val shownName = name ?: u.lastPathSegment ?: "file"
    val mime = try { c.contentResolver.getType(u) } catch (e: Exception) { null }
    var ext = shownName.substringAfterLast('.', "").lowercase()
    if (!TABLE.containsKey(ext)) {
        val m = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if (m != null && TABLE.containsKey(m)) ext = m
    }
    val t = TABLE[ext]
    return Info(shownName, ext, mime, size, available, t?.first ?: Route.WEB, t?.second ?: Cat.OTHER)
}

fun toast(c: Context, res: Int) { Toast.makeText(c, res, Toast.LENGTH_LONG).show() }

fun openExternal(c: Context, u: Uri, mime: String) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(u, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val ch = Intent.createChooser(i, c.getString(R.string.chooser_open))
        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(c, MainActivity::class.java)))
    try { c.startActivity(ch) } catch (e: Exception) { toast(c, R.string.toast_no_app) }
}

fun shareFile(c: Context, u: Uri, mime: String) {
    val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { c.startActivity(Intent.createChooser(i, null)) } catch (e: Exception) { toast(c, R.string.toast_share_failed) }
}
