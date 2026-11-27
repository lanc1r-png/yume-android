package app.yume.android.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Статусы избранного, как на сайте: «Смотрю/Читаю», «В планах», «Просмотрено/Прочитано», «Брошено». */
enum class ListStatus(val key: String, val color: Long) {
    ACTIVE("active", 0xFF40CC7A),
    PLANNED("planned", 0xFFFFB34D),
    DONE("done", 0xFF599EFF),
    DROPPED("dropped", 0xFFFF667A);

    fun title(book: Boolean): String = when (this) {
        ACTIVE -> if (book) "Читаю" else "Смотрю"
        PLANNED -> "В планах"
        DONE -> if (book) "Прочитано" else "Просмотрено"
        DROPPED -> "Брошено"
    }

    companion object {
        fun of(key: String?): ListStatus? = entries.firstOrNull { it.key == key }

        fun fromFolderName(name: String): ListStatus? {
            val n = name.lowercase()
            return when {
                n.contains("смотрю") || n.contains("читаю") -> ACTIVE
                n.contains("план") -> PLANNED
                n.contains("просмотр") || n.contains("прочитан") -> DONE
                n.contains("брош") -> DROPPED
                else -> null
            }
        }
    }
}

/** Метаданные тайтла в избранном (формат как в iOS; даты — секунды от 2001-01-01). */
data class FavMeta(
    val status: String? = null,
    val site: String? = null,
    val count: Int? = null,
    val release: Double? = null,
    val changed: Double? = null,
    val checked: Double? = null,
    val seen: Int? = null,
)

object FavMetaStore {
    private const val KEY = "favmeta.v1"
    private val map = HashMap<String, FavMeta>()
    private var version by mutableIntStateOf(0)

    fun load() {
        map.clear()
        try {
            val o = JSONObject(KV.getString(KEY, null) ?: "{}")
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val m = o.optJSONObject(k) ?: continue
                fun d(n: String): Double? = (J.clean(m.opt(n)) as? Number)?.toDouble()
                map[k] = FavMeta(
                    J.str(m.opt("status")), J.str(m.opt("site")), J.int(m.opt("count")),
                    d("release"), d("changed"), d("checked"), J.int(m.opt("seen")),
                )
            }
        } catch (e: Exception) {
            // повреждённые данные игнорируем
        }
        version++
    }

    private fun save() {
        val o = JSONObject()
        for ((k, v) in map) {
            val m = JSONObject()
            v.status?.let { m.put("status", it) }
            v.site?.let { m.put("site", it) }
            v.count?.let { m.put("count", it) }
            v.release?.let { m.put("release", it) }
            v.changed?.let { m.put("changed", it) }
            v.checked?.let { m.put("checked", it) }
            v.seen?.let { m.put("seen", it) }
            o.put(k, m)
        }
        KV.putString(KEY, o.toString())
        version++
    }

    private fun key(t: Title) = "${Site.current.key}-${t.id}"

    fun status(t: Title): ListStatus? {
        @Suppress("UNUSED_VARIABLE") val v = version
        return ListStatus.of(map[key(t)]?.status)
    }

    fun setStatus(t: Title, s: ListStatus?) {
        map[key(t)] = (map[key(t)] ?: FavMeta()).copy(status = s?.key)
        save()
    }

    fun applySiteStatuses(list: List<Pair<Title, ListStatus?>>, site: String) {
        var changed = false
        for ((t, st) in list) {
            val k = "$site-${t.id}"
            val m = map[k] ?: FavMeta()
            val s = st?.key
            if (m.site == s) continue
            map[k] = m.copy(status = s ?: m.status, site = s)
            changed = true
        }
        if (changed) save()
    }
}

/** Свои папки на сайте («Любимые» и другие) и какие тайтлы в них лежат. */
object SiteFolders {
    data class Folder(val id: Int, val name: String, val ids: List<Int>)

    var version by mutableIntStateOf(0)
        private set

    private fun key(site: String) = "sitelists.folders.$site"

