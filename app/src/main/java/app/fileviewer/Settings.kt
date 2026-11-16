package app.fileviewer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Preferences of the app: how the list is sorted and the reading options. They live in SharedPreferences, and the
 * properties are Compose state, so a screen that reads one is redrawn when it changes.
 */
class Settings(c: Context) {
    private val p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The order of the files on the home screen. */
    var sort by mutableStateOf(readSort())
        private set

    /** The size of text in documents shown as text and in web pages, in percent (100 = normal). */
    var textScale by mutableStateOf(readScale())
        private set

    /** Whether PDF pages are shown with inverted colors while the dark theme is on. */
    var darkPdf by mutableStateOf(p.getBoolean("dark_pdf", false))
        private set

    fun updateSort(v: Sort) {
        sort = v
        p.edit().putString("sort", v.name).apply()
    }

    fun updateTextScale(percent: Int) {
        val v = percent.coerceIn(MIN_TEXT, MAX_TEXT)
        textScale = v
        p.edit().putInt("text", v).apply()
    }

    fun updateDarkPdf(on: Boolean) {
        darkPdf = on
        p.edit().putBoolean("dark_pdf", on).apply()
    }

    private fun readSort(): Sort {
        val name = p.getString("sort", null) ?: return Sort.RECENT
        return Sort.entries.firstOrNull { it.name == name } ?: Sort.RECENT
    }

    private fun readScale(): Int = p.getInt("text", 100).coerceIn(MIN_TEXT, MAX_TEXT)

    companion object {
        const val MIN_TEXT = 80
        const val MAX_TEXT = 200
        const val STEP_TEXT = 10
    }
}
