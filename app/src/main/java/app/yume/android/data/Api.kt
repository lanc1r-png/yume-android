package app.yume.android.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ApiException(val code: Int, val body: String) : Exception(
    if (code >= 500 || body.trimStart().startsWith("<")) {
        "Сайт сейчас не отвечает (ошибка $code). Попробуйте позже."
    } else if (code == 429) {
        "Сайт просит подождать. Попробуйте через минуту."
    } else {
        "Сервер вернул ошибку $code: $body"
    },
)

enum class Sort(val raw: String, val title: String, val aliases: List<String>) {
    RATING("rate_avg", "По рейтингу", listOf("rate_avg", "rating_score")),
    VIEWS("views", "По просмотрам", listOf("views")),
    NEWEST("created_at", "Новинки", listOf("created_at")),
    LAST("last_episode_at", "Свежие главы", listOf("last_chapter_at", "last_episode_at")),
    NAME("name", "По названию", listOf("name", "rus_name"));

    fun label(site: Site): String = when (this) {
        VIEWS -> if (site.isAnime) "По просмотрам" else "По чтению"
        LAST -> if (site.isAnime) "Свежие серии" else "Свежие главы"
        else -> title
    }

    /** Названия полей сортировки у разных сайтов отличаются, пробуем по очереди. */
    fun aliasesFor(site: Site): List<String> = when {
        this == LAST && site.isAnime -> listOf("last_episode_at", "last_chapter_at")
        site.isBook -> aliases
        else -> aliases
    }
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

data class VideoServer(val id: String?, val label: String?, val url: String)

data class VariantsResult(val variants: List<PlayableVariant>, val note: String)

private val variantCounter = AtomicInteger(0)

/** Вариант воспроизведения: озвучка с набором качеств или веб-плеер (Kodik и др.). */
data class PlayableVariant(
    val title: String,
    val url: String,
    val subtitle: String? = null,
    val isWeb: Boolean = false,
    val kodik: Boolean = false,
    val player: String = "",
    val options: List<PlayableVariant> = emptyList(),
    val alts: List<String> = emptyList(),
    val uid: Int = variantCounter.incrementAndGet(),
)

object Api {
    private const val BASE = "https://api.cdnlibs.org/api"
    const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

    /** Заголовки для видео Animelib (как в iOS-версии). */
    val playbackHeaders: Map<String, String> = mapOf(
        "Referer" to "https://v5.animelib.org/",
        "Origin" to "https://v5.animelib.org",
    )

    /** Токен входа (вход в аккаунт появится позже). */
    @Volatile
    var token: String? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val httpClient: OkHttpClient get() = client

    /** Клиент для картинок: страницы манги и иллюстрации ранобэ требуют Referer сайта. */
    val imageClient: OkHttpClient = client.newBuilder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Referer", Site.current.referer)
                .header("User-Agent", UA)
                .build()
            chain.proceed(req)
        }
        .build()

    /** Не больше 4 запросов одновременно: сайт при перегрузке отвечает 429. */
    private val gate = kotlinx.coroutines.sync.Semaphore(4)

    /** Показывается, пока сайт просит подождать (429). */
    var waitNote by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    suspend fun raw(
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        method: String = "GET",
        json: JSONObject? = null,
        site: Site? = null,
    ): String {
        var attempt = 0
        while (true) {
            try {
                val body = gate.withPermit { rawOnce(path, query, method, json, site) }
                waitNote = null
                return body
            } catch (e: ApiException) {
                if (e.code == 429 && attempt < 3) {
                    attempt++
                    waitNote = "Сайт просит подождать…"
                    kotlinx.coroutines.delay(2500L * attempt)
                    continue
                }
                waitNote = null
                throw e
            }
        }
    }

    private suspend fun rawOnce(
        path: String,
        query: List<Pair<String, String>>,
        method: String,
        json: JSONObject?,
        siteOverride: Site?,
    ): String = withContext(Dispatchers.IO) {
        val site = siteOverride ?: Site.current
        val builder = "$BASE$path".toHttpUrl().newBuilder()
        for ((k, v) in query) builder.addQueryParameter(k, v)
        val rb = Request.Builder()
            .url(builder.build())
            .header("Referer", site.referer)
            .header("Origin", site.origin)
            .header("Site-Id", site.id)
            .header("Accept", "application/json")
        token?.let { rb.header("Authorization", "Bearer $it") }
        if (method == "GET") {
            rb.get()
        } else {
            rb.method(method, (json?.toString() ?: "").toRequestBody("application/json".toMediaType()))
        }
        val started = System.currentTimeMillis()
        val reqUrl = builder.build().toString().removePrefix(BASE)
        try {
            client.newCall(rb.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                RequestLog.add(method, reqUrl, resp.code, System.currentTimeMillis() - started, if (resp.isSuccessful) "" else body.take(160).replace("\n", " "))
                if (!resp.isSuccessful) throw ApiException(resp.code, body.take(300))
                body
            }
        } catch (e: java.io.IOException) {
            RequestLog.add(method, reqUrl, 0, System.currentTimeMillis() - started, e.message ?: "сбой сети")
            throw e
        }
    }

