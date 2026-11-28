@file:OptIn(ExperimentalLayoutApi::class)

package app.yume.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yume.android.Nav
import app.yume.android.Route
import app.yume.android.data.ActivityStore
import app.yume.android.data.AuthStore
import app.yume.android.data.FavMetaStore
import app.yume.android.data.FavoritesStore
import app.yume.android.data.HistoryStore
import app.yume.android.data.ReadStore
import app.yume.android.data.Site
import app.yume.android.data.SiteFolders
import app.yume.android.data.Title
import app.yume.android.data.UserGenreStats
import java.util.Calendar

private val GenreColors = listOf(Color(0xFF7C5CFF), Color(0xFF40CC7A), Color(0xFFFF9F0A), Color(0xFF0A84FF), Color(0xFFFF453A), Color(0xFF8E8E93))

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun Rail(items: List<Title>, sub: (Title) -> String?, progress: (Title) -> Float?) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.id }) { t ->
            Column(Modifier.width(112.dp).clickable { Nav.push(Route.Detail(t)) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(112.dp).height(168.dp).clip(RoundedCornerShape(16.dp))) {
                    RemoteImage(t.imageUrl, Modifier.fillMaxWidth().height(168.dp))
                    progress(t)?.let {
                        LinearProgressIndicator(progress = { it }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp))
                    }
                }
                Text(t.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                sub(t)?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

private class Ach(val title: String, val value: Int, val steps: List<Int>, val unit: String) {
    val level get() = steps.count { value >= it }
    val next get() = steps.firstOrNull { value < it }
}

@Composable
fun ProfileShowcase(total: Int) {
    val site = Site.current
    val book = site.isBook
    val userId = AuthStore.user?.id
    @Suppress("UNUSED_VARIABLE") val fv = SiteFolders.version
    var genres by remember(site) { mutableStateOf(UserGenreStats.genres(site)) }
    LaunchedEffect(site, userId) { if (userId != null) genres = UserGenreStats.load(userId) }

    // Любимые — папка сайта
    val fav = SiteFolders.favorite(site)
    val byId = FavoritesStore.items.associateBy { it.id }
    val favs = fav?.ids?.mapNotNull { byId[it] }.orEmpty()
    if (favs.isNotEmpty()) {
        Text("Любимые", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Rail(favs.take(15), { null }, { null })
    }

    val recent = HistoryStore.items.take(10)
    if (recent.isNotEmpty()) {
        Text(if (book) "Сейчас читаю" else "Сейчас смотрю", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val byTitle = recent.associateBy { it.title.id }
        Rail(
            recent.map { it.title },
            { byTitle[it.id]?.chapterTitle },
            { t ->
                if (!book) null else {
                    val all = FavMetaStore.count(t) ?: 0
                    val done = ReadStore.read[t.readKey.toString()]?.size ?: 0
                    if (all > 0 && done > 0) minOf(1f, done.toFloat() / all) else null
                }
            },
        )
    }

    if (genres.size >= 3) {
        Text("Мои жанры", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card {
            val top = genres.take(5)
            val rest = genres.drop(5).sumOf { it.count }
            val parts = top.map { it.name to it.count } + (if (rest > 0) listOf("Другое" to rest) else emptyList())
            val sum = maxOf(parts.sumOf { it.second }, 1)
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                parts.forEachIndexed { i, p ->
                    Box(Modifier.weight(p.second.toFloat() / sum).height(10.dp).background(GenreColors[i % GenreColors.size]))
                }
            }
            parts.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(GenreColors[i % GenreColors.size]))
                    Text(p.first, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${p.second * 100 / sum}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // За неделю
    val days = ActivityStore.days()
    val names = listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
    val week = (6 downTo 0).map { back ->
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }
        val d = days[ActivityStore.dayKey(c.timeInMillis)]
        val n = if (book) (d?.manga ?: 0) + (d?.novels ?: 0) else d?.episodes ?: 0
        names[c.get(Calendar.DAY_OF_WEEK) - 1] to n
    }
    val weekTotal = week.sumOf { it.second }
    val maxDay = maxOf(week.maxOf { it.second }, 1)
    Text("За неделю", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Card {
        Row {
            Text(
                if (weekTotal == 0) "Пока ни одной ${if (book) "главы" else "серии"}" else "$weekTotal ${if (book) "гл." else "эп."}",
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
            Text("за 7 дней", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            week.forEachIndexed { i, (name, n) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (n > 0) "$n" else " ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.fillMaxWidth().height((6 + 60f * n / maxDay).dp).clip(RoundedCornerShape(5.dp))
                            .background(if (n > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                    )
                    Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = if (i == 6) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }

    // Достижения
    val chapters = days.values.sumOf { if (book) it.manga + it.novels else it.episodes }
    val doneFolder = SiteFolders.get(site).firstOrNull { it.name.contains("прочитан", true) || it.name.contains("просмотр", true) }?.ids?.size ?: 0
    val achs = listOf(
        Ach("Дней подряд", AuthStore.user?.streak ?: 0, listOf(3, 7, 30, 100, 365), "дн."),
        Ach(if (book) "Глав прочитано" else "Серий просмотрено", chapters, listOf(10, 100, 500, 1000, 5000), if (book) "гл." else "эп."),
        Ach(if (book) "Дочитано" else "Досмотрено", doneFolder, listOf(1, 10, 50, 100, 500), "тайтл."),
        Ach("В библиотеке", total, listOf(10, 50, 100, 300, 1000), "тайтл."),
    )
    Text("Достижения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    for (pair in achs.chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (a in pair) {
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(a.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${a.value}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (a.level > 0) Text("ур. ${a.level}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    val lo = if (a.level > 0) a.steps[a.level - 1] else 0
                    val frac = a.next?.let { (a.value - lo).toFloat() / maxOf(it - lo, 1) } ?: 1f
                    LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(
                        a.next?.let { "до уровня ${a.level + 1}: $it ${a.unit}" } ?: "максимальный уровень",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
            if (pair.size == 1) Box(Modifier.weight(1f))
        }
    }
}
