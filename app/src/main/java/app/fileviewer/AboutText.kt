package app.fileviewer

/**
 * The lines that the About dialog copies for a bug report: the version of the app and the kind of build, the version of
 * Android and the device. [maker] and [model] are what the phone reports ("Google", "Pixel 7"); a model that already starts
 * with the maker's name is not given the name twice.
 */
fun aboutDetails(
    app: String,
    version: String,
    code: Long,
    debug: Boolean,
    release: String,
    sdk: Int,
    maker: String,
    model: String
): String {
    val parts = listOf(maker.trim(), model.trim()).filter { it.isNotEmpty() }
    val device = if (parts.size == 2 && parts[1].startsWith(parts[0], ignoreCase = true)) parts[1] else parts.joinToString(" ")
    return buildString {
        append(app).append(' ').append(version.ifEmpty { "?" })
        append(" (").append(code).append(", ").append(if (debug) "debug" else "release").append(")\n")
        append("Android ").append(release).append(" (API ").append(sdk).append(')')
        if (device.isNotEmpty()) append('\n').append(device)
    }
}
