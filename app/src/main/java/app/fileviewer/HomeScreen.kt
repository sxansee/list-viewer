package app.fileviewer

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Names, sizes and availability of recent files, kept while the process lives. They let the list paint at once when
 * you come back from a document; every row is still queried again in the background to catch deleted files.
 */
private val infoCache = HashMap<String, Info>()

/** 0 = today, 1 = yesterday, 2 = within the last week, 3 = earlier (or unknown). */
private fun dayBucket(t: Long): Int {
    if (t <= 0L) return 3
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val today = cal.timeInMillis
    val day = 24L * 60L * 60L * 1000L
    return when {
        t >= today -> 0
        t >= today - day -> 1
        t >= today - 6L * day -> 2
        else -> 3
    }
}

private fun bucketTitle(bucket: Int): Int = when (bucket) {
    0 -> R.string.section_today
    1 -> R.string.section_yesterday
    2 -> R.string.section_week
    else -> R.string.section_earlier
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    items: List<RecentItem>,
    onOpen: (Uri) -> Unit,
    onRemove: (RecentItem) -> Unit,
    onRestore: (RecentItem) -> Unit,
    onClear: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val snack = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Cat?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var infos by remember { mutableStateOf<Map<String, Info>>(HashMap(infoCache)) }
    val fresh = remember { HashSet<String>() }

    val removedText = stringResource(R.string.removed)
    val undoText = stringResource(R.string.undo)
    val unavailableText = stringResource(R.string.file_unavailable)
    val removeText = stringResource(R.string.remove)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            onOpen(u)
        }
    }

    // Names, sizes and availability are read off the main thread; rows appear as soon as their data is ready.
    LaunchedEffect(items) {
        val todo = items.filter { !fresh.contains(it.uri) }
        if (todo.isNotEmpty()) {
            val got = withContext(Dispatchers.IO) {
                todo.associate { it.uri to infoOf(ctx, Uri.parse(it.uri)) }
            }
            fresh.addAll(got.keys)
            infoCache.putAll(got)
            infos = infos + got
        }
    }

    val loaded = items.mapNotNull { r -> infos[r.uri]?.let { Pair(r, it) } }
    val cats = Cat.entries.filter { c -> loaded.any { it.second.cat == c } }
    val q = query.trim()
    val shown = loaded.filter { p ->
        (filter == null || p.second.cat == filter) && (q.isEmpty() || p.second.name.contains(q, ignoreCase = true))
    }
    val groups = shown.groupBy { dayBucket(it.first.time) }
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    val remove: (RecentItem) -> Unit = { item ->
        onRemove(item)
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            val r = snack.showSnackbar(message = removedText, actionLabel = undoText, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onRestore(item)
        }
    }
    val open: (RecentItem, Info) -> Unit = { item, info ->
        if (info.available) {
            onOpen(Uri.parse(item.uri))
        } else {
            scope.launch {
                snack.currentSnackbarData?.dismiss()
                val r = snack.showSnackbar(message = unavailableText, actionLabel = removeText, duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) onRemove(item)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    LogoTile(36.dp, Modifier.padding(start = 16.dp, end = 4.dp), stringResource(R.string.cd_logo))
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Ico(R.drawable.ic_more_vert, stringResource(R.string.cd_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (items.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_clear_list)) },
                                    leadingIcon = { Ico(R.drawable.ic_trash) },
                                    onClick = { menu = false; confirmClear = true }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_about)) },
                                leadingIcon = { Ico(R.drawable.ic_info) },
                                onClick = { menu = false; about = true }
                            )
                        }
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            if (items.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    expanded = fabExpanded,
                    icon = { Ico(R.drawable.ic_folder_open) },
                    text = { Text(stringResource(R.string.open_file)) }
                )
            }
        }
    ) { pad ->
        if (items.isEmpty()) {
            EmptyState(onPick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.padding(pad))
        } else {
            LazyColumn(
                modifier = Modifier.padding(pad).fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 112.dp)
            ) {
                item(key = "search") {
                    SearchField(query, { query = it }, { focus.clearFocus() })
                }
                if (cats.size > 1) {
                    item(key = "chips") {
                        FilterRow(cats, filter) { filter = it }
                    }
                }
                if (shown.isEmpty() && loaded.isNotEmpty()) {
                    item(key = "none") { NoResults() }
                }
                groups.forEach { (bucket, rows) ->
                    item(key = "header-$bucket") {
                        Text(
                            stringResource(bucketTitle(bucket)),
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(rows, key = { it.first.uri }) { row ->
                        val item = row.first
                        val info = row.second
                        val mime = info.mime ?: mimeOf(info.ext)
                        RecentRow(
                            info = info,
                            time = item.time,
                            bucket = bucket,
                            onOpen = { open(item, info) },
                            onShare = { shareFile(ctx, Uri.parse(item.uri), mime) },
                            onExternal = { openExternal(ctx, Uri.parse(item.uri), mime) },
                            onRemove = { remove(item) }
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
            text = { Text(stringResource(R.string.clear_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (about) AboutDialog { about = false }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Ico(R.drawable.ic_search) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Ico(R.drawable.ic_close, stringResource(R.string.cd_clear_search))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(cats: List<Cat>, selected: Cat?, onSelect: (Cat?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.filter_all)) },
                leadingIcon = if (selected == null) {
                    { Ico(R.drawable.ic_check, null, Modifier.size(18.dp)) }
                } else {
                    null
                }
            )
        }
        items(cats) { c ->
            FilterChip(
                selected = selected == c,
                onClick = { onSelect(if (selected == c) null else c) },
                label = { Text(stringResource(c.chipRes)) },
                leadingIcon = if (selected == c) {
                    { Ico(R.drawable.ic_check, null, Modifier.size(18.dp)) }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun NoResults() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.no_results), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.no_results_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RecentRow(
    info: Info,
    time: Long,
    bucket: Int,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onExternal: () -> Unit,
    onRemove: () -> Unit
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val typeText = stringResource(info.cat.titleRes)
    val missingText = stringResource(R.string.file_unavailable_short)
    val meta = remember(info, time, bucket, typeText, missingText) {
        if (!info.available) {
            missingText
        } else {
            val parts = ArrayList<String>()
            parts.add(typeText)
            if (info.size >= 0L) parts.add(Formatter.formatShortFileSize(ctx, info.size))
            if (time > 0L) {
                parts.add(
                    if (bucket <= 1) DateUtils.formatDateTime(ctx, time, DateUtils.FORMAT_SHOW_TIME)
                    else DateUtils.formatDateTime(ctx, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
                )
            }
            parts.joinToString("  ·  ")
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardColor())
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileTile(info)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                info.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (info.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = if (info.available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Ico(R.drawable.ic_more_vert, stringResource(R.string.cd_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (info.available) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share)) },
                        leadingIcon = { Ico(R.drawable.ic_share) },
                        onClick = { menu = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_open_external)) },
                        leadingIcon = { Ico(R.drawable.ic_external) },
                        onClick = { menu = false; onExternal() }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_remove_from_list)) },
                    leadingIcon = { Ico(R.drawable.ic_close) },
                    onClick = { menu = false; onRemove() }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoTile(112.dp)
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExtTile("PDF", Cat.PDF.color)
            ExtTile("DOC", Cat.DOC.color)
            ExtTile("XLS", Cat.SHEET.color)
            ExtTile("PPT", Cat.SLIDE.color)
            ExtTile("ZIP", Cat.ZIP.color)
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onPick,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Ico(R.drawable.ic_folder_open, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.choose_file))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.empty_offline),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AboutDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        try {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) { "" }
    }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { LogoTile(56.dp) },
        title = {
            Text(
                stringResource(R.string.app_name),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                if (version.isNotEmpty()) {
                    Text(
                        stringResource(R.string.about_version, version),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.about_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } }
    )
}
