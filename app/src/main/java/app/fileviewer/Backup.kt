package app.fileviewer

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * What a backup file holds: the folders and the entries of the list. The file is plain JSON, written and read here
 * without any library, so it can be checked on a computer without Android.
 */
data class Backup(val folders: List<Folder>, val files: List<RecentItem>)

/** A backup is only text; anything bigger than this is not one. */
const val BACKUP_MAX_BYTES = 8 * 1024 * 1024
const val BACKUP_MAX_FILES = 5000
const val BACKUP_MAX_FOLDERS = 500

private val FOLDER_ID = Regex("[A-Za-z0-9_-]{1,32}")

/** Writes the backup. [exported] is the time it was made (milliseconds), only for the person reading the file. */
fun backupToJson(folders: List<Folder>, files: List<RecentItem>, exported: Long): String {
    val b = StringBuilder()
    b.append("{\n")
    b.append("  \"app\": \"List\",\n")
    b.append("  \"format\": 1,\n")
    b.append("  \"exported\": ").append(exported).append(",\n")
    b.append("  \"folders\": [")
    folders.forEachIndexed { i, f ->
        b.append(if (i == 0) "\n    " else ",\n    ")
        b.append("{\"id\": ").append(quote(f.id))
        b.append(", \"name\": ").append(quote(f.name))
        b.append(", \"color\": ").append(f.color).append("}")
    }
    b.append(if (folders.isEmpty()) "],\n" else "\n  ],\n")
    b.append("  \"files\": [")
    files.forEachIndexed { i, f ->
        b.append(if (i == 0) "\n    " else ",\n    ")
        b.append("{\"uri\": ").append(quote(f.uri))
        b.append(", \"time\": ").append(f.time)
        if (f.name != null) b.append(", \"name\": ").append(quote(f.name))
        if (f.size >= 0L) b.append(", \"size\": ").append(f.size)
        if (f.alias != null) b.append(", \"alias\": ").append(quote(f.alias))
        if (f.folder != null) b.append(", \"folder\": ").append(quote(f.folder))
        if (f.pinned) b.append(", \"pinned\": true")
        if (f.pos > 0L) b.append(", \"pos\": ").append(f.pos)
        b.append("}")
    }
    b.append(if (files.isEmpty()) "]\n" else "\n  ]\n")
    b.append("}\n")
    return b.toString()
}

/**
 * Reads a backup. Returns null when the text is not a List backup. Entries that make no sense (an address that is not
 * a document, too long a name) are skipped, and the number of entries is capped, so a damaged or hostile file cannot
 * do harm.
 */
