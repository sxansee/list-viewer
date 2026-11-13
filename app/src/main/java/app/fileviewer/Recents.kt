package app.fileviewer

import android.content.Context
import android.net.Uri
import java.util.UUID

/**
 * One entry of the file list: the document URI and when it was last opened (0 = unknown).
 *
 * [alias] is a name the person gave the file inside List, without the extension (null = show the file's own name), and
 * [folder] is the id of the [Folder] the entry was put in (null = not in a folder). Neither changes the file itself.
 */
data class RecentItem(
    val uri: String,
    val time: Long,
    val alias: String? = null,
    val folder: String? = null
)

/** A folder made inside List. It only groups entries of the list; nothing is created on the device. */
data class Folder(val id: String, val name: String)

/**
 * The list of opened files, newest first, together with the names and folders the person chose. It lives in
 * SharedPreferences, so it is small and never touches the files.
 *
 * Storage format "v3": one entry per line, `<time in ms><TAB><uri><TAB><folder id><TAB><name>`; the last two columns may
 * be empty. Older lines with only the first two columns ("v2") and the first format with bare URIs (key "l") are still
 * read, and rewritten in the new format on the next save. Folders are stored under "folders", one `<id><TAB><name>` per line.
 *
 * The list keeps every entry that sits in a folder and the [MAX] newest entries that do not.
 */
class Recents(c: Context) {
    private val p = c.getSharedPreferences("recents", Context.MODE_PRIVATE)

    // ---- the list ----

    /** All entries, newest first. An entry whose folder no longer exists is shown as not being in a folder. */
    fun list(): List<RecentItem> {
        val known = readFolders().map { it.id }.toSet()
        return readItems().map { if (it.folder != null && it.folder !in known) it.copy(folder = null) else it }
    }

    private fun readItems(): List<RecentItem> {
        val v3 = p.getString("v3", null)
        if (v3 != null) return parse(v3)
        val v2 = p.getString("v2", null)
        if (v2 != null) return parse(v2)
        val old = (p.getString("l", "") ?: "").split("\n").filter { it.isNotBlank() }
        return old.map { RecentItem(it, 0L) }.distinctBy { it.uri }
    }

    private fun parse(text: String): List<RecentItem> {
        return text.split("\n").mapNotNull { line ->
            val f = line.split('\t', limit = 4)
            if (f.size < 2 || f[1].isBlank()) {
                null
            } else {
                RecentItem(
                    uri = f[1],
                    time = f[0].toLongOrNull() ?: 0L,
                    alias = f.getOrNull(3)?.takeIf { it.isNotBlank() },
                    folder = f.getOrNull(2)?.takeIf { it.isNotBlank() }
                )
            }
        }.distinctBy { it.uri }
    }

    private fun save(l: List<RecentItem>) {
        p.edit()
            .putString(
                "v3",
                l.joinToString("\n") { it.time.toString() + "\t" + it.uri + "\t" + (it.folder ?: "") + "\t" + (it.alias ?: "") }
            )
            .remove("v2")
            .remove("l")
            .apply()
    }

    /** Keeps every entry that sits in a folder and the [MAX] newest entries that do not. [l] must be newest first. */
    private fun trim(l: List<RecentItem>): List<RecentItem> {
        val out = ArrayList<RecentItem>(l.size)
        var loose = 0
        for (item in l) {
            if (item.folder != null) {
                out.add(item)
            } else if (loose < MAX) {
                out.add(item)
                loose++
            }
        }
        return out
    }

    /**
     * Puts a file at the top of the list. Its name and folder, if it already had any, stay; [folder] puts it into a
     * folder at once (a file opened from inside that folder), so it does not push an older entry out of the main list.
     */
    fun add(u: Uri, folder: String? = null) {
        val s = u.toString()
        val all = list()
        val old = all.firstOrNull { it.uri == s }
        val entry = RecentItem(s, System.currentTimeMillis(), old?.alias, folder ?: old?.folder)
        save(trim(listOf(entry) + all.filter { it.uri != s }))
    }

    fun remove(item: RecentItem) { save(list().filter { it.uri != item.uri }) }

    /** Puts a removed entry back (the "Undo" action), keeping the list sorted by time. */
    fun restore(item: RecentItem) {
        save(trim((list().filter { it.uri != item.uri } + item).sortedByDescending { it.time }))
    }

    /** Empties the list. Files that were put into folders stay: the folders are the person's own order. */
    fun clear() { save(list().filter { it.folder != null }) }

    // ---- names ----

    fun aliasOf(uri: String): String? = list().firstOrNull { it.uri == uri }?.alias

    /** Gives a file a name inside List, or takes it back with null or a blank text. */
    fun setAlias(uri: String, alias: String?) {
        val a = alias?.let { oneLine(it, MAX_NAME) }?.takeIf { it.isNotEmpty() }
        save(list().map { if (it.uri == uri) it.copy(alias = a) else it })
    }

    // ---- folders ----

    /** All folders, sorted by name. */
    fun folders(): List<Folder> = readFolders().sortedBy { it.name.lowercase() }

    fun folderOf(uri: String): String? = list().firstOrNull { it.uri == uri }?.folder

    /** Makes a folder and returns it. A folder with the same name (any case) is returned instead of a twin. */
    fun createFolder(name: String): Folder? {
        val n = oneLine(name, MAX_NAME)
        if (n.isEmpty()) return null
        val all = readFolders()
        val same = all.firstOrNull { it.name.equals(n, ignoreCase = true) }
        if (same != null) return same
        val f = Folder(UUID.randomUUID().toString().substring(0, 8), n)
        writeFolders(all + f)
        return f
    }

    fun renameFolder(id: String, name: String) {
        val n = oneLine(name, MAX_NAME)
        if (n.isEmpty()) return
        writeFolders(readFolders().map { if (it.id == id) it.copy(name = n) else it })
    }

    /** Deletes a folder; its files go back to the main list. */
    fun deleteFolder(id: String) {
        writeFolders(readFolders().filter { it.id != id })
        save(trim(list()))
    }

    /** Moves a file into a folder, or out of any folder with null. */
    fun moveTo(uri: String, folder: String?) {
        save(list().map { if (it.uri == uri) it.copy(folder = folder) else it })
    }

    private fun readFolders(): List<Folder> {
        val s = p.getString("folders", "") ?: ""
        return s.split("\n").mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0 || tab == line.length - 1) null else Folder(line.substring(0, tab), line.substring(tab + 1))
        }.distinctBy { it.id }
    }

    private fun writeFolders(l: List<Folder>) {
        p.edit().putString("folders", l.joinToString("\n") { it.id + "\t" + it.name }).apply()
    }

    /** Names are stored one per line and tab-separated, so line breaks and tabs become spaces. */
    private fun oneLine(s: String, max: Int): String = s.replace(Regex("[\\t\\r\\n]+"), " ").trim().take(max)

    companion object {
        /** How many entries without a folder the list keeps. */
        const val MAX = 50

        /** Longest name of a file or a folder. */
        const val MAX_NAME = 80
    }
}
