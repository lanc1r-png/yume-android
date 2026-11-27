@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.yume.android.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.yume.android.Prefs
import app.yume.android.data.ActivityStore
import app.yume.android.data.AuthStore
import app.yume.android.data.RequestLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Haptics {
    /** Лёгкий отклик на нажатие, если включено в настройках. */
    fun tap(view: View) {
        if (Prefs.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("log", text))
    android.widget.Toast.makeText(ctx, "Скопировано", android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
private fun TopBar(title: String, onClose: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1)
        actions()
    }
}

/** Журнал запросов сайта: что и с каким кодом ответил сервер. */
@Composable
fun SiteLogScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar("Журнал запросов сайта", onClose) {
            TextButton(onClick = { copy(ctx, RequestLog.text()) }) { Text("Копировать") }
            TextButton(onClick = { RequestLog.items.clear() }) { Text("Очистить") }
        }
        if (RequestLog.items.isEmpty()) CenterMessage("Пока пусто. Пользуйтесь приложением, запросы появятся здесь.")
        val f = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(RequestLog.items.toList()) { r ->
                Column {
                    Text(
                        "${f.format(Date(r.time))}  ${r.method}  ${if (r.code == 0) "сбой" else r.code.toString()}  ${r.ms} мс",
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = if (r.code in 200..299) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Text(r.url, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    if (r.note.isNotEmpty()) Text(r.note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Открывает любой адрес и записывает все запросы страницы. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AnySiteScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf("https://animelib.org/ru/") }
    var go by remember { mutableStateOf("https://animelib.org/ru/") }
    var showLog by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Любой сайт", onClose) {
            TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Страница" else "Запросы ${log.size}") }
            TextButton(onClick = { copy(ctx, log.reversed().joinToString("\n")) }) { Text("Копировать") }
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(url, { url = it }, Modifier.weight(1f), singleLine = true, label = { Text("Адрес") })
            TextButton(onClick = { go = if (url.startsWith("http")) url else "https://$url"; showLog = false }) { Text("Открыть") }
        }
        if (showLog) {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(log.reversed()) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
        } else {
            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                                request?.let { log.add("${it.method} ${it.url}") }
                                return null
                            }
                        }
                    }
                },
                update = { w -> if (w.tag != go) { w.tag = go; w.loadUrl(go) } },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.destroy() },
            )
        }
    }
}

/** Моя активность: серия дней, сколько прочитано и просмотрено. */
@Composable
fun StatsScreen(onClose: () -> Unit) {
    val days = ActivityStore.days()
    val cal = Calendar.getInstance()
    fun sumLast(n: Int, pick: (ActivityStore.Day) -> Int): Int {
        var t = 0
        for (i in 0 until n) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            days[ActivityStore.dayKey(c.timeInMillis)]?.let { t += pick(it) }
        }
        return t
    }
    var streak = 0
    run {
        var i = 0
        while (i < 400) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val d = days[ActivityStore.dayKey(c.timeInMillis)]
            val has = d != null && (d.episodes + d.manga + d.novels) > 0
            if (!has && i > 0) break
            if (has) streak++ else if (i == 0) { /* сегодня ещё не было — серия не рвётся */ }
            i++
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopBar("Моя активность", onClose)
        AuthStore.user?.streak?.let { Text("Серия входов на сайте: $it дн.", style = MaterialTheme.typography.titleMedium) }
        Text("Серия дней с активностью в приложении: $streak", style = MaterialTheme.typography.titleMedium)
        for ((label, n) in listOf("За 7 дней" to 7, "За 30 дней" to 30, "За всё время" to 400)) {
            Column {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(
                    "Серий: ${sumLast(n) { it.episodes }} · Глав манги: ${sumLast(n) { it.manga }} · Глав ранобэ: ${sumLast(n) { it.novels }}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Учитывается то, что открыто в приложении: каждая серия или глава считается один раз в день.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
