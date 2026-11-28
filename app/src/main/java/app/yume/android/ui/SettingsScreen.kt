@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.yume.android.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import app.yume.android.Nav
import app.yume.android.Prefs
import app.yume.android.Route
import app.yume.android.data.AuthStore
import app.yume.android.data.RequestLog
import androidx.compose.ui.platform.LocalView
import app.yume.android.data.FavoritesStore
import app.yume.android.data.OfflineStore
import app.yume.android.data.ProgressStore
import app.yume.android.data.Site
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
private fun SwitchRow(text: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = value, onCheckedChange = onChange)
    }
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
                PlatformTile("hentai", "Хентай"),
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

        SectionTitle("Акцентный цвет")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val colors = listOf("A87AFF", "E91E63", "2196F3", "009688", "4CAF50", "FACC15", "FF9800", "F44336", "64D2FF", "94A3B8")
            FilterChip(
                selected = Prefs.accent == "dynamic",
                onClick = { Prefs.selectAccent("dynamic") },
                label = { Text("Из обоев") },
            )
            for (hex in colors) {
                val selected = Prefs.accent.equals(hex, ignoreCase = true)
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor("#$hex")))
                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { Prefs.selectAccent(hex) },
                )
            }
        }

        SectionTitle("Отклик")
        val view = LocalView.current
        SwitchRow("Вибрация при нажатиях", Prefs.haptics) { Prefs.updateHaptics(it); if (it) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM) }
        SwitchRow("Не гасить экран, пока читаешь или смотришь", Prefs.keepAwake) { Prefs.updateKeepAwake(it) }
        SwitchRow("Стеклянная нижняя панель (размытие, Android 12+)", Prefs.glass) { Prefs.updateGlass(it) }

        val site = Site.of(Prefs.platform)
        if (site.isManga) {
        SectionTitle("Чтение манги")
        SwitchRow("Фон под страницу: по теме приложения", Prefs.mangaAutoBg) { Prefs.updateMangaAutoBg(it) }
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
            for ((key, label) in listOf("slide" to "Сдвиг", "fade" to "Плавно", "book" to "Как в книге")) {
                FilterChip(
                    selected = Prefs.mangaAnim == key,
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
        }
        if (site.isRanobe) {
            SectionTitle("Чтение ранобэ")
            Text("Режим", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((key, label) in listOf("pages" to "Страницы", "scroll" to "Прокрутка")) {
                    FilterChip(selected = Prefs.novelMode == key, onClick = { Prefs.selectNovelMode(key) }, label = { Text(label) })
                }
            }
            Text("Листание", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((key, label) in listOf("slide" to "Сдвиг", "fade" to "Плавно", "book" to "Как в книге")) {
                    FilterChip(selected = Prefs.novelAnim == key, onClick = { Prefs.selectNovelAnim(key) }, label = { Text(label) })
                }
            }
            Text(
                "В режиме «Страницы» глава режется на страницы по размеру экрана. Размер шрифта и тему можно менять в самой читалке.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionTitle("Загрузки")
            Text(
                "Скачано глав: ${OfflineStore.totalChapters()} · ${OfflineStore.sizeText()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { Nav.push(Route.Offline()) }) { Text("Открыть «Скачанное»") }
            Text("Лимит места", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (g in listOf(0, 1, 2, 3, 5, 10, 20, 50)) {
                    FilterChip(selected = Prefs.offLimit == g, onClick = { Prefs.selectOffLimit(g) }, label = { Text(if (g == 0) "Без лимита" else "$g ГБ") })
                }
            }
            if (Prefs.offLimit > 0) {
                Text("При превышении", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = Prefs.offOverflow == "refuse", onClick = { Prefs.selectOffOverflow("refuse") }, label = { Text("Отказаться") })
                    FilterChip(selected = Prefs.offOverflow == "purge", onClick = { Prefs.selectOffOverflow("purge") }, label = { Text("Удалять прочитанное") })
                }
                Text(
                    "Занято ${OfflineStore.sizeText()} из ${Prefs.offLimit} ГБ. «Отказаться» — загрузка встанет на лимите, «Удалять прочитанное» — освободит место старыми прочитанными главами.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SwitchRow("Скачивать только по Wi‑Fi", Prefs.offWifi) { Prefs.updateOffWifi(it) }
            SwitchRow("Докачивать 3 следующие главы при чтении", Prefs.offPrefetch) { Prefs.updateOffPrefetch(it) }
            if (OfflineStore.totalChapters() > 0) {
                OutlinedButton(onClick = { confirm = "offline" }) { Text("Удалить все загрузки") }
            }
        }
        if (site.isAnime) {
            SectionTitle("Просмотр")
            SwitchRow("Продолжать с места остановки", Prefs.resume) { Prefs.updateResume(it) }
            SwitchRow("Следующая серия сама после конца", Prefs.autoNext) { Prefs.updateAutoNext(it) }
            SwitchRow("Kodik: сразу прямой плеер, без веб-плеера", Prefs.kodikDirect) { Prefs.updateKodikDirect(it) }
            Text(
                "Kodik открывается в веб-плеере: выбери качество и нажми «на весь экран», тогда включится обычный плеер приложения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            OutlinedButton(onClick = {
                try {
                    val dir = java.io.File(ctx.cacheDir, "share").also { it.mkdirs() }
                    val f = java.io.File(dir, Backup.FILE_NAME)
                    f.writeBytes(Backup.toPlist())
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(i, "Отправить копию"))
                    Prefs.markBackupSent()
                } catch (e: Exception) {
                    toast("Не удалось подготовить файл: ${e.message}")
                }
            }) { Text("Отправить копию") }
        }
        val sentDays = if (Prefs.backupSent == 0L) null else ((System.currentTimeMillis() - Prefs.backupSent) / 86_400_000L).toInt()
        if (sentDays == null || sentDays >= 7) {
            Text(
                (if (sentDays == null) "Копия ещё не отправлялась." else "Копия не отправлялась $sentDays дн.") + " Отправьте файл в Telegram или сохраните: при переустановке данные не пропадут.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
        }

        SectionTitle("Кэш картинок")
        val loader = coil3.SingletonImageLoader.get(ctx)
        var cacheTick by remember { mutableIntStateOf(0) }
        val cacheBytes = if (cacheTick >= 0) (loader.diskCache?.size ?: 0L) else 0L
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Обложки и просмотренные страницы: ${"%.1f".format(cacheBytes / 1048576.0)} МБ", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                loader.diskCache?.clear()
                loader.memoryCache?.clear()
                cacheTick++
                toast("Кэш картинок очищен. Скачанные главы на месте.")
            }, enabled = cacheBytes > 0) { Text("Очистить") }
        }

        SectionTitle("Профиль")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Nav.push(Route.Stats()) }) { Text("Моя активность") }
            OutlinedButton(onClick = { Nav.push(Route.Friends()) }) { Text("Друзья") }
        }
        Text("Шапка и аватарка меняются во вкладке «Профиль» → «Оформление».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("Для разработчика")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Nav.push(Route.SiteLog()) }) { Text("Журнал запросов сайта (${RequestLog.items.size})") }
            OutlinedButton(onClick = { Nav.push(Route.AnySite()) }) { Text("Любой сайт") }
        }

        SectionTitle("Сброс")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirm = "history" }) { Text("Очистить историю") }
            OutlinedButton(onClick = { confirm = "read" }) { Text(if (site.isAnime) "Сбросить прогресс просмотра" else "Сбросить «прочитано»") }
            OutlinedButton(onClick = { confirm = "fav" }) { Text("Очистить избранное") }
        }

        AuthStore.user?.let { u ->
            OutlinedButton(onClick = { confirm = "signout" }, modifier = Modifier.fillMaxWidth()) {
                Text("Выйти из аккаунта ${u.username}", color = MaterialTheme.colorScheme.error)
            }
        } ?: Text("Вход — во вкладке «Профиль».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Yume ${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("Данные: ${Site.of(Prefs.platform).origin.removePrefix("https://")} · неофициальное приложение", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(
                    when (what) {
                        "history" -> "Очистить историю?"
                        "offline" -> "Удалить все скачанные главы?"
                        "fav" -> "Очистить избранное?"
                        "signout" -> "Выйти из аккаунта?"
                        else -> "Сбросить отметки?"
                    },
                )
            },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    when (what) {
                        "history" -> HistoryStore.clear()
                        "offline" -> OfflineStore.removeAll()
                        "fav" -> FavoritesStore.clear()
                        "signout" -> AuthStore.signOut()
                        else -> {
                            ReadStore.clear()
                            ProgressStore.clear()
                        }
                    }
                    confirm = null
                }) { Text("Да") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }
}
