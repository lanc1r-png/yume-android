@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.yume.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yume.android.data.Api
import app.yume.android.data.CommentItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class CommentSort(val title: String) { NEW("Новые"), POPULAR("Популярные") }

class CommentsModel(private val postId: Int, private val postType: String) {
    val items = mutableStateListOf<CommentItem>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var sort by mutableStateOf(CommentSort.NEW)
    val replies = mutableStateMapOf<Int, List<CommentItem>>()
    val loadingReplies = mutableStateListOf<Int>()
    private var page = 1
    var finished by mutableStateOf(false)
        private set

    fun sorted(): List<CommentItem> = when (sort) {
        CommentSort.NEW -> items.sortedWith(compareByDescending<CommentItem> { it.created }.thenByDescending { it.id })
        CommentSort.POPULAR -> items.sortedWith(compareByDescending<CommentItem> { it.up }.thenByDescending { it.created })
    }

    suspend fun setSort(s: CommentSort) {
        sort = s
        if (s == CommentSort.POPULAR) {
            var n = 0
            while (!finished && n < 4) {
                loadMore()
                n++
            }
        }
    }

    suspend fun loadMore() {
        if (loading || finished) return
        loading = true
        try {
            val r = Api.comments(postId, postType, page)
            val known = items.map { it.id }.toSet()
            val fresh = r.items.filter { it.id !in known }
            items.addAll(fresh)
            page += 1
            if (fresh.isEmpty() || r.hasNext == false) finished = true
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            finished = true
            if (items.isEmpty()) error = e.message ?: "Не удалось загрузить комментарии"
        } finally {
            loading = false
        }
    }

    suspend fun loadReplies(c: CommentItem) {
        loadingReplies.add(c.id)
        try {
            val known = items.map { it.id }.toSet()
            replies[c.id] = Api.commentReplies(postId, postType, c.id).filter { it.id !in known }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            replies[c.id] = emptyList()
        } finally {
            loadingReplies.remove(c.id)
        }
    }
}

@Composable
fun CommentsSheet(title: String, postId: Int, postType: String, onDismiss: () -> Unit) {
    val model = remember(postId, postType) { CommentsModel(postId, postType) }
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    LaunchedEffect(model) { model.loadMore() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (model.items.isNotEmpty() && last >= model.items.size - 3) model.loadMore()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                title,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in CommentSort.entries) {
                    FilterChip(
                        selected = model.sort == s,
                        onClick = { scope.launch { model.setSort(s) } },
                        label = { Text(s.title) },
                    )
                }
            }
            Text(
                "Писать комментарии можно будет после входа в аккаунт (скоро).",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(520.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val err = model.error
                if (err != null && model.items.isEmpty()) {
                    item {
                        CenterMessage(err, retryText = "Повторить", onRetry = { scope.launch { model.loadMore() } })
                    }
                } else if (model.items.isEmpty() && !model.loading) {
                    item { CenterMessage("Комментариев пока нет") }
                }
                items(model.sorted(), key = { it.id }) { c -> CommentRow(c, model, depth = 0) }
                if (model.loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: CommentItem, model: CommentsModel, depth: Int) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val children = if (c.replies.isEmpty()) (model.replies[c.id] ?: emptyList()) else c.replies

    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteImage(c.avatar, Modifier.size(38.dp).clip(CircleShape))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(c.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(c.text, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("👍 ${c.up}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (c.down > 0) {
                            Text("👎 ${c.down}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (c.replyCount > 0) {
                            TextButton(onClick = {
                                expanded = !expanded
                                if (expanded && children.isEmpty()) scope.launch { model.loadReplies(c) }
                            }) {
                                Text(if (expanded) "Скрыть ответы" else "Ответы (${c.replyCount})")
                            }
                        }
                    }
                }
            }
            if (expanded) {
                if (c.id in model.loadingReplies) CircularProgressIndicator(Modifier.size(24.dp))
                Column(
                    Modifier.padding(start = if (depth < 2) 18.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (r in children) CommentRow(r, model, depth + 1)
                }
            }
        }
    }
}
