package app.yume.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, val body: String) : Exception("Сервер вернул ошибку $code: $body")

enum class Sort(val raw: String, val title: String, val aliases: List<String>) {
    RATING("rate_avg", "По рейтингу", listOf("rate_avg", "rating_score")),
    VIEWS("views", "По просмотрам", listOf("views")),
    NEWEST("created_at", "Новинки", listOf("created_at")),
    LAST("last_episode_at", "Свежие главы", listOf("last_chapter_at", "last_episode_at")),
    NAME("name", "По названию", listOf("name", "rus_name")),
}

data class CatalogFilter(
    val query: String = "",
    val sort: Sort = Sort.RATING,
    val genres: Set<Int> = emptySet(),
    val tags: Set<Int> = emptySet(),
    val types: Set<Int> = emptySet(),
    val statuses: Set<Int> = emptySet(),
) {
    val activeCount: Int get() = genres.size + tags.size + types.size + statuses.size
}

data class FilterOptions(
    val genres: List<NamedItem> = emptyList(),
    val tags: List<NamedItem> = emptyList(),
    val types: List<NamedItem> = emptyList(),
    val statuses: List<NamedItem> = emptyList(),
) {
    val isEmpty: Boolean get() = genres.isEmpty() && tags.isEmpty() && types.isEmpty() && statuses.isEmpty()
}

data class CatalogPage(val items: List<Title>, val hasNext: Boolean)

object Api {
    private const val BASE = "https://api.cdnlibs.org/api"
    const val REFERER = "https://mangalib.me/"
    private const val ORIGIN = "https://mangalib.me"
    private const val SITE_ID = "1"
    const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

