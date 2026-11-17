package app.yume.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.yume.android.data.KV

/**
 * Настройки приложения. Ключи совпадают с iOS-версией,
 * чтобы резервные копии были совместимы.
 */
object Prefs {
    const val KEY_PLATFORM = "set.platform"
    const val KEY_THEME = "set.theme"
    const val KEY_MODE = "manga.mode.default"
    const val KEY_ANIM = "manga.anim"
    const val KEY_TAPZONES = "manga.tapzones"
    const val KEY_AWAKE = "set.keepAwake"
    const val KEY_ACCENT = "android.accent"

    /** Пока работает только манга. */
    val ACTIVE_PLATFORMS = setOf("manga")

    /** «anime», «ranobe» или «manga». */
    var platform by mutableStateOf("manga")
        private set

    /** «system», «light» или «dark». */
    var theme by mutableStateOf("system")
        private set

    /** Режим чтения по умолчанию: «auto», «pages» или «scroll». */
    var mangaMode by mutableStateOf("auto")
        private set

    /** Листание страниц: «slide», «fade» или «book». */
    var mangaAnim by mutableStateOf("slide")
        private set

    /** «dynamic» (цвета из обоев, Material You) или hex вида «A87AFF». */
    var accent by mutableStateOf("dynamic")
        private set

    var tapZones by mutableStateOf(true)
        private set

    var keepAwake by mutableStateOf(true)
        private set

    /** Читает все значения из хранилища (после запуска и после восстановления копии). */
    fun reload() {
        val saved = KV.getString(KEY_PLATFORM, "manga") ?: "manga"
        platform = if (saved in ACTIVE_PLATFORMS) saved else "manga"
        theme = KV.getString(KEY_THEME, "system") ?: "system"
        mangaMode = KV.getString(KEY_MODE, "auto") ?: "auto"
        mangaAnim = KV.getString(KEY_ANIM, "slide") ?: "slide"
        accent = KV.getString(KEY_ACCENT, "dynamic") ?: "dynamic"
        tapZones = KV.getBool(KEY_TAPZONES, true)
        keepAwake = KV.getBool(KEY_AWAKE, true)
    }

    fun selectPlatform(value: String) {
        if (value !in ACTIVE_PLATFORMS) return
        platform = value
        KV.putString(KEY_PLATFORM, value)
    }

    fun selectTheme(value: String) {
        theme = value
        KV.putString(KEY_THEME, value)
    }

    fun selectMangaMode(value: String) {
        mangaMode = value
        KV.putString(KEY_MODE, value)
    }

    fun selectMangaAnim(value: String) {
        mangaAnim = value
        KV.putString(KEY_ANIM, value)
    }

    fun selectAccent(value: String) {
        accent = value
        KV.putString(KEY_ACCENT, value)
    }

    fun updateTapZones(value: Boolean) {
        tapZones = value
        KV.putBool(KEY_TAPZONES, value)
    }

    fun updateKeepAwake(value: Boolean) {
        keepAwake = value
        KV.putBool(KEY_AWAKE, value)
    }
}
