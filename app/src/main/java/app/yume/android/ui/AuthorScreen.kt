@file:OptIn(ExperimentalMaterial3Api::class)

package app.yume.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yume.android.Nav
import app.yume.android.Route
import app.yume.android.data.Api
import app.yume.android.data.Title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AuthorModel(private val id: Int, private val slug: String?) {
    val items = mutableStateListOf<Title>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var page = 1
    private var hasNext = true

    suspend fun loadMore() {
        if (loading || !hasNext) return
        loading = true
        try {
            val r = Api.byAuthor(id, slug, page)
            val known = items.map { it.id }.toSet()
            items.addAll(r.items.filter { it.id !in known })
            hasNext = r.hasNext && r.items.isNotEmpty()
            page += 1
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            hasNext = false
            if (items.isEmpty()) error = e.message ?: "Не удалось загрузить тайтлы автора"
        } finally {
            loading = false
        }
    }
}

@Composable
fun AuthorScreen(route: Route.Author) {
    val model = remember(route.uid) { AuthorModel(route.authorId, route.slug) }
    val scope = rememberCoroutineScope()
    val state = rememberLazyGridState()

    LaunchedEffect(model) { model.loadMore() }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (model.items.isNotEmpty() && last >= model.items.size - 6) model.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(route.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            state = state,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val err = model.error
            if (err != null && model.items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CenterMessage(err, retryText = "Повторить", onRetry = { scope.launch { model.loadMore() } })
                }
            } else if (model.items.isEmpty() && !model.loading) {
                item(span = { GridItemSpan(maxLineSpan) }) { CenterMessage("Тайтлов не найдено") }
            }
            items(model.items, key = { it.id }) { t -> GridCard(t, onClick = { Nav.push(Route.Detail(t)) }) }
            if (model.loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
