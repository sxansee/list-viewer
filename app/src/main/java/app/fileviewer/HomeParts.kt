package app.fileviewer

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar

// The pieces of the home screen (see HomeScreen.kt): search, folders, filter chips, rows, the empty state, About.

/** 0 = today, 1 = yesterday, 2 = within the last week, 3 = earlier (or unknown). */
fun dayBucket(t: Long): Int {
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

fun bucketTitle(bucket: Int): Int = when (bucket) {
    0 -> R.string.section_today
    1 -> R.string.section_yesterday
    2 -> R.string.section_week
    else -> R.string.section_earlier
}

@Composable
fun SectionHeader(title: Int) {
    Text(
        stringResource(title),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun SearchField(value: String, hint: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(hint) },
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

/** The folders as a row of cards, with a button for a new one in front. */
@Composable
fun FolderStrip(folders: List<Folder>, all: List<RecentItem>, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val res = LocalContext.current.resources
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "new") { NewFolderCard(onNew) }
        items(folders, key = { it.id }) { f ->
            val n = all.count { it.folder == f.id }
            FolderCard(f.name, f.color, res.getQuantityString(R.plurals.folder_files, n, n)) { onOpen(f.id) }
        }
    }
}

@Composable
private fun FolderCard(name: String, color: Int, count: String, onClick: () -> Unit) {
    val tile = folderTile(color)
    Row(
        Modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cardColor())
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tile.first),
            contentAlignment = Alignment.Center
        ) {
            Ico(R.drawable.ic_folder, null, Modifier.size(20.dp), tile.second)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                count,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NewFolderCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(R.drawable.ic_folder_plus, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.folder_new),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterRow(cats: List<Cat>, selected: Cat?, onSelect: (Cat?) -> Unit) {
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

/** A centred title with a hint below it: no search results, an empty folder and the like. */
@Composable
fun Hint(title: Int, body: Int) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** What the menu of a row can do. */
class RowActions(
    val share: () -> Unit,
    val openOther: () -> Unit,
    val saveCopy: () -> Unit,
    val locate: () -> Unit,
    val pin: () -> Unit,
    val rename: () -> Unit,
    val move: () -> Unit,
    val select: () -> Unit,
    val remove: () -> Unit
)

/**
 * One file of the list. [selecting] is true while a selection is being made: the menu button is hidden then, a tap
 * calls [onClick] to toggle the row, and a selected row shows a tick instead of its tile. A long press calls
 * [onLongClick] (which starts or extends the selection).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentRow(
    info: Info,
    name: String,
    pinned: Boolean,
    folderName: String?,
    time: Long,
    bucket: Int,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    actions: RowActions
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val typeText = stringResource(info.cat.titleRes)
    val missingText = stringResource(R.string.file_unavailable_short)
    val meta = remember(info, time, bucket, typeText, missingText, folderName) {
        if (!info.available) {
            missingText
        } else {
            val parts = ArrayList<String>()
            if (folderName != null) parts.add(folderName)
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
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else cardColor())
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = if (selecting) 12.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) CheckBadge() else FileTile(info)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (info.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (pinned) {
                    Spacer(Modifier.width(6.dp))
                    Ico(
                        R.drawable.ic_star_filled,
                        stringResource(R.string.cd_pinned),
                        Modifier.size(14.dp),
                        MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = if (info.available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!selecting) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Ico(R.drawable.ic_more_vert, stringResource(R.string.cd_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (info.available) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Ico(R.drawable.ic_share) },
                            onClick = { menu = false; actions.share() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open_external)) },
                            leadingIcon = { Ico(R.drawable.ic_external) },
                            onClick = { menu = false; actions.openOther() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_save_copy)) },
                            leadingIcon = { Ico(R.drawable.ic_download) },
                            onClick = { menu = false; actions.saveCopy() }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_locate)) },
                            leadingIcon = { Ico(R.drawable.ic_search) },
                            onClick = { menu = false; actions.locate() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(if (pinned) R.string.action_unpin else R.string.action_pin)) },
                        leadingIcon = { Ico(if (pinned) R.drawable.ic_star_filled else R.drawable.ic_star) },
                        onClick = { menu = false; actions.pin() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = { Ico(R.drawable.ic_edit) },
                        onClick = { menu = false; actions.rename() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move)) },
                        leadingIcon = { Ico(R.drawable.ic_folder) },
                        onClick = { menu = false; actions.move() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_select)) },
                        leadingIcon = { Ico(R.drawable.ic_select_all) },
                        onClick = { menu = false; actions.select() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_remove_from_list)) },
                        leadingIcon = { Ico(R.drawable.ic_close) },
                        onClick = { menu = false; actions.remove() }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(onPick: () -> Unit, modifier: Modifier = Modifier) {
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
fun AboutDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val about = remember { appInfo(ctx) }
    val crash = remember { crashStore(ctx).last() }
    val appName = stringResource(R.string.app_name)
    AlertDialog(
        onDismissRequest = onClose,
        icon = { LogoTile(56.dp) },
        title = {
            Text(
                appName,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                if (about.version.isNotEmpty()) {
                    Text(
                        stringResource(R.string.about_version, about.version),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (about.debug) {
                        // A debug build is what the repository falls back to when the optimised one cannot be made.
                        Text(
                            stringResource(R.string.about_debug),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.about_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val kind = keyKindOf(about.signature)
                if (kind != KeyKind.UNKNOWN) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.about_sign, shortFingerprint(about.signature)),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        stringResource(if (kind == KeyKind.REPOSITORY) R.string.about_sign_public else R.string.about_sign_own),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (crash != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { copyText(ctx, appName, crash, R.string.crash_copied) },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) { Text(stringResource(R.string.about_crash)) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                copyText(ctx, appName, appDetails(ctx, about), R.string.about_copied)
            }) { Text(stringResource(R.string.about_copy)) }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } }
    )
}

/**
 * Shown once, after the app has closed because of an error: the report about it was saved on the device, and can be
 * copied (to send to the developer). Nothing is sent anywhere by the app itself.
 */
@Composable
fun CrashDialog(report: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val appName = stringResource(R.string.app_name)
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Ico(R.drawable.ic_warning, null) },
        title = { Text(stringResource(R.string.crash_title)) },
        text = { Text(stringResource(R.string.crash_body), style = MaterialTheme.typography.bodyMedium) },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
        confirmButton = {
            TextButton(onClick = {
                copyText(ctx, appName, report, R.string.crash_copied)
                onClose()
            }) { Text(stringResource(R.string.crash_copy)) }
        }
    )
}

