package app.fileviewer

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
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

/** A single open file: top bar with the name, the actions menu and the content for the file type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Viewer(uri: Uri, back: () -> Unit) {
    val ctx = LocalContext.current
    val info = remember(uri) { infoOf(ctx, uri) }
    val holder = remember(uri) { WebHolder() }
    val net = remember(uri) { AtomicBoolean(false) }
    var netOn by remember(uri) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf("") }
    val webLike = info.route == Route.WEB || info.route == Route.HTML
    val mime = info.mime ?: mimeOf(info.ext)

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
                            info.name,
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
                    if (webLike && info.available) {
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
                        Route.PDF -> PdfView(uri)
                        Route.IMAGE -> ZoomImage(uri, mime)
                        Route.MEDIA -> MediaView(uri, info.isVideo())
                        Route.HTML, Route.WEB -> WebDoc(uri, info, net, holder) { found = it }
                    }
                }
            }
        }
    }

    if (details) DetailsDialog(info) { details = false }
}

@Composable
private fun DetailsDialog(info: Info, onClose: () -> Unit) {
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
                DetailRow(stringResource(R.string.detail_name), info.name)
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
