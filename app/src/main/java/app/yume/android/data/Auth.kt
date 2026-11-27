package app.yume.android.data

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class ProfileUser(
    val id: Int,
    val username: String,
    val avatar: String?,
    val level: Int?,
    val streak: Int?,
    val maxStreak: Int?,
    val premium: Boolean,
    val cover: String?,
)

data class BookmarkFolder(val id: Int, val name: String, val count: Int?)

/**
 * Вход через сайт. Токен лежит в зашифрованном хранилище (Android Keystore), а не в основных настройках,
 * поэтому в резервную копию не попадает.
 */
object AuthStore {
    var token by mutableStateOf<String?>(null)
        private set
    var user by mutableStateOf<ProfileUser?>(null)
        private set
    var error by mutableStateOf<String?>(null)

    private var secure: SharedPreferences? = null

    fun init(ctx: Context) {
        secure = try {
            val key = androidx.security.crypto.MasterKey.Builder(ctx)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            androidx.security.crypto.EncryptedSharedPreferences.create(
                ctx,
                "yume_secure",
                key,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            null
        }
        token = secure?.getString("lib.token", null)
        Api.token = token
    }

    suspend fun signIn(access: String, refresh: String?) {
        token = access
        Api.token = access
        secure?.edit()?.putString("lib.token", access)?.apply()
        if (refresh != null) secure?.edit()?.putString("lib.refresh", refresh)?.apply()
        refreshUser()
    }

    fun signOut() {
        token = null
        Api.token = null
        user = null
        secure?.edit()?.remove("lib.token")?.remove("lib.refresh")?.apply()
        try {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            // не критично
        }
    }

    /** Ищет access_token в данных localStorage сайта, в том числе во вложенных JSON-строках. */
    fun findToken(obj: Any?): Pair<String, String?>? {
        when (obj) {
            is JSONObject -> {
                val a = obj.optString("access_token", "")
                if (a.isNotEmpty()) return a to obj.optString("refresh_token", "").ifEmpty { null }
                val it = obj.keys()
                while (it.hasNext()) findToken(obj.opt(it.next()))?.let { r -> return r }
            }
            is JSONArray -> for (i in 0 until obj.length()) findToken(obj.opt(i))?.let { r -> return r }
            is String -> {
                if (obj.startsWith("{") || obj.startsWith("[")) {
                    val parsed = try {
                        org.json.JSONTokener(obj).nextValue()
                    } catch (e: Exception) {
                        null
                    }
                    return findToken(parsed)
                }
            }
        }
        return null
    }

    suspend fun refreshUser() {
        if (token == null) return
        try {
            val root = J.parse(Api.raw("/auth/me")) as? JSONObject ?: throw ApiException(0, "Некорректный ответ")
            val d = root.optJSONObject("data") ?: root
            val id = J.int(d.opt("id")) ?: throw ApiException(0, "Нет данных профиля")
            val st = d.optJSONObject("login_streak")
            user = ProfileUser(
                id = id,
                username = J.str(d.opt("username")) ?: J.str(d.opt("name")) ?: "Профиль",
                avatar = J.url(d.opt("avatar")),
                level = J.int(d.opt("level")) ?: J.int(d.optJSONObject("user_level")?.opt("level")),
                streak = J.int(st?.opt("login_streak")) ?: J.int(d.opt("login_streak")),
                maxStreak = J.int(st?.opt("max_login_streak")),
                premium = d.optJSONObject("premium")?.optBoolean("enabled") ?: false,
                cover = deepCover(d, 0),
            )
            error = null
            if (user?.cover == null) loadCover(id)
        } catch (e: ApiException) {
            if (e.code == 401 || e.code == 403) {
                signOut()
                error = "Сессия закончилась, войдите снова"
            } else {
                error = e.message
            }
        } catch (e: java.io.IOException) {
            error = "Нет соединения"
        }
    }

    private fun deepCover(d: JSONObject, depth: Int): String? {
        val hints = listOf("cover", "background", "banner", "header", "bg")
        val it = d.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (hints.any { k.lowercase().contains(it) }) J.url(d.opt(k))?.let { u -> return u }
        }
        if (depth < 3) {
            val it2 = d.keys()
            while (it2.hasNext()) {
                val nested = d.optJSONObject(it2.next()) ?: continue
                deepCover(nested, depth + 1)?.let { u -> return u }
            }
        }
        return null
    }

    private suspend fun loadCover(id: Int) {
        for (fields in listOf(emptyList(), listOf("background"), listOf("cover"), listOf("banner"))) {
            try {
                val root = J.parse(Api.raw("/user/$id", fields.map { "fields[]" to it })) as? JSONObject ?: continue
                val d = root.optJSONObject("data") ?: root
                val u = deepCover(d, 0)
                if (u != null) {
                    user = user?.copy(cover = u)
                    return
                }
            } catch (e: ApiException) {
                // следующий вариант
            } catch (e: java.io.IOException) {
                return
            }
        }
    }

