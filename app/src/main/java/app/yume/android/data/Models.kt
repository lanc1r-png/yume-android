package app.yume.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Форматы JSON (toJson/fromJson) совпадают с Codable-моделями iOS-версии,
 * чтобы резервные копии открывались в обе стороны.
 */

data class Cover(val thumbnail: String?, val default: String?, val md: String?) {
    fun toJson(): JSONObject = JSONObject().apply {
        thumbnail?.let { put("thumbnail", it) }
        default?.let { put("default", it) }
        md?.let { put("md", it) }
    }

    companion object {
        fun from(o: JSONObject?): Cover? = o?.let {
            Cover(J.str(it.opt("thumbnail")), J.str(it.opt("default")), J.str(it.opt("md")))
        }
    }
}

data class NamedItem(val id: Int?, val name: String?, val label: String?, val slug: String?) {
    val title: String get() = name ?: label ?: ""

    fun toJson(): JSONObject = JSONObject().apply {
        id?.let { put("id", it) }
        name?.let { put("name", it) }
        label?.let { put("label", it) }
        slug?.let { put("slug_url", it) }
    }

    companion object {
        fun from(o: JSONObject?): NamedItem? = o?.let {
            NamedItem(J.int(it.opt("id")), J.str(it.opt("name")), J.str(it.opt("label")), J.str(it.opt("slug_url")))
        }

        fun list(a: JSONArray?): List<NamedItem>? =
            if (a == null) null else a.objects().mapNotNull { from(it) }
    }
}

private fun rate(v: Any?): String? = when (val x = J.clean(v)) {
    is String -> x
    is Number -> String.format(Locale.US, "%.1f", x.toDouble())
    else -> null
}

/** Тайтл (в iOS-коде называется Anime, но там же хранится и манга). */
data class Title(
    val id: Int,
    val name: String?,
    val rusName: String?,
    val engName: String?,
    val slugUrl: String?,
    val cover: Cover?,
    val summary: String?,
    val genres: List<NamedItem>?,
    val authors: List<NamedItem>?,
    val otherNames: List<String>?,
    val episodesCount: Int?,
    val rateAvg: String?,
    val status: NamedItem?,
    val type: NamedItem?,
    val ageRestriction: NamedItem?,
    val releaseDateString: String?,
) {
    val displayTitle: String
        get() = rusName?.takeIf { it.isNotEmpty() } ?: name ?: engName ?: "Без названия"

    /** Обложка среднего размера: для карточек и списков. */
    val imageUrl: String?
        get() = J.normalizeUrl(cover?.md ?: cover?.default ?: cover?.thumbnail)

    /** Самая крупная обложка: для экрана тайтла. */
    val coverLarge: String?
        get() = J.normalizeUrl(cover?.default ?: cover?.md ?: cover?.thumbnail)

    /** Ключ в хранилище прочитанного: у манги id отрицательный (у манги и ранобэ id тайтлов могут совпасть). */
    val readKey: Int get() = if (Site.current.isManga) -id else id

    val siteUrl: String? get() = slugUrl?.let { Site.current.titleUrlBase + it }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        name?.let { put("name", it) }
        rusName?.let { put("rus_name", it) }
        engName?.let { put("eng_name", it) }
        slugUrl?.let { put("slug_url", it) }
        cover?.let { put("cover", it.toJson()) }
        summary?.let { put("summary", it) }
        genres?.let { g -> put("genres", JSONArray(g.map { it.toJson() })) }
        authors?.let { g -> put("authors", JSONArray(g.map { it.toJson() })) }
        otherNames?.let { put("otherNames", JSONArray(it)) }
        episodesCount?.let { put("episodes_count", it) }
        rateAvg?.let { put("rate_avg", it) }
        status?.let { put("status", it.toJson()) }
        type?.let { put("type", it.toJson()) }
        ageRestriction?.let { put("ageRestriction", it.toJson()) }
        releaseDateString?.let { put("releaseDateString", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Title? {
            val id = J.int(o.opt("id")) ?: return null
            val sv = J.clean(o.opt("summary"))
            val summary = when (sv) {
                is String -> sv
                is JSONObject -> J.text(sv).trim()
                else -> null
            }
            val rating = rate(o.opt("rate_avg")) ?: rate(o.optJSONObject("rating")?.opt("average"))
            return Title(
                id = id,
                name = J.str(o.opt("name")),
                rusName = J.str(o.opt("rus_name")),
                engName = J.str(o.opt("eng_name")),
                slugUrl = J.str(o.opt("slug_url")),
                cover = Cover.from(o.optJSONObject("cover")),
                summary = summary,
                genres = NamedItem.list(o.optJSONArray("genres")),
                authors = NamedItem.list(o.optJSONArray("authors")),
                otherNames = o.optJSONArray("otherNames")?.strings(),
                episodesCount = J.int(o.opt("episodes_count")),
                rateAvg = rating,
                status = NamedItem.from(o.optJSONObject("status")),
                type = NamedItem.from(o.optJSONObject("type")),
                ageRestriction = NamedItem.from(o.optJSONObject("ageRestriction")),
                releaseDateString = J.str(o.opt("releaseDateString")),
            )
        }
    }
}

data class Chapter(val id: Int, val volume: String, val number: String, val name: String) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("volume", volume)
        put("number", number)
        put("name", name)
    }

    val title: String get() = if (name.isEmpty()) "Глава $number" else "Глава $number · $name"

    companion object {
        fun from(d: JSONObject): Chapter? {
            val id = J.int(d.opt("id")) ?: return null
            return Chapter(id, J.s(d.opt("volume")) ?: "1", J.s(d.opt("number")) ?: "0", J.str(d.opt("name")) ?: "")
        }
    }
}

