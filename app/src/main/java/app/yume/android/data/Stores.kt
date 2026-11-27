package app.yume.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** Общее хранилище ключ-значение. Ключи совпадают с iOS (UserDefaults) ради резервных копий. */
object KV {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("yume", Context.MODE_PRIVATE)
    }

    fun getString(key: String, def: String?): String? = try {
        sp.getString(key, def)
    } catch (e: ClassCastException) {
        def
    }

    fun getBool(key: String, def: Boolean): Boolean = try {
        sp.getBoolean(key, def)
    } catch (e: ClassCastException) {
        def
    }

    fun getFloat(key: String, def: Float): Float = try {
        sp.getFloat(key, def)
    } catch (e: ClassCastException) {
        def
    }

    fun putFloat(key: String, value: Float) {
        sp.edit().putFloat(key, value).apply()
    }

    fun putString(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }

    fun putBool(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
    }

    fun remove(key: String) {
        sp.edit().remove(key).apply()
    }

    fun all(): Map<String, Any?> = sp.all

    fun putAny(key: String, v: Any?) {
        val e = sp.edit()
        when (v) {
            is String -> e.putString(key, v)
            is Boolean -> e.putBoolean(key, v)
            is Int -> e.putInt(key, v)
            is Long -> e.putLong(key, v)
            is Float -> e.putFloat(key, v)
            is Double -> e.putFloat(key, v.toFloat())
            else -> {}
        }
        e.apply()
    }
}

class TitleList(private val key: String) {
    val items = mutableStateListOf<Title>()

    fun load() {
        items.clear()
        val s = KV.getString(key, null) ?: return
        try {
            items.addAll(JSONArray(s).objects().mapNotNull { Title.fromJson(it) })
        } catch (e: Exception) {
            // повреждённые данные игнорируем
        }
    }

    fun contains(t: Title): Boolean = items.any { it.id == t.id }

    fun toggle(t: Title) {
        val i = items.indexOfFirst { it.id == t.id }
        if (i >= 0) items.removeAt(i) else items.add(0, t)
        save()
    }

    /**
     * Тайтлы из списков на сайте: новые добавляются в конец, а те, что раньше пришли с сайта
     * и оттуда убраны, убираются и здесь. Добавленное только в приложении не трогаем.
     */
    fun mergeFromSite(list: List<Title>, siteKey: String) {
        val idsKey = "sitelists.ids.$siteKey"
        val before = try {
            JSONArray(KV.getString(idsKey, null) ?: "[]").let { a -> (0 until a.length()).map { a.optInt(it) }.toSet() }
        } catch (e: Exception) {
            emptySet()
        }
        val now = list.map { it.id }.toSet()
        val out = items.filter { !(it.id in before && it.id !in now) }.toMutableList()
        val have = out.map { it.id }.toMutableSet()
        for (a in list) if (have.add(a.id)) out.add(a)
        if (out.map { it.id } != items.map { it.id }) {
            items.clear()
            items.addAll(out)
            save()
        }
        KV.putString(idsKey, JSONArray(now.toList()).toString())
    }

    fun clear() {
        items.clear()
        KV.remove(key)
    }

    private fun save() {
        KV.putString(key, JSONArray(items.map { it.toJson() }).toString())
    }
}

/** Избранное: три отдельных списка по платформам, как в iOS. Обращения идут к списку текущей платформы. */
object FavoritesStore {
    private val anime = TitleList("favorites.v1")
    private val novels = TitleList("favorites.novels.v1")
    private val manga = TitleList("favorites.manga.v1")

    private val current: TitleList
        get() = when (Site.current) {
            Site.ANIME -> anime
            Site.RANOBE -> novels
            Site.MANGA -> manga
        }

    val items get() = current.items

    fun forSite(site: Site): TitleList = when (site) {
        Site.ANIME -> anime
        Site.RANOBE -> novels
        Site.MANGA -> manga
    }

    fun load() {
        anime.load()
        novels.load()
        manga.load()
    }

    fun contains(t: Title): Boolean = current.contains(t)
    fun toggle(t: Title) = current.toggle(t)
    fun clear() = current.clear()
}

class HistoryList(private val key: String) {
    val items = mutableStateListOf<HistoryItem>()

    fun load() {
        items.clear()
        val s = KV.getString(key, null) ?: return
        try {
            items.addAll(JSONArray(s).objects().mapNotNull { HistoryItem.from(it) })
        } catch (e: Exception) {
            // повреждённые данные игнорируем
        }
    }

    fun record(title: Title, id: Int, text: String) {
        items.removeAll { it.title.id == title.id }
        items.add(0, HistoryItem(title, id, text, HistoryItem.now()))
        while (items.size > 30) items.removeAt(items.lastIndex)
        save()
    }

    fun remove(titleId: Int) {
        items.removeAll { it.title.id == titleId }
        save()
    }

    fun clear() {
        items.clear()
        save()
    }

    private fun save() {
        KV.putString(key, JSONArray(items.map { it.toJson() }).toString())
    }
}

/** История: «Продолжить смотреть / читать». Три списка по платформам. */
object HistoryStore {
    private val anime = HistoryList("history.v1")
    private val novels = HistoryList("history.novels.v1")
    private val manga = HistoryList("history.manga.v1")

