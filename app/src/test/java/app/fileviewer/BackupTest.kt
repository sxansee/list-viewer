package app.fileviewer

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val folders = listOf(Folder("ab12cd34", "Work “2024”", 3), Folder("ef56ab78", "Back\\slash \"quoted\"", 0))
    private val files = listOf(
        RecentItem(doc("plain"), 1731571200000L),
        RecentItem(
            "content://com.android.providers.media.documents/document/document%3A1234",
            1731571300000L,
            alias = "Отчёт за год\twith tab\nand break",
            folder = "ab12cd34",
            pinned = true,
            pos = 42L,
            name = "report 😀 final.pdf",
            size = 1048576L
        ),
        RecentItem("file:///storage/emulated/0/Download/a%20b.txt", 5L, name = "a b.txt", size = 0L)
    )

    @Test
    fun aBackupSurvivesAWriteAndARead() {
        val text = backupToJson(folders, files, 1731571400000L)
        val back = backupFromJson(text)
        assertNotNull(back)
        assertEquals(folders, back!!.folders)
        // Tabs and line breaks never survive in names: they are turned into spaces on the way in.
        assertEquals(files[0], back.files[0])
        assertEquals(files[1].copy(alias = "Отчёт за год with tab and break"), back.files[1])
        assertEquals(files[2], back.files[2])
    }

    @Test
    fun aBackupOfNothingIsStillABackup() {
        val back = backupFromJson(backupToJson(emptyList(), emptyList(), 0L))
        assertNotNull(back)
        assertTrue(back!!.files.isEmpty() && back.folders.isEmpty())
    }

    @Test
    fun theFileIsPlainReadableJson() {
        val text = backupToJson(folders.take(1), files.take(1), 1L)
        assertTrue(text.contains("\"app\": \"List\""))
        assertTrue(text.contains("\"format\": 1"))
        assertTrue(text.contains("\"uri\": \"content://x/plain\""))
    }

    @Test
    fun textThatIsNotABackupIsRefused() {
        val junk = listOf(
            "", "   ", "not json", "[]", "{}", "null", "42", "\"List\"",
            "{\"app\": \"Other\", \"files\": []}",
            "{\"app\": \"List\"}",
            "{\"app\": \"List\", \"files\": {}}",
            "{\"app\": \"List\", \"files\": [}",
            "{\"app\": \"List\", \"files\": []} trailing",
            "{\"app\": \"List\", \"files\": [\"unterminated]}",
            "{\"app\": \"List\", \"files\": [\"bad \\q escape\"]}",
            "[".repeat(100) + "]".repeat(100)
        )
        for (t in junk) assertNull("should be refused: $t", backupFromJson(t))
    }

    @Test
    fun aByteOrderMarkAndOtherWhitespaceAreFine() {
        val t = "﻿ \n\t{\"app\":\"List\",\"files\":[ ],\"folders\":[ ]}\r\n"
        assertNotNull(backupFromJson(t))
    }

    @Test
    fun entriesThatMakeNoSenseAreSkipped() {
        val text = """
            {"app":"List","format":1,
             "folders":[
               {"id":"good1234","name":"Fine","color":2},
               {"id":"bad id","name":"Spaces in id"},
               {"id":"","name":"No id"},
               {"id":"nameless1","name":"   "},
               {"id":"colorful","name":"Colours","color":4000},
               5, "text", null
             ],
             "files":[
               {"uri":"content://x/ok","time":7,"folder":"good1234","pinned":true,"pos":3,"size":10,"name":"ok.txt"},
               {"uri":"http://evil.example/a.pdf"},
               {"uri":"javascript:alert(1)"},
               {"uri":"content://x/with space"},
               {"uri":"content://x/with\nbreak"},
               {"uri":"content://x/dup"}, {"uri":"content://x/dup","alias":"second"},
               {"uri":42}, {"time":1}, [], "x", null,
               {"uri":"content://x/odd","time":-5,"pos":-1,"size":-3,"folder":"bad folder","pinned":"yes","alias":"   "}
             ]}
        """.trimIndent()
        val b = backupFromJson(text)!!
        assertEquals(listOf("good1234", "colorful"), b.folders.map { it.id })
        assertEquals(Recents.COLORS - 1, b.folders[1].color)
        assertEquals(listOf(doc("ok"), doc("dup"), doc("odd")), b.files.map { it.uri })
        assertEquals(RecentItem(doc("ok"), 7L, folder = "good1234", pinned = true, pos = 3L, name = "ok.txt", size = 10L), b.files[0])
        assertNull(b.files[1].alias)
        assertEquals(RecentItem(doc("odd"), 0L), b.files[2])
    }

    @Test
    fun theNumberOfEntriesIsCapped() {
        val sb = StringBuilder("{\"app\":\"List\",\"files\":[")
        for (i in 1..(BACKUP_MAX_FILES + 100)) {
            if (i > 1) sb.append(',')
            sb.append("{\"uri\":\"content://x/").append(i).append("\"}")
        }
        sb.append("]}")
        assertEquals(BACKUP_MAX_FILES, backupFromJson(sb.toString())!!.files.size)
    }

    @Test
    fun unicodeEscapesAreUnderstood() {
        val b = backupFromJson("{\"app\":\"List\",\"files\":[{\"uri\":\"content://x/a\",\"name\":\"\\u041e\\u0442\\u0447\\u0451\\u0442 \\ud83d\\ude00\\/\\\\\"}]}")!!
        assertEquals("Отчёт 😀/\\", b.files[0].name)
    }

    @Test
    fun readingIsLimitedInSize() {
        assertEquals(10, readLimited(ByteArrayInputStream(ByteArray(10)), 10)!!.size)
        assertNull(readLimited(ByteArrayInputStream(ByteArray(11)), 10))
        assertEquals(0, readLimited(ByteArrayInputStream(ByteArray(0)), 10)!!.size)
        assertEquals(20000, readLimited(ByteArrayInputStream(ByteArray(20000)), 1 shl 20)!!.size)
    }
}
