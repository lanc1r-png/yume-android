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
    const val KEY_RESUME = "set.resume"
    const val KEY_AUTONEXT = "set.autoNext"
    const val KEY_KODIK_DIRECT = "set.kodikDirect"
    const val KEY_NOVEL_MODE = "novel.mode"
    const val KEY_NOVEL_ANIM = "novel.anim"
    const val KEY_OFF_WIFI = "off.wifi"
    const val KEY_OFF_PREFETCH = "off.prefetch"
    const val KEY_HAPTICS = "set.haptics"
    const val KEY_MANGA_BG = "manga.autoBG"
    const val KEY_OFF_LIMIT = "off.limitGB"
    const val KEY_OFF_OVERFLOW = "off.overflow"
    const val KEY_BACKUP_SENT = "backup.sent"

    val ACTIVE_PLATFORMS = setOf("anime", "ranobe", "manga")

    /** «anime», «ranobe» или «manga». */
    var platform by mutableStateOf("anime")
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

    var resume by mutableStateOf(true)
        private set

    var autoNext by mutableStateOf(true)
        private set

    var kodikDirect by mutableStateOf(true)
        private set

    /** Ранобэ: «pages» или «scroll». */
    var novelMode by mutableStateOf("scroll")
        private set

    var novelAnim by mutableStateOf("book")
        private set

    var offWifi by mutableStateOf(false)
        private set

    var offPrefetch by mutableStateOf(false)
        private set

    var haptics by mutableStateOf(true)
        private set

    var mangaAutoBg by mutableStateOf(true)
        private set

    /** Лимит места под загрузки, ГБ (0 — без лимита). */
    var offLimit by mutableStateOf(0)
        private set

    /** При превышении лимита: «refuse» или «purge». */
    var offOverflow by mutableStateOf("refuse")
        private set

    var backupSent by mutableStateOf(0L)
        private set

    /** Читает все значения из хранилища (после запуска и после восстановления копии). */
    fun reload() {
        val saved = KV.getString(KEY_PLATFORM, "anime") ?: "anime"
        platform = if (saved in ACTIVE_PLATFORMS) saved else "anime"
        theme = KV.getString(KEY_THEME, "system") ?: "system"
        mangaMode = KV.getString(KEY_MODE, "auto") ?: "auto"
        mangaAnim = KV.getString(KEY_ANIM, "slide") ?: "slide"
        accent = KV.getString(KEY_ACCENT, "dynamic") ?: "dynamic"
        tapZones = KV.getBool(KEY_TAPZONES, true)
        haptics = KV.getBool(KEY_HAPTICS, true)
        mangaAutoBg = KV.getBool(KEY_MANGA_BG, true)
        offLimit = KV.getString(KEY_OFF_LIMIT, "0")?.toIntOrNull() ?: 0
        offOverflow = KV.getString(KEY_OFF_OVERFLOW, "refuse") ?: "refuse"
        backupSent = KV.getString(KEY_BACKUP_SENT, "0")?.toLongOrNull() ?: 0L
        keepAwake = KV.getBool(KEY_AWAKE, true)
        resume = KV.getBool(KEY_RESUME, true)
        autoNext = KV.getBool(KEY_AUTONEXT, true)
        kodikDirect = KV.getBool(KEY_KODIK_DIRECT, true)
        novelMode = KV.getString(KEY_NOVEL_MODE, "scroll") ?: "scroll"
        novelAnim = KV.getString(KEY_NOVEL_ANIM, "book") ?: "book"
        offWifi = KV.getBool(KEY_OFF_WIFI, false)
        offPrefetch = KV.getBool(KEY_OFF_PREFETCH, false)
    }

    fun updateResume(v: Boolean) { resume = v; KV.putBool(KEY_RESUME, v) }
    fun updateAutoNext(v: Boolean) { autoNext = v; KV.putBool(KEY_AUTONEXT, v) }
    fun updateKodikDirect(v: Boolean) { kodikDirect = v; KV.putBool(KEY_KODIK_DIRECT, v) }
    fun selectNovelMode(v: String) { novelMode = v; KV.putString(KEY_NOVEL_MODE, v) }
    fun selectNovelAnim(v: String) { novelAnim = v; KV.putString(KEY_NOVEL_ANIM, v) }
    fun updateOffWifi(v: Boolean) { offWifi = v; KV.putBool(KEY_OFF_WIFI, v) }
    fun updateOffPrefetch(v: Boolean) { offPrefetch = v; KV.putBool(KEY_OFF_PREFETCH, v) }
    fun updateHaptics(v: Boolean) { haptics = v; KV.putBool(KEY_HAPTICS, v) }
    fun updateMangaAutoBg(v: Boolean) { mangaAutoBg = v; KV.putBool(KEY_MANGA_BG, v) }
    fun selectOffLimit(v: Int) { offLimit = v; KV.putString(KEY_OFF_LIMIT, v.toString()) }
    fun selectOffOverflow(v: String) { offOverflow = v; KV.putString(KEY_OFF_OVERFLOW, v) }
    fun markBackupSent() { backupSent = System.currentTimeMillis(); KV.putString(KEY_BACKUP_SENT, backupSent.toString()) }

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