    private val current: HistoryList
        get() = when (Site.current) {
            Site.ANIME -> anime
            Site.RANOBE -> novels
            Site.MANGA -> manga
        }

    val items get() = current.items

    fun load() {
        anime.load()
        novels.load()
        manga.load()
    }

    fun recordChapter(title: Title, chapterId: Int, chapterTitle: String) = current.record(title, chapterId, chapterTitle)
    fun recordEpisode(title: Title, ep: EpisodeShort) = current.record(title, ep.id, ep.title)
    fun remove(titleId: Int) = current.remove(titleId)
    fun clear() = current.clear()
}

/** Позиции просмотра серий (progress.v1). Экраны обновляются только при заметных изменениях. */
object ProgressStore {
    private const val KEY = "progress.v1"
    private val map = HashMap<String, WatchProgress>()
    private var version by androidx.compose.runtime.mutableIntStateOf(0)

    fun load() {
        map.clear()
        try {
            KV.getString(KEY, null)?.let { s ->
                val o = JSONObject(s)
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val e = o.optJSONObject(k) ?: continue
                    map[k] = WatchProgress(
                        (J.clean(e.opt("position")) as? Number)?.toDouble() ?: 0.0,
                        (J.clean(e.opt("duration")) as? Number)?.toDouble() ?: 0.0,
                        (J.clean(e.opt("updated")) as? Number)?.toDouble() ?: 0.0,
                    )
                }
            }
        } catch (e: Exception) {
            // повреждённые данные игнорируем
        }
        version++
    }

    fun get(id: Int): WatchProgress? {
        @Suppress("UNUSED_VARIABLE")
        val v = version
        return map[id.toString()]
    }

    fun position(id: Int): Double = map[id.toString()]?.position ?: 0.0

    fun opened(id: Int) {
        if (map[id.toString()] == null) {
            map[id.toString()] = WatchProgress(0.0, 0.0, HistoryItem.now())
            save()
            version++
        }
    }

    fun update(id: Int, position: Double, duration: Double) {
        val k = id.toString()
        val old = map[k]
        val new = WatchProgress(position, duration, HistoryItem.now())
        map[k] = new
        save()
        if (old == null || old.finished != new.finished || Math.abs(old.position - position) >= 30) version++
    }

    fun clear() {
        map.clear()
        KV.remove(KEY)
        version++
    }

    private fun save() {
        val o = JSONObject()
        for ((k, v) in map) {
            o.put(k, JSONObject().put("position", v.position).put("duration", v.duration).put("updated", v.updated))
        }
        KV.putString(KEY, o.toString())
    }
}

/** Что прочитано: read.v1 {"<ключ тайтла>": [id глав]} и readlast.v1 {"<ключ тайтла>": id главы}. */
object ReadStore {
    private const val READ_KEY = "read.v1"
    private const val LAST_KEY = "readlast.v1"
    val read = mutableStateMapOf<String, List<Int>>()
    val last = mutableStateMapOf<String, Int>()

    fun load() {
        read.clear()
        last.clear()
        try {
            KV.getString(READ_KEY, null)?.let { s ->
                val o = JSONObject(s)
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val arr = o.optJSONArray(k) ?: continue
                    read[k] = (0 until arr.length()).mapNotNull { i -> J.int(arr.opt(i)) }
                }
            }
            KV.getString(LAST_KEY, null)?.let { s ->
                val o = JSONObject(s)
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    J.int(o.opt(k))?.let { v -> last[k] = v }
                }
            }
        } catch (e: Exception) {
            // повреждённые данные игнорируем
        }
    }

    fun isRead(titleKey: Int, chapterId: Int): Boolean = read[titleKey.toString()]?.contains(chapterId) == true

    fun lastChapter(titleKey: Int): Int? = last[titleKey.toString()]

    fun mark(titleKey: Int, chapterId: Int) {
        val k = titleKey.toString()
        val list = read[k] ?: emptyList()
        if (chapterId !in list) read[k] = list + chapterId
        last[k] = chapterId
        save()
    }

    fun clear() {
        read.clear()
        last.clear()
        KV.remove(READ_KEY)
        KV.remove(LAST_KEY)
    }

    private fun save() {
        val r = JSONObject()
        for ((k, v) in read) r.put(k, JSONArray(v))
        val l = JSONObject()
        for ((k, v) in last) l.put(k, v)
        KV.putString(READ_KEY, r.toString())
        KV.putString(LAST_KEY, l.toString())
    }
}

object SearchHistory {
    private const val KEY = "search.history"
    val items = mutableStateListOf<String>()

    fun load() {
        items.clear()
        val s = KV.getString(KEY, null) ?: return
        try {
            items.addAll(JSONArray(s).strings())
        } catch (e: Exception) {
            // повреждённые данные игнорируем
        }
    }

    fun add(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        items.removeAll { it.equals(t, ignoreCase = true) }
        items.add(0, t)
        while (items.size > 8) items.removeAt(items.lastIndex)
        save()
    }

    fun clear() {
        items.clear()
        KV.remove(KEY)
    }

    private fun save() {
        KV.putString(KEY, JSONArray(items.toList()).toString())
    }
}

object Stores {
    fun loadAll() {
        FavoritesStore.load()
        HistoryStore.load()
        ProgressStore.load()
        FavMetaStore.load()
        LocalFriends.load()
        ReadStore.load()
        SearchHistory.load()
    }
}
