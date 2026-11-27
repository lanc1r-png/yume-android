@file:OptIn(ExperimentalMaterial3Api::class)

package app.yume.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yume.android.data.Chapter
import app.yume.android.data.ReadStore

/** Список глав: поиск, тома, отметки прочитанного, порядок. */
@Composable
fun MangaChapterPicker(chapters: List<Chapter>, current: Int, readKey: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(true) }
    val read = ReadStore.read[readKey.toString()].orEmpty().toSet()
    val q = query.trim().lowercase()
    var entries = chapters.mapIndexed { i, c -> i to c }
    if (q.isNotEmpty()) {
        entries = entries.filter { (_, c) -> c.number.lowercase().contains(q) || c.name.lowercase().contains(q) || "том ${c.volume}".contains(q) }
    }
    if (newestFirst) entries = entries.reversed()
    val state = rememberLazyListState()
    LaunchedEffect(Unit) {
        val pos = entries.indexOfFirst { it.first == current }
        if (pos > 2) state.scrollToItem(pos - 2)
    }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Главы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${chapters.size} всего · прочитано ${chapters.count { it.id in read }}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { newestFirst = !newestFirst }) { Text(if (newestFirst) "Сначала новые" else "Сначала старые") }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Номер, название или том") })
            LazyColumn(state = state, modifier = Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(entries, key = { _, e -> e.second.id }) { n, (i, c) ->
                    val prevVol = if (n > 0) entries[n - 1].second.volume else null
                    if (c.volume != prevVol) {
                        Text("Том ${c.volume}", Modifier.padding(top = 10.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val isCur = i == current
                    val isRead = c.id in read
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (isCur) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onPick(i) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            c.number, Modifier.width(52.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                .padding(vertical = 10.dp),
                            textAlign = TextAlign.Center, maxLines = 1, fontWeight = FontWeight.Bold,
                            color = if (isCur) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (c.name.isEmpty()) "Глава ${c.number}" else c.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isCur) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isRead && !isCur) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        if (isCur) Text("●", color = MaterialTheme.colorScheme.primary) else if (isRead) Text("✓", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}
