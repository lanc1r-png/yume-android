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

object FavoritesStore {
    private const val KEY = "favorites.manga.v1"
    val items = mutableStateListOf<Title>()

    fun load() {
        items.clear()
        val s = KV.getString(KEY, null) ?: return
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

    private fun save() {
        KV.putString(KEY, JSONArray(items.map { it.toJson() }).toString())
    }
}

object HistoryStore {
    private const val KEY = "history.manga.v1"
    val items = mutableStateListOf<HistoryItem>()

    fun load() {
        items.clear()
        val s = KV.getString(KEY, null) ?: return
        try {
            items.addAll(JSONArray(s).objects().mapNotNull { HistoryItem.from(it) })
        } catch (e: Exception) {
            // повреждённые данные игнорируем
        }
    }

    fun recordChapter(title: Title, chapterId: Int, chapterTitle: String) {
        items.removeAll { it.title.id == title.id }
        items.add(0, HistoryItem(title, chapterId, chapterTitle, HistoryItem.now()))
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
        KV.putString(KEY, JSONArray(items.map { it.toJson() }).toString())
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
        ReadStore.load()
        SearchHistory.load()
    }
}
