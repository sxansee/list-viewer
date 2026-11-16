package app.fileviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    private var opened by mutableStateOf<Uri?>(null)

    /** True while the open document was handed over by another app ("Open with", "Share"): Back then returns to that app. */
    private var external = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState != null) {
            opened = savedInstanceState.getString(KEY_URI)?.let { Uri.parse(it) }
            external = savedInstanceState.getBoolean(KEY_EXTERNAL)
        } else {
            takeIntent(intent)
        }
        setContent {
            AppTheme {
                App(
                    uri = opened,
                    onOpen = { u ->
                        external = false
                        opened = u
                    },
                    onClose = {
                        if (external) {
                            finish()
                        } else {
                            opened = null
                        }
                    }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_URI, opened?.toString())
        outState.putBoolean(KEY_EXTERNAL, external)
    }

    /**
     * Reacts to the way the app was started. A document that another app opened with us ("Open with") or shared with
     * us opens at once. Several shared documents are put into the list, and the list is shown.
     */
    private fun takeIntent(i: Intent?) {
        if (i == null) return
        when (i.action) {
            Intent.ACTION_VIEW -> {
                val u = i.data ?: return
                keepAccess(i, u)
                opened = u
                external = true
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val shared = sharedUris(i)
                for (u in shared) keepAccess(i, u)
                when {
                    shared.isEmpty() -> toast(this, R.string.share_nothing)
                    shared.size == 1 -> {
                        opened = shared[0]
                        external = true
                    }
                    else -> {
                        val store = Recents(this)
                        // The first of the shared files ends up on top.
                        for (u in shared.asReversed()) store.add(u)
                        Toast.makeText(this, getString(R.string.shared_added, shared.size), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /** The documents that were shared: the stream extras, or, when an app put them only there, the clip data. */
    @Suppress("DEPRECATION")
    private fun sharedUris(i: Intent): List<Uri> {
        val out = ArrayList<Uri>()
        try {
            if (i.action == Intent.ACTION_SEND_MULTIPLE) {
                val l = i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (l != null) out.addAll(l)
            } else {
                val u = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (u != null) out.add(u)
            }
        } catch (e: Exception) {
            // An extra of an unexpected kind: the clip data below may still have the documents.
        }
        val clip = i.clipData
        if (out.isEmpty() && clip != null) {
            for (n in 0 until clip.itemCount) {
                val u = clip.getItemAt(n).uri
                if (u != null) out.add(u)
            }
        }
        return out.filter { it.scheme == "content" || it.scheme == "file" }.distinct()
    }

    /** Keeps read access to a document that another app handed over, so it can be reopened from the recent list later. */
    private fun keepAccess(i: Intent, u: Uri) {
        if ((i.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) == 0) return
        keepRead(this, u)
    }

    private companion object {
        const val KEY_URI = "uri"
        const val KEY_EXTERNAL = "external"
    }
}

/** Two screens, the list of files and the viewer, with a short slide-and-fade between them. */
@Composable
fun App(uri: Uri?, onOpen: (Uri) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Recents(ctx) }
    val settings = remember { Settings(ctx) }
    var items by remember { mutableStateOf(store.list()) }
    var folders by remember { mutableStateOf(store.folders()) }
    // The open folder is kept here, so that coming back from a document lands in the same folder.
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    val refresh: () -> Unit = {
        items = store.list()
        folders = store.folders()
    }

    LaunchedEffect(uri) {
        if (uri != null) store.add(uri)
        refresh()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = uri,
            transitionSpec = {
                if (targetState != null) {
                    (fadeIn(tween(240)) + slideInHorizontally(tween(280)) { it / 8 }) togetherWith fadeOut(tween(140))
                } else {
                    fadeIn(tween(240)) togetherWith (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 8 })
                }
            },
            label = "screen"
        ) { u ->
            if (u != null) {
                // The viewer can rename a file, move it to a folder or note where reading stopped, so the list is reloaded on the way back.
                Viewer(u, { refresh(); onClose() }, store, settings)
            } else {
                HomeScreen(
                    items = items,
                    folders = folders,
                    folder = folder,
                    store = store,
                    settings = settings,
                    onFolder = { folder = it },
                    onChanged = refresh,
                    onOpen = onOpen
                )
            }
        }
    }
}
