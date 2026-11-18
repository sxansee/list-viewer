package app.fileviewer

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A single open file: top bar with the name, the actions menu and the content for the file type.
 * [store] holds the name and the folder the person gave the file inside List, and the place where reading stopped;
 * the menu can change the first two, the content reports the third. [settings] has the reading options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Viewer(uri: Uri, back: () -> Unit, store: Recents, settings: Settings) {
    val ctx = LocalContext.current
    // A file opened from the list was just read by the list; asking the provider once more would only hold up the first frame.
    val info = remember(uri) { cachedInfo(uri.toString())?.takeIf { it.available } ?: infoOf(ctx, uri) }
    val holder = remember(uri) { WebHolder() }
    val pdfHolder = remember(uri) { PdfHolder() }
    val net = remember(uri) { AtomicBoolean(false) }
    var netOn by remember(uri) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(false) }
    val key = uri.toString()
    var alias by remember(uri) { mutableStateOf(store.aliasOf(key)) }
    // Where reading stopped last time (a page, a moment or a place in the text); it is read once, when the file opens.
    val start = remember(uri) { store.posOf(key) }
    val savePos: (Long) -> Unit = { store.setPos(key, it) }
    val saveCopy = rememberCopySaver()
    val shown = displayName(info, alias)
    val webLike = info.route == Route.WEB || info.route == Route.HTML
    val isPdf = info.route == Route.PDF
    val mime = info.mime ?: mimeOf(info.ext)
    val hasOptions = info.available && (webLike || isPdf)
    // The text of a PDF is read for the search only when the person asks for it; the file is let go when the viewer is left.
    val pdfFind = remember(uri) { PdfSearch(ctx, uri) }
    DisposableEffect(uri) { onDispose { pdfFind.close() } }

    val typeText = stringResource(info.cat.titleRes)
    val subtitle = remember(info, typeText) {
        if (info.size >= 0L) typeText + "  ·  " + Formatter.formatShortFileSize(ctx, info.size) else typeText
    }

    // Back closes the search bar first, then steps back through the pages of an HTML file, then leaves the file.
    BackHandler {
        val w = holder.view
        if (finding) {
            w?.clearMatches()
            finding = false
            found = ""
        } else if (webLike && w != null && w.canGoBack()) {
            w.goBack()
        } else {
            back()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            shown,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Ico(R.drawable.ic_arrow_back, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if ((webLike || isPdf) && info.available) {
                        IconButton(onClick = {
                            if (finding) holder.view?.clearMatches()
                            finding = !finding
                            found = ""
                        }) {
                            Ico(R.drawable.ic_search, stringResource(R.string.cd_search))
                        }
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
                                    onClick = { menu = false; shareFile(ctx, uri, mime) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_open_external)) },
                                    leadingIcon = { Ico(R.drawable.ic_external) },
                                    onClick = { menu = false; openExternal(ctx, uri, mime) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_save_copy)) },
                                    leadingIcon = { Ico(R.drawable.ic_download) },
                                    onClick = { menu = false; saveCopy(uri, shown) }
                                )
                            }
                            if (hasOptions) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.options_title)) },
                                    leadingIcon = { Ico(R.drawable.ic_text_size) },
                                    onClick = { menu = false; options = true }
                                )
                            }
                            if (info.route == Route.HTML) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (netOn) R.string.action_load_online_off else R.string.action_load_online
                                            )
                                        )
                                    },
                                    leadingIcon = { Ico(R.drawable.ic_globe) },
                                    onClick = {
                                        menu = false
                                        netOn = !netOn
                                        net.set(netOn)
                                        holder.view?.reload()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename)) },
                                leadingIcon = { Ico(R.drawable.ic_edit) },
                                onClick = { menu = false; renaming = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_move)) },
                                leadingIcon = { Ico(R.drawable.ic_folder) },
                                onClick = { menu = false; moving = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_details)) },
                                leadingIcon = { Ico(R.drawable.ic_info) },
                                onClick = { menu = false; details = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (finding && webLike) {
                FindBar(holder, found) {
                    finding = false
                    found = ""
                }
            }
            if (finding && isPdf) {
                PdfFindBar(pdfFind, pdfHolder) { finding = false }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!info.available) {
                    MessageState(
                        iconRes = R.drawable.ic_warning,
                        title = stringResource(R.string.file_unavailable_short),
                        body = stringResource(R.string.missing_body)
                    ) {
                        OutlinedButton(onClick = back) { Text(stringResource(R.string.cd_back)) }
                    }
                } else {
                    when (info.route) {
                        Route.PDF -> PdfView(uri, start.toInt(), settings.darkPdf && isSystemInDarkTheme(), pdfHolder) { savePos(it.toLong()) }
                        Route.IMAGE -> ZoomImage(uri, mime)
                        Route.MEDIA -> MediaView(uri, info.isVideo(), start, shown, mime, savePos)
                        Route.HTML, Route.WEB -> WebDoc(uri, info, net, holder, start, settings.textScale, savePos) { found = it }
                    }
                }
            }
        }
    }

    if (renaming) {
        RenameDialog(
            info = info,
            alias = alias,
            onSave = { a ->
                store.setAlias(key, a)
                alias = store.aliasOf(key)
                renaming = false
            },
            onDismiss = { renaming = false }
        )
    }
    if (moving) {
        MoveDialog(
            folders = store.folders(),
            current = store.folderOf(key),
            onPick = { id ->
                store.moveTo(key, id)
                moving = false
            },
            onCreate = { name, color ->
                val f = store.createFolder(name, color)
                if (f != null) store.moveTo(key, f.id)
                moving = false
            },
            onDismiss = { moving = false }
        )
    }
    if (details) DetailsDialog(info, shown) { details = false }
    if (options) {
        ReadingOptionsDialog(
            settings = settings,
            showText = webLike,
            showPdf = info.route == Route.PDF,
            onDismiss = { options = false }
        )
    }
}

@Composable
private fun DetailsDialog(info: Info, shown: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val unknown = stringResource(R.string.detail_unknown)
    val format = listOfNotNull(info.ext.takeIf { it.isNotEmpty() }?.uppercase(), info.mime)
        .joinToString("  ·  ")
        .ifEmpty { unknown }
    val size = if (info.size >= 0L) Formatter.formatFileSize(ctx, info.size) else unknown
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.action_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DetailRow(stringResource(R.string.detail_name), shown)
                // A name chosen in List is shown above; the file itself keeps the name it has on the device.
                if (shown != info.name) DetailRow(stringResource(R.string.detail_original), info.name)
                DetailRow(stringResource(R.string.detail_type), stringResource(info.cat.titleRes))
                DetailRow(stringResource(R.string.detail_format), format)
                DetailRow(stringResource(R.string.detail_size), size)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
