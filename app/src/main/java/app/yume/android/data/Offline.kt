package app.yume.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.yume.android.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ---- Блоки текста главы ----

data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false)

sealed class Block {
    class Para(val runs: List<Run>) : Block()
    class Heading(val text: String) : Block()
    class Quote(val text: String) : Block()
    class Image(val url: String) : Block()
    object Rule : Block()
}

/** Разбирает текст главы: JSON редактора (ProseMirror) или HTML. */
object ReaderParser {
    private const val SITE = "https://ranobelib.me"

    fun parse(content: Any?, attachments: JSONArray?): List<Block> {
        val att = attachments.objects()
        fun attachmentUrl(key: String?): String? {
            if (key == null) return null
            val a = att.firstOrNull { J.s(it.opt("name")) == key || J.s(it.opt("id")) == key || J.s(it.opt("filename")) == key }
                ?: return null
            val u = J.str(a.opt("url")) ?: return null
            return abs(u)
        }

        val out = ArrayList<Block>()
        when (val c = J.clean(content)) {
            is JSONObject -> node(c, out, ::attachmentUrl)
            is JSONArray -> for (o in c.objects()) node(o, out, ::attachmentUrl)
            is String -> {
                val t = c.trim()
                if (t.startsWith("{")) {
                    try {
                        node(JSONObject(t), out, ::attachmentUrl)
                    } catch (e: Exception) {
                        html(t, out)
                    }
                } else {
                    html(t, out)
                }
            }
        }
        return out
    }

    private fun abs(u: String): String = when {
        u.startsWith("http") -> u
        u.startsWith("//") -> "https:$u"
        u.startsWith("/") -> SITE + u
        else -> "$SITE/$u"
    }

    private fun runs(n: JSONObject, acc: MutableList<Run>, bold: Boolean = false, italic: Boolean = false) {
        when (n.optString("type")) {
            "text" -> {
                var b = bold
                var i = italic
                val marks = n.optJSONArray("marks")
                if (marks != null) {
                    for (m in marks.objects()) {
                        when (m.optString("type")) {
                            "bold", "strong" -> b = true
                            "italic", "em" -> i = true
                        }
                    }
                }
                val t = n.optString("text", "")
                if (t.isNotEmpty()) acc.add(Run(t, b, i))
            }
            "hardBreak" -> acc.add(Run("\n", bold, italic))
            else -> n.optJSONArray("content").objects().forEach { runs(it, acc, bold, italic) }
        }
    }

    private fun plain(n: JSONObject): String {
        val acc = ArrayList<Run>()
        runs(n, acc)
        return acc.joinToString("") { it.text }.trim()
    }

    private fun node(n: JSONObject, out: MutableList<Block>, resolve: (String?) -> String?) {
        when (n.optString("type")) {
            "doc" -> n.optJSONArray("content").objects().forEach { node(it, out, resolve) }
            "paragraph" -> {
                val acc = ArrayList<Run>()
                runs(n, acc)
                if (acc.any { it.text.isNotBlank() }) out.add(Block.Para(acc))
            }
            "heading" -> plain(n).takeIf { it.isNotEmpty() }?.let { out.add(Block.Heading(it)) }
            "blockquote" -> plain(n).takeIf { it.isNotEmpty() }?.let { out.add(Block.Quote(it)) }
            "horizontalRule" -> out.add(Block.Rule)
            "image" -> {
                val attrs = n.optJSONObject("attrs")
                val imgs = attrs?.optJSONArray("images")
                var added = false
                if (imgs != null) {
                    for (i in 0 until imgs.length()) {
                        val o = imgs.opt(i)
                        val key = if (o is JSONObject) J.s(o.opt("image")) else J.s(o)
                        resolve(key)?.let {
                            out.add(Block.Image(it))
                            added = true
                        }
                    }
                }
                if (!added) {
                    val src = J.str(attrs?.opt("src")) ?: J.str(attrs?.opt("url"))
                    if (src != null) out.add(Block.Image(abs(src)))
                }
            }
            else -> n.optJSONArray("content").objects().forEach { node(it, out, resolve) }
        }
    }

    private fun html(src: String, out: MutableList<Block>) {
        val parts = Regex("(?is)<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>|<p[^>]*>(.*?)</p>|<h[1-6][^>]*>(.*?)</h[1-6]>|<hr\\s*/?>")
        var any = false
        for (m in parts.findAll(src)) {
            any = true
            val img = m.groups[1]?.value
            val para = m.groups[2]?.value
            val head = m.groups[3]?.value
            when {
                img != null -> out.add(Block.Image(abs(img)))
                head != null -> J.stripHtml(head).trim().takeIf { it.isNotEmpty() }?.let { out.add(Block.Heading(it)) }
                para != null -> {
                    val t = J.stripHtml(para.replace("<br>", "\n").replace("<br/>", "\n")).trim()
                    if (t.isNotEmpty()) out.add(Block.Para(listOf(Run(t))))
                }
                else -> out.add(Block.Rule)
            }
        }
        if (!any) {
            J.stripHtml(src).split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                out.add(Block.Para(listOf(Run(it))))
            }
        }
    }

    fun parseRaw(raw: String): List<Block> {
        val root = J.parse(raw) as? JSONObject ?: return emptyList()
        val d = root.optJSONObject("data") ?: return emptyList()
        return parse(d.opt("content"), d.optJSONArray("attachments"))
    }
}

