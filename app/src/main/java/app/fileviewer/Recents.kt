package app.fileviewer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.util.UUID

/**
 * One entry of the file list: the document URI and when it was last opened (0 = unknown).
 *
 * The rest is what the person did with the entry inside List; none of it changes the file itself:
 * - [alias]: a name chosen in List, without the extension (null = show the file's own name);
 * - [folder]: the id of the [Folder] the entry was put in (null = not in a folder);
 * - [pinned]: the entry is starred and shown at the top;
 * - [pos]: where reading stopped (a page of a PDF, a share of the document in thousandths, a moment of a recording
 *   in milliseconds); 0 = from the start;
 * - [name] and [size]: the file's own name and size when List last could read it, so that an entry whose file is no
 *   longer reachable (a restored backup, a file that was moved) can still be shown by name. [size] is -1 when unknown.
 */
data class RecentItem(
    val uri: String,
    val time: Long,
    val alias: String? = null,
    val folder: String? = null,
    val pinned: Boolean = false,
    val pos: Long = 0L,
    val name: String? = null,
    val size: Long = -1L
)

/** A folder made inside List. It only groups entries of the list; nothing is created on the device. [color] is an index of the palette. */
data class Folder(val id: String, val name: String, val color: Int = 0)

/** Names are stored one per line and tab-separated, so line breaks and tabs become spaces. */
fun oneLine(s: String, max: Int): String = s.replace(Regex("[\\t\\r\\n]+"), " ").trim().take(max)

/** One entry per line: `time TAB uri TAB folder TAB alias TAB pinned TAB pos TAB size TAB name`. Empty columns mean "none". */
fun encodeItems(l: List<RecentItem>): String = l.joinToString("\n") {
    listOf(
        it.time.toString(),
        it.uri,
        it.folder ?: "",
        it.alias ?: "",
        if (it.pinned) "1" else "",
        if (it.pos > 0L) it.pos.toString() else "",
        if (it.size >= 0L) it.size.toString() else "",
        it.name ?: ""
    ).joinToString("\t")
}

/** Reads [encodeItems] output, and also the shorter lines of older versions (time and uri; folder and alias). */
fun decodeItems(text: String): List<RecentItem> {
    return text.split("\n").mapNotNull { line ->
        val f = line.split('\t', limit = 8)
        if (f.size < 2 || f[1].isBlank()) {
            null
        } else {
            RecentItem(
                uri = f[1],
                time = f[0].toLongOrNull() ?: 0L,
                alias = f.getOrNull(3)?.takeIf { it.isNotBlank() },
                folder = f.getOrNull(2)?.takeIf { it.isNotBlank() },
                pinned = f.getOrNull(4) == "1",
                pos = f.getOrNull(5)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                name = f.getOrNull(7)?.takeIf { it.isNotBlank() },
                size = f.getOrNull(6)?.toLongOrNull()?.takeIf { it >= 0L } ?: -1L
            )
        }
    }.distinctBy { it.uri }
}

/** The entry [base] with its empty fields filled from [other] (the same file, seen twice). */
fun fillGaps(base: RecentItem, other: RecentItem): RecentItem = base.copy(
    time = maxOf(base.time, other.time),
    alias = base.alias ?: other.alias,
    folder = base.folder ?: other.folder,
    pinned = base.pinned || other.pinned,
    pos = if (base.pos > 0L) base.pos else other.pos,
    name = base.name ?: other.name,
    size = if (base.size >= 0L) base.size else other.size
)

/** What [Recents.mergeBackup] did: how many entries were new and how many already existed but got something added. */
data class ImportResult(val added: Int, val updated: Int)

/** Where [Recents] keeps its text: Android keeps it in SharedPreferences ([PrefsStore]), the tests keep it in a map. */
interface TextStore {
    fun get(key: String): String?

    /** Writes [value] under [key] and forgets every key in [drop]. */
    fun put(key: String, value: String, drop: List<String> = emptyList())
}

class PrefsStore(private val p: SharedPreferences) : TextStore {
    override fun get(key: String): String? = p.getString(key, null)

    override fun put(key: String, value: String, drop: List<String>) {
        val e = p.edit().putString(key, value)
        for (k in drop) e.remove(k)
        e.apply()
    }
}

/**
 * The list of opened files, newest first, together with what the person did with them: names, folders, stars and the
 * place where reading stopped. It lives in SharedPreferences (through a [TextStore]), so it is small and never touches the files.
 *
 * Storage format "v4" (see [encodeItems]); the older "v3" and "v2" and the first format with bare URIs (key "l") are
 * still read, and rewritten in the new format on the next save. Folders are stored under "folders", one
 * `id TAB name TAB color` per line (the colour column is missing in older data).
 *
 * The list keeps every entry the person organized (it is in a folder, starred or renamed) and the [MAX] newest of the
 * others. The cap is applied when entries are added, so moving or unstarring a file never makes it vanish at once.
 */
