package app.fileviewer

import java.io.File
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureTest {
    @Test
    fun theFingerprintIsWrittenTheWayKeytoolPrintsIt() {
        // SHA-256 of nothing at all.
        assertEquals(
            "E3:B0:C4:42:98:FC:1C:14:9A:FB:F4:C8:99:6F:B9:24:27:AE:41:E4:64:9B:93:4C:A4:95:99:1B:78:52:B8:55",
            fingerprintOf(ByteArray(0))
        )
    }

    @Test
    fun theFingerprintHasThirtyTwoPairs() {
        val f = fingerprintOf("certificate".toByteArray())
        assertEquals(32, f.split(':').size)
        assertTrue(f.all { it == ':' || it in '0'..'9' || it in 'A'..'F' })
        assertEquals(95, f.length)
    }

    @Test
    fun aShortFingerprintKeepsTheFirstBytes() {
        assertEquals("6A:8A:7B:4F:40:FD:3A:C6…", shortFingerprint(PUBLIC_KEY_SHA256))
        assertEquals("6A:8A:7B…", shortFingerprint(PUBLIC_KEY_SHA256, 3))
        assertEquals("6A:8A", shortFingerprint("6A:8A", 8))
        assertEquals("", shortFingerprint(""))
    }

    @Test
    fun theKindOfTheKeyIsToldFromTheFingerprint() {
        assertEquals(KeyKind.UNKNOWN, keyKindOf(""))
        assertEquals(KeyKind.REPOSITORY, keyKindOf(PUBLIC_KEY_SHA256))
        assertEquals(KeyKind.REPOSITORY, keyKindOf(PUBLIC_KEY_SHA256.lowercase()))
        assertEquals(KeyKind.OTHER, keyKindOf("00:11:22"))
    }

    /** The constant has to stay the fingerprint of the key that is committed to the repository. */
    @Test
    fun thePublicKeyIsTheOneInTheRepository() {
        val file = listOf(File("debug.keystore"), File("app/debug.keystore")).firstOrNull { it.isFile } ?: return
        val ks = KeyStore.getInstance(file, "android".toCharArray())
        val cert = ks.getCertificate("androiddebugkey")
        assertEquals(PUBLIC_KEY_SHA256, fingerprintOf(cert.encoded))
    }
}
