package app.yume.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Настройки приложения. Ключи совпадают с iOS-версией (set.platform, set.theme),
 * чтобы резервные копии были совместимы.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("yume", Context.MODE_PRIVATE)

    /** «anime», «ranobe» или «manga». Пока работает только манга. */
    var platform: String by mutableStateOf(readPlatform())
        private set

    /** «system», «light» или «dark». */
    var theme: String by mutableStateOf(sp.getString(KEY_THEME, "system") ?: "system")
        private set

    private fun readPlatform(): String {
        val saved = sp.getString(KEY_PLATFORM, "manga") ?: "manga"
        return if (saved in ACTIVE_PLATFORMS) saved else "manga"
    }

    fun setPlatform(value: String) {
        if (value !in ACTIVE_PLATFORMS) return
        platform = value
        sp.edit().putString(KEY_PLATFORM, value).apply()
    }

    fun setTheme(value: String) {
        theme = value
        sp.edit().putString(KEY_THEME, value).apply()
    }

    companion object {
        const val KEY_PLATFORM = "set.platform"
        const val KEY_THEME = "set.theme"
        val ACTIVE_PLATFORMS = setOf("manga")
    }
}