    private suspend fun rawRoot(path: String, query: List<Pair<String, String>> = emptyList()): JSONObject {
        val text = raw(path, query)
        return J.parse(text) as? JSONObject ?: throw ApiException(0, "Некорректный ответ сервера")
    }

    // ---- Каталог ----

    suspend fun catalog(f: CatalogFilter, page: Int): CatalogPage {
        val site = Site.current
        var lastError: Throwable = IllegalStateException("Не удалось загрузить каталог")
        for (sort in f.sort.aliasesFor(site)) {
            for (withSite in listOf(true, false)) {
                try {
                    return fetchCatalog(site, f, page, withSite, sort)
                } catch (e: ApiException) {
                    if (e.code == 422 || e.code == 400) lastError = e else throw e
                }
            }
        }
        throw lastError
    }

    private suspend fun fetchCatalog(site: Site, f: CatalogFilter, page: Int, withSite: Boolean, sort: String): CatalogPage {
        val q = ArrayList<Pair<String, String>>()
        q.add("page" to page.toString())
        q.add("sort_by" to sort)
        q.add("sort_type" to "desc")
        if (withSite) q.add("site_id[]" to site.id)
        val text = f.query.trim()
        if (text.isNotEmpty()) q.add("q" to text)
        for (id in f.genres.sorted()) q.add("genres[]" to id.toString())
        for (id in f.tags.sorted()) q.add("tags[]" to id.toString())
        for (id in f.types.sorted()) q.add("types[]" to id.toString())
        for (id in f.statuses.sorted()) q.add("status[]" to id.toString())
        val root = rawRoot(site.apiPath, q)
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

    private val authorKnown = HashMap<String, String>()

    private suspend fun listRun(extra: List<Pair<String, String>>, page: Int): CatalogPage {
        val site = Site.current
        for (withSite in listOf(true, false)) {
            val q = ArrayList<Pair<String, String>>()
            q.add("page" to page.toString())
            q.add("sort_by" to "rate_avg")
            q.add("sort_type" to "desc")
            q.addAll(extra)
            if (withSite) q.add("site_id[]" to site.id)
            try {
                val root = rawRoot(site.apiPath, q)
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
        val siteKey = Site.current.key
        authorKnown[siteKey]?.let { known ->
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
                        authorKnown[siteKey] = "$name|$kind"
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

    // ---- Тайтл ----

    suspend fun details(slug: String): Title {
        val base = listOf("summary", "genres", "authors", "otherNames")
        fun q(fields: List<String>) = fields.map { "fields[]" to it }
        val path = Site.current.apiPath + "/" + slug
        val data = try {
            rawRoot(path, q(base + "rate_avg"))
        } catch (e: ApiException) {
            rawRoot(path, q(base))
        }
        val d = data.optJSONObject("data") ?: throw ApiException(0, "Нет данных о тайтле")
        return Title.fromJson(d) ?: throw ApiException(0, "Нет данных о тайтле")
    }

    // ---- Ранобэ и манга: главы ----

    suspend fun chapters(slug: String): List<Chapter> {
        val root = rawRoot("/manga/$slug/chapters")
        return root.optJSONArray("data").objects().mapNotNull { Chapter.from(it) }
    }

    fun chapterPath(slug: String) = "/manga/$slug/chapter"

    fun chapterQuery(ch: Chapter) = listOf("number" to ch.number, "volume" to ch.volume)

    /** Ответ сайта с текстом главы как есть (для чтения и для скачивания). */
    suspend fun chapterRaw(slug: String, ch: Chapter): String = raw(chapterPath(slug), chapterQuery(ch))

    // ---- Манга: страницы ----

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
        val root = J.parse(chapterRaw(slug, ch)) as? JSONObject ?: throw ApiException(0, "Некорректный ответ сервера")
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

    // ---- Аниме: серии и видео ----

    suspend fun episodes(animeId: Int): List<EpisodeShort> {
        val root = rawRoot("/episodes", listOf("anime_id" to animeId.toString()))
        return root.optJSONArray("data").objects().mapNotNull { EpisodeShort.from(it) }
    }

    @Volatile
    private var cachedServers: List<VideoServer>? = null

    private suspend fun videoServers(): List<VideoServer> {
        cachedServers?.let { if (it.isNotEmpty()) return it }
        val root = rawRoot("/constants", listOf("fields[]" to "videoServers"))
        val d = root.optJSONObject("data") ?: root
        val list = d.optJSONArray("videoServers").objects().mapNotNull { e ->
            val u = J.str(e.opt("url")) ?: return@mapNotNull null
            VideoServer(J.str(e.opt("id")), J.str(e.opt("label")), u)
        }
        if (list.isNotEmpty()) cachedServers = list
        return list
    }

    private fun absolute(href: String, base: String): String {
        if (href.startsWith("http")) return href
        if (href.startsWith("//")) return "https:$href"
        val b = base.trimEnd('/')
        val h = if (href.startsWith("/")) href else "/$href"
        return "$b$h"
    }

    suspend fun variants(episodeId: Int): VariantsResult {
        val text = raw("/episodes/$episodeId")
        val servers = try {
            videoServers()
        } catch (e: ApiException) {
            emptyList()
        } catch (e: java.io.IOException) {
            emptyList()
        }
        val mainServer = servers.firstOrNull { (it.id ?: "").lowercase().contains("main") } ?: servers.firstOrNull()
        val base: String? = mainServer?.url
        val otherBases = servers.map { it.url }.filter { it != base }

        val root = J.parse(text) as? JSONObject ?: throw ApiException(0, "Некорректный ответ сервера")
        val d = root.optJSONObject("data") ?: root
        val players = d.optJSONArray("players").objects()

        val native = ArrayList<PlayableVariant>()
        val web = ArrayList<PlayableVariant>()
        val seen = LinkedHashMap<String, Int>()
        for (p in players) {
            val name = J.str(p.opt("player")) ?: ""
            seen[name] = (seen[name] ?: 0) + 1
            val team = J.str(p.optJSONObject("team")?.opt("name")) ?: "—"
            val tt = p.optJSONObject("translation_type")
            val kind = J.str(tt?.opt("label")) ?: J.str(tt?.opt("title")) ?: J.str(tt?.opt("name")) ?: ""
            val label = if (kind.isEmpty()) team else "$team ($kind)"

            if (name.lowercase() == "animelib") {
                if (base == null) continue
                val video = p.optJSONObject("video")
                val list = video?.optJSONArray("quality").objects()
                val sub = p.optJSONArray("subtitles").objects().firstOrNull()?.let { J.str(it.opt("src")) }
                    ?.let { absolute(it, base) }
                val options = ArrayList<PlayableVariant>()
                for (q in list.sortedByDescending { J.int(it.opt("quality")) ?: 0 }) {
                    val href = J.str(q.opt("href")) ?: continue
                    val h = J.int(q.opt("quality"))?.let { "${it}p" } ?: "—"
                    options.add(
                        PlayableVariant(
                            title = h,
                            url = absolute(href, base),
                            subtitle = sub,
                            alts = otherBases.map { absolute(href, it) },
                        ),
                    )
                }
                val best = options.firstOrNull() ?: continue
                native.add(PlayableVariant(title = label, url = best.url, subtitle = sub, player = "Animelib", options = options))
            } else {
                val src = J.str(p.opt("src")) ?: continue
                val full = if (src.startsWith("//")) "https:$src" else src
                val isK = full.contains("kodik")
                val pn = name.ifEmpty { "Плеер" }
                web.add(PlayableVariant(title = "$label · $pn", url = full, isWeb = true, kodik = isK, player = pn))
            }
        }

        var note = ""
        if (native.isEmpty()) {
            val list = seen.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}×${it.value}" }
            note = if (seen.keys.any { it.lowercase() == "animelib" }) {
                if (base == null) "Animelib: адрес видео-сервера не получен (в ответе: $list)"
                else "Animelib: в ответе есть плеер, но без ссылок на видео"
            } else {
                "Animelib: сервер не отдал этот плеер (в ответе: ${list.ifEmpty { "пусто" }})"
            }
        }
        val result = native + web
        if (result.isEmpty()) throw ApiException(0, "Для этой серии нет доступного видео")
        return VariantsResult(result, note)
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
