package tech.xelerate.tvwall.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Hands a downloaded APK to the system installer; someone confirms with the remote. */
object ApkInstaller {
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
