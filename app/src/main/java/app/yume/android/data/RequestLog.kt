package app.yume.android.data

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Последние запросы к сайту: чтобы понять, что именно отвечает сервер. */
object RequestLog {
    data class Item(val time: Long, val method: String, val url: String, val code: Int, val ms: Long, val note: String)

    val items = mutableStateListOf<Item>()

    fun add(method: String, url: String, code: Int, ms: Long, note: String = "") {
        val clean = url.replace(Regex("(?i)(token|access_token)=[^&]+"), "$1=***")
        val item = Item(System.currentTimeMillis(), method, clean, code, ms, note)
        // Запросы идут из разных потоков, а список читает интерфейс: пишем только с главного.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            items.add(0, item)
            while (items.size > 300) items.removeAt(items.size - 1)
        }
    }

    fun text(): String {
        val f = SimpleDateFormat("HH:mm:ss", Locale.US)
        return items.joinToString("\n") { "${f.format(Date(it.time))} ${it.method} ${it.code} ${it.ms}мс ${it.url}" + (if (it.note.isNotEmpty()) "\n   ${it.note}" else "") }
    }
}