    fun get(site: Site = Site.current): List<Folder> {
        @Suppress("UNUSED_VARIABLE") val v = version
        return try {
            JSONArray(KV.getString(key(site.key), null) ?: "[]").objects().map { o ->
                Folder(
                    J.int(o.opt("id")) ?: 0,
                    J.str(o.opt("name")) ?: "",
                    o.optJSONArray("ids")?.let { a -> (0 until a.length()).mapNotNull { J.int(a.opt(it)) } } ?: emptyList(),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun set(list: List<Folder>, site: String) {
        val arr = JSONArray()
        for (f in list) arr.put(JSONObject().put("id", f.id).put("name", f.name).put("ids", JSONArray(f.ids)))
        KV.putString(key(site), arr.toString())
        version++
    }
}

/** Списки на сайте → «Избранное» и статусы. Не чаще раза в полчаса; по запросу — сразу. */
object SiteListSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<String, Boolean>()
    val lastError = ConcurrentHashMap<String, String>()
    var syncing by mutableStateOf(false)
        private set

    fun syncAll(force: Boolean = false) {
        if (AuthStore.token == null) return
        scope.launch {
            syncing = true
            for (site in Site.entries) sync(site, force)
            syncing = false
        }
    }

    private suspend fun sync(site: Site, force: Boolean) {
        if (running.putIfAbsent(site.key, true) != null) return
        try {
            val uid = AuthStore.user?.id ?: return
            val lastKey = "sitelists.last.${site.key}"
            val now = System.currentTimeMillis()
            if (!force && now - (KV.getString(lastKey, null)?.toLongOrNull() ?: 0L) < 1_800_000L) return
            val root = J.parse(Api.raw("/bookmarks/folder/$uid", site = site)) as? JSONObject
            val arr = root?.optJSONArray("data") ?: run { lastError[site.key] = "неожиданный ответ сайта"; return }
            val folders = arr.objects().mapNotNull { d ->
                val id = J.int(d.opt("id")) ?: return@mapNotNull null
                id to (J.str(d.opt("name")) ?: J.str(d.opt("title")) ?: "")
            }
            if (folders.isEmpty()) return
            val titles = ArrayList<Title>()
            val status = HashMap<Int, ListStatus>()
            val seenAll = HashSet<Int>()
            val custom = ArrayList<SiteFolders.Folder>()
            for ((fid, name) in folders) {
                val st = ListStatus.fromFolderName(name)
                val members = ArrayList<Int>()
                val seen = HashSet<Int>()
                var page = 1
                while (page <= 40) {
                    val q = listOf("user_id" to uid.toString(), "page" to page.toString(), "status" to fid.toString())
                    val r = J.parse(Api.raw("/bookmarks", q, site = site)) as? JSONObject
                    val list = r?.optJSONArray("data") ?: run { lastError[site.key] = "неожиданный ответ сайта"; return }
                    val got = list.objects().mapNotNull { AuthStore.titleFromBookmark(it) }.filter { seen.add(it.id) }
                    if (got.isEmpty()) break
                    for (a in got) {
                        if (seenAll.add(a.id)) titles.add(a)
                        if (st != null) status[a.id] = st
                        members.add(a.id)
                    }
                    page++
                }
                if (st == null) custom.add(SiteFolders.Folder(fid, name, members))
            }
            FavoritesStore.forSite(site).mergeFromSite(titles, site.key)
            FavMetaStore.applySiteStatuses(titles.map { it to status[it.id] }, site.key)
            SiteFolders.set(custom, site.key)
            lastError.remove(site.key)
            KV.putString(lastKey, now.toString())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError[site.key] = e.message ?: "ошибка синхронизации"
        } finally {
            running.remove(site.key)
        }
    }
}

/** Отметка «прочитано» на сайте, когда читаешь главу (только ранобэ и манга, только вошедшим). */
object SiteSync {
    private val sent = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun view(novelId: Int, chapterId: Int) {
        if (AuthStore.token == null) return
        val site = Site.current
        if (!site.isBook) return
        val key = "${site.key}:$novelId:$chapterId"
        if (!sent.add(key)) return
        scope.launch {
            try {
                Api.raw("${site.apiPath}/$novelId/chapters/$chapterId/view", method = "POST", json = JSONObject(), site = site)
            } catch (e: Exception) {
                sent.remove(key)
            }
        }
    }
}
