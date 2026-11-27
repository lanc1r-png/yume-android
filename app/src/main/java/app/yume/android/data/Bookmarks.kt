package app.yume.android.data

import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private fun now01() = System.currentTimeMillis() / 1000.0 - 978307200.0

/** Закладка. Манга: номер страницы. Ранобэ: номер абзаца главы. */
data class Bookmark(
    val id: String = UUID.randomUUID().toString().uppercase(),
    val titleKey: Int,
    val chapterID: Int,
    val chapterLabel: String,
    val position: Int,
    val isText: Boolean,
    val snippet: String,
    val note: String = "",
    val created: Double = now01(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("titleKey", titleKey).put("chapterID", chapterID)
        .put("chapterLabel", chapterLabel).put("position", position).put("isText", isText)
        .put("snippet", snippet).put("note", note).put("created", created)

    companion object {
        fun from(o: JSONObject): Bookmark? {
            val t = J.int(o.opt("titleKey")) ?: return null
            val c = J.int(o.opt("chapterID")) ?: return null
            return Bookmark(
                o.optString("id").ifEmpty { UUID.randomUUID().toString().uppercase() }, t, c, o.optString("chapterLabel"),
                o.optInt("position"), o.optBoolean("isText"), o.optString("snippet"), o.optString("note"), o.optDouble("created", now01()),
            )
        }
    }
}

object BookmarkStore {
    private const val KEY = "bookmarks.v1"
    val items = mutableStateListOf<Bookmark>()

    fun load() {
        items.clear()
        try {
            items.addAll(JSONArray(KV.getString(KEY, null) ?: "[]").objects().mapNotNull { Bookmark.from(it) })
        } catch (e: Exception) {
            // повреждённые данные игнорируем
        }
    }

    private fun save() = KV.putString(KEY, JSONArray(items.map { it.toJson() }).toString())

    fun forTitle(k: Int) = items.filter { it.titleKey == k }.sortedByDescending { it.created }

    fun find(titleKey: Int, chapterID: Int, from: Int, to: Int) =
        items.firstOrNull { it.titleKey == titleKey && it.chapterID == chapterID && it.position in from until to }

    fun add(b: Bookmark) { items.add(b); save() }

    fun remove(id: String) { items.removeAll { it.id == id }; save() }

    fun setNote(id: String, note: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { items[i] = items[i].copy(note = note); save() }
    }
}

/** Выделение. В Android выделяется абзац целиком: start — номер абзаца, length — 0. */
data class Highlight(
    val id: String = UUID.randomUUID().toString().uppercase(),
    val titleKey: Int,
    val chapterID: Int,
    val chapterLabel: String,
    val start: Int,
    val length: Int,
    val color: Int,
    val note: String,
    val snippet: String,
    val created: Double = now01(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("titleKey", titleKey).put("chapterID", chapterID)
        .put("chapterLabel", chapterLabel).put("start", start).put("length", length).put("color", color)
        .put("note", note).put("snippet", snippet).put("created", created)

    companion object {
        val PALETTE = listOf(0xFFFFD633L, 0xFF59D973L, 0xFF4DA6FFL, 0xFFFF73B3L)

        fun from(o: JSONObject): Highlight? {
            val t = J.int(o.opt("titleKey")) ?: return null
            val c = J.int(o.opt("chapterID")) ?: return null
            return Highlight(
                o.optString("id").ifEmpty { UUID.randomUUID().toString().uppercase() }, t, c, o.optString("chapterLabel"),
                o.optInt("start"), o.optInt("length"), o.optInt("color"), o.optString("note"), o.optString("snippet"),
                o.optDouble("created", now01()),
            )
        }
    }
}

object HighlightStore {
    private const val KEY = "highlights.v1"
    val items = mutableStateListOf<Highlight>()

    fun load() {
        items.clear()
        try {
            items.addAll(JSONArray(KV.getString(KEY, null) ?: "[]").objects().mapNotNull { Highlight.from(it) })
        } catch (e: Exception) {
            // повреждённые данные игнорируем
        }
    }

    private fun save() = KV.putString(KEY, JSONArray(items.map { it.toJson() }).toString())

    fun forTitle(k: Int) = items.filter { it.titleKey == k }.sortedByDescending { it.created }

    fun block(titleKey: Int, chapterID: Int, block: Int) =
        items.firstOrNull { it.titleKey == titleKey && it.chapterID == chapterID && it.start == block }

    fun set(titleKey: Int, chapterID: Int, label: String, block: Int, snippet: String, color: Int?, note: String?) {
        val i = items.indexOfFirst { it.titleKey == titleKey && it.chapterID == chapterID && it.start == block }
        if (i >= 0) {
            items[i] = items[i].copy(color = color ?: items[i].color, note = note ?: items[i].note)
        } else {
            items.add(Highlight(titleKey = titleKey, chapterID = chapterID, chapterLabel = label, start = block, length = 0, color = color ?: 0, note = note ?: "", snippet = snippet.take(120)))
        }
        save()
    }

    fun remove(id: String) { items.removeAll { it.id == id }; save() }
}
