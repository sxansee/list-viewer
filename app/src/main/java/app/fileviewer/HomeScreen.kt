package app.fileviewer

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Names, sizes and availability of recent files, kept while the process lives. They let the list paint at once when
 * you come back from a document; every row is still queried again in the background to catch deleted files.
 */
private val infoCache = HashMap<String, Info>()

/** What the list knew about a file the last time it looked, or null for a file it has not seen. Used by the main thread only. */
fun cachedInfo(uri: String): Info? = infoCache[uri]

/** Marks "the selected files are in different folders" for [MoveDialog], whose current choice is then none of the folders. */
private const val MIXED = "\u0000mixed"

/** A file of the list together with what is known about it right now. */
private class Entry(val item: RecentItem, val info: Info) {
    /** The name as shown: the one chosen in List plus the real extension, or the file's own name. */
    val name: String get() = displayName(info, item.alias)
}

/** A part of the list under one heading ([title] is a string resource; none means no heading). */
private class Section(val key: String, val title: Int?, val rows: List<Entry>)

private fun order(rows: List<Entry>, sort: Sort): List<Entry> = sortRows(
    rows,
    sort,
    time = { it.item.time },
    name = { it.name },
    size = { it.info.size },
    type = { it.info.cat.ordinal }
)

/**
 * Splits the shown files into the parts of the screen. Pinned files come first, in their own part. The rest is divided
 * by day when the list is ordered by time and by type when it is ordered by type; by name or size it is one part.
 */
private fun sectionsOf(rows: List<Entry>, sort: Sort): List<Section> {
    val out = ArrayList<Section>()
    val pinned = rows.filter { it.item.pinned }
    val rest = rows.filter { !it.item.pinned }
    if (pinned.isNotEmpty()) out.add(Section("pinned", R.string.section_pinned, order(pinned, sort)))
    when (sort) {
        Sort.RECENT -> {
            for ((bucket, list) in order(rest, sort).groupBy { dayBucket(it.item.time) }) {
                out.add(Section("day-$bucket", bucketTitle(bucket), list))
            }
        }
        Sort.TYPE -> {
            for ((cat, list) in order(rest, sort).groupBy { it.info.cat }) {
                out.add(Section("type-" + cat.name, cat.titleRes, list))
            }
        }
        else -> {
            if (rest.isNotEmpty()) {
                out.add(Section("rest", if (pinned.isNotEmpty()) R.string.section_others else null, order(rest, sort)))
            }
        }
    }
    return out
}

private fun backupName(): String =
    "List-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"

