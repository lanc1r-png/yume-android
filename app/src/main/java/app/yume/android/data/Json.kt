package app.yume.android.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Мягкий разбор JSON: формат ответов сайта меняется, поэтому без жёстких схем. */
object J {
    fun clean(v: Any?): Any? = if (v == null || v == JSONObject.NULL) null else v

    fun int(v: Any?): Int? = when (val x = clean(v)) {
        is Int -> x
        is Long -> x.toInt()
        is Double -> x.toInt()
        is String -> x.toIntOrNull()
        else -> null
    }

    fun str(v: Any?): String? = (clean(v) as? String)?.takeIf { it.isNotEmpty() }

    /** Строка из числа или строки (номера глав приходят и так, и так). */
    fun s(v: Any?): String? = when (val x = clean(v)) {
        is String -> x.takeIf { it.isNotEmpty() }
        is Int -> x.toString()
        is Long -> x.toString()
        is Double -> if (x == Math.rint(x)) x.toLong().toString() else x.toString()
        else -> null
    }

    fun parse(text: String): Any? = try {
        JSONTokener(text).nextValue()
    } catch (e: Exception) {
        null
    }

    fun text(v: Any?): String = when (val x = clean(v)) {
        is String -> stripHtml(x)
        is JSONObject -> {
            val type = x.optString("type", "")
            if (type == "text") {
                x.optString("text", "")
            } else {
                val arr = x.optJSONArray("content")
                val inner = if (arr == null) "" else (0 until arr.length()).joinToString("") { text(arr.opt(it)) }
                when (type) {
                    "paragraph", "heading" -> inner + "\n"
                    "hardBreak" -> "\n"
                    else -> inner
                }
            }
        }
        is JSONArray -> (0 until x.length()).joinToString("") { text(x.opt(it)) }
        else -> ""
    }

    fun stripHtml(s: String): String {
        if (!s.contains("<")) return s
        var t = s.replace("<br>", "\n").replace("</p>", "\n")
        t = t.replace(Regex("<[^>]+>"), "")
        return t.replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
    }

    fun normalizeUrl(raw: String?): String? {
        var t = raw ?: return null
        if (t.isEmpty()) return null
        if (t.startsWith("//")) t = "https:$t"
        else if (t.startsWith("/")) t = "https://cover.cdnlibs.org$t"
        return t
    }

    fun url(v: Any?): String? {
        val s: String? = when (val x = clean(v)) {
            is String -> x
            is JSONObject -> str(x.opt("url")) ?: str(x.opt("default")) ?: str(x.opt("md"))
                ?: str(x.opt("thumbnail")) ?: str(x.opt("src"))
            else -> null
        }
        return normalizeUrl(s)
    }

    private val ruDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru"))

    fun date(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        return try {
            OffsetDateTime.parse(s).format(ruDate)
        } catch (e: Exception) {
            s.take(10)
        }
    }
}

fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(it) }
    return out
}

fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) J.str(opt(i))?.let { out.add(it) }
    return out
}
