package app.yume.android.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class Friend(
    val id: Int,
    val username: String,
    val avatar: String? = null,
    val cover: String? = null,
    val online: Boolean? = null,
    val lastSeen: Long? = null,
    val level: Int? = null,
    val streak: Int? = null,
    val premium: Boolean = false,
) {
    val statusText: String
        get() {
            if (online == true) return "в сети"
            val t = lastSeen ?: return ""
            val sec = (System.currentTimeMillis() - t) / 1000
            val rel = when {
                sec < 90 -> "только что"
                sec < 3600 -> "${sec / 60} мин. назад"
                sec < 86400 -> "${sec / 3600} ч. назад"
                else -> "${sec / 86400} дн. назад"
            }
            return "был(а) $rel"
        }

    fun merge(o: Friend): Friend = copy(
        avatar = avatar ?: o.avatar, cover = cover ?: o.cover, online = online ?: o.online,
        lastSeen = lastSeen ?: o.lastSeen, level = level ?: o.level, streak = streak ?: o.streak,
        premium = premium || o.premium,
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("username", username)
        avatar?.let { put("avatar", it) }
        cover?.let { put("cover", it) }
        online?.let { put("online", it) }
        lastSeen?.let { put("lastSeen", it) }
        level?.let { put("level", it) }
        streak?.let { put("streak", it) }
        put("premium", premium)
    }

    companion object {
        fun fromJson(o: JSONObject): Friend? {
            val id = J.int(o.opt("id")) ?: return null
            return Friend(
                id, J.str(o.opt("username")) ?: return null, J.str(o.opt("avatar")), J.str(o.opt("cover")),
                if (o.has("online")) o.optBoolean("online") else null,
                (J.clean(o.opt("lastSeen")) as? Number)?.toLong(), J.int(o.opt("level")), J.int(o.opt("streak")),
                o.optBoolean("premium"),
            )
        }
    }
}

object FriendParser {
    fun date(v: Any?): Long? {
        val s = J.str(v) ?: return null
        try {
            return java.time.Instant.parse(s).toEpochMilli()
        } catch (e: Exception) {
            // другой формат
        }
        return try {
            val t = s.replace("T", " ").take(19)
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(t)?.time
        } catch (e: Exception) {
            null
        }
    }

    /** Запись о друге может быть самим пользователем или обёрткой с вложенным user. */
    fun friend(d: JSONObject): Friend? {
        val cands = ArrayList<JSONObject>()
        cands.add(d)
        val it = d.keys()
        while (it.hasNext()) d.optJSONObject(it.next())?.let { cands.add(it) }
        for (c in cands) {
            val id = J.int(c.opt("id")) ?: continue
            val name = J.str(c.opt("username")) ?: J.str(c.opt("name")) ?: continue
            var online: Boolean? = if (c.has("is_online")) c.optBoolean("is_online") else if (c.has("online")) c.optBoolean("online") else null
            var seen: Long? = null
            for (k in listOf("last_online_at", "last_seen_at", "last_online", "last_activity_at", "last_seen")) {
                val dt = date(c.opt(k))
                if (dt != null) {
                    seen = dt
                    break
                }
            }
            if (online == null && seen != null) online = System.currentTimeMillis() - seen!! < 300_000
            val st = c.optJSONObject("login_streak")
            return Friend(
                id, name, J.url(c.opt("avatar")), AuthStore.deepCover(c, 0), online, seen,
                J.int(c.opt("level")) ?: J.int(c.optJSONObject("user_level")?.opt("level")),
                J.int(st?.opt("login_streak")) ?: J.int(c.opt("login_streak")),
                c.optJSONObject("premium")?.optBoolean("enabled") ?: false,
            )
        }
        return null
    }

