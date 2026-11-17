@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.yume.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateListOf
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
import app.yume.android.data.FilterOptions
import app.yume.android.data.NamedItem
import app.yume.android.data.SearchHistory
import app.yume.android.data.Sort
import app.yume.android.data.Title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

object CatalogModel {
    var query by mutableStateOf("")
    var sort by mutableStateOf(Sort.RATING)
    var genres by mutableStateOf(emptySet<Int>())
    var tags by mutableStateOf(emptySet<Int>())
    var types by mutableStateOf(emptySet<Int>())
    var statuses by mutableStateOf(emptySet<Int>())

    val items = mutableStateListOf<Title>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var options by mutableStateOf(FilterOptions())
    var optionsLoading by mutableStateOf(false)

    private var page = 1
    private var hasNext = true
    private var generation = 0
    var loadedQuery = ""
        private set

    fun filter() = CatalogFilter(query, sort, genres, tags, types, statuses)

    val activeCount: Int get() = genres.size + tags.size + types.size + statuses.size

    private var loadedSite = ""

    /** После смены платформы фильтры и список от прежнего сайта не подходят. */
    fun syncSite() {
        val k = Site.current.key
        if (loadedSite == k) return
        if (loadedSite.isNotEmpty()) {
            query = ""
            sort = Sort.RATING
            resetFilters()
            options = FilterOptions()
            items.clear()
            generation++
            loading = false
        }
        loadedSite = k
    }

    suspend fun reload() {
        generation++
        loadedQuery = query
        page = 1
        hasNext = true
        items.clear()
        error = null
        loading = false
        loadMore()
    }

    suspend fun loadMore() {
        if (!hasNext || loading) return
        loading = true
        val gen = generation
        try {
            val r = Api.catalog(filter(), page)
            if (gen != generation) return
            val known = items.map { it.id }.toSet()
            items.addAll(r.items.filter { it.id !in known })
            hasNext = r.hasNext
            page += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (gen == generation) error = e.message ?: "Не удалось загрузить каталог"
        } finally {
            if (gen == generation) loading = false
        }
    }

    fun toggle(kind: String, id: Int) {
        fun flip(s: Set<Int>) = if (id in s) s - id else s + id
        when (kind) {
            "genres" -> genres = flip(genres)
            "tags" -> tags = flip(tags)
            "types" -> types = flip(types)
            else -> statuses = flip(statuses)
        }
    }

    fun resetFilters() {
        genres = emptySet()
        tags = emptySet()
        types = emptySet()
        statuses = emptySet()
    }

    suspend fun loadOptions(force: Boolean = false) {
        if (optionsLoading || (!force && !options.isEmpty)) return
        optionsLoading = true
        try {
            options = Api.filterOptions()
        } finally {
            optionsLoading = false
        }
    }
}

