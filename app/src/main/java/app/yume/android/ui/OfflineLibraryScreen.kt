@file:OptIn(ExperimentalMaterial3Api::class)

package app.yume.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yume.android.Nav
import app.yume.android.Route
import app.yume.android.data.DownloadManager
import app.yume.android.data.OfflineEntry
import app.yume.android.data.OfflineStore

private fun fmt(b: Long): String = when {
    b >= 1024L * 1024 * 1024 -> "%.1f ГБ".format(b / (1024.0 * 1024 * 1024))
    b >= 1024L * 1024 -> "%.1f МБ".format(b / (1024.0 * 1024))
    else -> "%.0f КБ".format(b / 1024.0)
}

/** Всё, что скачано для чтения без сети. */
@Composable
fun OfflineLibraryScreen(onClose: () -> Unit) {
    val list = OfflineStore.entries.values.sortedByDescending { it.updated }
    var confirm by remember { mutableStateOf<OfflineEntry?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        OneHeader(
            "Скачанное", onClose,
            subtitle = "${OfflineStore.totalChapters()} гл. · ${fmt(OfflineStore.totalBytes())}",
        ) {
            if (list.isNotEmpty()) TextButton(onClick = { confirmAll = true }) { Text("Удалить всё") }
        }
        if (DownloadManager.pending.isNotEmpty()) {
            Text(
                "Загружается: ${DownloadManager.status.ifEmpty { "…" }} · в очереди ${DownloadManager.pending.size}",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
            )
        }
        if (list.isEmpty()) CenterMessage("Пока ничего не скачано.\nНа странице ранобэ нажмите «Скачать ⬇».")
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.novel.id }) { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { Nav.push(Route.Detail(e.novel)) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteImage(e.novel.imageUrl, Modifier.width(56.dp).height(84.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(e.novel.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${e.files.size} гл. · ${fmt(e.bytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { confirm = e }) { Text("Удалить") }
                }
            }
        }
    }
    confirm?.let { e ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Удалить загрузки «${e.novel.displayTitle}»?") },
            text = { Text("Будет освобождено ${fmt(e.bytes)}.") },
            confirmButton = { TextButton(onClick = { OfflineStore.remove(e.novel.id); confirm = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Удалить все загрузки?") },
            confirmButton = { TextButton(onClick = { OfflineStore.removeAll(); confirmAll = false }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Отмена") } },
        )
    }
}
