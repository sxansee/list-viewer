package app.fileviewer

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
    @Test
    fun theAddressOfAFileIsHidden() {
        assertEquals(
            "java.io.FileNotFoundException: content://… : open failed",
            hidePaths("java.io.FileNotFoundException: content://com.android.providers.media.documents/document/document%3A1234 : open failed")
        )
        assertEquals("opened file://… twice", hidePaths("opened file:///storage/emulated/0/a.pdf twice"))
    }

    @Test
    fun aPathInTheSharedStorageIsHiddenButTheReasonStays() {
        assertEquals(
            "/… (Permission denied)",
            hidePaths("/storage/emulated/0/My Docs/a b.pdf (Permission denied)")
        )
        assertEquals(
            "java.io.FileNotFoundException: /…: open failed: ENOENT (No such file or directory)",
            hidePaths("java.io.FileNotFoundException: /sdcard/Download/report.pdf: open failed: ENOENT (No such file or directory)")
        )
    }

    @Test
    fun aMessageWithoutFilesIsNotChanged() {
        val text = "java.lang.IllegalStateException: Page 4 is out of range\n\tat app.fileviewer.PdfView(Viewers.kt:210)"
        assertEquals(text, hidePaths(text))
    }

    @Test
    fun aShortListOfLinesIsKept() {
        val lines = (1..70).map { "line $it" }
        assertEquals(lines, shorten(lines))
        val longer = (1..71).map { "line $it" }
        assertEquals(longer, shorten(longer))
    }

    @Test
    fun aLongListKeepsTheStartAndTheEnd() {
        val lines = (1..200).map { "line $it" }
        val cut = shorten(lines)
        assertEquals(45 + 1 + 25, cut.size)
        assertEquals("line 1", cut.first())
        assertEquals("line 45", cut[44])
        assertEquals("… 130 lines left out …", cut[45])
        assertEquals("line 176", cut[46])
        assertEquals("line 200", cut.last())
    }

    @Test
    fun theReportHasTheDetailsTheTimeTheThreadAndTheTrace() {
        val r = crashReport(
            "List 2.5.0 (105, release)\nAndroid 14 (API 34)\nGoogle Pixel 7",
            "2026-11-20 09:21:05 +0300",
            "main",
            "java.lang.IllegalStateException: boom\n\tat app.fileviewer.X.y(X.kt:12)\n"
        )
        assertEquals(
            "List 2.5.0 (105, release)\nAndroid 14 (API 34)\nGoogle Pixel 7\n" +
                "Crash: 2026-11-20 09:21:05 +0300\nThread: main\n\n" +
                "java.lang.IllegalStateException: boom\n\tat app.fileviewer.X.y(X.kt:12)\n",
            r
        )
    }

    @Test
    fun aReportWithoutDetailsStartsWithTheTime() {
        val r = crashReport("", "t", "main", "boom")
        assertEquals("Crash: t\nThread: main\n\nboom\n", r)
    }

    @Test
    fun aReportHidesTheFilesInTheMessage() {
        val r = crashReport("", "t", "main", "java.io.IOException: content://x/y/secret.pdf failed")
        assertFalse(r.contains("secret"))
        assertTrue(r.contains("content://…"))
    }

    @Test
    fun aVeryLongLineIsCut() {
        val r = crashReport("", "t", "main", "e" + "x".repeat(2000))
        val line = r.lines().first { it.startsWith("e") }
        assertEquals(401, line.length)
        assertTrue(line.endsWith("…"))
    }

    @Test
    fun aHugeTraceIsShortened() {
        val trace = (1..1000).joinToString("\n") { "\tat frame$it" }
        val r = crashReport("", "t", "main", trace)
        assertTrue(r.contains("lines left out"))
        assertTrue(r.lines().size < 90)
        assertTrue(r.contains("frame1\n"))
        assertTrue(r.contains("frame1000"))
    }

    @Test
    fun theTraceOfAnErrorHasItsCauses() {
        val trace = traceOf(IllegalStateException("outer", IllegalArgumentException("inner")))
        assertTrue(trace.startsWith("java.lang.IllegalStateException: outer"))
        assertTrue(trace.contains("Caused by: java.lang.IllegalArgumentException: inner"))
    }

    private fun store(): CrashStore {
        val dir = Files.createTempDirectory("crash").toFile()
        dir.deleteOnExit()
        return CrashStore(File(dir, "files"))
    }

    @Test
    fun noCrashMeansNoReport() {
        val s = store()
        assertNull(s.last())
        assertNull(s.unseen())
        s.markSeen()
        assertNull(s.unseen())
    }

    @Test
    fun aSavedReportIsOfferedOnceAndStaysAvailable() {
        val s = store()
        s.save("report one")
        assertEquals("report one", s.last())
        assertEquals("report one", s.unseen())
        s.markSeen()
        assertNull(s.unseen())
        assertEquals("report one", s.last())
    }

    @Test
    fun aNewCrashIsOfferedAgain() {
        val s = store()
        s.save("report one")
        s.markSeen()
        s.save("report two")
        assertEquals("report two", s.unseen())
        assertEquals("report two", s.last())
        s.markSeen()
        assertNull(s.unseen())
    }

    @Test
    fun aBlankReportCountsAsNone() {
        val s = store()
        s.save("  \n ")
        assertNull(s.last())
        assertNull(s.unseen())
    }

    @Test
    fun theReportSurvivesANewStoreOnTheSameFolder() {
        val dir = Files.createTempDirectory("crash2").toFile()
        dir.deleteOnExit()
        CrashStore(dir).save("kept")
        val again = CrashStore(dir)
        assertNotNull(again.unseen())
        again.markSeen()
        assertNull(CrashStore(dir).unseen())
    }
}