@Composable
fun CatalogScreen() {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var showFilters by remember { mutableStateOf(false) }
    var rolling by remember { mutableStateOf(false) }

    val site = Site.of(Prefs.platform)
    LaunchedEffect(site) {
        CatalogModel.syncSite()
        if (CatalogModel.items.isEmpty()) CatalogModel.reload()
    }

    LaunchedEffect(Router.pendingQuery, Router.pendingSort) {
        var changed = false
        Router.pendingQuery?.let {
            CatalogModel.query = it
            Router.pendingQuery = null
            changed = true
        }
        Router.pendingSort?.let {
            CatalogModel.sort = it
            Router.pendingSort = null
            changed = true
        }
        if (changed) CatalogModel.reload()
    }

    // живой поиск: ждём, пока закончите печатать
    LaunchedEffect(CatalogModel.query) {
        if (CatalogModel.query == CatalogModel.loadedQuery) return@LaunchedEffect
        delay(450)
        if (CatalogModel.query != CatalogModel.loadedQuery) CatalogModel.reload()
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (CatalogModel.items.isNotEmpty() && last >= CatalogModel.items.size - 6) CatalogModel.loadMore()
        }
    }

    fun roll() {
        if (rolling) return
        rolling = true
        scope.launch {
            var pick: Title? = null
            val f = CatalogModel.filter()
            for (i in 0 until 3) {
                if (pick != null) break
                pick = try {
                    Api.catalog(f, Random.nextInt(1, 26)).items.randomOrNull()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            if (pick == null) {
                pick = try {
                    Api.catalog(f, 1).items.randomOrNull()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            rolling = false
            pick?.let { Nav.push(Route.Detail(it)) }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        state = gridState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        site.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    TextButton(onClick = { roll() }) {
                        if (rolling) CircularProgressIndicator(Modifier.padding(2.dp)) else Text("🎲 Случайная")
                    }
                    val n = CatalogModel.activeCount
                    OutlinedButton(onClick = { showFilters = true }) { Text(if (n > 0) "Фильтры ($n)" else "Фильтры") }
                }
                SearchField(
                    CatalogModel.query,
                    onChange = { CatalogModel.query = it },
                    onSubmit = {
                        SearchHistory.add(CatalogModel.query)
                        scope.launch { CatalogModel.reload() }
                    },
                )
                if (CatalogModel.query.isEmpty() && SearchHistory.items.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SearchHistory.items.toList()) { q ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    CatalogModel.query = q
                                    scope.launch { CatalogModel.reload() }
                                },
                                label = { Text(q) },
                            )
                        }
                        item {
                            TextButton(onClick = { SearchHistory.clear() }) { Text("Очистить") }
                        }
                    }
                }
            }
        }

        val err = CatalogModel.error
        if (err != null && CatalogModel.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CenterMessage(err, retryText = "Повторить", onRetry = { scope.launch { CatalogModel.reload() } })
            }
        } else if (CatalogModel.items.isEmpty() && !CatalogModel.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) { CenterMessage("Ничего не найдено") }
        }

        items(CatalogModel.items, key = { it.id }) { t ->
            GridCard(t, onClick = { Nav.push(Route.Detail(t)) })
        }

        if (CatalogModel.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showFilters) FilterSheet(onDismiss = { showFilters = false })
}

@Composable
private fun OptionBlock(title: String, kind: String, list: List<NamedItem>, selected: Set<Int>) {
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (item in list) {
                val id = item.id ?: continue
                FilterChip(
                    selected = id in selected,
                    onClick = { CatalogModel.toggle(kind, id) },
                    label = { Text(item.title) },
                )
            }
        }
    }
}

@Composable
private fun FilterSheet(onDismiss: () -> Unit) {
    val site = Site.of(Prefs.platform)
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { CatalogModel.loadOptions() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Фильтры", style = MaterialTheme.typography.headlineSmall)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Сортировка", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in Sort.entries) {
                        FilterChip(
                            selected = CatalogModel.sort == s,
                            onClick = { CatalogModel.sort = s },
                            label = { Text(s.label(site)) },
                        )
                    }
                }
            }

            if (CatalogModel.optionsLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.padding(2.dp))
                    Text("Загружаю жанры и теги…")
                }
            } else if (CatalogModel.options.isEmpty) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Не удалось загрузить жанры и теги.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { scope.launch { CatalogModel.loadOptions(force = true) } }) { Text("Повторить") }
                }
            }

            OptionBlock("Тип", "types", CatalogModel.options.types, CatalogModel.types)
            OptionBlock("Статус", "statuses", CatalogModel.options.statuses, CatalogModel.statuses)
            OptionBlock("Жанры", "genres", CatalogModel.options.genres, CatalogModel.genres)
            OptionBlock("Теги", "tags", CatalogModel.options.tags, CatalogModel.tags)

            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { CatalogModel.resetFilters() },
                    enabled = CatalogModel.activeCount > 0,
                    modifier = Modifier.weight(1f),
                ) { Text("Сбросить") }
                Button(
                    onClick = {
                        onDismiss()
                        scope.launch { CatalogModel.reload() }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Показать") }
            }
        }
    }
}
