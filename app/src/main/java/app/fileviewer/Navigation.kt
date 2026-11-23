package app.fileviewer

/**
 * Where an open file stands among the files of the list it was opened from: its [position] (counted from 1) out of
 * [total], and the files [previous] and [next] to it (null at the ends).
 */
class Neighbours(val position: Int, val total: Int, val previous: String?, val next: String?)

/**
 * The neighbours of [current] in [queue], the addresses of the files that were shown in the list, in the order they were
 * shown. Null when there is nothing to step through: a file that was not opened from the list, or a list of one file.
 */
fun neighbours(queue: List<String>, current: String): Neighbours? {
    if (queue.size < 2) return null
    val i = queue.indexOf(current)
    if (i < 0) return null
    return Neighbours(i + 1, queue.size, queue.getOrNull(i - 1), queue.getOrNull(i + 1))
}
