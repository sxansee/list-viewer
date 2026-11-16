package app.fileviewer

/** How the files on the home screen are ordered. */
enum class Sort { RECENT, NAME, SIZE, TYPE }

/** A letter as it counts in sorting: lower case, and the Russian "ё" counts as "е" (in code order it would come after "я"). */
private fun fold(c: Char): Char {
    val l = c.lowercaseChar()
    return if (l == '\u0451') '\u0435' else l
}

/** Compares two names the way a person reads them: ignoring case, and with numbers by value ("file 2" before "file 10"). */
fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val x = a[i]
        val y = b[j]
        if (x.isDigit() && y.isDigit()) {
            var ie = i
            while (ie < a.length && a[ie].isDigit()) ie++
            var je = j
            while (je < b.length && b[je].isDigit()) je++
            val na = a.substring(i, ie).trimStart('0')
            val nb = b.substring(j, je).trimStart('0')
            if (na.length != nb.length) return na.length - nb.length
            val c = na.compareTo(nb)
            if (c != 0) return c
            i = ie
            j = je
        } else {
            val c = fold(x).compareTo(fold(y))
            if (c != 0) return c
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}

/**
 * Puts [rows] in the order of [sort]: newest first, by name, largest first (unknown sizes last), or grouped by [type]
 * with the newest first inside a group. Equal rows keep their order.
 */
fun <T> sortRows(
    rows: List<T>,
    sort: Sort,
    time: (T) -> Long,
    name: (T) -> String,
    size: (T) -> Long,
    type: (T) -> Int
): List<T> = when (sort) {
    Sort.RECENT -> rows.sortedByDescending(time)
    Sort.NAME -> rows.sortedWith(Comparator { x, y -> naturalCompare(name(x), name(y)) })
    Sort.SIZE -> rows.sortedByDescending(size)
    Sort.TYPE -> rows.sortedWith(compareBy<T> { type(it) }.thenByDescending { time(it) })
}
