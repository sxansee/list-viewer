package app.fileviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTest {
    private fun titled(uri: String) = "title of $uri"

    @Test
    fun aFileThatWasNotOpenedFromTheListIsPlayedAlone() {
        val p = playlistOf(emptyList(), "a", { true }, ::titled)
        assertEquals(listOf("a"), p.tracks.map { it.uri })
        assertEquals(0, p.start)
        assertEquals("title of a", p.tracks[0].title)
    }

    @Test
    fun onlyAudioFilesAreQueuedInTheOrderOfTheList() {
        val queue = listOf("doc1", "song1", "photo", "song2", "song3", "doc2")
        val p = playlistOf(queue, "song2", { it.startsWith("song") }, ::titled)
        assertEquals(listOf("song1", "song2", "song3"), p.tracks.map { it.uri })
        assertEquals(1, p.start)
        assertEquals(3, p.size)
    }

    @Test
    fun theOpenedFileIsKeptEvenIfItIsNotRecognisedAsAudio() {
        val p = playlistOf(listOf("a", "b", "c"), "b", { it == "a" }, ::titled)
        assertEquals(listOf("a", "b"), p.tracks.map { it.uri })
        assertEquals(1, p.start)
    }

    @Test
    fun theFirstAndTheLastFileKeepTheirPlace() {
        val queue = listOf("s1", "x", "s2", "s3")
        assertEquals(0, playlistOf(queue, "s1", { it.startsWith("s") }, ::titled).start)
        val last = playlistOf(queue, "s3", { it.startsWith("s") }, ::titled)
        assertEquals(2, last.start)
        assertEquals("s3", last.tracks[last.start].uri)
    }

    @Test
    fun aLongQueueIsCutAroundTheOpenedFile() {
        val queue = (1..500).map { "s$it" }
        val p = playlistOf(queue, "s300", { true }, ::titled, max = 200)
        assertEquals(200, p.size)
        assertEquals("s300", p.tracks[p.start].uri)
        assertEquals("s200", p.tracks.first().uri)
        assertEquals("s399", p.tracks.last().uri)
    }

    @Test
    fun aLongQueueIsCutAtTheStartAndAtTheEnd() {
        val queue = (1..500).map { "s$it" }
        val first = playlistOf(queue, "s1", { true }, ::titled, max = 200)
        assertEquals(0, first.start)
        assertEquals("s1", first.tracks.first().uri)
        assertEquals("s200", first.tracks.last().uri)
        val last = playlistOf(queue, "s500", { true }, ::titled, max = 200)
        assertEquals(199, last.start)
        assertEquals("s301", last.tracks.first().uri)
        assertEquals("s500", last.tracks.last().uri)
        val near = playlistOf(queue, "s495", { true }, ::titled, max = 200)
        assertEquals("s495", near.tracks[near.start].uri)
        assertEquals("s500", near.tracks.last().uri)
    }

    @Test
    fun aQueueOfExactlyTheLimitIsNotCut() {
        val queue = (1..200).map { "s$it" }
        val p = playlistOf(queue, "s50", { true }, ::titled, max = 200)
        assertEquals(200, p.size)
        assertEquals(49, p.start)
    }

    @Test
    fun titlesAreAskedOnlyForTheQueuedFiles() {
        val asked = ArrayList<String>()
        val queue = (1..10).map { "s$it" }
        playlistOf(queue, "s5", { true }, { asked.add(it); it }, max = 4)
        assertEquals(4, asked.size)
        assertTrue("s5" in asked)
    }

    @Test
    fun thePlaceInTheQueueCountsFromOne() {
        val p = playlistOf(listOf("a", "b", "c"), "b", { true }, ::titled)
        assertEquals(1, p.positionOf("a"))
        assertEquals(2, p.positionOf("b"))
        assertEquals(3, p.positionOf("c"))
        assertEquals(0, p.positionOf("zzz"))
    }

    @Test
    fun aPieceThatWasHeardToTheEndStartsOverNextTime() {
        assertEquals(0L, leftAt(180_000L, finished = true))
        assertEquals(42_000L, leftAt(42_000L, finished = false))
        assertEquals(0L, leftAt(-5L, finished = false))
    }
}
