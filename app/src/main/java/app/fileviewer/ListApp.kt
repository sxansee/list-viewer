package app.fileviewer

import android.app.Application

/** The application object: it makes sure that a crash leaves a report behind (see [CrashLog]). */
class ListApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