// ---- Офлайн-хранилище ----

data class OfflineEntry(
    val novel: Title,
    val chapters: List<Chapter>,
    val files: Map<Int, Long>,
    val updated: Long,
) {
    val bytes: Long get() = files.values.sum()
}

/** Скачанные главы лежат в filesDir/Offline/<id тайтла>/: <id главы>.json и картинки. Не попадают в бэкап. */
object OfflineStore {
    private var root: File? = null
    val entries = mutableStateMapOf<Int, OfflineEntry>()

    fun init(ctx: Context) {
        val dir = File(ctx.filesDir, "Offline")
        dir.mkdirs()
        root = dir
        try {
            val f = File(dir, "manifest.json")
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (o in arr.objects()) {
                    val novel = Title.fromJson(o.optJSONObject("novel") ?: continue) ?: continue
                    val chapters = o.optJSONArray("chapters").objects().mapNotNull { Chapter.from(it) }
                    val files = HashMap<Int, Long>()
                    val fo = o.optJSONObject("files")
                    if (fo != null) {
                        val it = fo.keys()
                        while (it.hasNext()) {
                            val k = it.next()
                            k.toIntOrNull()?.let { id -> files[id] = fo.optLong(k) }
                        }
                    }
                    entries[novel.id] = OfflineEntry(novel, chapters, files, o.optLong("updated"))
                }
            }
        } catch (e: Exception) {
            // повреждённый манифест игнорируем
        }
    }

    private fun persist() {
        val dir = root ?: return
        val arr = JSONArray()
        for (e in entries.values) {
            arr.put(
                JSONObject()
                    .put("novel", e.novel.toJson())
                    .put("chapters", JSONArray(e.chapters.map { it.toJson() }))
                    .put("files", JSONObject().also { o -> e.files.forEach { (k, v) -> o.put(k.toString(), v) } })
                    .put("updated", e.updated),
            )
        }
        try {
            File(dir, "manifest.json").writeText(arr.toString())
        } catch (e: Exception) {
            // не критично
        }
    }

    fun folder(novel: Int): File = File(root ?: File("/nonexistent"), novel.toString()).also { it.mkdirs() }
    fun chapterFile(novel: Int, chapter: Int) = File(folder(novel), "$chapter.json")
    fun coverFile(novel: Int) = File(folder(novel), "cover.img")

    fun imageFile(novel: Int, chapter: Int, url: String): File {
        var h = 5381L
        for (b in url.toByteArray()) h = h * 33 + b
        return File(folder(novel), "${chapter}_${java.lang.Long.toString(h and 0x7fffffffffffL, 36)}.img")
    }

    fun isDownloaded(novel: Int, chapter: Int): Boolean = entries[novel]?.files?.containsKey(chapter) == true

    fun totalChapters(): Int = entries.values.sumOf { it.files.size }

    fun sizeText(): String {
        val b = entries.values.sumOf { it.bytes }.toDouble()
        return when {
            b >= 1024 * 1024 * 1024 -> "%.1f ГБ".format(b / (1024 * 1024 * 1024))
            b >= 1024 * 1024 -> "%.1f МБ".format(b / (1024 * 1024))
            else -> "%.0f КБ".format(b / 1024)
        }
    }

    fun savedChapters(novel: Int): List<Chapter> {
        val e = entries[novel] ?: return emptyList()
        return e.chapters.filter { e.files.containsKey(it.id) }
    }

    fun refresh(novel: Title, chapters: List<Chapter>) {
        val e = entries[novel.id] ?: return
        entries[novel.id] = e.copy(novel = novel, chapters = chapters)
        persist()
    }

    fun record(novel: Title, chapters: List<Chapter>, chapter: Chapter, raw: String, extraBytes: Long) {
        chapterFile(novel.id, chapter.id).writeText(raw)
        val old = entries[novel.id]
        val files = (old?.files ?: emptyMap()) + (chapter.id to (raw.toByteArray().size + extraBytes))
        entries[novel.id] = OfflineEntry(novel, chapters.ifEmpty { old?.chapters ?: emptyList() }, files, System.currentTimeMillis())
        persist()
    }

    fun remove(novel: Int, chapter: Int) {
        val e = entries[novel] ?: return
        chapterFile(novel, chapter).delete()
        folder(novel).listFiles()?.filter { it.name.startsWith("${chapter}_") }?.forEach { it.delete() }
        val files = e.files - chapter
        if (files.isEmpty()) {
            remove(novel)
        } else {
            entries[novel] = e.copy(files = files)
            persist()
        }
    }

    fun remove(novel: Int) {
        folder(novel).deleteRecursively()
        entries.remove(novel)
        persist()
    }

    fun removeAll() {
        for (id in entries.keys.toList()) folder(id).deleteRecursively()
        entries.clear()
        persist()
    }

    /** Глава с телефона: картинки подменяются локальными файлами. */
    fun blocks(novel: Int, chapter: Int): List<Block>? {
        if (!isDownloaded(novel, chapter)) return null
        val f = chapterFile(novel, chapter)
        if (!f.exists()) return null
        val parsed = ReaderParser.parseRaw(f.readText())
        if (parsed.isEmpty()) return null
        return parsed.map { b ->
            if (b is Block.Image) {
                val img = imageFile(novel, chapter, b.url)
                if (img.exists()) Block.Image(Uri.fromFile(img).toString()) else b
            } else {
                b
            }
        }
    }

    fun coverUrl(novel: Int): String? = coverFile(novel).takeIf { it.exists() }?.let { Uri.fromFile(it).toString() }
}