fun backupFromJson(text: String): Backup? {
    val root = try {
        JsonReader(text.removePrefix("﻿")).readAll()
    } catch (e: Exception) {
        return null
    }
    if (root !is Map<*, *>) return null
    if (root["app"] != "List") return null
    val rawFiles = root["files"] as? List<*> ?: return null

    val folders = ArrayList<Folder>()
    for (f in (root["folders"] as? List<*> ?: emptyList<Any?>()).take(BACKUP_MAX_FOLDERS)) {
        val m = f as? Map<*, *> ?: continue
        val id = (m["id"] as? String) ?: continue
        val name = oneLine(m["name"] as? String ?: "", Recents.MAX_NAME)
        if (name.isEmpty() || !FOLDER_ID.matches(id)) continue
        val color = ((m["color"] as? Number)?.toInt() ?: 0).coerceIn(0, Recents.COLORS - 1)
        folders.add(Folder(id, name, color))
    }

    val files = ArrayList<RecentItem>()
    for (f in rawFiles.take(BACKUP_MAX_FILES)) {
        val m = f as? Map<*, *> ?: continue
        val uri = (m["uri"] as? String) ?: continue
        if (!(uri.startsWith("content://") || uri.startsWith("file://"))) continue
        if (uri.length > 4096 || uri.any { it.isWhitespace() || it < ' ' }) continue
        files.add(
            RecentItem(
                uri = uri,
                time = (m["time"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L,
                alias = (m["alias"] as? String)?.let { oneLine(it, Recents.MAX_NAME) }?.takeIf { it.isNotEmpty() },
                folder = (m["folder"] as? String)?.takeIf { FOLDER_ID.matches(it) },
                pinned = m["pinned"] == true,
                pos = (m["pos"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L,
                name = (m["name"] as? String)?.let { oneLine(it, Recents.MAX_FILE_NAME) }?.takeIf { it.isNotEmpty() },
                size = (m["size"] as? Number)?.toLong()?.takeIf { it >= 0L } ?: -1L
            )
        )
    }
    return Backup(folders, files.distinctBy { it.uri })
}

/** Reads at most [max] bytes; returns null when the stream is longer than that. */
fun readLimited(input: InputStream, max: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > max) return null
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

private fun quote(s: String): String {
    val b = StringBuilder(s.length + 2)
    b.append('"')
    for (ch in s) {
        when {
            ch == '"' -> b.append("\\\"")
            ch == '\\' -> b.append("\\\\")
            ch == '\n' -> b.append("\\n")
            ch == '\r' -> b.append("\\r")
            ch == '\t' -> b.append("\\t")
            ch < ' ' -> b.append("\\u").append(Integer.toHexString(ch.code).padStart(4, '0'))
            else -> b.append(ch)
        }
    }
    b.append('"')
    return b.toString()
}

/** A small JSON reader: objects become maps, arrays lists, numbers Long or Double, plus strings, booleans and null. */
private class JsonReader(private val s: String) {
    private var i = 0

    fun readAll(): Any? {
        val v = value(0)
        skipWs()
        if (i != s.length) fail("Extra text")
        return v
    }

    private fun fail(msg: String): Nothing = throw IllegalArgumentException(msg + " at " + i)

    private fun skipWs() {
        while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
    }

    private fun value(depth: Int): Any? {
        if (depth > 32) fail("Too deep")
        skipWs()
        if (i >= s.length) fail("Unexpected end")
        val c = s[i]
        return when {
            c == '{' -> obj(depth)
            c == '[' -> arr(depth)
            c == '"' -> str()
            c == 't' -> word("true", true)
            c == 'f' -> word("false", false)
            c == 'n' -> word("null", null)
            c == '-' || (c >= '0' && c <= '9') -> num()
            else -> fail("Unexpected character")
        }
    }

    private fun word(w: String, v: Any?): Any? {
        if (!s.startsWith(w, i)) fail("Unexpected word")
        i += w.length
        return v
    }

    private fun num(): Any {
        val start = i
        while (i < s.length && "+-.eE0123456789".indexOf(s[i]) >= 0) i++
        val t = s.substring(start, i)
        return t.toLongOrNull() ?: t.toDoubleOrNull() ?: fail("Bad number")
    }

    private fun str(): String {
        i++
        val b = StringBuilder()
        while (true) {
            if (i >= s.length) fail("Unterminated string")
            val c = s[i++]
            if (c == '"') return b.toString()
            if (c != '\\') {
                b.append(c)
                continue
            }
            if (i >= s.length) fail("Bad escape")
            when (val e = s[i++]) {
                '"' -> b.append('"')
                '\\' -> b.append('\\')
                '/' -> b.append('/')
                'b' -> b.append('\b')
                'f' -> b.append('\u000c')
                'n' -> b.append('\n')
                'r' -> b.append('\r')
                't' -> b.append('\t')
                'u' -> {
                    if (i + 4 > s.length) fail("Bad escape")
                    val code = s.substring(i, i + 4).toIntOrNull(16) ?: fail("Bad escape")
                    b.append(code.toChar())
                    i += 4
                }
                else -> fail("Bad escape " + e)
            }
        }
    }

    private fun arr(depth: Int): List<Any?> {
        i++
        val l = ArrayList<Any?>()
        skipWs()
        if (i < s.length && s[i] == ']') {
            i++
            return l
        }
        while (true) {
            l.add(value(depth + 1))
            skipWs()
            if (i >= s.length) fail("Unexpected end")
            val c = s[i++]
            if (c == ']') return l
            if (c != ',') fail("Expected a comma")
        }
    }

    private fun obj(depth: Int): Map<String, Any?> {
        i++
        val m = LinkedHashMap<String, Any?>()
        skipWs()
        if (i < s.length && s[i] == '}') {
            i++
            return m
        }
        while (true) {
            skipWs()
            if (i >= s.length || s[i] != '"') fail("Expected a name")
            val k = str()
            skipWs()
            if (i >= s.length || s[i] != ':') fail("Expected a colon")
            i++
            m[k] = value(depth + 1)
            skipWs()
            if (i >= s.length) fail("Unexpected end")
            val c = s[i++]
            if (c == '}') return m
            if (c != ',') fail("Expected a comma")
        }
    }
}
