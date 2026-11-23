package app.fileviewer

import java.security.MessageDigest

/**
 * The fingerprint (SHA-256) of the certificate that every build from the repository is signed with unless its owner set
 * up a key of their own (see tools/New-ReleaseKey.ps1). The key is committed to the repository (app/debug.keystore), so
 * anyone can sign an update that the app would accept; a test checks that this value is still the one in that file.
 */
const val PUBLIC_KEY_SHA256 =
    "6A:8A:7B:4F:40:FD:3A:C6:AF:BB:9B:21:79:A9:D2:89:5A:87:07:7B:50:09:F4:B8:09:98:A2:A5:97:ED:86:23"

/** Who holds the key that signed this installation. */
enum class KeyKind { UNKNOWN, REPOSITORY, OTHER }

/** SHA-256 of a certificate as pairs of hex digits with colons, the way keytool prints it: "6A:8A:7B:…". */
fun fingerprintOf(certificate: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(certificate)
    val out = StringBuilder(digest.size * 3)
    for (b in digest) {
        if (out.isNotEmpty()) out.append(':')
        val v = b.toInt() and 0xFF
        out.append("0123456789ABCDEF"[v shr 4]).append("0123456789ABCDEF"[v and 15])
    }
    return out.toString()
}

/** The first [bytes] bytes of a fingerprint: enough to tell keys apart at a glance, short enough for a dialog. */
fun shortFingerprint(fingerprint: String, bytes: Int = 8): String {
    val parts = fingerprint.split(':')
    return if (parts.size <= bytes) fingerprint else parts.take(bytes).joinToString(":") + "…"
}

fun keyKindOf(fingerprint: String): KeyKind = when {
    fingerprint.isEmpty() -> KeyKind.UNKNOWN
    fingerprint.equals(PUBLIC_KEY_SHA256, ignoreCase = true) -> KeyKind.REPOSITORY
    else -> KeyKind.OTHER
}