    suspend fun detail(id: Int): Friend? = try {
        val root = J.parse(Api.raw("/user/$id")) as? JSONObject
        friend(root?.optJSONObject("data") ?: root ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    fun userId(text: String): Int? {
        val t = text.trim()
        t.toIntOrNull()?.let { return it }
        val i = t.indexOf("/user/")
        if (i < 0) return null
        return t.substring(i + 6).takeWhile { it.isDigit() }.toIntOrNull()
    }

    fun parseList(text: String): List<Friend>? {
        val root = J.parse(text) ?: return null
        var arr: JSONArray? = null
        if (root is JSONObject) {
            arr = root.optJSONArray("data")
            if (arr == null) {
                val inner = root.optJSONObject("data")
                arr = inner?.optJSONArray("items") ?: inner?.optJSONArray("friends") ?: inner?.optJSONArray("data")
            }
            if (arr == null) arr = root.optJSONArray("friends") ?: root.optJSONArray("items")
        } else if (root is JSONArray) {
            arr = root
        }
        return arr?.objects()?.mapNotNull { friend(it) }
    }
}

object LocalFriends {
    private const val KEY = "friends.local.v1"
    val items = mutableStateListOf<Friend>()

    fun load() {
        items.clear()
        try {
            items.addAll(JSONArray(KV.getString(KEY, null) ?: "[]").objects().mapNotNull { Friend.fromJson(it) })
        } catch (e: Exception) {
            // повреждённые данные игнорируем
        }
    }

    fun contains(id: Int) = items.any { it.id == id }

    fun toggle(f: Friend) {
        val i = items.indexOfFirst { it.id == f.id }
        if (i >= 0) items.removeAt(i) else items.add(0, f)
        KV.putString(KEY, JSONArray(items.map { it.toJson() }).toString())
    }
}

object FriendsModel {
    private const val CACHE = "friends.cache.v1"
    private const val ENDPOINT = "friends.endpoint"
    val friends = mutableStateListOf<Friend>()
    var loading by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var debug by mutableStateOf("")
    private var syncReplaced = false

    fun init() {
        try {
            friends.addAll(JSONArray(KV.getString(CACHE, null) ?: "[]").objects().mapNotNull { Friend.fromJson(it) })
        } catch (e: Exception) {
            // кэш подтянется заново
        }
    }

    private fun saveCache() = KV.putString(CACHE, JSONArray(friends.map { it.toJson() }).toString())

    fun beginSync() { syncReplaced = false }

    /** Друзья, пойманные из запросов сайта. Адрес запроса запоминаем, чтобы дальше брать список напрямую. */
    fun absorb(list: List<Friend>, endpoint: String) {
        if (!syncReplaced) {
            friends.clear()
            syncReplaced = true
        }
        for (f in list) {
            val i = friends.indexOfFirst { it.id == f.id }
            if (i >= 0) friends[i] = friends[i].merge(f) else friends.add(f)
        }
        KV.putString(ENDPOINT, endpoint)
        debug = ""
        loaded = true
        saveCache()
    }

    private fun savedEndpoint(): Pair<String, List<Pair<String, String>>>? {
        val e = KV.getString(ENDPOINT, null) ?: return null
        return try {
            val u = java.net.URI(e)
            val i = u.path.indexOf("/api/")
            if (i < 0) return null
            val q = (u.rawQuery ?: "").split("&").filter { it.isNotEmpty() }.map {
                val kv = it.split("=", limit = 2)
                java.net.URLDecoder.decode(kv[0], "UTF-8") to java.net.URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
            }.filter { it.first != "page" }
            "/" + u.path.substring(i + 5) to q
        } catch (ex: Exception) {
            null
        }
    }

    fun merged(api: List<Friend>, local: List<Friend>): List<Friend> {
        val all = ArrayList(api)
        for (l in local) if (all.none { it.id == l.id }) all.add(l)
        return all.sortedWith(
            compareByDescending<Friend> { it.online == true }
                .thenByDescending { it.lastSeen ?: 0L }
                .thenBy { it.username.lowercase() },
        )
    }

    suspend fun load(userId: Int) {
        if (loading) return
        loading = true
        try {
            val u = "user_id" to userId.toString()
            val p1 = "page" to "1"
            val cands = ArrayList<Pair<String, List<Pair<String, String>>>>()
            savedEndpoint()?.let { cands.add(it) }
            cands.addAll(
                listOf(
                    "/user/friends" to listOf(p1), "/user/friends" to listOf(u), "/user/friends" to listOf(u, p1),
                    "/user/friends" to emptyList(), "/user/$userId/friends" to listOf(p1), "/user/$userId/friends" to emptyList(),
                    "/users/$userId/friends" to emptyList(), "/friends" to listOf(u), "/friends" to emptyList(),
                    "/social/friends" to listOf(u), "/user/$userId/relations" to emptyList(),
                    "/user/relations" to listOf(u), "/user/$userId/subscriptions" to emptyList(),
                ),
            )
            val log = ArrayList<String>()
            var result: List<Friend>? = null
            var used: Pair<String, List<Pair<String, String>>>? = null
            for ((path, q) in cands) {
                try {
                    val list = FriendParser.parseList(Api.raw(path, q))
                    if (list != null) {
                        result = list
                        used = path to q
                        break
                    }
                    log.add("$path: ответ без списка")
                } catch (e: ApiException) {
                    log.add("$path: ${e.code}")
                } catch (e: java.io.IOException) {
                    log.add("$path: сбой")
                }
            }
            if (result != null) {
                val all = ArrayList(result)
                if (used != null && all.isNotEmpty()) {
                    var page = 2
                    while (page <= 8) {
                        val qq = used.second.filter { it.first != "page" } + ("page" to page.toString())
                        val more = try {
                            FriendParser.parseList(Api.raw(used.first, qq))
                        } catch (e: Exception) {
                            null
                        } ?: break
                        val known = all.map { it.id }.toSet()
                        val fresh = more.filter { it.id !in known }
                        if (fresh.isEmpty()) break
                        all.addAll(fresh)
                        page++
                    }
                }
                friends.clear()
                friends.addAll(all)
                debug = ""
                enrich()
                saveCache()
            } else {
                debug = log.joinToString("\n")
            }
        } finally {
            loading = false
            loaded = true
        }
    }

    private suspend fun enrich() {
        val need = friends.filter { it.online == null }.take(40).map { it.id }
        for (id in need) {
            val d = FriendParser.detail(id) ?: continue
            val i = friends.indexOfFirst { it.id == id }
            if (i >= 0) friends[i] = friends[i].merge(d)
        }
    }
}