/**
 * The home screen: the files that were opened, with search, a filter by type, an order of the person's choice and
 * several files at once (long press to start selecting).
 *
 * Files can be put into folders (made in List, see [Recents]). The main list shows the files that are not in a folder
 * and a strip of folders on top; [folder] is the id of the open folder, or null for the main list. A search in the
 * main list looks through all files. Everything the person changes goes straight to [store]; [onChanged] then reloads
 * the data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    items: List<RecentItem>,
    folders: List<Folder>,
    folder: String?,
    store: Recents,
    settings: Settings,
    onFolder: (String?) -> Unit,
    onChanged: () -> Unit,
    onOpen: (Uri) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val snack = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val sort = settings.sort

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Cat?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<RecentItem?>(null) }
    var moving by remember { mutableStateOf<Set<String>?>(null) }
    var locating by remember { mutableStateOf<RecentItem?>(null) }
    var restored by remember { mutableStateOf<ImportResult?>(null) }
    var askNewFolder by remember { mutableStateOf(false) }
    var askEditFolder by remember { mutableStateOf(false) }
    var askDeleteFolder by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf<Set<String>>(emptySet()) }
    var infos by remember { mutableStateOf<Map<String, Info>>(HashMap(infoCache)) }
    var reload by remember { mutableStateOf(0) }
    val fresh = remember { HashSet<String>() }

    // The open folder. One that no longer exists (deleted, or a stale saved state) simply means the main list.
    val current = folders.firstOrNull { it.id == folder }
    LaunchedEffect(folder, current) {
        if (folder != null && current == null) onFolder(null)
    }
    val goTo: (String?) -> Unit = { id ->
        query = ""
        filter = null
        sel = emptySet()
        onFolder(id)
    }
    BackHandler(enabled = current != null) { goTo(null) }
    LaunchedEffect(current?.id) { listState.scrollToItem(0) }

    // The files that are selected (the ones that are gone from the list are dropped), and the way out of selecting.
    val known = items.map { it.uri }.toSet()
    val picked = sel.filter { it in known }.toSet()
    val selecting = picked.isNotEmpty()
    BackHandler(enabled = selecting) { sel = emptySet() }

    val removedText = stringResource(R.string.removed)
    val undoText = stringResource(R.string.undo)
    val unavailableText = stringResource(R.string.file_unavailable)
    val locateText = stringResource(R.string.locate_short)

    val saveCopy = rememberCopySaver()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) {
            keepRead(ctx, u)
            // A file opened from inside a folder is put into that folder.
            if (current != null) store.add(u, current.id)
            onOpen(u)
        }
    }

    // A file that cannot be reached is pointed at its new place; the entry keeps its name, folder, star and page.
    val locator = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        val old = locating
        locating = null
        if (u != null && old != null) {
            keepRead(ctx, u)
            store.relink(old.uri, u.toString())
            // The old address is of no use any more.
            if (old.uri != u.toString()) releaseRead(ctx, Uri.parse(old.uri))
            // The same file picked again has the same address, and so a new check is needed to see it is back.
            fresh.remove(u.toString())
            reload++
            onChanged()
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { target: Uri? ->
        if (target != null) {
            val b = store.backup()
            CoroutineScope(Dispatchers.IO).launch {
                val ok = writeText(ctx, target, backupToJson(b.folders, b.files, System.currentTimeMillis()))
                withContext(Dispatchers.Main) { toast(ctx, if (ok) R.string.backup_saved else R.string.backup_failed) }
            }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) {
            scope.launch {
                val b = withContext(Dispatchers.IO) { readBackup(ctx, u) }
                if (b == null) {
                    toast(ctx, R.string.restore_failed)
                } else {
                    val r = store.mergeBackup(b)
                    onChanged()
                    if (r.added == 0 && r.updated == 0) toast(ctx, R.string.restore_nothing) else restored = r
                }
            }
        }
    }

    // Names, sizes and availability are read off the main thread; rows appear as soon as their data is ready.
    // What was read is also written down, so that a file that goes missing later is still shown by its name.
    LaunchedEffect(items, reload) {
        val todo = items.filter { !fresh.contains(it.uri) }
        if (todo.isNotEmpty()) {
            val got = withContext(Dispatchers.IO) {
                todo.associate { it.uri to infoOf(ctx, Uri.parse(it.uri), it.name, it.size) }
            }
            fresh.addAll(got.keys)
            infoCache.putAll(got)
            infos = infos + got
            val seen = HashMap<String, Pair<String, Long>>()
            for (r in todo) {
                val i = got[r.uri]
                if (i != null && i.available && (r.name != i.name || (i.size >= 0L && r.size != i.size))) {
                    seen[r.uri] = Pair(i.name, i.size)
                }
            }
            if (seen.isNotEmpty()) store.noteFiles(seen)
        }
    }

    val q = query.trim()
    val loaded = items.mapNotNull { r -> infos[r.uri]?.let { Entry(r, it) } }
    // The main list shows the files that are not in a folder, but a search looks through all of them.
    val inScope: (RecentItem) -> Boolean = { r ->
        when {
            current != null -> r.folder == current.id
            q.isNotEmpty() -> true
            else -> r.folder == null
        }
    }
    val scoped = items.count { inScope(it) }
    val pool = loaded.filter { inScope(it.item) }
    val cats = Cat.entries.filter { c -> pool.any { it.info.cat == c } }
    val activeFilter = filter?.takeIf { it in cats }
    val shown = pool.filter { p ->
        (activeFilter == null || p.info.cat == activeFilter) &&
            (q.isEmpty() || p.info.name.contains(q, ignoreCase = true) || p.name.contains(q, ignoreCase = true))
    }
    val sections = sectionsOf(shown, sort)
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    val toggle: (String) -> Unit = { u -> sel = if (u in sel) sel - u else sel + u }

    val removeItems: (List<RecentItem>) -> Unit = { gone ->
        if (gone.isNotEmpty()) {
            val uris = gone.map { it.uri }.toSet()
            store.removeAll(uris)
            sel = sel - uris
            onChanged()
            scope.launch {
                snack.currentSnackbarData?.dismiss()
                val message = if (gone.size == 1) removedText else ctx.getString(R.string.removed_many, gone.size)
                val r = snack.showSnackbar(message = message, actionLabel = undoText, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) {
                    store.restoreAll(gone)
                    onChanged()
                } else {
                    // The removal is final now, so the lasting access to these files is given back (unless a file came back meanwhile).
                    val still = store.list().map { it.uri }.toSet()
                    for (g in gone) if (g.uri !in still) releaseRead(ctx, Uri.parse(g.uri))
                }
            }
        }
    }
    val open: (RecentItem, Info) -> Unit = { item, info ->
        if (info.available) {
            onOpen(Uri.parse(item.uri))
        } else {
            scope.launch {
                snack.currentSnackbarData?.dismiss()
                val r = snack.showSnackbar(message = unavailableText, actionLabel = locateText, duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) {
                    locating = item
                    locator.launch(arrayOf("*/*"))
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selecting) {
                val chosen = items.filter { it.uri in picked }
                SelectionBar(
                    count = picked.size,
                    allPinned = chosen.all { it.pinned },
                    canShare = loaded.any { it.item.uri in picked && it.info.available },
                    onCancel = { sel = emptySet() },
                    onSelectAll = { sel = shown.map { it.item.uri }.toSet() },
                    onShare = {
                        shareFiles(
                            ctx,
                            loaded.filter { it.item.uri in picked && it.info.available }.map { Uri.parse(it.item.uri) }
                        )
                    },
                    onPin = {
                        store.setPinned(picked, !chosen.all { it.pinned })
                        sel = emptySet()
                        onChanged()
                    },
                    onMove = { moving = picked },
                    onRemove = { removeItems(chosen) }
                )
            } else {
                LargeTopAppBar(
                    title = {
                        Text(
                            current?.name ?: stringResource(R.string.app_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (current != null) {
                            IconButton(onClick = { goTo(null) }) {
                                Ico(R.drawable.ic_arrow_back, stringResource(R.string.cd_back))
                            }
                        } else {
                            LogoTile(36.dp, Modifier.padding(start = 16.dp, end = 4.dp), stringResource(R.string.cd_logo))
                        }
                    },
                    actions = {
                        if (items.isNotEmpty()) {
                            SortButton(sort) { settings.updateSort(it) }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Ico(R.drawable.ic_more_vert, stringResource(R.string.cd_more))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (current != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.folder_rename)) },
                                        leadingIcon = { Ico(R.drawable.ic_edit) },
                                        onClick = { menu = false; askEditFolder = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.folder_delete)) },
                                        leadingIcon = { Ico(R.drawable.ic_trash) },
                                        onClick = { menu = false; askDeleteFolder = true }
                                    )
                                } else {
                                    if (items.any { it.folder == null && !it.pinned }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_clear_list)) },
                                            leadingIcon = { Ico(R.drawable.ic_trash) },
                                            onClick = { menu = false; confirmClear = true }
                                        )
                                    }
                                    if (items.isNotEmpty() || folders.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_backup)) },
                                            leadingIcon = { Ico(R.drawable.ic_download) },
                                            onClick = { menu = false; exporter.launch(backupName()) }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_restore)) },
                                        leadingIcon = { Ico(R.drawable.ic_upload) },
                                        onClick = { menu = false; importer.launch(arrayOf("*/*")) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_about)) },
                                        leadingIcon = { Ico(R.drawable.ic_info) },
                                        onClick = { menu = false; about = true }
                                    )
                                }
                            }
                        }
                    },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            if ((items.isNotEmpty() || current != null) && !selecting) {
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    expanded = fabExpanded,
                    icon = { Ico(R.drawable.ic_folder_open) },
                    text = { Text(stringResource(R.string.open_file)) }
                )
            }
        }
    ) { pad ->
        if (items.isEmpty() && current == null) {
            EmptyState(onPick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.padding(pad))
        } else {
            LazyColumn(
                modifier = Modifier.padding(pad).fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 112.dp)
            ) {
                item(key = "search") {
                    SearchField(
                        value = query,
                        hint = stringResource(if (current != null) R.string.search_hint_folder else R.string.search_hint),
                        onChange = {
                            query = it
                            sel = emptySet()
                        },
                        onDone = { focus.clearFocus() }
                    )
                }
                if (current == null && q.isEmpty()) {
                    item(key = "folders") {
                        FolderStrip(folders, items, onOpen = { goTo(it) }, onNew = { askNewFolder = true })
                    }
                }
                if (cats.size > 1) {
                    item(key = "chips") {
                        FilterRow(cats, activeFilter) {
                            filter = it
                            sel = emptySet()
                        }
                    }
                }
                if (scoped == 0) {
                    item(key = "empty") {
                        if (current != null) {
                            Hint(R.string.folder_empty_title, R.string.folder_empty_body)
                        } else {
                            Hint(R.string.sorted_title, R.string.sorted_body)
                        }
                    }
                } else if (shown.isEmpty() && pool.isNotEmpty()) {
                    item(key = "none") { Hint(R.string.no_results, R.string.no_results_hint) }
                }
                for (section in sections) {
                    if (section.title != null) {
                        item(key = "header-" + section.key) { SectionHeader(section.title) }
                    }
                    items(section.rows, key = { it.item.uri }) { e ->
                        val item = e.item
                        val info = e.info
                        val uri = Uri.parse(item.uri)
                        val mime = info.mime ?: mimeOf(info.ext)
                        RecentRow(
                            info = info,
                            name = e.name,
                            pinned = item.pinned,
                            // In search results the folder is named, because the file may not be where one would look.
                            folderName = if (current == null) folders.firstOrNull { it.id == item.folder }?.name else null,
                            time = item.time,
                            bucket = dayBucket(item.time),
                            selecting = selecting,
                            selected = item.uri in picked,
                            onClick = { if (selecting) toggle(item.uri) else open(item, info) },
                            onLongClick = { toggle(item.uri) },
                            actions = RowActions(
                                share = { shareFile(ctx, uri, mime) },
                                openOther = { openExternal(ctx, uri, mime) },
                                saveCopy = { saveCopy(uri, e.name) },
                                locate = {
                                    locating = item
                                    locator.launch(arrayOf("*/*"))
                                },
                                pin = {
                                    store.setPinned(setOf(item.uri), !item.pinned)
                                    onChanged()
                                },
                                rename = { renaming = item },
                                move = { moving = setOf(item.uri) },
                                select = { sel = sel + item.uri },
                                remove = { removeItems(listOf(item)) }
                            )
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_title)) },
            text = {
                val organized = items.any { it.folder != null || it.pinned }
                Text(stringResource(if (organized) R.string.clear_body_folders else R.string.clear_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        // What is cleared: the files that are in no folder and not starred (see Recents.clear).
                        val gone = items.filter { it.folder == null && !it.pinned }
                        store.clear()
                        onChanged()
                        for (g in gone) releaseRead(ctx, Uri.parse(g.uri))
                    }
                ) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (about) AboutDialog { about = false }

    restored?.let { r ->
        AlertDialog(
            onDismissRequest = { restored = null },
            title = { Text(stringResource(R.string.restore_title)) },
            text = { Text(stringResource(R.string.restore_body, r.added, r.updated)) },
            confirmButton = { TextButton(onClick = { restored = null }) { Text(stringResource(R.string.close)) } }
        )
    }

    renaming?.let { r ->
        val info = infos[r.uri]
        if (info != null) {
            RenameDialog(
                info = info,
                alias = r.alias,
                onSave = { a ->
                    store.setAlias(r.uri, a)
                    renaming = null
                    onChanged()
                },
                onDismiss = { renaming = null }
            )
        }
    }
    moving?.let { uris ->
        val homes = items.filter { it.uri in uris }.map { it.folder }.distinct()
        MoveDialog(
            folders = folders,
            current = if (homes.size == 1) homes[0] else MIXED,
            onPick = { id ->
                store.moveAll(uris, id)
                moving = null
                sel = emptySet()
                onChanged()
            },
            onCreate = { name, color ->
                val f = store.createFolder(name, color)
                if (f != null) store.moveAll(uris, f.id)
                moving = null
                sel = emptySet()
                onChanged()
            },
            onDismiss = { moving = null }
        )
    }
    if (askNewFolder) {
        FolderDialog(
            title = R.string.folder_new,
            initial = "",
            initialColor = 0,
            confirm = R.string.create,
            taken = folders.map { it.name },
            onSave = { name, color ->
                store.createFolder(name, color)
                askNewFolder = false
                onChanged()
            },
            onDismiss = { askNewFolder = false }
        )
    }
    if (askEditFolder && current != null) {
        FolderDialog(
            title = R.string.folder_rename,
            initial = current.name,
            initialColor = current.color,
            confirm = R.string.save,
            taken = folders.filter { it.id != current.id }.map { it.name },
            onSave = { name, color ->
                store.updateFolder(current.id, name, color)
                askEditFolder = false
                onChanged()
            },
            onDismiss = { askEditFolder = false }
        )
    }
    if (askDeleteFolder && current != null) {
        AlertDialog(
            onDismissRequest = { askDeleteFolder = false },
            title = { Text(stringResource(R.string.folder_delete_title, current.name)) },
            text = { Text(stringResource(R.string.folder_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askDeleteFolder = false
                        store.deleteFolder(current.id)
                        goTo(null)
                        onChanged()
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { askDeleteFolder = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
