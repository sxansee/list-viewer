package app.fileviewer

import android.content.Context
import android.net.Uri

/** One entry of the "recent files" list: the document URI and when it was last opened (0 = unknown). */
data class RecentItem(val uri: String, val time: Long)

/**
 * The list of recently opened files, newest first, kept in SharedPreferences.
 *
 * Storage format "v2": one entry per line, `<time in ms><TAB><uri>`. The first version only stored the URIs
 * (key "l"); those entries are read as "opened at an unknown time" and rewritten in the new format on the next save.
 */
class Recents(c: Context) {
    private val p = c.getSharedPreferences("recents", Context.MODE_PRIVATE)

    fun list(): List<RecentItem> {
        val v2 = p.getString("v2", null)
        if (v2 != null) {
            return v2.split("\n").mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null
                else RecentItem(line.substring(tab + 1), line.substring(0, tab).toLongOrNull() ?: 0L)
            }.filter { it.uri.isNotBlank() }.distinctBy { it.uri }
        }
        val old = (p.getString("l", "") ?: "").split("\n").filter { it.isNotBlank() }
        return old.map { RecentItem(it, 0L) }.distinctBy { it.uri }
    }

    private fun save(l: List<RecentItem>) {
        p.edit()
            .putString("v2", l.joinToString("\n") { it.time.toString() + "\t" + it.uri })
            .remove("l")
            .apply()
    }

    fun add(u: Uri) {
        val s = u.toString()
        save((listOf(RecentItem(s, System.currentTimeMillis())) + list().filter { it.uri != s }).take(MAX))
    }

    fun remove(item: RecentItem) { save(list().filter { it.uri != item.uri }) }

    /** Puts a removed entry back (the "Undo" action), keeping the list sorted by time. */
    fun restore(item: RecentItem) {
        save((list().filter { it.uri != item.uri } + item).sortedByDescending { it.time }.take(MAX))
    }

    fun clear() { save(emptyList()) }

    private companion object { const val MAX = 50 }
}