/** Очередь загрузок глав (по одной, с повторами при 429 и 5xx). */
object DownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null

    val pending = mutableStateListOf<Int>()
    var current by mutableStateOf<Int?>(null)
        private set
    val failed = mutableStateMapOf<Int, String>()
    var status by mutableStateOf("")
        private set

    private class Job2(val novel: Title, val all: List<Chapter>, val chapter: Chapter)

    private val jobs = ArrayList<Job2>()
    private var worker: Job? = null

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
    }

    fun enqueue(novel: Title, all: List<Chapter>, targets: List<Chapter>) {
        synchronized(jobs) {
            for (c in targets) {
                if (OfflineStore.isDownloaded(novel.id, c.id) || c.id in pending) continue
                jobs.add(Job2(novel, all, c))
                pending.add(c.id)
                failed.remove(c.id)
            }
        }
        if (worker?.isActive != true) worker = scope.launch { run() }
    }

    fun cancel(chapterId: Int) {
        synchronized(jobs) {
            jobs.removeAll { it.chapter.id == chapterId }
        }
        pending.remove(chapterId)
    }

    private fun metered(): Boolean {
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return cm.isActiveNetworkMetered
    }

    private suspend fun run() {
        while (true) {
            val job = synchronized(jobs) { if (jobs.isEmpty()) null else jobs.removeAt(0) } ?: break
            current = job.chapter.id
            if (Prefs.offWifi) {
                while (metered()) {
                    status = "Ждём Wi‑Fi…"
                    delay(3000)
                }
            }
            status = job.chapter.title
            try {
                download(job)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed[job.chapter.id] = e.message ?: "Ошибка загрузки"
            }
            pending.remove(job.chapter.id)
            delay(350)
        }
        current = null
        status = ""
    }

    private suspend fun download(job: Job2) {
        val slug = job.novel.slugUrl ?: throw ApiException(0, "У тайтла нет адреса")
        val c = job.chapter
        var raw: String? = null
        var last: Exception = ApiException(0, "Не удалось загрузить главу")
        for (attempt in 0 until 3) {
            try {
                raw = Api.chapterRaw(slug, c)
                break
            } catch (e: ApiException) {
                last = e
                if (e.code == 429 || e.code >= 500) delay(3000L * (attempt + 1)) else throw e
            }
        }
        val text = raw ?: throw last
        val blocks = ReaderParser.parseRaw(text)
        if (blocks.isEmpty()) throw ApiException(0, "Текст главы пуст или недоступен")
        val urls = blocks.filterIsInstance<Block.Image>().map { it.url }.distinct()
        var bytes = 0L
        for (u in urls) bytes += fetchImage(u, OfflineStore.imageFile(job.novel.id, c.id, u))
        val cover = job.novel.imageUrl
        if (cover != null && !OfflineStore.coverFile(job.novel.id).exists()) fetchImage(cover, OfflineStore.coverFile(job.novel.id))
        withContext(Dispatchers.Main) { OfflineStore.record(job.novel, job.all, c, text, bytes) }
    }

    private suspend fun fetchImage(url: String, file: File): Long = withContext(Dispatchers.IO) {
        if (file.exists() && file.length() > 0) return@withContext file.length()
        for (attempt in 0 until 3) {
            try {
                val req = Request.Builder().url(url).header("Referer", Site.RANOBE.referer).header("User-Agent", Api.UA).build()
                Api.httpClient.newCall(req).execute().use { r ->
                    val data = r.body?.bytes()
                    if (r.isSuccessful && data != null && data.isNotEmpty()) {
                        file.writeBytes(data)
                        return@withContext data.size.toLong()
                    }
                }
            } catch (e: java.io.IOException) {
                // повторим
            }
            delay(600L * (attempt + 1))
        }
        0L
    }
}
