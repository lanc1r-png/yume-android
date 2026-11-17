@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.yume.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yume.android.Nav
import app.yume.android.Route
import app.yume.android.data.Api
import app.yume.android.data.Chapter
import app.yume.android.data.FavoritesStore
import app.yume.android.data.ReadStore
import app.yume.android.data.Title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun DetailScreen(initial: Title) {
    val ctx = LocalContext.current
    var t by remember { mutableStateOf(initial) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reversed by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(initial.id, tick) {
        loading = true
        error = null
        val slug = initial.slugUrl
        if (slug == null) {
            error = "У тайтла нет адреса"
            loading = false
            return@LaunchedEffect
        }
        coroutineScope {
            val d = async {
                try {
                    Api.details(slug)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            val c = async {
                try {
                    Result.success(Api.chapters(slug))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            d.await()?.let { full ->
                t = full.copy(cover = full.cover ?: t.cover, slugUrl = full.slugUrl ?: t.slugUrl)
            }
            val res = c.await()
            val list = res.getOrNull()
            if (list != null) chapters = list else error = res.exceptionOrNull()?.message ?: "Не удалось загрузить главы"
        }
        loading = false
    }

    val resumeIndex = run {
        val last = ReadStore.lastChapter(t.readKey)
        val i = if (last != null) chapters.indexOfFirst { it.id == last } else -1
        if (i >= 0) i else 0
    }
    val hasLast = ReadStore.lastChapter(t.readKey) != null

    val groups = remember(chapters) {
        val map = LinkedHashMap<String, MutableList<Int>>()
        chapters.forEachIndexed { i, c -> map.getOrPut(c.volume) { mutableListOf() }.add(i) }
        map.entries.map { it.key to it.value.toList() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    t.siteUrl?.let { url ->
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            ctx.startActivity(Intent.createChooser(send, null))
                        }) { Icon(Icons.Filled.Share, "Поделиться") }
                    }
                    val fav = FavoritesStore.contains(t)
                    IconButton(onClick = { FavoritesStore.toggle(t) }) {
                        Icon(
                            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "В избранное",
                            tint = if (fav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RemoteImage(t.imageUrl, Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(16.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t.displayTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        t.name?.takeIf { it != t.displayTitle }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            t.status?.title?.takeIf { it.isNotEmpty() }?.let { Pill(it) }
                            t.type?.title?.takeIf { it.isNotEmpty() }?.let { Pill(it) }
                            t.ageRestriction?.title?.takeIf { it.isNotEmpty() }?.let { Pill(it) }
                            t.rateAvg?.let { Pill("★ $it") }
                            if (chapters.isNotEmpty()) Pill("${chapters.size} гл.")
                        }
                    }
                }
            }

            if (chapters.isNotEmpty()) {
                item {
                    Button(
                        onClick = { Nav.push(Route.Reader(t, chapters, resumeIndex)) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            if (!hasLast) "Начать читать" else "Продолжить: Глава ${chapters[resumeIndex].number}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            t.genres?.takeIf { it.isNotEmpty() }?.let { g ->
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (item in g) Pill(item.title)
                    }
                }
            }

            t.summary?.takeIf { it.isNotEmpty() }?.let { s ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                s,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (expanded) Int.MAX_VALUE else 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (s.length > 220) {
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text(if (expanded) "Свернуть" else "Читать полностью")
                                }
                            }
                        }
                    }
                }
            }

            t.authors?.takeIf { it.isNotEmpty() }?.let { au ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Автор", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (a in au) Pill(a.title)
                        }
                    }
                }
            }

            t.siteUrl?.let { url ->
                item {
                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Открыть на сайте") }
                }
            }

            item {
                Surface(
                    onClick = { showComments = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Комментарии", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("›", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Главы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (chapters.size > 1) {
                        TextButton(onClick = { reversed = !reversed }) {
                            Text(if (reversed) "Сначала новые" else "Сначала старые")
                        }
                    }
                }
            }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            error?.let { e ->
                item { CenterMessage(e, retryText = "Повторить", onRetry = { tick++ }) }
            }
            if (!loading && chapters.isEmpty() && error == null) {
                item { CenterMessage("Глав пока нет") }
            }

            val shownGroups = if (reversed) groups.reversed() else groups
            for ((volume, indices) in shownGroups) {
                item(key = "v-$volume") {
                    Text(
                        "Том $volume",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val ordered = if (reversed) indices.reversed() else indices
                items(ordered, key = { chapters[it].id }) { i ->
                    ChapterRow(t, chapters[i]) { Nav.push(Route.Reader(t, chapters, i)) }
                }
            }
        }
    }

    if (showComments) {
        CommentsSheet(
            title = "Комментарии",
            postId = t.id,
            postType = "manga",
            onDismiss = { showComments = false },
        )
    }
}

@Composable
private fun ChapterRow(t: Title, c: Chapter, onClick: () -> Unit) {
    val isRead = ReadStore.isRead(t.readKey, c.id)
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = if (isRead) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            c.number,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    if (c.name.isEmpty()) "Глава ${c.number}" else c.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (isRead) {
                    Text("Прочитано", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isRead) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
