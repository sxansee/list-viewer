package app.fileviewer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * What the About dialog and the crash report know about this installation: the version and its number, whether it is the
 * slower debug build, and the fingerprint of the key it is signed with (empty when that could not be read).
 */
class AppInfo(val version: String, val code: Long, val debug: Boolean, val signature: String)

@Suppress("DEPRECATION")
fun appInfo(ctx: Context): AppInfo {
    var version = ""
    var code = 0L
    try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        version = pi.versionName ?: ""
        code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    } catch (e: Exception) {
        // The version stays unknown.
    }
    val debug = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    return AppInfo(version, code, debug, signatureOf(ctx))
}

/** The fingerprint of the certificate the installed app is signed with, or "" when the system does not tell. */
@Suppress("DEPRECATION")
private fun signatureOf(ctx: Context): String {
    return try {
        val pm = ctx.packageManager
        val certificate = if (Build.VERSION.SDK_INT >= 28) {
            val pi = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            pi.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }
        if (certificate == null) "" else fingerprintOf(certificate.toByteArray())
    } catch (e: Exception) {
        ""
    }
}

/** The lines for a bug report: version, build, Android and device, and the key the app is signed with. */
fun appDetails(ctx: Context, info: AppInfo = appInfo(ctx)): String = aboutDetails(
    app = ctx.getString(R.string.app_name),
    version = info.version,
    code = info.code,
    debug = info.debug,
    release = Build.VERSION.RELEASE ?: "",
    sdk = Build.VERSION.SDK_INT,
    maker = Build.MANUFACTURER ?: "",
    model = Build.MODEL ?: "",
    signature = info.signature
)
