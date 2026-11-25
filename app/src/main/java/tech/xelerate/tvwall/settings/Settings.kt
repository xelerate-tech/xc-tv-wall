package tech.xelerate.tvwall.settings

import android.content.Context
import tech.xelerate.tvwall.core.SlideSpeed

/** Per-TV preferences set from the on-screen menu. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Volatile
    var slideSeconds: Int = SlideSpeed.normalize(prefs.getInt(KEY_SLIDE_SECONDS, SlideSpeed.DEFAULT_SECONDS))
        set(value) {
            field = SlideSpeed.normalize(value)
            prefs.edit().putInt(KEY_SLIDE_SECONDS, field).apply()
        }

    private companion object {
        const val KEY_SLIDE_SECONDS = "slideSeconds"
    }
}
