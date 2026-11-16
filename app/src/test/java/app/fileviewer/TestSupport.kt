package app.fileviewer

/** A [TextStore] that keeps its text in memory. */
class MapStore : TextStore {
    val map = HashMap<String, String>()

    override fun get(key: String): String? = map[key]

    override fun put(key: String, value: String, drop: List<String>) {
        map[key] = value
        for (k in drop) map.remove(k)
    }
}

/** A list whose clock moves by one millisecond each time it is read, so the order of events is always clear. */
fun recentsWith(store: MapStore = MapStore()): Recents {
    var t = 1_000_000L
    return Recents(store) {
        t += 1L
        t
    }
}

fun doc(name: String): String = "content://x/$name"
