package app.yume.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yume.android.Nav
import app.yume.android.Route
import app.yume.android.Prefs
import app.yume.android.data.FavMetaStore
import app.yume.android.data.FavoritesStore
import app.yume.android.data.ListStatus
import app.yume.android.data.Site
import app.yume.android.data.SiteFolders
import app.yume.android.data.SiteListSync
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FavoritesScreen() {
    val site = Site.of(Prefs.platform)
    var filter by remember(site) { mutableStateOf<String?>(null) }
    val folders = SiteFolders.get(site)
    val all = FavoritesStore.items.toList()
    val shown = when {
        filter == null -> all
        filter!!.startsWith("s:") -> all.filter { FavMetaStore.status(it)?.key == filter!!.removePrefix("s:") }
        else -> {
            val f = folders.firstOrNull { "f:${it.id}" == filter }
            all.filter { f != null && it.id in f.ids }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    "Избранное",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    androidx.compose.material3.FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Все ${all.size}") })
                    for (st in ListStatus.entries) {
                        val n = all.count { FavMetaStore.status(it) == st }
                        if (n > 0) {
                            androidx.compose.material3.FilterChip(
                                selected = filter == "s:${st.key}",
                                onClick = { filter = "s:${st.key}" },
                                label = { Text("${st.title(site.isBook)} $n") },
                            )
                        }
                    }
                    for (f in folders) {
                        val n = all.count { it.id in f.ids }
                        if (n > 0) {
                            androidx.compose.material3.FilterChip(
                                selected = filter == "f:${f.id}",
                                onClick = { filter = "f:${f.id}" },
                                label = { Text("${f.name} $n") },
                            )
                        }
                    }
                }
                SiteListSync.lastError[site.key]?.let {
                    Text("Синхронизация: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (FavoritesStore.items.isEmpty()) {
                    CenterMessage("Пока пусто.\nОткройте тайтл и нажмите сердечко.", Modifier.padding(top = 60.dp))
                }
            }
        }
        items(shown, key = { it.id }) { t ->
            GridCard(t, onClick = { Nav.push(Route.Detail(t)) })
        }
    }
}
