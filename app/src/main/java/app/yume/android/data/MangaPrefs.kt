package app.yume.android.data

import org.json.JSONObject

/** Свои настройки чтения для одного тайтла (если включены, общие не действуют). Формат как в iOS. */
data class MangaTitlePrefs(
    val enabled: Boolean = false,
    val anim: String = "slide",
    val autoBG: Boolean = false,
    val autoNext: Boolean = true,
    val speed: Float = 60f,
    val dim: Float = 0f,
    val warm: Float = 0f,
    val gray: Boolean = false,
) {
    fun save(titleId: Int) {
        KV.putString(
            "manga.tprefs.$titleId",
            JSONObject().put("enabled", enabled).put("anim", anim).put("autoBG", autoBG).put("autoNext", autoNext)
                .put("speed", speed.toDouble()).put("dim", dim.toDouble()).put("warm", warm.toDouble()).put("gray", gray).toString(),
        )
    }

    companion object {
        fun load(titleId: Int): MangaTitlePrefs = try {
            val o = JSONObject(KV.getString("manga.tprefs.$titleId", null) ?: return MangaTitlePrefs())
            MangaTitlePrefs(
                o.optBoolean("enabled"), o.optString("anim", "slide"), o.optBoolean("autoBG"), o.optBoolean("autoNext", true),
                o.optDouble("speed", 60.0).toFloat(), o.optDouble("dim", 0.0).toFloat(), o.optDouble("warm", 0.0).toFloat(), o.optBoolean("gray"),
            )
        } catch (e: Exception) {
            MangaTitlePrefs()
        }
    }
}