data class EpisodeShort(val id: Int, val name: String?, val number: String?, val season: String?, val date: String?) {
    val title: String
        get() {
            val n = number?.let { "Серия $it" } ?: "Серия"
            return if (!name.isNullOrEmpty()) "$n · $name" else n
        }

    companion object {
        fun from(d: JSONObject): EpisodeShort? {
            val id = J.int(d.opt("id")) ?: return null
            return EpisodeShort(id, J.str(d.opt("name")), J.s(d.opt("number")), J.s(d.opt("season")), J.str(d.opt("created_at")))
        }
    }
}

/** Позиция просмотра серии. updated: секунды от 2001-01-01, как в iOS. */
data class WatchProgress(val position: Double, val duration: Double, val updated: Double) {
    val finished: Boolean get() = duration > 0 && position / duration > 0.92
}

data class MangaPage(val id: Int, val urls: List<String>, val width: Float, val height: Float) {
    val ratio: Float get() = if (width > 0f && height > 0f) height / width else 0f
}

data class HistoryItem(val title: Title, val chapterId: Int, val chapterTitle: String, val updated: Double) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("anime", title.toJson())
        put("episodeID", chapterId)
        put("episodeTitle", chapterTitle)
        put("updated", updated)
    }

    companion object {
        /** iOS хранит дату как секунды от 2001-01-01. */
        const val IOS_EPOCH_OFFSET = 978307200.0

        fun now(): Double = System.currentTimeMillis() / 1000.0 - IOS_EPOCH_OFFSET

        fun from(o: JSONObject): HistoryItem? {
            val t = Title.fromJson(o.optJSONObject("anime") ?: return null) ?: return null
            val cid = J.int(o.opt("episodeID")) ?: return null
            val upd = (J.clean(o.opt("updated")) as? Number)?.toDouble() ?: 0.0
            return HistoryItem(t, cid, o.optString("episodeTitle", ""), upd)
        }
    }
}

data class CommentItem(
    val id: Int,
    val text: String,
    val username: String,
    val avatar: String?,
    val up: Int,
    val down: Int,
    val date: String,
    val created: String,
    val replyCount: Int,
    val replies: List<CommentItem>,
) {
    companion object {
        fun from(d: JSONObject, extra: List<CommentItem> = emptyList()): CommentItem? {
            val id = J.int(d.opt("id")) ?: return null
            val raw = J.clean(d.opt("comment")) ?: J.clean(d.opt("content"))
                ?: J.clean(d.opt("text")) ?: J.clean(d.opt("body"))
            val text = J.text(raw).trim()
            val u = d.optJSONObject("user")
            val username = J.str(u?.opt("username")) ?: J.str(u?.opt("name")) ?: "Аноним"
            val avatar = J.url(u?.opt("avatar"))
            val votes = d.optJSONObject("votes")
            val up = J.int(votes?.opt("up")) ?: J.int(d.opt("likes_count")) ?: J.int(d.opt("likes"))
                ?: J.int(d.opt("votes_up")) ?: 0
            val down = J.int(votes?.opt("down")) ?: J.int(d.opt("dislikes_count")) ?: 0
            val created = J.str(d.opt("created_at")) ?: J.str(d.opt("date")) ?: ""
            val rawReplies = d.optJSONArray("replies") ?: d.optJSONArray("children") ?: d.optJSONArray("comments")
            val parsed = rawReplies.objects().mapNotNull { from(it) } + extra
            val count = maxOf(
                parsed.size,
                J.int(d.opt("comments_count")) ?: J.int(d.opt("replies_count")) ?: J.int(d.opt("children_count")) ?: 0,
            )
            return CommentItem(id, text, username, avatar, up, down, J.date(created), created, count, parsed)
        }
    }
}

/**
 * Ответ сервера может быть списком или объектом со вложенными массивами:
 * собираем все комментарии, где бы они ни лежали, и выстраиваем ветки по parent_comment.
 */
object CommentParser {
    private fun collect(v: Any?, out: MutableList<JSONObject>) {
        when (v) {
            is JSONObject -> {
                if (J.int(v.opt("id")) != null && v.has("comment")) {
                    out.add(v)
                    return
                }
                val keys = ArrayList<String>()
                val it = v.keys()
                while (it.hasNext()) keys.add(it.next())
                keys.sort()
                for (k in keys) collect(v.opt(k), out)
            }
            is JSONArray -> for (i in 0 until v.length()) collect(v.opt(i), out)
            else -> {}
        }
    }

    fun parse(root: Any?): List<CommentItem> {
        val raw = ArrayList<JSONObject>()
        collect(root, raw)
        val seen = HashSet<Int>()
        val unique = raw.filter { d -> J.int(d.opt("id"))?.let { seen.add(it) } ?: false }
        val ids = unique.mapNotNull { J.int(it.opt("id")) }.toSet()
        val children = HashMap<Int, MutableList<JSONObject>>()
        val tops = ArrayList<JSONObject>()
        for (d in unique) {
            val p = J.int(d.opt("parent_comment"))
            if (p != null && p != J.int(d.opt("id")) && p in ids) {
                children.getOrPut(p) { mutableListOf() }.add(d)
            } else {
                tops.add(d)
            }
        }

        fun build(d: JSONObject, depth: Int): CommentItem? {
            val id = J.int(d.opt("id")) ?: 0
            val kids = if (depth < 6) (children[id] ?: emptyList<JSONObject>()).mapNotNull { build(it, depth + 1) } else emptyList()
            return CommentItem.from(d, kids)
        }
        return tops.mapNotNull { build(it, 0) }
    }
}
