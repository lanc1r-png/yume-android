@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package app.yume.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import app.yume.android.Nav
import app.yume.android.Prefs
import app.yume.android.Route
import app.yume.android.data.FavMetaStore
import app.yume.android.data.FavNames
import app.yume.android.data.FavSort
import app.yume.android.data.FavUpdater
import app.yume.android.data.FavoritesStore
import app.yume.android.data.HistoryStore
import app.yume.android.data.KV
import app.yume.android.data.ListStatus
import app.yume.android.data.ReadStore
import app.yume.android.data.Site
import app.yume.android.data.SiteFolders
import app.yume.android.data.SiteListSync
import app.yume.android.data.Title

@Composable
private fun FavChip(text: String, on: Boolean, dot: Color?, onClick: () -> Unit, onLong: (() -> Unit)? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Color.White else dot))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun FavCard(t: Title, site: Site, onClick: () -> Unit) {
    val st = FavMetaStore.status(t)
    val newCount = FavMetaStore.newCount(t)
    val meta = FavMetaStore.meta(t)
    val hist = HistoryStore.items.firstOrNull { it.title.id == t.id }
    val subtitle = hist?.chapterTitle?.takeIf { it.isNotEmpty() }
        ?: meta?.count?.takeIf { it > 0 }?.let { if (site.isBook) "$it гл." else "$it эп." }
    val progress = if (site.isBook) {
        val total = meta?.count ?: 0
        val done = ReadStore.read[t.readKey.toString()]?.size ?: 0
        if (total > 0 && done > 0) minOf(1f, done.toFloat() / total) else null
    } else null
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.combinedClickable(onClick = { FavMetaStore.markSeen(t); onClick() }, onLongClick = { menu = true }), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
            RemoteImage(t.imageUrl, Modifier.fillMaxSize())
            if (st != null) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape).background(Color(st.color)),
                    contentAlignment = Alignment.Center,
                ) { Text(st.title(site.isBook).take(1), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            if (newCount > 0) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text("+$newCount", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            progress?.let {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Text("Статус", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (s in ListStatus.entries) {
                    DropdownMenuItem(
                        text = { Text((if (st == s) "✓ " else "") + s.title(site.isBook)) },
                        onClick = { FavMetaStore.setStatus(t, s); menu = false },
                    )
                }
                if (st != null) DropdownMenuItem(text = { Text("Без статуса") }, onClick = { FavMetaStore.setStatus(t, null); menu = false })
                DropdownMenuItem(text = { Text("Убрать из избранного") }, onClick = { FavoritesStore.toggle(t); menu = false })
            }
        }
        Text(t.displayTitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun FavoritesScreen() {
    val site = Site.of(Prefs.platform)
    val book = site.isBook
    var filter by remember(site) { mutableStateOf<String?>(null) }
    var sortKey by remember { mutableStateOf(KV.getString("fav.sort", "added") ?: "added") }
    var sortMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ListStatus?>(null) }
    var renameText by remember { mutableStateOf("") }
    val sort = FavSort.of(sortKey)
    val folders = SiteFolders.get(site)
    val all = FavoritesStore.items.toList()
    @Suppress("UNUSED_VARIABLE") val namesTick = FavNames.version

    LaunchedEffect(site) {
        SiteListSync.syncAll()
        FavUpdater.refresh(FavoritesStore.items.toList(), false)
    }

    val filtered = when {
        filter == null -> all
        filter!!.startsWith("s:") -> all.filter { FavMetaStore.status(it)?.key == filter!!.removePrefix("s:") }
        else -> {
            val f = folders.firstOrNull { "f:${it.id}" == filter }
            all.filter { f != null && it.id in f.ids }
        }
    }
    val shown = when (sort) {
        FavSort.ADDED -> filtered
        FavSort.UPDATED -> filtered.sortedByDescending { FavMetaStore.updateDate(it) ?: Double.NEGATIVE_INFINITY }
        FavSort.OPENED -> filtered.sortedByDescending { t -> HistoryStore.items.firstOrNull { it.title.id == t.id }?.updated ?: 0.0 }
        FavSort.TITLE -> filtered.sortedBy { it.displayTitle.lowercase() }
        FavSort.RATING -> filtered.sortedByDescending { it.rateAvg?.toDoubleOrNull() ?: 0.0 }
    }
    val hasNew = FavMetaStore.totalNew(all) > 0

    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Избранное", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = { Nav.push(Route.NewInFavorites()) }) {
                        Icon(Icons.Filled.Notifications, "Новое", tint = if (hasNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.MoreVert, "Сортировка") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            for (o in FavSort.entries) {
                                DropdownMenuItem(
                                    text = { Text((if (o == sort) "✓ " else "") + o.title) },
                                    onClick = { sortKey = o.key; KV.putString("fav.sort", o.key); sortMenu = false },
                                )
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FavChip("Все ${all.size}", filter == null, null, { filter = null })
                    for (st in ListStatus.entries) {
                        val n = all.count { FavMetaStore.status(it) == st }
                        if (n > 0 || filter == "s:${st.key}") {
                            FavChip(
                                "${st.title(book)} $n", filter == "s:${st.key}", Color(st.color),
                                onClick = { filter = "s:${st.key}" },
                                onLong = { renameText = st.title(book); renaming = st },
                            )
                        }
                    }
                    for (f in folders) {
                        val n = all.count { it.id in f.ids }
                        if (n > 0 || filter == "f:${f.id}") {
                            val style = app.yume.android.data.FolderStyles.style(f.id)
                            val dot = style?.hex?.let { parseHex(it) } ?: if (f.name.lowercase().contains("любим")) Color(0xFFFF6B9E) else MaterialTheme.colorScheme.primary
                            FavChip((style?.emoji?.let { "$it " } ?: "") + "${f.name} $n", filter == "f:${f.id}", dot, { filter = "f:${f.id}" })
                        }
                    }
                }
                Text("Долгое нажатие на статус — переименовать. Долгое нажатие на обложку — сменить статус.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SiteListSync.lastError[site.key]?.let {
                    Text("Списки с сайта не подтянулись: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (sort == FavSort.UPDATED && all.isNotEmpty() && all.all { FavMetaStore.updateDate(it) == null }) {
                    Text(
                        if (FavUpdater.checking) "Проверяю обновления…" else "Сайт не сообщил даты выхода: порядок по обновлению появится, когда приложение их узнает.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (all.isEmpty()) {
                    CenterMessage("Пока пусто.\nОткройте тайтл и нажмите сердечко.", Modifier.padding(top = 60.dp))
                } else if (shown.isEmpty()) {
                    CenterMessage("В этом списке пока пусто.\nЗадайте статус долгим нажатием на обложку.", Modifier.padding(top = 40.dp))
                }
            }
        }
        items(shown, key = { it.id }) { t -> FavCard(t, site, onClick = { Nav.push(Route.Detail(t)) }) }
    }

    renaming?.let { st ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Название папки") },
            text = {
                Column {
                    OutlinedTextField(renameText, { renameText = it }, singleLine = true)
                    Text("Папка «${st.defaultTitle(book)}» будет называться так. Фильтр и статус не изменятся.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { FavNames.set(st, book, renameText); renaming = null }) { Text("Сохранить") } },
            dismissButton = {
                Row {
                    if (FavNames.get(st, book) != null) TextButton(onClick = { FavNames.set(st, book, null); renaming = null }) { Text("Вернуть") }
                    TextButton(onClick = { renaming = null }) { Text("Отмена") }
                }
            },
        )
    }
}