    // ---- Закладки сайта ----

    suspend fun folders(): List<BookmarkFolder> {
        val uid = user?.id ?: return emptyList()
        val root = J.parse(Api.raw("/bookmarks/folder/$uid")) as? JSONObject ?: return emptyList()
        return root.optJSONArray("data").objects().mapNotNull { d ->
            val id = J.int(d.opt("id")) ?: return@mapNotNull null
            BookmarkFolder(
                id,
                J.str(d.opt("name")) ?: J.str(d.opt("title")) ?: "Список",
                J.int(d.opt("count")) ?: J.int(d.opt("items_count")) ?: J.int(d.opt("titles_count")),
            )
        }
    }

    suspend fun bookmarks(folder: Int?, page: Int): List<Title> {
        val uid = user?.id ?: return emptyList()
        val q = ArrayList<Pair<String, String>>()
        q.add("user_id" to uid.toString())
        q.add("page" to page.toString())
        if (folder != null) q.add("status" to folder.toString())
        val root = J.parse(Api.raw("/bookmarks", q)) as? JSONObject ?: return emptyList()
        return root.optJSONArray("data").objects().mapNotNull { titleFrom(it) }
    }

    /** Тайтл в списке закладок может лежать на разной глубине: выбираем самый похожий объект. */
    fun titleFromBookmark(d: JSONObject): Title? = titleFrom(d)

    private fun titleFrom(d: JSONObject): Title? {
        val candidates = ArrayList<JSONObject>()
        val it = d.keys()
        while (it.hasNext()) d.optJSONObject(it.next())?.let { candidates.add(it) }
        candidates.add(d)
        var best: Pair<Int, JSONObject>? = null
        for (c in candidates) {
            if (J.int(c.opt("id")) == null) continue
            var score = 0
            if (J.str(c.opt("rus_name")) != null) score += 4
            if (J.str(c.opt("name")) != null) score += 2
            if (c.has("cover")) score += 2
            if (J.str(c.opt("slug_url")) != null) score += 1
            if (score >= 3 && (best == null || score > best.first)) best = score to c
        }
        return best?.let { Title.fromJson(it.second) }
    }

    /** Возвращает текст ошибки или null при успехе. */
    suspend fun addToFolder(title: Title, folder: Int): String? {
        val type = Site.current.postType
        val bodies = listOf(
            JSONObject().put("id", title.id).put("type", folder).put("model", type),
            JSONObject().put("bookmark", JSONObject().put("type", folder)).put("id", title.id).put("model", type),
            JSONObject().put("type", folder).put("relation_id", title.id).put("relation_type", type),
        )
        var last = ""
        for (b in bodies) {
            try {
                Api.raw("/bookmarks", method = "POST", json = b)
                return null
            } catch (e: ApiException) {
                last = e.message ?: ""
            }
        }
        return "Не получилось добавить: $last"
    }

    suspend fun removeFromFolders(title: Title): String? {
        val type = Site.current.postType
        var last = ""
        for (q in listOf(listOf("type" to type), emptyList())) {
            try {
                Api.raw("/bookmarks/${title.id}", q, method = "DELETE")
                return null
            } catch (e: ApiException) {
                last = e.message ?: ""
            }
        }
        return "Не получилось убрать: $last"
    }

    // ---- Комментарии ----

    suspend fun sendComment(text: String, postId: Int, postType: String, parent: Int?): String? {
        if (token == null) return "Сначала войдите во вкладке «Профиль»"
        val base = JSONObject().put("comment", text).put("post_id", postId).put("post_type", postType)
        if (parent != null) base.put("parent_comment", parent)
        val alt = JSONObject(base.toString()).put("is_spoiler", false).put("attachments", JSONArray())
        var last = ""
        for (b in listOf(base, alt)) {
            try {
                Api.raw("/comments", method = "POST", json = b)
                return null
            } catch (e: ApiException) {
                last = e.message ?: ""
            }
        }
        return last
    }

    suspend fun like(commentId: Int): String? {
        if (token == null) return "Сначала войдите во вкладке «Профиль»"
        val attempts = listOf(
            "/comments/$commentId/vote" to JSONObject().put("type", "up"),
            "/comments/$commentId/vote" to JSONObject().put("vote", 1),
            "/comments/$commentId/like" to JSONObject(),
        )
        var last = ""
        for ((path, body) in attempts) {
            try {
                Api.raw(path, method = "POST", json = body)
                return null
            } catch (e: ApiException) {
                last = e.message ?: ""
            }
        }
        return last
    }
}