class Recents(private val store: TextStore, private val now: () -> Long = { System.currentTimeMillis() }) {
    constructor(c: Context) : this(PrefsStore(c.getSharedPreferences("recents", Context.MODE_PRIVATE)))

    // ---- the list ----

    /** All entries, newest first. An entry whose folder no longer exists is shown as not being in a folder. */
    fun list(): List<RecentItem> {
        val known = readFolders().map { it.id }.toSet()
        return readItems().map { if (it.folder != null && it.folder !in known) it.copy(folder = null) else it }
    }

    private fun readItems(): List<RecentItem> {
        for (key in listOf("v4", "v3", "v2")) {
            val s = store.get(key)
            if (s != null) return decodeItems(s)
        }
        val old = (store.get("l") ?: "").split("\n").filter { it.isNotBlank() }
        return old.map { RecentItem(it, 0L) }.distinctBy { it.uri }
    }

    private fun save(l: List<RecentItem>) {
        store.put("v4", encodeItems(l), listOf("v3", "v2", "l"))
    }

    private fun find(uri: String): RecentItem? = list().firstOrNull { it.uri == uri }

    /** Changes the entries with these URIs and saves, but only when something really changed. */
    private fun change(uris: Set<String>, f: (RecentItem) -> RecentItem) {
        var touched = false
        val out = list().map {
            if (it.uri in uris) {
                val n = f(it)
                if (n != it) touched = true
                n
            } else {
                it
            }
        }
        if (touched) save(out)
    }

    /** Newest first; keeps every organized entry and the [MAX] newest of the others. */
    private fun trim(l: List<RecentItem>): List<RecentItem> {
        val out = ArrayList<RecentItem>(l.size)
        var loose = 0
        for (item in l.sortedByDescending { it.time }) {
            if (item.folder != null || item.pinned || item.alias != null) {
                out.add(item)
            } else if (loose < MAX) {
                out.add(item)
                loose++
            }
        }
        return out
    }

    /**
     * Puts a file at the top of the list. Everything the person did with it, if it was in the list already, stays;
     * [folder] puts it into a folder at once (a file opened from inside that folder).
     */
    fun add(u: Uri, folder: String? = null) { add(u.toString(), folder) }

    fun add(uri: String, folder: String? = null) {
        val all = list()
        val old = all.firstOrNull { it.uri == uri }
        val entry = (old ?: RecentItem(uri, 0L)).copy(time = now(), folder = folder ?: old?.folder)
        save(trim(listOf(entry) + all.filter { it.uri != uri }))
    }

    fun remove(item: RecentItem) { removeAll(setOf(item.uri)) }

    fun removeAll(uris: Set<String>) { save(list().filter { it.uri !in uris }) }

    /** Puts a removed entry back (the "Undo" action). */
    fun restore(item: RecentItem) { restoreAll(listOf(item)) }

    fun restoreAll(items: List<RecentItem>) {
        val back = items.map { it.uri }.toSet()
        save(trim(list().filter { it.uri !in back } + items))
    }

    /** Empties the list. Files in folders and starred files stay: they are the person's own order. */
    fun clear() { save(list().filter { it.folder != null || it.pinned }) }

    // ---- names, stars, place ----

    fun aliasOf(uri: String): String? = find(uri)?.alias

    /** Gives a file a name inside List, or takes it back with null or a blank text. */
    fun setAlias(uri: String, alias: String?) {
        val a = alias?.let { oneLine(it, MAX_NAME) }?.takeIf { it.isNotEmpty() }
        change(setOf(uri)) { it.copy(alias = a) }
    }

    fun setPinned(uris: Set<String>, pinned: Boolean) { change(uris) { it.copy(pinned = pinned) } }

    fun posOf(uri: String): Long = find(uri)?.pos ?: 0L

    /** Remembers where reading stopped; 0 forgets it. */
    fun setPos(uri: String, pos: Long) {
        val v = pos.coerceAtLeast(0L)
        change(setOf(uri)) { it.copy(pos = v) }
    }

    /**
     * Remembers the real name and size of files that could be read just now (path to the entry: its URI). A file that
     * cannot be read later is then still shown by its name. An unknown size (negative) keeps the old one.
     */
    fun noteFiles(seen: Map<String, Pair<String, Long>>) {
        if (seen.isEmpty()) return
        var touched = false
        val out = list().map {
            val s = seen[it.uri]
            if (s == null) {
                it
            } else {
                val n = oneLine(s.first, MAX_FILE_NAME).takeIf { x -> x.isNotEmpty() } ?: it.name
                val c = it.copy(name = n, size = if (s.second >= 0L) s.second else it.size)
                if (c != it) touched = true
                c
            }
        }
        if (touched) save(out)
    }