    /** Токен входа (вход в аккаунт появится позже). */
    @Volatile
    var token: String? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Клиент для картинок: страницы манги требуют Referer сайта. */
    val imageClient: OkHttpClient = client.newBuilder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Referer", REFERER)
                .header("User-Agent", UA)
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun raw(
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        method: String = "GET",
        json: JSONObject? = null,
    ): String = withContext(Dispatchers.IO) {
        val builder = "$BASE$path".toHttpUrl().newBuilder()
        for ((k, v) in query) builder.addQueryParameter(k, v)
        val rb = Request.Builder()
            .url(builder.build())
            .header("Referer", REFERER)
            .header("Origin", ORIGIN)
            .header("Site-Id", SITE_ID)
            .header("Accept", "application/json")
        token?.let { rb.header("Authorization", "Bearer $it") }
        if (method == "GET") {
            rb.get()
        } else {
            rb.method(method, (json?.toString() ?: "").toRequestBody("application/json".toMediaType()))
        }
        client.newCall(rb.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body.take(300))
            body
        }
    }

    private suspend fun rawRoot(path: String, query: List<Pair<String, String>> = emptyList()): JSONObject {
        val text = raw(path, query)
        return J.parse(text) as? JSONObject ?: throw ApiException(0, "Некорректный ответ сервера")
    }

    // ---- Каталог ----

    suspend fun catalog(f: CatalogFilter, page: Int): CatalogPage {
        var lastError: Throwable = IllegalStateException("Не удалось загрузить каталог")
        for (sort in f.sort.aliases) {
            for (withSite in listOf(true, false)) {
                try {
                    return fetchCatalog(f, page, withSite, sort)
                } catch (e: ApiException) {
                    if (e.code == 422 || e.code == 400) lastError = e else throw e
                }
            }
        }
        throw lastError
    }

    private suspend fun fetchCatalog(f: CatalogFilter, page: Int, withSite: Boolean, sort: String): CatalogPage {
        val q = ArrayList<Pair<String, String>>()
        q.add("page" to page.toString())
        q.add("sort_by" to sort)
        q.add("sort_type" to "desc")
        if (withSite) q.add("site_id[]" to SITE_ID)
        val text = f.query.trim()
        if (text.isNotEmpty()) q.add("q" to text)
        for (id in f.genres.sorted()) q.add("genres[]" to id.toString())
        for (id in f.tags.sorted()) q.add("tags[]" to id.toString())
        for (id in f.types.sorted()) q.add("types[]" to id.toString())
        for (id in f.statuses.sorted()) q.add("status[]" to id.toString())
        val root = rawRoot("/manga", q)
        val items = root.optJSONArray("data").objects().mapNotNull { Title.fromJson(it) }
        val hasNext = root.optJSONObject("meta")?.opt("has_next_page") as? Boolean ?: items.isNotEmpty()
        return CatalogPage(items, hasNext)
    }

    suspend fun filterOptions(): FilterOptions = FilterOptions(
        genres = constantList("genres", listOf("genres")),
        tags = constantList("tags", listOf("tags")),
        types = constantList("types", listOf("types", "type")),
        statuses = constantList("status", listOf("status", "statuses")),
    )

    private suspend fun constantList(field: String, keys: List<String>): List<NamedItem> {
        try {
            val data = rawRoot("/constants", listOf("fields[]" to field)).optJSONObject("data") ?: return emptyList()
            var arr: JSONArray? = null
            for (k in keys) {
                val a = data.optJSONArray(k)
                if (a != null) {
                    arr = a
                    break
                }
            }
            if (arr == null) {
                val it = data.keys()
                while (it.hasNext()) {
                    val a = data.optJSONArray(it.next())
                    if (a != null) {
                        arr = a
                        break
                    }
                }
            }
            return arr.objects().mapNotNull { NamedItem.from(it) }.filter { it.id != null && it.title.isNotEmpty() }
        } catch (e: ApiException) {
            return emptyList()
        } catch (e: java.io.IOException) {
            return emptyList()
        }
    }

    // ---- Все тайтлы автора ----

    @Volatile
    private var authorKnown: String? = null

    private suspend fun listRun(extra: List<Pair<String, String>>, page: Int): CatalogPage {
        for (withSite in listOf(true, false)) {
            val q = ArrayList<Pair<String, String>>()
            q.add("page" to page.toString())
            q.add("sort_by" to "rate_avg")
            q.add("sort_type" to "desc")
            q.addAll(extra)
            if (withSite) q.add("site_id[]" to SITE_ID)
            try {
                val root = rawRoot("/manga", q)
                val items = root.optJSONArray("data").objects().mapNotNull { Title.fromJson(it) }
                val hasNext = root.optJSONObject("meta")?.opt("has_next_page") as? Boolean ?: items.isNotEmpty()
                return CatalogPage(items, hasNext)
            } catch (e: ApiException) {
                if ((e.code == 422 || e.code == 400) && withSite) continue else throw e
            }
        }
        throw ApiException(0, "Не удалось загрузить список")
    }

    /**
     * Имя параметра фильтра по автору сайт нигде не описывает, поэтому пробуем варианты
     * (по id и по slug) и принимаем тот, что реально меняет выдачу.
     */
    suspend fun byAuthor(id: Int, slug: String?, page: Int): CatalogPage {
        authorKnown?.let { known ->
            val parts = known.split("|")
            val value = if (parts.size > 1 && parts[1] == "slug") (slug ?: id.toString()) else id.toString()
            return listRun(listOf(parts[0] to value), page)
        }
        val baseline = try {
            listRun(emptyList(), page).items.map { it.id }
        } catch (e: ApiException) {
            emptyList()
        } catch (e: java.io.IOException) {
            emptyList()
        }
        val names = listOf(
            "authors[]", "people[]", "author[]", "peoples[]", "persons[]", "authors", "people",
            "author", "person", "artists[]", "creators[]", "staff[]", "people_id", "author_id",
        )
        val values = ArrayList<Pair<String, String>>()
        values.add("id" to id.toString())
        if (!slug.isNullOrEmpty()) values.add("slug" to slug)
        for ((kind, value) in values) {
            for (name in names) {
                try {
                    val r = listRun(listOf(name to value), page)
                    val ids = r.items.map { it.id }
                    if (ids.isNotEmpty() && ids != baseline) {
                        authorKnown = "$name|$kind"
                        return r
                    }
                } catch (e: ApiException) {
                    // пробуем следующий вариант
                } catch (e: java.io.IOException) {
                    // пробуем следующий вариант
                }
            }
        }
        throw ApiException(404, "Фильтр по автору не сработал")
    }

    // ---- Тайтл и главы ----

    suspend fun details(slug: String): Title {
        val base = listOf("summary", "genres", "authors", "otherNames")
        fun q(fields: List<String>) = fields.map { "fields[]" to it }
        val data = try {
            rawRoot("/manga/$slug", q(base + "rate_avg"))
        } catch (e: ApiException) {
            rawRoot("/manga/$slug", q(base))
        }
        val d = data.optJSONObject("data") ?: throw ApiException(0, "Нет данных о тайтле")
        return Title.fromJson(d) ?: throw ApiException(0, "Нет данных о тайтле")
    }

    suspend fun chapters(slug: String): List<Chapter> {
        val root = rawRoot("/manga/$slug/chapters")
        return root.optJSONArray("data").objects().mapNotNull { Chapter.from(it) }
    }

    // ---- Страницы манги ----

    @Volatile
    private var cachedBases: List<String>? = null
    private const val FALLBACK_IMG = "https://img3.cdnlibs.org"

    private suspend fun imageBases(): List<String> {
        cachedBases?.let { return it }
        var list: List<String> = emptyList()
        try {
            val arr = rawRoot("/constants", listOf("fields[]" to "imageServers"))
                .optJSONObject("data")?.optJSONArray("imageServers")
            if (arr != null) {
                val mine = arr.objects().filter { srv ->
                    val ids = srv.optJSONArray("site_ids")
                    ids == null || ids.length() == 0 || (0 until ids.length()).any { J.int(ids.opt(it)) == 1 }
                }
                val main = mine.filter { it.optString("id") == "main" }
                val rest = mine.filter { it.optString("id") != "main" }
                list = (main + rest).mapNotNull { J.str(it.opt("url")) }
                if (list.isNotEmpty()) {
                    cachedBases = list + (if (FALLBACK_IMG in list) emptyList() else listOf(FALLBACK_IMG))
                }
            }
        } catch (e: ApiException) {
            // берём запасной сервер
        } catch (e: java.io.IOException) {
            // берём запасной сервер
        }
        if (FALLBACK_IMG !in list) list = list + FALLBACK_IMG
        return list
    }

    /** Путь страницы приходит как «//manga/…»: это часть адреса на сервере картинок, а не хост. */
    private fun resolve(path: String, base: String): String {
        if (path.startsWith("http")) return path
        val b = base.trimEnd('/')
        val p = path.trimStart('/')
        return "$b/$p"
    }

    private fun rawUrl(path: String, base: String): String? {
        if (path.startsWith("http")) return null
        return base + (if (path.startsWith("/")) path else "/$path")
    }

    suspend fun pages(slug: String, ch: Chapter): List<MangaPage> {
        val root = rawRoot("/manga/$slug/chapter", listOf("number" to ch.number, "volume" to ch.volume))
        val d = root.optJSONObject("data") ?: throw ApiException(0, "Глава недоступна")
        val raw = d.optJSONArray("pages").objects()
        val bases = imageBases()
        fun num(v: Any?): Float = ((J.clean(v) as? Number)?.toFloat()) ?: (J.int(v)?.toFloat() ?: 0f)
        val out = ArrayList<MangaPage>()
        for (p in raw) {
            val path = J.str(p.opt("url")) ?: J.str(p.opt("image")) ?: continue
            val urls = LinkedHashSet<String>()
            for (b in bases) {
                urls.add(resolve(path, b))
                rawUrl(path, b)?.let { urls.add(it) }
            }
            if (urls.isEmpty()) continue
            out.add(MangaPage(out.size, urls.toList(), num(p.opt("width")), num(p.opt("height"))))
        }
        return out
    }

    // ---- Комментарии ----

    data class CommentsPage(val items: List<CommentItem>, val hasNext: Boolean?)

    suspend fun comments(postId: Int, postType: String, page: Int): CommentsPage {
        val text = raw(
            "/comments",
            listOf("post_id" to postId.toString(), "post_type" to postType, "page" to page.toString()),
        )
        val root = J.parse(text)
        val list = CommentParser.parse(root)
        val meta = (root as? JSONObject)?.optJSONObject("meta")
        return CommentsPage(list, meta?.opt("has_next_page") as? Boolean)
    }

    suspend fun commentReplies(postId: Int, postType: String, parentId: Int): List<CommentItem> {
        for (key in listOf("parent_comment", "parent_id", "parent_comment_id")) {
            try {
                val text = raw(
                    "/comments",
                    listOf("post_id" to postId.toString(), "post_type" to postType, key to parentId.toString()),
                )
                val list = CommentParser.parse(J.parse(text)).filter { it.id != parentId }
                if (list.isNotEmpty()) return list
            } catch (e: ApiException) {
                // пробуем следующий вариант параметра
            }
        }
        return emptyList()
    }
}
