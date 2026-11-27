package app.yume.android.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

/** Своя шапка или аватарка профиля: файл + положение кадра. kind: image, gif или video. */
data class ProfileMedia(
    val file: String,
    val kind: String,
    val aspect: Double,
    val scale: Double = 1.0,
    val x: Double = 0.0,
    val y: Double = 0.0,
) {
    val isVideo get() = kind == "video"

    fun toJson() = JSONObject().put("file", file).put("kind", kind).put("aspect", aspect)
        .put("scale", scale).put("x", x).put("y", y)

    /** Не даём увести картинку так, чтобы за кадром остался пустой край. */
    fun clamped(frameAspect: Double): ProfileMedia {
        val a = maxOf(aspect, 0.05)
        val s = scale.coerceIn(1.0, 6.0)
        // базовый размер в долях кадра: покрываем кадр по меньшей стороне
        val bw = if (a > frameAspect) a / frameAspect else 1.0
        val bh = if (a > frameAspect) 1.0 else frameAspect / a
        val mx = maxOf((bw * s - 1) / 2, 0.0)
        val my = maxOf((bh * s - 1) / 2, 0.0)
        return copy(scale = s, x = x.coerceIn(-mx, mx), y = y.coerceIn(-my, my))
    }

    companion object {
        fun from(o: JSONObject) = ProfileMedia(
            o.optString("file"), o.optString("kind", "image"), o.optDouble("aspect", 1.0),
            o.optDouble("scale", 1.0), o.optDouble("x", 0.0), o.optDouble("y", 0.0),
        )
    }
}

object ProfileLook {
    private lateinit var dir: File
    var header by mutableStateOf<ProfileMedia?>(null)
        private set
    var avatar by mutableStateOf<ProfileMedia?>(null)
        private set
    var useHeader by mutableStateOf(false)
    var useAvatar by mutableStateOf(false)

    fun init(c: Context) {
        dir = File(c.filesDir, "ProfileLook").also { it.mkdirs() }
        useHeader = KV.getBool("profile.header.on", false)
        useAvatar = KV.getBool("profile.avatar.on", false)
        header = read("profile.header")
        avatar = read("profile.avatar")
    }

    private fun read(key: String): ProfileMedia? = try {
        KV.getString(key, null)?.let { ProfileMedia.from(JSONObject(it)) }
    } catch (e: Exception) {
        null
    }

    fun file(m: ProfileMedia) = File(dir, m.file)

    private fun exists(m: ProfileMedia?) = m?.takeIf { file(it).exists() }
    val activeHeader get() = if (useHeader) exists(header) else null
    val activeAvatar get() = if (useAvatar) exists(avatar) else null

    fun setUse(isHeader: Boolean, on: Boolean) {
        if (isHeader) { useHeader = on; KV.putBool("profile.header.on", on) } else { useAvatar = on; KV.putBool("profile.avatar.on", on) }
    }

    fun set(isHeader: Boolean, m: ProfileMedia?) {
        val old = if (isHeader) header else avatar
        if (old != null && old.file != m?.file) file(old).delete()
        val key = if (isHeader) "profile.header" else "profile.avatar"
        if (m != null) KV.putString(key, m.toJson().toString()) else KV.remove(key)
        if (isHeader) header = m else avatar = m
        setUse(isHeader, m != null)
    }

    fun updateCrop(isHeader: Boolean, m: ProfileMedia) {
        KV.putString(if (isHeader) "profile.header" else "profile.avatar", m.toJson().toString())
        if (isHeader) header = m else avatar = m
    }

    /** Копирует выбранный файл в папку приложения. GIF и видео остаются как есть, чтобы двигались. */
    fun import(c: Context, uri: Uri, prefix: String): ProfileMedia {
        val mime = c.contentResolver.getType(uri) ?: ""
        val isVideo = mime.startsWith("video")
        val isGif = mime == "image/gif"
        val ext = when {
            isVideo -> "mp4"
            isGif -> "gif"
            else -> "jpg"
        }
        val name = "$prefix-${System.currentTimeMillis()}.$ext"
        val dst = File(dir, name)
        c.contentResolver.openInputStream(uri)?.use { i ->
            dst.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    total += n
                    if (isGif && total > 20_000_000) { dst.delete(); throw IllegalStateException("GIF слишком большой (до 20 МБ)") }
                    if (isVideo && total > 60_000_000) { dst.delete(); throw IllegalStateException("Видео слишком большое (до 60 МБ)") }
                    o.write(buf, 0, n)
                }
            }
        } ?: throw IllegalStateException("Не получилось прочитать файл")
        val aspect: Double = if (isVideo) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(dst.absolutePath)
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toDoubleOrNull() ?: 0.0
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toDoubleOrNull() ?: 0.0
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (w <= 0 || h <= 0) { dst.delete(); throw IllegalStateException("В видео нет картинки") }
                if (rot == 90 || rot == 270) h / w else w / h
            } finally {
                r.release()
            }
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(dst.absolutePath, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) { dst.delete(); throw IllegalStateException("Не получилось прочитать картинку") }
            o.outWidth.toDouble() / o.outHeight
        }
        return ProfileMedia(name, if (isVideo) "video" else if (isGif) "gif" else "image", aspect)
    }
}

/** Свой цвет или эмодзи для списков в профиле. */
object FolderStyles {
    data class Style(val hex: String? = null, val emoji: String? = null)

    private const val KEY = "profile.folderStyles"
    private var items by mutableStateOf<Map<Int, Style>>(emptyMap())

    fun load() {
        items = try {
            val o = JSONObject(KV.getString(KEY, null) ?: "{}")
            val m = HashMap<Int, Style>()
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val s = o.optJSONObject(k) ?: continue
                k.toIntOrNull()?.let { id -> m[id] = Style(s.optString("hex").ifEmpty { null }, s.optString("emoji").ifEmpty { null }) }
            }
            m
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun style(id: Int): Style? = items[id]

    fun set(id: Int, s: Style?) {
        val m = HashMap(items)
        if (s == null || (s.hex == null && s.emoji.isNullOrEmpty())) m.remove(id) else m[id] = s
        items = m
        val o = JSONObject()
        for ((k, v) in m) o.put(k.toString(), JSONObject().apply { v.hex?.let { put("hex", it) }; v.emoji?.let { put("emoji", it) } })
        KV.putString(KEY, o.toString())
    }
}