    /**
     * Points an entry at another URI, because the file was picked again. Its name, folder, star and place stay,
     * and it moves to the top. If the new URI was in the list already, the two entries become one.
     */
    fun relink(oldUri: String, newUri: String) {
        if (oldUri == newUri) return
        val all = list()
        val old = all.firstOrNull { it.uri == oldUri } ?: return
        val same = all.firstOrNull { it.uri == newUri }
        val moved = fillGaps(old.copy(uri = newUri, time = now()), same ?: old.copy(uri = newUri))
        save(trim(listOf(moved) + all.filter { it.uri != oldUri && it.uri != newUri }))
    }

    // ---- folders ----

    /** All folders, sorted by name. */
    fun folders(): List<Folder> = readFolders().sortedBy { it.name.lowercase() }

    fun folderOf(uri: String): String? = find(uri)?.folder

    /** Makes a folder and returns it. A folder with the same name (any case) is returned instead of a twin. */
    fun createFolder(name: String, color: Int = 0): Folder? {
        val n = oneLine(name, MAX_NAME)
        if (n.isEmpty()) return null
        val all = readFolders()
        val same = all.firstOrNull { it.name.equals(n, ignoreCase = true) }
        if (same != null) return same
        val f = Folder(newId(all), n, color.coerceIn(0, COLORS - 1))
        writeFolders(all + f)
        return f
    }

    /** Changes the name and the colour of a folder. A blank name keeps the old one. */
    fun updateFolder(id: String, name: String, color: Int) {
        val n = oneLine(name, MAX_NAME)
        val c = color.coerceIn(0, COLORS - 1)
        writeFolders(readFolders().map { if (it.id == id) it.copy(name = n.ifEmpty { it.name }, color = c) else it })
    }

    /** Deletes a folder; its files go back to the main list. */
    fun deleteFolder(id: String) { writeFolders(readFolders().filter { it.id != id }) }

    /** Moves a file into a folder, or out of any folder with null. */
    fun moveTo(uri: String, folder: String?) { moveAll(setOf(uri), folder) }

    fun moveAll(uris: Set<String>, folder: String?) { change(uris) { it.copy(folder = folder) } }

    private fun newId(taken: List<Folder>): String {
        while (true) {
            val id = UUID.randomUUID().toString().substring(0, 8)
            if (taken.none { it.id == id }) return id
        }
    }

    private fun readFolders(): List<Folder> {
        val s = store.get("folders") ?: ""
        return s.split("\n").mapNotNull { line ->
            val a = line.split('\t')
            if (a.size < 2 || a[0].isEmpty() || a[1].isEmpty()) {
                null
            } else {
                Folder(a[0], a[1], (a.getOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, COLORS - 1))
            }
        }.distinctBy { it.id }
    }

    private fun writeFolders(l: List<Folder>) {
        store.put("folders", l.joinToString("\n") { it.id + "\t" + it.name + "\t" + it.color })
    }

    // ---- backup ----

    /** Everything a backup holds. */
    fun backup(): Backup = Backup(folders(), list())

    /**
     * Merges a backup into the list. Entries that are not here yet are added; for the ones that are, only what is
     * empty here is filled in (a name, a folder, a star, a place), so nothing the person has now is overwritten.
     * A folder with the same id or the same name is reused instead of being made twice.
     */
    fun mergeBackup(b: Backup): ImportResult {
        val mine = ArrayList(readFolders())
        val idOf = HashMap<String, String>()
        for (f in b.folders) {
            val same = mine.firstOrNull { it.id == f.id } ?: mine.firstOrNull { it.name.equals(f.name, ignoreCase = true) }
            if (same != null) {
                idOf[f.id] = same.id
            } else {
                mine.add(f.copy(color = f.color.coerceIn(0, COLORS - 1)))
                idOf[f.id] = f.id
            }
        }
        val all = LinkedHashMap<String, RecentItem>()
        for (e in list()) all[e.uri] = e
        var added = 0
        var updated = 0
        for (raw in b.files) {
            val item = raw.copy(folder = raw.folder?.let { idOf[it] })
            val have = all[item.uri]
            if (have == null) {
                all[item.uri] = item
                added++
            } else {
                val merged = fillGaps(have, item)
                if (merged != have) {
                    all[item.uri] = merged
                    updated++
                }
            }
        }
        writeFolders(mine)
        save(trim(all.values.toList()))
        return ImportResult(added, updated)
    }

    companion object {
        /** How many entries that were not organized the list keeps. */
        const val MAX = 50

        /** Longest name of a file (as shown) or of a folder. */
        const val MAX_NAME = 80

        /** Longest real file name that is remembered. */
        const val MAX_FILE_NAME = 255

        /** How many colours a folder can have (index 0 is the default one). The palette in the UI must have this many. */
        const val COLORS = 8
    }
}
