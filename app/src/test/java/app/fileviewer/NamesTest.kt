package app.fileviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTest {
    @Test
    fun extensions() {
        assertEquals(".pdf", extensionOf("report.pdf"))
        assertEquals(".gz", extensionOf("archive.tar.gz"))
        assertEquals("", extensionOf("README"))
        assertEquals("", extensionOf(".bashrc"))
        assertEquals("", extensionOf("trailing."))
        assertEquals("", extensionOf(""))
    }

    @Test
    fun typedNamesLoseTheExtensionTheyRepeat() {
        assertEquals("Q3 report", typedName("  Q3 report  ", ".pdf"))
        assertEquals("Q3 report", typedName("Q3 report.PDF", ".pdf"))
        assertEquals("Q3 report", typedName("Q3\nreport", ".pdf"))
        assertEquals(".pdf", typedName(".pdf", ".pdf"))
        assertEquals("notes", typedName("notes", ""))
    }

    @Test
    fun fileNamesForTheSaveDialog() {
        assertEquals("a_b_c.pdf", safeFileName("a/b\\c.pdf"))
        assertEquals("what_.txt", safeFileName("what?.txt"))
        assertEquals("file", safeFileName("   "))
        assertEquals("Отчёт.docx", safeFileName("  Отчёт.docx "))
    }
}
