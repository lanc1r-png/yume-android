package app.yume.android.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Сколько серий и глав открыто за день. Один и тот же эпизод или глава за день считаются один раз. */
object ActivityStore {
    private const val KEY = "activity.v1"
    data class Day(val episodes: Int = 0, val manga: Int = 0, val novels: Int = 0)

    var version by mutableIntStateOf(0)
        private set

    fun dayKey(t: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

    private fun load(): JSONObject = try {
        JSONObject(KV.getString(KEY, null) ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    fun record(kind: String, id: Int) {
        val root = load()
        val day = dayKey()
        val d = root.optJSONObject(day) ?: JSONObject()
        val seen = d.optJSONArray("seen") ?: JSONArray()
        val tag = "$kind:$id"
        for (i in 0 until seen.length()) if (seen.optString(i) == tag) return
        seen.put(tag)
        d.put("seen", seen)
        d.put(kind, d.optInt(kind) + 1)
        root.put(day, d)
        // хранить дольше полугода незачем
        val cutoff = dayKey(System.currentTimeMillis() - 200L * 86400_000)
        val it = root.keys()
        val old = ArrayList<String>()
        while (it.hasNext()) { val k = it.next(); if (k < cutoff) old.add(k) }
        old.forEach { root.remove(it) }
        KV.putString(KEY, root.toString())
        version++
    }

    fun days(): Map<String, Day> {
        @Suppress("UNUSED_VARIABLE") val v = version
        val root = load()
        val out = HashMap<String, Day>()
        val it = root.keys()
        while (it.hasNext()) {
            val k = it.next()
            val d = root.optJSONObject(k) ?: continue
            out[k] = Day(d.optInt("episodes"), d.optInt("manga"), d.optInt("novels"))
        }
        return out
    }
}

/** Жанры из /user/{id}/stats: ищем в ответе список «название — число». */
object UserGenreStats {
    data class Genre(val name: String, val count: Int)

    private val cache = HashMap<String, List<Genre>>()
    private val loadedAt = HashMap<String, Long>()

    fun genres(site: Site = Site.current): List<Genre> = cache[site.key] ?: try {
        JSONArray(KV.getString("userstats.genres.${site.key}", null) ?: "[]").objects()
            .mapNotNull { o -> J.str(o.opt("name"))?.let { Genre(it, o.optInt("count")) } }
            .also { cache[site.key] = it }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun load(userId: Int): List<Genre> {
        val site = Site.current
        val t = loadedAt[site.key]
        if (t != null && System.currentTimeMillis() - t < 1_800_000) return genres(site)
        loadedAt[site.key] = System.currentTimeMillis()
        val root = try {
            J.parse(Api.raw("/user/$userId/stats"))
        } catch (e: Exception) {
            loadedAt.remove(site.key)
            null
        } ?: return genres(site)
        val list = parse(root)
        if (list.isNotEmpty()) {
            cache[site.key] = list
            KV.putString(
                "userstats.genres.${site.key}",
                JSONArray(list.map { JSONObject().put("name", it.name).put("count", it.count) }).toString(),
            )
        }
        return genres(site)
    }

    private val nameKeys = listOf("rus_name", "name", "title", "label")
    private val countKeys = listOf("count", "value", "total", "cnt", "items_count", "chapters_count", "titles_count", "amount")

    private fun item(d: JSONObject): Genre? {
        val src = JSONObject(d.toString())
        for (k in listOf("genre", "item", "model")) {
            val sub = d.optJSONObject(k) ?: continue
            val it = sub.keys()
            while (it.hasNext()) { val kk = it.next(); if (!src.has(kk)) src.put(kk, sub.get(kk)) }
        }
        val name = nameKeys.firstNotNullOfOrNull { J.str(src.opt(it))?.takeIf { s -> s.isNotEmpty() } } ?: return null
        val n = countKeys.firstNotNullOfOrNull { J.int(d.opt(it)) ?: J.int(src.opt(it)) } ?: return null
        return Genre(name, n)
    }

    fun parse(root: Any): List<Genre> {
        var bestScore = 0
        var best: List<Genre> = emptyList()
        fun walk(v: Any?, path: String, depth: Int) {
            if (depth >= 6) return
            if (v is JSONArray) {
                val objs = v.objects()
                if (objs.isEmpty()) return
                val got = objs.mapNotNull { item(it) }
                if (got.size >= maxOf(2, objs.size / 2)) {
                    val score = got.size + (if (path.contains("genre")) 1000 else 0) - (if (path.contains("tag")) 500 else 0)
                    if (score > bestScore) { bestScore = score; best = got }
                }
                for (x in objs.take(3)) walk(x, path, depth + 1)
            } else if (v is JSONObject) {
                if (path.contains("genre")) {
                    val pairs = ArrayList<Genre>()
                    val it = v.keys()
                    while (it.hasNext()) { val k = it.next(); J.int(v.opt(k))?.let { pairs.add(Genre(k, it)) } }
                    if (pairs.size >= 2 && pairs.size + 900 > bestScore) { bestScore = pairs.size + 900; best = pairs }
                }
                val it = v.keys()
                while (it.hasNext()) { val k = it.next(); walk(v.opt(k), path + "/" + k.lowercase(), depth + 1) }
            }
        }
        walk(root, "", 0)
        return best.filter { it.count > 0 }.sortedByDescending { it.count }
    }
}
