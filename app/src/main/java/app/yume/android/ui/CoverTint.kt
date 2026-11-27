package app.yume.android.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap

/** Главный цвет обложки: страница тайтла слегка подкрашивается им. */
object CoverTint {
    private val cache = HashMap<String, Color>()

    suspend fun color(ctx: Context, url: String?): Color? {
        if (url == null) return null
        cache[url]?.let { return it }
        return try {
            val req = ImageRequest.Builder(ctx).data(url).size(32, 32).allowHardware(false).build()
            val res = SingletonImageLoader.get(ctx).execute(req) as? SuccessResult ?: return null
            val bmp = res.image.toBitmap()
            var r = 0L; var g = 0L; var b = 0L; var w = 0L
            for (x in 0 until bmp.width) for (y in 0 until bmp.height) {
                val p = bmp.getPixel(x, y)
                val rr = (p shr 16) and 0xFF; val gg = (p shr 8) and 0xFF; val bb = p and 0xFF
                val mx = maxOf(rr, gg, bb); val mn = minOf(rr, gg, bb)
                // насыщенные пиксели весят больше, серые и почти чёрные почти не учитываем
                val weight = ((mx - mn) + 8).toLong() * (if (mx < 30) 0 else 1)
                r += rr * weight; g += gg * weight; b += bb * weight; w += weight
            }
            if (w == 0L) return null
            Color((r / w).toInt(), (g / w).toInt(), (b / w).toInt()).also { cache[url] = it }
        } catch (e: Exception) {
            null
        }
    }
}
