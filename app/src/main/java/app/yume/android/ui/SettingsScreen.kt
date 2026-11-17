@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.yume.android.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yume.android.Prefs
import app.yume.android.data.Backup
import app.yume.android.data.HistoryStore
import app.yume.android.data.ReadStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PlatformTile(val key: String, val title: String)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var backupTick by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<String?>(null) }

    fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: Exception) {
                // разрешение могло уже быть постоянным
            }
            val ok = Backup.setFolder(ctx, uri)
            backupTick++
            toast(if (ok) "Папка выбрана, копия сохранена" else "Не удалось записать копию в эту папку")
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(Backup.toPlist()) }
                toast("Копия сохранена")
            } catch (e: Exception) {
                toast("Не удалось сохранить: ${e.message}")
            }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val n = bytes?.let { Backup.restore(it) }
                toast(if (n == null) "Это не файл копии Yume" else "Восстановлено записей: $n")
            } catch (e: Exception) {
                toast("Не удалось прочитать файл: ${e.message}")
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)

        SectionTitle("Платформа")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tiles = listOf(
                PlatformTile("anime", "Аниме"),
                PlatformTile("ranobe", "Ранобэ"),
                PlatformTile("manga", "Манга"),
            )
            for (t in tiles) {
                val enabled = t.key in Prefs.ACTIVE_PLATFORMS
                val selected = enabled && Prefs.platform == t.key
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (enabled) 1f else 0.55f)
                        .clickable(enabled = enabled) { Prefs.selectPlatform(t.key) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(t.title, fontWeight = FontWeight.SemiBold)
                        if (!enabled) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(
                                    "Скоро",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                        } else {
                            Text(
                                "Доступно",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        SectionTitle("Тема")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, label) in listOf("light" to "Светлая", "dark" to "Тёмная", "system" to "Как в системе")) {
                FilterChip(
                    selected = Prefs.theme == key,
                    onClick = { Prefs.selectTheme(key) },
                    label = { Text(label) },
                )
            }
        }

        SectionTitle("Чтение манги")
        Text("Режим по умолчанию", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, label) in listOf("auto" to "Авто", "pages" to "Страницы", "scroll" to "Лента")) {
                FilterChip(
                    selected = Prefs.mangaMode == key,
                    onClick = { Prefs.selectMangaMode(key) },
                    label = { Text(label) },
                )
            }
        }
        Text("Листание страниц", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, label) in listOf("slide" to "Сдвиг", "fade" to "Плавно")) {
                FilterChip(
                    selected = (if (Prefs.mangaAnim == "fade") "fade" else "slide") == key,
                    onClick = { Prefs.selectMangaAnim(key) },
                    label = { Text(label) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Зоны нажатия", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Касание у левого края листает назад, у правого вперёд.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = Prefs.tapZones, onCheckedChange = { Prefs.updateTapZones(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Не гасить экран при чтении", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = Prefs.keepAwake, onCheckedChange = { Prefs.updateKeepAwake(it) })
        }

        SectionTitle("Резервная копия")
        Text(
            "Избранное, история, прогресс и настройки. Формат совместим с iOS-версией. Токен входа в копию не попадает.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // backupTick читаем, чтобы подпись обновлялась после выбора папки
        val folder = if (backupTick >= 0) Backup.folderName(ctx) else null
        val lastWrite = Backup.lastFolderWrite()
        Text(
            if (folder == null) "Папка не выбрана (копия лежит только внутри приложения)"
            else "Папка: $folder" + (lastWrite?.let {
                " · обновлено " + SimpleDateFormat("d MMM HH:mm", Locale("ru")).format(Date(it))
            } ?: "") + if (Backup.lastFolderFailed()) " · последняя запись не удалась" else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickFolder.launch(null) }) { Text(if (folder == null) "Выбрать папку" else "Сменить папку") }
            if (folder != null) {
                OutlinedButton(onClick = {
                    Backup.clearFolder()
                    backupTick++
                }) { Text("Отключить папку") }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportFile.launch(Backup.FILE_NAME) }) { Text("Сохранить в файл") }
            OutlinedButton(onClick = { importFile.launch(arrayOf("*/*")) }) { Text("Загрузить из файла") }
        }

        SectionTitle("Данные")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirm = "history" }) { Text("Очистить историю") }
            OutlinedButton(onClick = { confirm = "read" }) { Text("Сбросить «прочитано»") }
        }
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "history") "Очистить историю?" else "Сбросить отметки «прочитано»?") },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    if (what == "history") HistoryStore.clear() else ReadStore.clear()
                    confirm = null
                }) { Text("Да") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }
}
