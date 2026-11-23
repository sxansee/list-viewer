package app.fileviewer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The name of the options of the open file: those of reading for a document, those of playback for audio. */
fun optionsTitle(audio: Boolean): Int = if (audio) R.string.options_audio_title else R.string.options_title

/**
 * The reading options: the size of text, the dark pages of a PDF and the playback of audio in the background.
 * [showText], [showPdf] and [showAudio] choose which of them are offered, depending on the open file. Changes apply at
 * once and are kept.
 */
@Composable
fun ReadingOptionsDialog(settings: Settings, showText: Boolean, showPdf: Boolean, showAudio: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(optionsTitle(showAudio && !showText && !showPdf))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (showText) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.options_text_size),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                settings.textScale.toString() + "%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = settings.textScale.toFloat(),
                            onValueChange = { settings.updateTextScale(it.roundToInt()) },
                            valueRange = Settings.MIN_TEXT.toFloat()..Settings.MAX_TEXT.toFloat(),
                            steps = (Settings.MAX_TEXT - Settings.MIN_TEXT) / Settings.STEP_TEXT - 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.options_text_hint),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (settings.textScale != 100) {
                                TextButton(onClick = { settings.updateTextScale(100) }) {
                                    Text(stringResource(R.string.options_reset))
                                }
                            }
                        }
                    }
                }
                if (showPdf) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.options_dark_pdf), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.options_dark_pdf_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = settings.darkPdf, onCheckedChange = { settings.updateDarkPdf(it) })
                    }
                }
                if (showAudio) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.options_background_audio), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.options_background_audio_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = settings.backgroundAudio, onCheckedChange = { settings.updateBackgroundAudio(it) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

/**
 * Returns the function that saves a copy of a file: it opens the system "save as" screen with the given name, and
 * copies the file there. This is how a file gets the name chosen in List on the device itself.
 */
@Composable
fun rememberCopySaver(): (Uri, String) -> Unit {
    val ctx = LocalContext.current
    var source by remember { mutableStateOf<Uri?>(null) }
    // The generic type keeps the name exactly as given; a specific one makes some providers add a second extension.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { target: Uri? ->
        val from = source
        source = null
        if (target != null && from != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val ok = copyFile(ctx, from, target)
                withContext(Dispatchers.Main) { toast(ctx, if (ok) R.string.copy_saved else R.string.copy_failed) }
            }
        }
    }
    return { uri, name ->
        source = uri
        launcher.launch(safeFileName(name))
    }
}

/** Copies a document to another one; a half-written target is deleted when the copy fails. */
private fun copyFile(c: Context, from: Uri, to: Uri): Boolean {
    var ok = false
    try {
        c.contentResolver.openInputStream(from)?.use { input ->
            c.contentResolver.openOutputStream(to, "wt")?.use { out ->
                input.copyTo(out)
                ok = true
            }
        }
    } catch (e: Exception) {
        ok = false
    }
    if (!ok) {
        try {
            DocumentsContract.deleteDocument(c.contentResolver, to)
        } catch (e: Exception) {
            // The copy failed and the empty file stays; nothing more can be done.
        }
    }
    return ok
}

/** Writes text into a document that the person chose with the "save as" screen. */
fun writeText(c: Context, to: Uri, text: String): Boolean {
    return try {
        val out = c.contentResolver.openOutputStream(to, "wt") ?: return false
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        true
    } catch (e: Exception) {
        false
    }
}

/** Reads a backup file; null when it cannot be read or is not a List backup. */
fun readBackup(c: Context, from: Uri): Backup? {
    return try {
        val bytes = c.contentResolver.openInputStream(from)?.use { readLimited(it, BACKUP_MAX_BYTES) } ?: return null
        backupFromJson(String(bytes, Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }
}
