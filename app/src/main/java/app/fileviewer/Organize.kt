package app.fileviewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Renaming and folders live only inside List (see Recents.kt): the files on the device are never touched.

/** The extension of [name] with its dot (".pdf"), or "" when there is none. A leading dot (".bashrc") is not one. */
fun extensionOf(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0 && dot < name.length - 1) name.substring(dot) else ""
}

/** The name shown for a file: the name chosen in List plus the file's real extension, or the file's own name. */
fun displayName(info: Info, alias: String?): String =
    if (alias.isNullOrBlank()) info.name else alias + extensionOf(info.name)

/** What was typed, as a name: one line, trimmed, and without the extension if the person typed that too. */
private fun typedName(text: String, ext: String): String {
    var t = text.replace(Regex("[\\t\\r\\n]+"), " ").trim()
    if (ext.isNotEmpty() && t.length > ext.length && t.endsWith(ext, ignoreCase = true)) {
        t = t.dropLast(ext.length).trimEnd()
    }
    return t
}

private fun selectAll(s: String) = TextFieldValue(s, TextRange(0, s.length))

/**
 * Puts the cursor into the text field, and so the keyboard on screen, as soon as the dialog opens. It is called from
 * inside the dialog's content; the window of a dialog is not always attached on the first frame, so it tries a few times.
 */
@Composable
private fun AutoFocus(focus: FocusRequester) {
    LaunchedEffect(Unit) {
        var tries = 0
        while (tries < 5) {
            try {
                focus.requestFocus()
                break
            } catch (e: Exception) {
                tries++
                delay(60)
            }
        }
    }
}

/**
 * Asks for a new name of a file. The extension is shown after the field and cannot be edited.
 * [onSave] gets the new name without the extension, or null when the original name is wanted back.
 */
@Composable
fun RenameDialog(info: Info, alias: String?, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    val ext = extensionOf(info.name)
    val base = info.name.removeSuffix(ext)
    var text by remember { mutableStateOf(selectAll(alias ?: base)) }
    val focus = remember { FocusRequester() }
    val typed = typedName(text.text, ext)
    val save: () -> Unit = {
        if (typed.isNotEmpty()) onSave(if (typed == base) null else typed)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            Column {
                AutoFocus(focus)
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.text.length <= Recents.MAX_NAME) text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    suffix = if (ext.isNotEmpty()) { { Text(ext) } } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() })
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.rename_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (alias != null) {
                    TextButton(onClick = { onSave(null) }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.rename_reset))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = save, enabled = typed.isNotEmpty()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/**
 * Asks for the name of a folder (a new one or a rename). [taken] holds the names of the other folders:
 * a name that is already used is refused. [title] and [confirm] are string resources.
 */
@Composable
fun FolderNameDialog(
    title: Int,
    initial: String,
    confirm: Int,
    taken: List<String>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(selectAll(initial)) }
    val focus = remember { FocusRequester() }
    val name = text.text.replace(Regex("[\\t\\r\\n]+"), " ").trim()
    val duplicate = taken.any { it.equals(name, ignoreCase = true) }
    val ok = name.isNotEmpty() && !duplicate
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                AutoFocus(focus)
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.text.length <= Recents.MAX_NAME) text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) { { Text(stringResource(R.string.folder_exists)) } } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ok) onSave(name) })
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = ok) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/**
 * Lets the person choose a folder for a file: the main list (no folder), one of [folders], or a new one.
 * [current] is the folder the file is in now (null = none). [onPick] gets a folder id, or null for the main list.
 */
@Composable
fun MoveDialog(
    folders: List<Folder>,
    current: String?,
    onPick: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        FolderNameDialog(
            title = R.string.folder_new,
            initial = "",
            confirm = R.string.create,
            taken = folders.map { it.name },
            onSave = onCreate,
            onDismiss = { creating = false }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.move_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PickRow(null, stringResource(R.string.move_none), current == null, false) { onPick(null) }
                    for (f in folders) {
                        PickRow(R.drawable.ic_folder, f.name, f.id == current, false) { onPick(f.id) }
                    }
                    PickRow(R.drawable.ic_folder_plus, stringResource(R.string.folder_new), false, true) { creating = true }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun PickRow(icon: Int?, label: String, selected: Boolean, accent: Boolean, onClick: () -> Unit) {
    val tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Ico(icon, null, Modifier.size(24.dp), tint)
        } else {
            Spacer(Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = tint,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Ico(R.drawable.ic_check, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
        }
    }
}
