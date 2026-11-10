package app.fileviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    private var opened by mutableStateOf<Uri?>(null)

    /** True while the open document was handed over by another app ("Open with"): Back then returns to that app. */
    private var external = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState != null) {
            opened = savedInstanceState.getString(KEY_URI)?.let { Uri.parse(it) }
            external = savedInstanceState.getBoolean(KEY_EXTERNAL)
        } else if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            keepAccess(intent)
            opened = intent.data
            external = true
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

    /** Keeps read access to a document that another app opened with us, so it can be reopened from the recent list later. */
    private fun keepAccess(i: Intent) {
        val u = i.data ?: return
        if ((i.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) == 0) return
        try {
            contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Not every provider offers a lasting grant; the document still opens now.
        }
    }

    private companion object {
        const val KEY_URI = "uri"
        const val KEY_EXTERNAL = "external"
    }
}

/** Two screens, the recent list and the viewer, with a short slide-and-fade between them. */
@Composable
fun App(uri: Uri?, onOpen: (Uri) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val recents = remember { Recents(ctx) }
    var items by remember { mutableStateOf(recents.list()) }

    LaunchedEffect(uri) {
        if (uri != null) {
            recents.add(uri)
            items = recents.list()
        }
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
                Viewer(u, onClose)
            } else {
                HomeScreen(
                    items = items,
                    onOpen = onOpen,
                    onRemove = { item ->
                        recents.remove(item)
                        items = recents.list()
                    },
                    onRestore = { item ->
                        recents.restore(item)
                        items = recents.list()
                    },
                    onClear = {
                        recents.clear()
                        items = recents.list()
                    }
                )
            }
        }
    }
}
