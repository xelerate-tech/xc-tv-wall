package tech.xelerate.tvwall.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tech.xelerate.tvwall.ui.PlayerActivity

/**
 * Opens the slideshow when the Fire TV boots and after the app updated itself
 * (needs SYSTEM_ALERT_WINDOW granted via adb on Fire OS 8).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldLaunch(intent.action)) return
        context.startActivity(Intent(context, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        fun shouldLaunch(action: String?): Boolean =
            action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
    }
}
