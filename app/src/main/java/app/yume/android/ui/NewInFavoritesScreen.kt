@file:OptIn(ExperimentalMaterial3Api::class)

package app.yume.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yume.android.Nav
import app.yume.android.Route
import app.yume.android.data.FavMetaStore
import app.yume.android.data.FavUpdater
import app.yume.android.data.FavoritesStore
import app.yume.android.data.KV
import app.yume.android.data.Site
import app.yume.android.data.Title

/** «2001-01-01» + секунды → «сегодня», «вчера», «3 дн. назад»… */
fun agoText(sec2001: Double): String {
    val ms = ((sec2001 + 978307200.0) * 1000).toLong()
    val days = ((System.currentTimeMillis() - ms) / 86_400_000L).toInt().coerceAtLeast(0)
    return when {
        days == 0 -> "сегодня"
        days == 1 -> "вчера"
        days < 7 -> "$days дн. назад"
        days < 30 -> "${days / 7} нед. назад"
        days < 365 -> "${maxOf(1, days / 30)} мес. назад"
        else -> if (days / 365 == 1) "год назад" else "${days / 365} г. назад"
    }
}

@Composable
fun NewInFavoritesScreen(onClose: () -> Unit) {
    val unit = if (Site.current.isBook) "гл." else "сер."
    val favs = FavoritesStore.items.toList()
    LaunchedEffect(Unit) { FavUpdater.refresh(favs, true) }
    val now = System.currentTimeMillis() / 1000.0 - 978307200.0
    val fresh = favs.filter { FavMetaStore.newCount(it) > 0 }.sortedByDescending { FavMetaStore.updateDate(it) ?: 0.0 }
    val recent = favs.filter { t ->
        FavMetaStore.newCount(t) == 0 && (FavMetaStore.updateDate(t)?.let { now - it < 14 * 86400 } ?: false)
    }.sortedByDescending { FavMetaStore.updateDate(it) ?: 0.0 }

    @Composable
    fun Row2(t: Title) {
        val n = FavMetaStore.newCount(t)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { FavMetaStore.markSeen(t); Nav.push(Route.Detail(t)) }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(t.imageUrl, Modifier.width(56.dp).height(84.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (n > 0) {
                        Text(
                            "+$n $unit", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    FavMetaStore.updateDate(t)?.let { Text(agoText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OneHeader("Новое", onClose)
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(if (fresh.isEmpty()) "Всё просмотрено" else "Новое в ${fresh.size} тайтлах", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val last = KV.getString("new.lastVisit", null)?.toLongOrNull()
                    Text(
                        if (last != null) "Отмечено: ${agoText((last / 1000.0) - 978307200.0)}" else if (FavUpdater.checking) "Проверяю…" else "Избранное проверяется само",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (fresh.isNotEmpty()) {
                item { OutlinedButton(onClick = { FavMetaStore.markAllSeen(favs) }, modifier = Modifier.fillMaxWidth()) { Text("Отметить всё просмотренным") } }
                item { Text("НОВОЕ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(fresh, key = { it.id }) { Row2(it) }
            }
            if (recent.isNotEmpty()) {
                item { Text("НЕДАВНО ОБНОВЛЯЛИСЬ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(recent, key = { "r${it.id}" }) { Row2(it) }
            }
            if (fresh.isEmpty() && recent.isEmpty()) {
                item { CenterMessage("Пока ничего нового. Откройте экран позже: проверка идёт сама.", Modifier.padding(top = 30.dp)) }
            }
        }
    }
}
