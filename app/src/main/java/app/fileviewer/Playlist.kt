package app.fileviewer

/*
 * The queue of an audio screen: which files are played one after another. It knows nothing about Android, so the unit
 * tests (app/src/test) run it directly.
 */

/** One piece of audio in the queue: the address of the file, and what to call it until its own tags say otherwise. */
class Track(val uri: String, val title: String)

/**
 * The audio to play one piece after another: [tracks] in the order of the list they came from, and the index [start]
 * of the one that was opened.
 */
class Playlist(val tracks: List<Track>, val start: Int) {
    val size: Int get() = tracks.size

    /** The place of the file in the queue counted from 1, or 0 when it is not in it. */
    fun positionOf(uri: String): Int = tracks.indexOfFirst { it.uri == uri } + 1
}

/** A very long queue is cut to this many pieces around the opened one: the player keeps a list of what it is to play. */
const val MAX_PLAYLIST = 200

/**
 * The queue for the file [current] that was opened from the list whose files are [queue] (in the order they were shown):
 * the audio files among them ([isAudio]) with their names ([titleOf]). A file that was not opened from the list is
 * played alone. When there are more than [max] audio files, the ones around the opened one are taken.
 */
fun playlistOf(
    queue: List<String>,
    current: String,
    isAudio: (String) -> Boolean,
    titleOf: (String) -> String,
    max: Int = MAX_PLAYLIST
): Playlist {
    val at = queue.indexOf(current)
    val chosen = if (at < 0) listOf(current) else queue.filterIndexed { i, uri -> i == at || isAudio(uri) }
    val index = chosen.indexOf(current).coerceAtLeast(0)
    var from = 0
    var to = chosen.size
    if (chosen.size > max) {
        from = (index - max / 2).coerceIn(0, chosen.size - max)
        to = from + max
    }
    return Playlist(chosen.subList(from, to).map { Track(it, titleOf(it)) }, index - from)
}

/**
 * Where to remember a piece of audio that playback has just left, [position] being the moment it was at: the start when
 * it was [finished] (heard to the end, so playback moved on by itself).
 */
fun leftAt(position: Long, finished: Boolean): Long = if (finished) 0L else position.coerceAtLeast(0L)
