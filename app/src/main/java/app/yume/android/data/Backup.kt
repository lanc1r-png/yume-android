package app.yume.android.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Xml
import androidx.documentfile.provider.DocumentFile
import app.yume.android.Prefs
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Резервная копия в том же формате, что и в iOS-версии: XML plist
 * {app: "Yume", version: 1, date, defaults: {ключ: значение}}.
 * JSON-данные (избранное, история, прочитано) лежат внутри как <data> с UTF-8 текстом.
 */
object Backup {
    private val prefixes = listOf(
        "favorites", "history", "progress", "read", "search.", "set.",
        "accent.", "reader.", "manga.", "novel.", "off.",
    )

    const val FILE_NAME = "Yume-backup.plist"
    private const val FOLDER_KEY = "backup.folder.uri"
    private const val DATE_KEY = "backup.folder.date"
    private const val FAIL_KEY = "backup.folder.failed"

    fun allowed(key: String): Boolean = prefixes.any { key.startsWith(it) }

    private fun isData(key: String): Boolean =
        key.startsWith("favorites.") || key.startsWith("history.") || key.startsWith("progress") ||
            key == "read.v1" || key == "readlast.v1"

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun toPlist(): ByteArray {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" ")
        sb.append("\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n")
        sb.append("<plist version=\"1.0\">\n<dict>\n")
        sb.append("<key>app</key>\n<string>Yume</string>\n")
        sb.append("<key>date</key>\n<date>").append(isoNow()).append("</date>\n")
        sb.append("<key>defaults</key>\n<dict>\n")
        for ((key, v) in KV.all().toSortedMap()) {
            if (!allowed(key) || v == null) continue
            val value = when {
                isData(key) && v is String ->
                    "<data>" + Base64.encodeToString(v.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) + "</data>"
                key == "search.history" && v is String -> arrayXml(v)
                v is Boolean -> if (v) "<true/>" else "<false/>"
                v is Int || v is Long -> "<integer>$v</integer>"
                v is Float || v is Double -> "<real>$v</real>"
                v is String -> "<string>" + esc(v) + "</string>"
                else -> null
            } ?: continue
            sb.append("<key>").append(esc(key)).append("</key>\n").append(value).append("\n")
        }
        sb.append("</dict>\n<key>version</key>\n<integer>1</integer>\n</dict>\n</plist>\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun arrayXml(json: String): String? = try {
        val a = JSONArray(json)
        val sb = StringBuilder("<array>")
        for (s in a.strings()) sb.append("<string>").append(esc(s)).append("</string>")
        sb.append("</array>").toString()
    } catch (e: Exception) {
        null
    }

    private fun isoNow(): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date())
    }

    /** Возвращает число восстановленных записей или null, если файл не от Yume. */
    fun restore(data: ByteArray): Int? {
        val root = try {
            parsePlist(data) as? Map<*, *>
        } catch (e: Exception) {
            null
        } ?: return null
        if (root["app"] != "Yume") return null
        val defs = root["defaults"] as? Map<*, *> ?: return null
        var n = 0
        for ((k, v) in defs) {
            val key = k as? String ?: continue
            if (!allowed(key)) continue
            when (v) {
                is ByteArray -> KV.putString(key, String(v, Charsets.UTF_8))
                is List<*> -> KV.putString(key, JSONArray(v.filterIsInstance<String>()).toString())
                else -> KV.putAny(key, v)
            }
            n++
        }
        Prefs.reload()
        Stores.loadAll()
        return n
    }

    // ---- разбор plist ----

    private fun parsePlist(data: ByteArray): Any? {
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(data), "UTF-8")
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "plist") {
                while (p.next() != XmlPullParser.START_TAG) {
                    if (p.eventType == XmlPullParser.END_DOCUMENT) return null
                }
                return parseValue(p)
            }
            ev = p.next()
        }
        return null
    }

    private fun parseValue(p: XmlPullParser): Any? {
        when (p.name) {
            "dict" -> {
                val m = LinkedHashMap<String, Any?>()
                var key: String? = null
                while (true) {
                    val ev = p.next()
                    if (ev == XmlPullParser.END_DOCUMENT) break
                    if (ev == XmlPullParser.END_TAG && p.name == "dict") break
                    if (ev == XmlPullParser.START_TAG) {
                        if (p.name == "key") {
                            key = p.nextText()
                        } else {
                            val v = parseValue(p)
                            if (key != null) m[key] = v
                            key = null
                        }
                    }
                }
                return m
            }
            "array" -> {
                val l = ArrayList<Any?>()
                while (true) {
                    val ev = p.next()
                    if (ev == XmlPullParser.END_DOCUMENT) break
                    if (ev == XmlPullParser.END_TAG && p.name == "array") break
                    if (ev == XmlPullParser.START_TAG) l.add(parseValue(p))
                }
                return l
            }
            "string" -> return p.nextText()
            "integer" -> {
                val t = p.nextText().trim()
                return t.toLongOrNull()?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it } ?: t
            }
            "real" -> return p.nextText().trim().toDoubleOrNull()
            "true" -> {
                p.nextText()
                return true
            }
            "false" -> {
                p.nextText()
                return false
            }
            "data" -> return Base64.decode(p.nextText().trim(), Base64.DEFAULT)
            "date" -> return p.nextText()
            else -> {
                p.nextText()
                return null
            }
        }
    }

    // ---- файл в приложении и папка пользователя (Storage Access Framework) ----

    fun folderUri(): Uri? = KV.getString(FOLDER_KEY, null)?.let { Uri.parse(it) }

    fun folderName(ctx: Context): String? =
        folderUri()?.let { DocumentFile.fromTreeUri(ctx, it)?.name }

    fun lastFolderWrite(): Long? = KV.getString(DATE_KEY, null)?.toLongOrNull()

    fun lastFolderFailed(): Boolean = KV.getBool(FAIL_KEY, false)

    fun setFolder(ctx: Context, uri: Uri): Boolean {
        KV.putString(FOLDER_KEY, uri.toString())
        return writeAuto(ctx)
    }

    fun clearFolder() {
        KV.remove(FOLDER_KEY)
        KV.remove(DATE_KEY)
        KV.remove(FAIL_KEY)
    }

    private fun writeToFolder(ctx: Context, data: ByteArray): Boolean {
        val uri = folderUri() ?: return true
        return try {
            val tree = DocumentFile.fromTreeUri(ctx, uri) ?: return false
            val file = tree.findFile(FILE_NAME) ?: tree.createFile("application/octet-stream", FILE_NAME)
                ?: return false
            val out = ctx.contentResolver.openOutputStream(file.uri, "wt") ?: return false
            out.use { it.write(data) }
            KV.putString(DATE_KEY, System.currentTimeMillis().toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Пишет копию во внутреннюю папку приложения и, если выбрана, в папку пользователя. */
    fun writeAuto(ctx: Context): Boolean {
        val data = toPlist()
        val ok = writeToFolder(ctx, data)
        KV.putBool(FAIL_KEY, !ok)
        try {
            File(ctx.filesDir, FILE_NAME).writeBytes(data)
        } catch (e: Exception) {
            // внутренний файл — запасной, ошибку не показываем
        }
        return ok
    }
}
