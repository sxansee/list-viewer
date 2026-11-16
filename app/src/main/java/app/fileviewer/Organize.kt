package app.fileviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Renaming and folders live only inside List (see Recents.kt): the files on the device are never touched.

/** The name shown for a file: the name chosen in List plus the file's real extension, or the file's own name. */
fun displayName(info: Info, alias: String?): String =
    if (alias.isNullOrBlank()) info.name else alias + extensionOf(info.name)

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
 * Asks for the name and the color of a folder (a new one, or an existing one being changed). [taken] holds the names
 * of the other folders: a name that is already used is refused. [title] and [confirm] are string resources.
 */
@Composable
fun FolderDialog(
    title: Int,
    initial: String,
    initialColor: Int,
    confirm: Int,
    taken: List<String>,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(selectAll(initial)) }
    var color by remember { mutableStateOf(initialColor) }
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
                    keyboardActions = KeyboardActions(onDone = { if (ok) onSave(name, color) })
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.folder_color),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, color) }, enabled = ok) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** A row of colored dots, one for each color a folder can have; the chosen one carries a tick. */
@Composable
private fun ColorPicker(selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        for (i in 0 until Recents.COLORS) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(folderColor(i))
                        .clickable { onPick(i) },
                    contentAlignment = Alignment.Center
                ) {
                    if (i == selected) {
                        Ico(R.drawable.ic_check, null, Modifier.size(16.dp), Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Lets the person choose a folder for files: the main list (no folder), one of [folders], or a new one.
 * [current] is the folder the files are in now (null = none; any text that is not a folder id marks a mixed choice).
 * [onPick] gets a folder id, or null for the main list.
 */
@Composable
fun MoveDialog(
    folders: List<Folder>,
    current: String?,
    onPick: (String?) -> Unit,
    onCreate: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        FolderDialog(
            title = R.string.folder_new,
            initial = "",
            initialColor = 0,
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
                    PickRow(null, stringResource(R.string.move_none), current == null, false, null) { onPick(null) }
                    for (f in folders) {
                        PickRow(R.drawable.ic_folder, f.name, f.id == current, false, folderColor(f.color)) { onPick(f.id) }
                    }
                    PickRow(R.drawable.ic_folder_plus, stringResource(R.string.folder_new), false, true, null) { creating = true }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun PickRow(
    icon: Int?,
    label: String,
    selected: Boolean,
    accent: Boolean,
    iconTint: Color?,
    onClick: () -> Unit
) {
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
            Ico(icon, null, Modifier.size(24.dp), iconTint ?: tint)
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
