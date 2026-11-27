package app.yume.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yume.android.Nav
import app.yume.android.Route
import app.yume.android.data.HistoryItem
import app.yume.android.data.ProgressStore
import app.yume.android.data.ReadStore
import app.yume.android.data.FavMetaStore
import app.yume.android.data.Site
import app.yume.android.data.Title

/** Последний тайтл крупно: прогресс и кнопка «Продолжить». */
@Composable
fun ContinueHero(item: HistoryItem) {
    val book = Site.current.isBook
    val ctx = LocalContext.current
    val tint by produceState<Color?>(null, item.title.id) { value = CoverTint.color(ctx, item.title.coverLarge ?: item.title.imageUrl) }
    val c = tint ?: MaterialTheme.colorScheme.primary
    val frac: Float? = if (!book) {
        val p = ProgressStore.get(item.chapterId)
        if (p != null && p.duration > 0 && p.position > 5) minOf(1.0, p.position / p.duration).toFloat() else null
    } else {
        val total = FavMetaStore.count(item.title) ?: 0
        val done = ReadStore.read[item.title.readKey.toString()]?.size ?: 0
        if (total > 0 && done > 0) minOf(1f, done.toFloat() / total) else null
    }
    Row(
        Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(138.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(c.copy(alpha = 0.45f), MaterialTheme.colorScheme.surfaceContainer)))
            .clickable { Nav.push(Route.Detail(item.title)) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RemoteImage(item.title.imageUrl, Modifier.width(76.dp).height(114.dp).clip(RoundedCornerShape(14.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                item.chapterTitle, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 9.dp, vertical = 4.dp),
            )
            frac?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.weight(1f))
                    Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Продолжить", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** «Выбор дня»: один тайтл крупно, окрашенный цветом обложки. */
@Composable
fun TodayHero(t: Title) {
    val book = Site.current.isBook
    val ctx = LocalContext.current
    val tint by produceState<Color?>(null, t.id) { value = CoverTint.color(ctx, t.coverLarge ?: t.imageUrl) }
    val c = tint ?: MaterialTheme.colorScheme.primary
    val meta = t.genres?.map { it.title }?.filter { it.isNotEmpty() }?.take(2)?.joinToString(" · ")?.takeIf { it.isNotEmpty() }
        ?: listOfNotNull(t.status?.title, if (book) null else t.episodesCount?.let { "$it эп." }).filter { it.isNotEmpty() }.joinToString(" · ")
    Row(
        Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(c.copy(alpha = 0.75f), c.copy(alpha = 0.4f), MaterialTheme.colorScheme.surfaceContainer)))
            .clickable { Nav.push(Route.Detail(t)) }.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RemoteImage(t.coverLarge ?: t.imageUrl, Modifier.width(128.dp).height(192.dp).clip(RoundedCornerShape(16.dp)))
        Column(Modifier.weight(1f).height(192.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "✦ ВЫБОР ДНЯ", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 9.dp, vertical = 4.dp),
            )
            Text(t.displayTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                t.rateAvg?.let { Badge(it, star = true) }
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.weight(1f))
            Text(
                if (book) "▶ Читать" else "▶ Смотреть", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}
