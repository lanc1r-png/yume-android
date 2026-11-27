package app.yume.android.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Сортировка избранного. */
enum class FavSort(val key: String, val title: String) {
    ADDED("added", "По добавлению"),
    UPDATED("updated", "По обновлению (новые серии и главы)"),
    OPENED("opened", "Недавно открытые"),
    TITLE("title", "По названию"),
    RATING("rating", "По рейтингу");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: ADDED
    }
}

/** Свои названия для папок избранного («Читаю», «В планах»…). */
object FavNames {
    private const val KEY = "fav.names.v1"
    private var cache: Map<String, String>? = null
    var version by mutableIntStateOf(0)
        private set

    private fun all(): Map<String, String> {
        cache?.let { return it }
        val m = HashMap<String, String>()
        try {
            val o = JSONObject(KV.getString(KEY, null) ?: "{}")
            val it = o.keys()
            while (it.hasNext()) { val k = it.next(); J.str(o.opt(k))?.let { v -> m[k] = v } }
        } catch (e: Exception) {
            // повреждённые данные игнорируем
        }
        cache = m
        return m
    }

    private fun k(st: ListStatus, book: Boolean) = st.key + if (book) ".b" else ".a"

    fun get(st: ListStatus, book: Boolean): String? {
        @Suppress("UNUSED_VARIABLE") val v = version
        return all()[k(st, book)]?.takeIf { it.isNotEmpty() }
    }

    fun set(st: ListStatus, book: Boolean, name: String?) {
        val m = HashMap(all())
        val t = name?.trim().orEmpty()
        if (t.isEmpty()) m.remove(k(st, book)) else m[k(st, book)] = t.take(24)
        cache = m
        val o = JSONObject()
        for ((a, b) in m) o.put(a, b)
        KV.putString(KEY, o.toString())
        version++
    }
}

object FavUpdate {
    private fun parseDate(s: String): Long? {
        try {
            return java.time.Instant.parse(s).toEpochMilli()
        } catch (e: Exception) {
            // не ISO
        }
        val t = s.replace("T", " ")
        val pattern = if (t.length >= 19) "yyyy-MM-dd HH:mm:ss" else "yyyy-MM-dd"
        return try {
            java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(t.take(pattern.length))?.time
        } catch (e: Exception) {
            null
        }
    }

    /** Достаёт из ответа сайта число серий/глав и дату последнего выхода. */
    fun parse(root: Any): Pair<Int?, Long?> {
        var count: Int? = null
        var date: Long? = null
        fun walk(v: Any?, depth: Int) {
            if (depth >= 3) return
            if (v is JSONObject) {
                val it = v.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val lk = k.lowercase()
                    val x = v.opt(k)
                    if (count == null && lk in setOf("episodes_count", "chapters_count", "items_count")) count = J.int(x)
                    if (date == null && "last" in lk && ("episode" in lk || "chapter" in lk)) {
                        val str = x as? String
                        if (str != null && str.length >= 10 && str[0].isDigit()) date = parseDate(str)
                    }
                    if (x is JSONObject || x is JSONArray) walk(x, depth + 1)
                }
            } else if (v is JSONArray) {
                for (i in 0 until minOf(v.length(), 5)) walk(v.opt(i), depth + 1)
            }
        }
        walk(root, 0)
        return count to date
    }
}

/** Плановая проверка обновлений избранного: при открытии вкладки и раз в час. */
object FavUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false
    var checking by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun refresh(list: List<Title>, force: Boolean) {
        if (running) return
        val site = Site.current
        // только то, что читаешь (и добавленное без статуса)
        val todo = list.filter { t ->
            val st = FavMetaStore.status(t)
            (st == null || st == ListStatus.ACTIVE) && (force || FavMetaStore.needsCheck(t))
        }.take(80)
        if (todo.isEmpty()) return
        running = true
        checking = true
        scope.launch {
            try {
                for (chunk in todo.chunked(4)) {
                    val res = coroutineScope {
                        chunk.map { t -> async { val r = Api.updateInfo(t.slugUrl); Triple(t, r.first, r.second) } }.awaitAll()
                    }
                    if (Site.current == site) kotlinx.coroutines.withContext(Dispatchers.Main) { FavMetaStore.applyBatch(res) }
                }
            } finally {
                running = false
                checking = false
            }
        }
    }

    fun refreshIfDue() {
        val last = KV.getString("new.lastRun", "0")?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last < 3_600_000) return
        KV.putString("new.lastRun", System.currentTimeMillis().toString())
        refresh(FavoritesStore.items.toList(), false)
    }
}
