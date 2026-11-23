package app.fileviewer

import android.app.Application
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun crashStore(ctx: Context): CrashStore = CrashStore(ctx.filesDir)

object CrashLog {
    /**
     * Writes a report when any thread dies of an uncaught exception, then hands the exception on, so the system still
     * ends the app the way it always did.
     */
    fun install(app: Application) {
        val store = crashStore(app)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val details = try { appDetails(app) } catch (e: Throwable) { "" }
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
                store.save(crashReport(details, time, thread.name, traceOf(error)))
            } catch (e: Throwable) {
                // Nothing can be done about a failure while the app is failing.
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
