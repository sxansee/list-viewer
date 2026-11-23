package app.fileviewer

/**
 * The lines that the About dialog copies for a bug report: the version of the app and the kind of build, the version of
 * Android and the device. [maker] and [model] are what the phone reports ("Google", "Pixel 7"); a model that already starts
 * with the maker's name is not given the name twice. [signature] is the fingerprint of the key the installed app is signed
 * with (empty when it could not be read): the report then says whether that is the public key of the repository.
 */
fun aboutDetails(
    app: String,
    version: String,
    code: Long,
    debug: Boolean,
    release: String,
    sdk: Int,
    maker: String,
    model: String,
    signature: String = ""
): String {
    val parts = listOf(maker.trim(), model.trim()).filter { it.isNotEmpty() }
    val device = if (parts.size == 2 && parts[1].startsWith(parts[0], ignoreCase = true)) parts[1] else parts.joinToString(" ")
    return buildString {
        append(app).append(' ').append(version.ifEmpty { "?" })
        append(" (").append(code).append(", ").append(if (debug) "debug" else "release").append(")\n")
        append("Android ").append(release).append(" (API ").append(sdk).append(')')
        if (device.isNotEmpty()) append('\n').append(device)
        when (keyKindOf(signature)) {
            KeyKind.UNKNOWN -> Unit
            KeyKind.REPOSITORY -> append("\nSigned with the repository key (").append(shortFingerprint(signature)).append(')')
            KeyKind.OTHER -> append("\nSigned with another key (").append(signature).append(')')
        }
    }
}
