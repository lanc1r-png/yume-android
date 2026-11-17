@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.yume.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yume.android.Nav
import app.yume.android.Prefs
import app.yume.android.data.Site
import app.yume.android.Route
import app.yume.android.Router
import app.yume.android.data.Api
import app.yume.android.data.CatalogFilter
import app.yume.android.data.CatalogPage
import app.yume.android.data.HistoryStore
import app.yume.android.data.SearchHistory
import app.yume.android.data.Sort
import app.yume.android.data.Title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Данные главной живут вне экрана, чтобы не грузиться заново при переключении вкладок. */
object HomeModel {
    var fresh by mutableStateOf(emptyList<Title>())
    var popular by mutableStateOf(emptyList<Title>())
    var newest by mutableStateOf(emptyList<Title>())
    var top by mutableStateOf(emptyList<Title>())
    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var popularPage = 1
    var popularHasNext by mutableStateOf(true)

    private suspend fun fetch(sort: Sort, page: Int = 1): CatalogPage? = try {
        Api.catalog(CatalogFilter(sort = sort), page)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private var loadedFor = ""

    suspend fun load(force: Boolean = false) {
        if (loading) return
        val key = Site.current.key
        if (loadedFor != key) {
            fresh = emptyList()
            popular = emptyList()
            newest = emptyList()
            top = emptyList()
            error = null
            popularHasNext = true
            popularPage = 1
            loadedFor = key
        }
        if (!force && fresh.isNotEmpty()) return
        loading = true
        try {
            coroutineScope {
                val a = async { fetch(Sort.LAST) }
                val b = async { fetch(Sort.VIEWS) }
                val c = async { fetch(Sort.NEWEST) }
                val d = async { fetch(Sort.RATING) }
                val ra = a.await()
                val rb = b.await()
                val rc = c.await()
                val rd = d.await()
                ra?.let { fresh = it.items }
                rb?.let {
                    popular = it.items
                    popularPage = 1
                    popularHasNext = it.hasNext
                }
                rc?.let { newest = it.items }
                rd?.let { top = it.items }
                error = if (ra == null && rb == null && rc == null && rd == null) {
                    "Не удалось загрузить данные. Проверьте соединение."
                } else {
                    null
                }
            }
        } finally {
            loading = false
        }
    }

    suspend fun morePopular() {
        if (loadingMore || !popularHasNext) return
        loadingMore = true
        try {
            val r = fetch(Sort.VIEWS, popularPage + 1)
            if (r != null) {
                val known = popular.map { it.id }.toSet()
                popular = popular + r.items.filter { it.id !in known }
                popularPage += 1
                popularHasNext = r.hasNext && r.items.isNotEmpty()
            } else {
                popularHasNext = false
            }
        } finally {
            loadingMore = false
        }
    }
}

@Composable
private fun PosterRow(list: List<Title>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.id }) { t -> PosterCard(t, onClick = { Nav.push(Route.Detail(t)) }) }
    }
}

@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    val site = Site.of(Prefs.platform)
    LaunchedEffect(site) { HomeModel.load() }
    var query by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        site.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    IconButton(onClick = { scope.launch { HomeModel.load(force = true) } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                    }
                }
                SearchField(query, onChange = { query = it }, onSubmit = {
                    val q = query.trim()
                    if (q.isNotEmpty()) {
                        SearchHistory.add(q)
                        Router.openCatalog(query = q)
                        query = ""
                    }
                })
            }
        }

        val err = HomeModel.error
        if (err != null && HomeModel.fresh.isEmpty()) {
            item {
                CenterMessage(err, retryText = "Повторить", onRetry = { scope.launch { HomeModel.load(force = true) } })
            }
        }

        if (HomeModel.loading && HomeModel.fresh.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            }
        }

        if (HistoryStore.items.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(if (site.isAnime) "Продолжить смотреть" else "Продолжить читать", actionText = "Очистить", onAction = { HistoryStore.clear() })
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(HistoryStore.items.toList(), key = { it.title.id }) { h ->
                            ContinueCard(h, onClick = { Nav.push(Route.Detail(h.title)) })
                        }
                    }
                }
            }
        }

        if (HomeModel.fresh.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(Sort.LAST.label(site), onClick = { Router.openCatalog(sort = Sort.LAST) })
                    PosterRow(HomeModel.fresh.take(14))
                }
            }
        }

        if (HomeModel.popular.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(if (site.isAnime) "Сейчас смотрят" else "Сейчас читают", onClick = { Router.openCatalog(sort = Sort.VIEWS) })
                    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeModel.popular.forEachIndexed { i, t ->
                            TitleRow(t, rank = i + 1, onClick = { Nav.push(Route.Detail(t)) })
                        }
                        if (HomeModel.popularHasNext) {
                            OutlinedButton(
                                onClick = { scope.launch { HomeModel.morePopular() } },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (HomeModel.loadingMore) CircularProgressIndicator(Modifier.padding(2.dp)) else Text("Показать ещё")
                            }
                        }
                    }
                }
            }
        }

        if (HomeModel.newest.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Новинки", onClick = { Router.openCatalog(sort = Sort.NEWEST) })
                    PosterRow(HomeModel.newest)
                }
            }
        }

        if (HomeModel.top.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Лучшее по рейтингу", onClick = { Router.openCatalog(sort = Sort.RATING) })
                    PosterRow(HomeModel.top)
                }
            }
        }
    }
}
