package app.fileviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsTest {
    @Test
    fun newestFirstWithoutDuplicates() {
        val r = recentsWith()
        r.add(doc("a"))
        r.add(doc("b"))
        r.add(doc("c"))
        assertEquals(listOf(doc("c"), doc("b"), doc("a")), r.list().map { it.uri })
        r.add(doc("a"))
        assertEquals(listOf(doc("a"), doc("c"), doc("b")), r.list().map { it.uri })
    }

    @Test
    fun olderFormatsAreReadAndRewritten() {
        val old = MapStore()
        old.map["l"] = "content://x/1\ncontent://x/2\n\ncontent://x/1"
        val r1 = recentsWith(old)
        assertEquals(listOf(RecentItem(doc("1"), 0L), RecentItem(doc("2"), 0L)), r1.list())
        r1.add(doc("3"))
        assertTrue(old.map.containsKey("v4"))
        assertFalse(old.map.containsKey("l"))
        assertEquals(listOf(doc("3"), doc("1"), doc("2")), r1.list().map { it.uri })

        val v2 = MapStore()
        v2.map["v2"] = "1700000000000\tcontent://x/p\n1600000000000\tcontent://x/q"
        val r2 = recentsWith(v2)
        assertEquals(RecentItem(doc("p"), 1700000000000L), r2.list()[0])
        r2.setAlias(doc("q"), "Quarterly")
        assertFalse(v2.map.containsKey("v2"))
        assertEquals(RecentItem(doc("q"), 1600000000000L, alias = "Quarterly"), recentsWith(v2).list()[1])

        val v3 = MapStore()
        v3.map["v3"] = "5\tcontent://x/z\tf1\tName"
        v3.map["folders"] = "f1\tWork"
        val item = recentsWith(v3).list().single()
        assertEquals(RecentItem(doc("z"), 5L, alias = "Name", folder = "f1"), item)
        assertFalse(item.pinned)
        assertEquals(0L, item.pos)
        assertEquals(-1L, item.size)
    }

    @Test
    fun aliasesAreCleanedAndKept() {
        val s = MapStore()
        val r = recentsWith(s)
        r.add(doc("d"))
        r.setAlias(doc("d"), "  My\tgood\nreport  ")
        assertEquals("My good report", r.aliasOf(doc("d")))
        r.setAlias(doc("d"), "   ")
        assertNull(r.aliasOf(doc("d")))
        r.setAlias(doc("d"), "x".repeat(200))
        assertEquals(Recents.MAX_NAME, r.aliasOf(doc("d"))?.length)
        r.setAlias(doc("d"), "Keep me")
        r.add(doc("d"))
        assertEquals("Keep me", r.aliasOf(doc("d")))
        r.setAlias(doc("nothing"), "Ghost")
        assertEquals(1, r.list().size)
        r.add(doc("докум%20ент.pdf"))
        r.setAlias(doc("докум%20ент.pdf"), "Отчёт за год")
        assertEquals("Отчёт за год", recentsWith(s).aliasOf(doc("докум%20ент.pdf")))
    }

    @Test
    fun foldersAreCreatedRenamedAndDeleted() {
        val r = recentsWith()
        r.add(doc("a"))
        r.add(doc("b"))
        r.add(doc("c"))
        val work = r.createFolder("  Work ")!!
        assertEquals("Work", work.name)
        assertEquals(0, work.color)
        assertEquals(work.id, r.createFolder("work")!!.id)
        assertEquals(1, r.folders().size)
        assertNull(r.createFolder("   "))
        r.createFolder("study")
        r.createFolder("Alpha")
        assertEquals(listOf("Alpha", "study", "Work"), r.folders().map { it.name })

        r.moveTo(doc("a"), work.id)
        r.moveTo(doc("b"), work.id)
        assertEquals(work.id, r.folderOf(doc("a")))
        assertNull(r.folderOf(doc("c")))
        r.updateFolder(work.id, "Job", 3)
        assertEquals(Folder(work.id, "Job", 3), r.folders().first { it.id == work.id })
        r.updateFolder(work.id, "  ", 5)
        assertEquals(Folder(work.id, "Job", 5), r.folders().first { it.id == work.id })
        r.moveTo(doc("b"), null)
        assertNull(r.folderOf(doc("b")))
        r.add(doc("a"))
        assertEquals(work.id, r.folderOf(doc("a")))

        r.clear()
        assertEquals(listOf(doc("a")), r.list().map { it.uri })
        r.deleteFolder(work.id)
        assertEquals(listOf(Pair(doc("a"), null as String?)), r.list().map { Pair(it.uri, it.folder) })
        assertEquals(listOf("Alpha", "study"), r.folders().map { it.name })
    }

    @Test
    fun folderColorsStayInTheirRange() {
        val s = MapStore()
        val r = recentsWith(s)
        assertEquals(Recents.COLORS - 1, r.createFolder("Loud", 99)!!.color)
        assertEquals(0, r.createFolder("Quiet", -4)!!.color)
        // Data written before colours existed has two columns only.
        s.map["folders"] = "f1\tOld folder\nf2\tNew\t4\nf3\tBroken\tx\nf4\tHuge\t900"
        assertEquals(
            listOf(Folder("f1", "Old folder", 0), Folder("f2", "New", 4), Folder("f3", "Broken", 0), Folder("f4", "Huge", Recents.COLORS - 1)),
            recentsWith(s).folders().sortedBy { it.id }
        )
    }

    @Test
    fun theCapKeepsEverythingThePersonOrganized() {
        val r = recentsWith()
        val f = r.createFolder("Keep")!!
        r.add(doc("kept"), f.id)
        r.add(doc("starred"))
        r.setPinned(setOf(doc("starred")), true)
        r.add(doc("renamed"))
        r.setAlias(doc("renamed"), "Mine")
        for (i in 1..60) r.add(doc("n$i"))
        val l = r.list()
        assertEquals(53, l.size)
        assertEquals(50, l.count { it.folder == null && !it.pinned && it.alias == null })
        assertTrue(l.none { it.uri == doc("n1") })
        assertTrue(l.any { it.uri == doc("n60") })
        assertTrue(l.any { it.uri == doc("kept") && it.folder == f.id })
        assertTrue(l.any { it.uri == doc("starred") && it.pinned })
        assertTrue(l.any { it.uri == doc("renamed") && it.alias == "Mine" })

        for (i in 1..70) r.add(doc("f$i"), f.id)
        assertEquals(71, r.list().count { it.folder != null })
        assertEquals(50, r.list().count { it.folder == null && !it.pinned && it.alias == null })

        // Taking a file out of a folder never makes it vanish on the spot.
        val before = r.list().size
        r.moveAll(setOf(doc("f1"), doc("f2")), null)
        assertEquals(before, r.list().size)
    }

    @Test
    fun filingAnOpenedFileDoesNotPushOthersOut() {
        val r = recentsWith()
        val f = r.createFolder("F")!!
        for (i in 1..50) r.add(doc("n$i"))
        r.add(doc("new"), f.id)
        assertEquals(51, r.list().size)
        assertTrue(r.list().any { it.uri == doc("n1") })
    }

    @Test
    fun removeAndRestoreKeepEverything() {
        val r = recentsWith()
        val f = r.createFolder("F")!!
        r.add(doc("a"))
        r.add(doc("b"))
        r.add(doc("c"))
        r.setAlias(doc("b"), "Bee")
        r.moveTo(doc("b"), f.id)
        r.setPinned(setOf(doc("b")), true)
        r.setPos(doc("b"), 12L)
        val b = r.list().first { it.uri == doc("b") }
        r.remove(b)
        assertEquals(listOf(doc("c"), doc("a")), r.list().map { it.uri })
        r.restore(b)
        assertEquals(listOf(doc("c"), doc("b"), doc("a")), r.list().map { it.uri })
        assertEquals(b, r.list().first { it.uri == doc("b") })

        val all = r.list()
        r.removeAll(setOf(doc("a"), doc("c")))
        assertEquals(listOf(doc("b")), r.list().map { it.uri })
        r.restoreAll(all.filter { it.uri != doc("b") })
        assertEquals(all, r.list())
    }

    @Test
    fun corruptDataDoesNotCrash() {
        val s = MapStore()
        s.map["v4"] = "garbage\n\n\t\n12\n99\tcontent://x/ok\t\t\nabc\tcontent://x/ok2\tf1\tNm\t1\t40\t2048\tReal name.pdf\nx\tcontent://x/ok3\t\t\tyes\t-5\tbig\t"
        s.map["folders"] = "\n\tnoid\nnotab\nf1\tFolder One\nf1\tTwin"
        val r = recentsWith(s)
        assertEquals(listOf(doc("ok"), doc("ok2"), doc("ok3")), r.list().map { it.uri })
        assertEquals(RecentItem(doc("ok"), 99L), r.list()[0])
        assertEquals(RecentItem(doc("ok2"), 0L, alias = "Nm", folder = "f1", pinned = true, pos = 40L, name = "Real name.pdf", size = 2048L), r.list()[1])
        assertEquals(RecentItem(doc("ok3"), 0L), r.list()[2])
        assertEquals(listOf(Folder("f1", "Folder One", 0)), r.folders())
    }

    @Test
    fun anEntryOfAMissingFolderIsShownInTheMainList() {
        val s = MapStore()
        s.map["v4"] = "5\tcontent://x/z\tdeadbeef\tName"
        assertEquals(RecentItem(doc("z"), 5L, alias = "Name"), recentsWith(s).list().single())
    }

    @Test
    fun starsAndClear() {
        val r = recentsWith()
        r.add(doc("a"))
        r.add(doc("b"))
        r.add(doc("c"))
        r.setPinned(setOf(doc("a"), doc("c")), true)
        assertEquals(setOf(doc("a"), doc("c")), r.list().filter { it.pinned }.map { it.uri }.toSet())
        r.add(doc("a"))
        assertTrue(r.list().first { it.uri == doc("a") }.pinned)
        r.clear()
        assertEquals(setOf(doc("a"), doc("c")), r.list().map { it.uri }.toSet())
        r.setPinned(setOf(doc("a")), false)
        assertFalse(r.list().first { it.uri == doc("a") }.pinned)
    }

    @Test
    fun theReadingPlaceIsRemembered() {
        val s = MapStore()
        val r = recentsWith(s)
        r.add(doc("book"))
        assertEquals(0L, r.posOf(doc("book")))
        r.setPos(doc("book"), 37L)
        assertEquals(37L, recentsWith(s).posOf(doc("book")))
        r.add(doc("book"))
        assertEquals(37L, r.posOf(doc("book")))
        r.setPos(doc("book"), -9L)
        assertEquals(0L, r.posOf(doc("book")))
        r.setPos(doc("nothing"), 5L)
        assertEquals(1, r.list().size)
        // Saving the same place again changes nothing, so the stored text stays as it is.
        r.setPos(doc("book"), 12L)
        val text = s.map["v4"]
        r.setPos(doc("book"), 12L)
        assertEquals(text, s.map["v4"])
    }

    @Test
    fun namesAndSizesOfReadableFilesAreRemembered() {
        val r = recentsWith()
        r.add(doc("a"))
        r.add(doc("b"))
        r.noteFiles(mapOf(doc("a") to Pair("Report\t2024.pdf", 1234L), doc("zzz") to Pair("Nobody.txt", 1L)))
        assertEquals("Report 2024.pdf", r.list().first { it.uri == doc("a") }.name)
        assertEquals(1234L, r.list().first { it.uri == doc("a") }.size)
        assertNull(r.list().first { it.uri == doc("b") }.name)
        r.noteFiles(mapOf(doc("a") to Pair("Report 2024.pdf", -1L)))
        assertEquals(1234L, r.list().first { it.uri == doc("a") }.size)
        r.add(doc("a"))
        assertEquals("Report 2024.pdf", r.list().first { it.uri == doc("a") }.name)
    }

    @Test
    fun relinkKeepsNameFolderStarAndPlace() {
        val r = recentsWith()
        val f = r.createFolder("F")!!
        r.add(doc("old"))
        r.setAlias(doc("old"), "Contract")
        r.moveTo(doc("old"), f.id)
        r.setPinned(setOf(doc("old")), true)
        r.setPos(doc("old"), 8L)
        r.add(doc("other"))
        r.relink(doc("old"), doc("new"))
        val moved = r.list().first()
        assertEquals(RecentItem(doc("new"), moved.time, alias = "Contract", folder = f.id, pinned = true, pos = 8L), moved)
        assertTrue(r.list().none { it.uri == doc("old") })
        assertEquals(2, r.list().size)

        // The new address is in the list already: the two become one, and what was known is kept.
        r.add(doc("again"))
        r.setAlias(doc("again"), "Second")
        r.relink(doc("new"), doc("again"))
        assertEquals(2, r.list().size)
        val merged = r.list().first()
        assertEquals(doc("again"), merged.uri)
        assertEquals("Contract", merged.alias)
        assertTrue(merged.pinned)
        assertEquals(8L, merged.pos)

        r.relink(doc("none"), doc("whatever"))
        r.relink(doc("again"), doc("again"))
        assertEquals(2, r.list().size)
    }

    @Test
    fun aBackupIsMergedWithoutOverwriting() {
        val r = recentsWith()
        val mine = r.createFolder("Work")!!
        r.add(doc("here"))
        r.setAlias(doc("here"), "My name")
        r.setPos(doc("here"), 3L)

        val backup = Backup(
            folders = listOf(Folder("zzzz1111", "work", 2), Folder("aaaa2222", "Trips", 5)),
            files = listOf(
                RecentItem(doc("here"), 10L, alias = "Backup name", folder = "zzzz1111", pinned = true, pos = 99L, name = "here.pdf", size = 7L),
                RecentItem(doc("gone"), 20L, alias = "Ticket", folder = "aaaa2222", pinned = true, pos = 4L, name = "ticket.pdf", size = 9L),
                RecentItem(doc("loose"), 30L, name = "loose.txt", folder = "unknown")
            )
        )
        val result = r.mergeBackup(backup)
        assertEquals(ImportResult(added = 2, updated = 1), result)
        assertEquals(listOf("Trips", "Work"), r.folders().map { it.name })
        val trips = r.folders().first { it.name == "Trips" }
        assertEquals(5, trips.color)

        val here = r.list().first { it.uri == doc("here") }
        assertEquals("My name", here.alias)
        assertEquals(3L, here.pos)
        assertEquals(mine.id, here.folder)
        assertTrue(here.pinned)
        assertEquals("here.pdf", here.name)
        assertEquals(7L, here.size)

        val gone = r.list().first { it.uri == doc("gone") }
        assertEquals(RecentItem(doc("gone"), 20L, alias = "Ticket", folder = trips.id, pinned = true, pos = 4L, name = "ticket.pdf", size = 9L), gone)
        assertNull(r.list().first { it.uri == doc("loose") }.folder)

        // Doing it twice changes nothing more.
        assertEquals(ImportResult(0, 0), r.mergeBackup(backup))
    }
}