private fun sortLabel(s: Sort): Int = when (s) {
    Sort.RECENT -> R.string.sort_recent
    Sort.NAME -> R.string.sort_name
    Sort.SIZE -> R.string.sort_size
    Sort.TYPE -> R.string.sort_type
}

/** The button in the top bar that opens the menu with the orders of the list; the one in use carries a tick. */
@Composable
fun SortButton(current: Sort, onPick: (Sort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Ico(R.drawable.ic_sort, stringResource(R.string.cd_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                stringResource(R.string.sort_title),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for (s in Sort.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(sortLabel(s))) },
                    trailingIcon = if (s == current) {
                        { Ico(R.drawable.ic_check, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = { open = false; onPick(s) }
                )
            }
        }
    }
}

/**
 * The top bar while files are being selected: how many, and what can be done with them all. The most used actions are
 * buttons; "select all" and "share" are in the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionBar(
    count: Int,
    allPinned: Boolean,
    canShare: Boolean,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onPin: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val on = MaterialTheme.colorScheme.onSecondaryContainer
    TopAppBar(
        title = {
            Text(stringResource(R.string.selected_count, count), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Ico(R.drawable.ic_close, stringResource(R.string.cd_cancel_selection))
            }
        },
        actions = {
            IconButton(onClick = onPin) {
                Ico(
                    if (allPinned) R.drawable.ic_star_filled else R.drawable.ic_star,
                    stringResource(if (allPinned) R.string.action_unpin else R.string.action_pin)
                )
            }
            IconButton(onClick = onMove) {
                Ico(R.drawable.ic_folder, stringResource(R.string.action_move))
            }
            IconButton(onClick = onRemove) {
                Ico(R.drawable.ic_trash, stringResource(R.string.action_remove_from_list))
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Ico(R.drawable.ic_more_vert, stringResource(R.string.cd_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_select_all)) },
                        leadingIcon = { Ico(R.drawable.ic_select_all) },
                        onClick = { menu = false; onSelectAll() }
                    )
                    if (canShare) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Ico(R.drawable.ic_share) },
                            onClick = { menu = false; onShare() }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            navigationIconContentColor = on,
            titleContentColor = on,
            actionIconContentColor = on
        )
    )
}
