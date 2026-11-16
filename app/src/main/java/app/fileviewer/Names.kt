package app.fileviewer

// Renaming lives only inside List (see Recents.kt): the files on the device are never touched.

/** The extension of [name] with its dot (".pdf"), or "" when there is none. A leading dot (".bashrc") is not one. */
fun extensionOf(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0 && dot < name.length - 1) name.substring(dot) else ""
}

/** What was typed, as a name: one line, trimmed, and without the extension if the person typed that too. */
fun typedName(text: String, ext: String): String {
    var t = text.replace(Regex("[\\t\\r\\n]+"), " ").trim()
    if (ext.isNotEmpty() && t.length > ext.length && t.endsWith(ext, ignoreCase = true)) {
        t = t.dropLast(ext.length).trimEnd()
    }
    return t
}

/** A file name that any file manager accepts: characters that are not allowed in names become underscores. */
fun safeFileName(name: String): String {
    val n = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim()
    return n.ifEmpty { "file" }
}
