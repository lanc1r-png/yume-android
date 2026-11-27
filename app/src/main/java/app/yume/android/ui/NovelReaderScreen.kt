@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.yume.android.ui

import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.yume.android.Prefs
import app.yume.android.Route
import app.yume.android.data.Api
import app.yume.android.data.Block
import app.yume.android.data.Chapter
import app.yume.android.data.DownloadManager
import app.yume.android.data.FavoritesStore
import app.yume.android.data.HistoryStore
import app.yume.android.data.KV
import app.yume.android.data.OfflineStore
import app.yume.android.data.ReadStore
import app.yume.android.data.ReaderParser
import app.yume.android.data.Title
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

sealed class NovelPage {
    class Text(val text: CharSequence) : NovelPage()
    class Img(val url: String) : NovelPage()
}

private fun paintOf(fontPx: Float, serif: Boolean, color: Int) = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
    textSize = fontPx
    this.color = color
    typeface = if (serif) Typeface.SERIF else Typeface.SANS_SERIF
}

private fun layoutOf(text: CharSequence, widthPx: Int, fontPx: Float, serif: Boolean, lineGap: Float, color: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paintOf(fontPx, serif, color), widthPx)
        .setLineSpacing(fontPx * lineGap, 1f)
        .setIncludePad(false)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .build()

/** Режет главу на страницы по размеру экрана: сначала весь текст до иллюстрации собирается и делится по строкам. */
private fun paginate(
    blocks: List<Block>,
    title: String,
    widthPx: Int,
    heightPx: Int,
    fontPx: Float,
    serif: Boolean,
    lineGap: Float,
    color: Int,
): List<NovelPage> {
    val pages = ArrayList<NovelPage>()
    var sb = SpannableStringBuilder()
    val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE

    fun gap() {
        val s = sb.length
        sb.append("\n")
        sb.setSpan(AbsoluteSizeSpan((fontPx * 0.5f).toInt()), s, sb.length, flags)
    }

    fun append(text: String, bold: Boolean = false, italic: Boolean = false, size: Float? = null, indent: Int = 0) {
        val s = sb.length
        sb.append(text)
        val e = sb.length
        if (e == s) return
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        if (style != Typeface.NORMAL) sb.setSpan(StyleSpan(style), s, e, flags)
        if (size != null) sb.setSpan(AbsoluteSizeSpan(size.toInt()), s, e, flags)
        if (indent > 0) sb.setSpan(LeadingMarginSpan.Standard(indent), s, e, flags)
    }

    fun flush() {
        if (sb.isEmpty()) return
        val layout = layoutOf(sb, widthPx, fontPx, serif, lineGap, color)
        var s = 0
        val n = layout.lineCount
        while (s < n && pages.size < 3000) {
            // пустые строки-разделители не должны открывать страницу
            while (s < n && sb.subSequence(layout.getLineStart(s), layout.getLineEnd(s)).isBlank()) s++
            if (s >= n) break
            val top = layout.getLineTop(s)
            var e = s
            while (e < n && layout.getLineBottom(e) - top <= heightPx) e++
            if (e == s) e = s + 1
            val a = layout.getLineStart(s)
            val b = layout.getLineEnd(e - 1)
            pages.add(NovelPage.Text(SpannedString(sb.subSequence(a, b))))
            s = e
        }
        sb = SpannableStringBuilder()
    }

    append(title, bold = true, size = fontPx + 6 * fontPx / 18f)
    append("\n")
    gap()
    for (b in blocks) {
        when (b) {
            is Block.Para -> {
                for (r in b.runs) append(r.text, r.bold, r.italic)
                append("\n")
                gap()
            }
            is Block.Heading -> {
                append(b.text, bold = true, size = fontPx + 4 * fontPx / 18f)
                append("\n")
                gap()
            }
            is Block.Quote -> {
                append(b.text, italic = true, indent = (fontPx * 0.8f).toInt())
                append("\n")
                gap()
            }
            is Block.Rule -> {
                append("* * *\n")
                gap()
            }
            is Block.Image -> {
                flush()
                pages.add(NovelPage.Img(b.url))
            }
        }
    }
    flush()
    return pages
}

private class ReaderColors(val bg: Color, val fg: Color)

private fun colorsFor(theme: String, systemDark: Boolean): ReaderColors {
    val t = if (theme == "auto") {
        when (Prefs.theme) {
            "light" -> "light"
            "dark" -> "dark"
            else -> if (systemDark) "dark" else "light"
        }
    } else {
        theme
    }
    return when (t) {
        "sepia" -> ReaderColors(Color(0xFFF5EDD9), Color(0xFF3B2E2E))
        "light" -> ReaderColors(Color.White, Color(0xFF1A1A1A))
        else -> ReaderColors(Color(0xFF121212), Color(0xFFE6E6E6))
    }
}

@Composable
fun NovelReaderScreen(route: Route.Novel, onClose: () -> Unit) {
    val title = route.title
    val chapters = route.chapters
    val ctx = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()

    var index by remember { mutableIntStateOf(route.start.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))) }
    var blocks by remember { mutableStateOf<List<Block>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var fontSize by remember { mutableFloatStateOf(KV.getFloat("reader.font", 18f)) }
    var theme by remember { mutableStateOf(KV.getString("reader.theme", "dark") ?: "dark") }
    var serif by remember { mutableStateOf(KV.getBool("reader.serif", false)) }
    var lineGap by remember { mutableFloatStateOf(KV.getFloat("reader.lh", 0.4f)) }
    var chromeHidden by remember { mutableStateOf(Prefs.novelMode == "pages") }
    var showSettings by remember { mutableStateOf(false) }
    var pages by remember { mutableStateOf<List<NovelPage>>(emptyList()) }
    var paginating by remember { mutableStateOf(false) }
    var pagesFor by remember { mutableIntStateOf(-1) }
    var showChapters by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { pages.size + 1 })
    val colors = colorsFor(theme, systemDark)
    val mode = Prefs.novelMode
    val anim = Prefs.novelAnim

    LaunchedEffect(index) {
        if (!chapters.indices.contains(index)) return@LaunchedEffect
        val ch = chapters[index]
        loading = true
        error = null
        blocks = emptyList()
        pages = emptyList()
        pagesFor = -1
        ReadStore.mark(title.readKey, ch.id)
        HistoryStore.recordChapter(title, ch.id, ch.title)
        val local = OfflineStore.blocks(title.id, ch.id)
        if (local != null) {
            blocks = local
            loading = false
            return@LaunchedEffect
        }
        val slug = title.slugUrl
        if (slug == null) {
            error = "У тайтла нет адреса"
            loading = false
            return@LaunchedEffect
        }
        try {
            val raw = Api.chapterRaw(slug, ch)
            val parsed = withContext(Dispatchers.Default) { ReaderParser.parseRaw(raw) }
            blocks = parsed
            if (parsed.isEmpty()) {
                error = "Текст главы пуст или недоступен."
            } else if (Prefs.offPrefetch && index + 1 < chapters.size) {
                DownloadManager.enqueue(title, chapters, chapters.subList(index + 1, minOf(index + 4, chapters.size)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить главу"
        }
        loading = false
    }

    DisposableEffect(chromeHidden) {
        val window = ctx.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeHidden) controller?.hide(WindowInsetsCompat.Type.systemBars())
        else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    DisposableEffect(Unit) {
        view.keepScreenOn = Prefs.keepAwake
        onDispose {
            view.keepScreenOn = false
            val window = ctx.findActivity()?.window
            window?.let { WindowCompat.getInsetsController(it, view) }?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun turn(delta: Int) {
        val n = pagerState.currentPage + delta
        if (n < 0 || n > pages.size) return
        scope.launch { pagerState.animateScrollToPage(n) }
    }

    fun onTap(x: Float) {
        when {
            x < 0.3f -> turn(-1)
            x > 0.7f -> turn(1)
            else -> chromeHidden = !chromeHidden
        }
    }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        if (mode == "scroll") {
            ScrollBody(
                title = chapters.getOrNull(index)?.title ?: "",
                blocks = blocks,
                loading = loading,
                error = error,
                fontSize = fontSize,
                serif = serif,
                lineGap = lineGap,
                colors = colors,
                hasNext = index < chapters.size - 1,
                onNext = { index++ },
                onTap = { chromeHidden = !chromeHidden },
                key = index,
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val widthPx = with(density) { (maxWidth - 48.dp).roundToPx() }
                val heightPx = with(density) { (maxHeight - 96.dp).roundToPx() }
                val fontPx = with(density) { fontSize.sp.toPx() }
                val fgInt = colors.fg.toArgb()

                LaunchedEffect(blocks, fontSize, serif, lineGap, fgInt, widthPx, heightPx, index) {
                    if (loading || blocks.isEmpty() || widthPx < 100) {
                        pages = emptyList()
                        return@LaunchedEffect
                    }
                    paginating = true
                    val t = chapters.getOrNull(index)?.title ?: ""
                    val sameChapter = pagesFor == index
                    val oldCount = if (sameChapter) pages.size else 0
                    val oldIndex = if (sameChapter) pagerState.currentPage else 0
                    val result = withContext(Dispatchers.Default) {
                        paginate(blocks, t, widthPx, heightPx, fontPx, serif, lineGap, fgInt)
                    }
                    pages = result
                    pagesFor = index
                    val target = if (oldCount > 0) (oldIndex.toFloat() / oldCount * result.size).toInt() else 0
                    paginating = false
                    pagerState.scrollToPage(target.coerceIn(0, result.size))
                }

                if (loading || paginating) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.fg)
                } else if (error != null) {
                    Text(error ?: "", Modifier.align(Alignment.Center).padding(24.dp), color = Color(0xFFFFA726))
                } else if (pages.isNotEmpty()) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { p ->
                        val offset = (pagerState.currentPage - p) + pagerState.currentPageOffsetFraction
                        Box(
                            Modifier
                                .fillMaxSize()
                                .zIndex(if (anim == "book" && offset > 0f) 1f else 0f)
                                .graphicsLayer {
                                    if (anim == "fade") {
                                        translationX = offset * size.width
                                        alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f)
                                    } else if (anim == "book") {
                                        translationX = offset * size.width
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                        if (offset > 0f) {
                                            scaleX = (1f - offset).coerceIn(0f, 1f)
                                            alpha = 1f - 0.35f * offset.coerceIn(0f, 1f)
                                        } else if (offset < -1f) {
                                            alpha = 0f
                                        }
                                    }
                                }
                                .background(colors.bg)
                                .pointerInput(Unit) {
                                    detectTapGestures { o -> onTap(o.x / size.width.toFloat()) }
                                },
                        ) {
                            if (p < pages.size) {
                                when (val pg = pages[p]) {
                                    is NovelPage.Text -> {
                                        val layout = remember(pg, widthPx, fontPx, serif, lineGap, fgInt) {
                                            layoutOf(pg.text, widthPx, fontPx, serif, lineGap, fgInt)
                                        }
                                        Canvas(Modifier.fillMaxSize()) {
                                            drawIntoCanvas { c ->
                                                c.nativeCanvas.save()
                                                c.nativeCanvas.translate(24.dp.toPx(), 48.dp.toPx())
                                                layout.draw(c.nativeCanvas)
                                                c.nativeCanvas.restore()
                                            }
                                        }
                                    }
                                    is NovelPage.Img -> {
                                        AsyncImage(
                                            model = pg.url,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
                                        )
                                    }
                                }
                                Text(
                                    "${p + 1} / ${pages.size}",
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                                    color = colors.fg.copy(alpha = 0.45f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            } else {
                                Column(
                                    Modifier.fillMaxSize().padding(32.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        if (index + 1 < chapters.size) "Глава закончилась" else "Это последняя глава",
                                        color = colors.fg.copy(alpha = 0.7f),
                                    )
                                    if (index + 1 < chapters.size) {
                                        Button(
                                            onClick = { index++ },
                                            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                        ) { Text("Следующая глава") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !chromeHidden,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Закрыть") }
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(
                            title.displayTitle,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            chapters.getOrNull(index)?.let { "Том ${it.volume} Глава ${it.number}" } ?: "",
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showComments = true }) { Icon(Icons.Filled.Create, "Комментарии к главе") }
                }
            }
        }

        AnimatedVisibility(
            visible = !chromeHidden,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                ) {
                    IconButton(onClick = { index-- }, enabled = index > 0) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Предыдущая глава") }
                    IconButton(onClick = { showChapters = true }) { Icon(Icons.AutoMirrored.Filled.List, "Список глав") }
                    val fav = FavoritesStore.contains(title)
                    IconButton(onClick = { FavoritesStore.toggle(title) }) {
                        Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "В избранное")
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Настройки чтения") }
                    IconButton(onClick = { index++ }, enabled = index < chapters.size - 1) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Следующая глава") }
                }
            }
        }
    }

    if (showComments) {
        chapters.getOrNull(index)?.let { cur ->
            CommentsSheet(title = "Комментарии к главе ${cur.number}", postId = cur.id, postType = "chapter", onDismiss = { showComments = false })
        }
    }

    if (showChapters) {
        ModalBottomSheet(onDismissRequest = { showChapters = false }) {
            LazyColumn(Modifier.padding(horizontal = 8.dp)) {
                items(chapters.size) { i ->
                    val c = chapters[i]
                    TextButton(onClick = { index = i; showChapters = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Том ${c.volume} Глава ${c.number}" + if (c.name.isNotEmpty()) " · ${c.name}" else "",
                            Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            fontWeight = if (i == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (ReadStore.isRead(title.readKey, c.id) && i != index) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Чтение", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Размер шрифта: ${fontSize.toInt()}", Modifier.weight(1f))
                    OutlinedButton(onClick = { fontSize = (fontSize - 2f).coerceAtLeast(12f); KV.putFloat("reader.font", fontSize) }) { Text("А−") }
                    Box(Modifier.width(8.dp))
                    OutlinedButton(onClick = { fontSize = (fontSize + 2f).coerceAtMost(34f); KV.putFloat("reader.font", fontSize) }) { Text("А+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Шрифт с засечками", Modifier.weight(1f))
                    Switch(checked = serif, onCheckedChange = { serif = it; KV.putBool("reader.serif", it) })
                }
                Text("Режим")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, l) in listOf("pages" to "Страницы", "scroll" to "Прокрутка")) {
                        FilterChip(selected = Prefs.novelMode == k, onClick = {
                            Prefs.selectNovelMode(k)
                            chromeHidden = k == "pages"
                        }, label = { Text(l) })
                    }
                }
                Text("Листание")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, l) in listOf("slide" to "Сдвиг", "fade" to "Плавно", "book" to "Как в книге")) {
                        FilterChip(selected = Prefs.novelAnim == k, onClick = { Prefs.selectNovelAnim(k) }, label = { Text(l) })
                    }
                }
                Text("Интервал")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((v, l) in listOf(0.25f to "Плотный", 0.4f to "Обычный", 0.6f to "Свободный")) {
                        FilterChip(selected = lineGap == v, onClick = { lineGap = v; KV.putFloat("reader.lh", v) }, label = { Text(l) })
                    }
                }
                Text("Тема")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, l) in listOf("auto" to "Как в системе", "dark" to "Тёмная", "sepia" to "Сепия", "light" to "Светлая")) {
                        FilterChip(selected = theme == k, onClick = { theme = k; KV.putString("reader.theme", k) }, label = { Text(l) })
                    }
                }
                chapters.getOrNull(index)?.let { cur ->
                    if (OfflineStore.isDownloaded(title.id, cur.id)) {
                        TextButton(onClick = { OfflineStore.remove(title.id, cur.id) }) { Text("Удалить загрузку главы") }
                    } else {
                        TextButton(onClick = { DownloadManager.enqueue(title, chapters, listOf(cur)) }) { Text("Скачать главу") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollBody(
    title: String,
    blocks: List<Block>,
    loading: Boolean,
    error: String?,
    fontSize: Float,
    serif: Boolean,
    lineGap: Float,
    colors: ReaderColors,
    hasNext: Boolean,
    onNext: () -> Unit,
    onTap: () -> Unit,
    key: Int,
) {
    val family = if (serif) FontFamily.Serif else FontFamily.SansSerif
    val lh = (fontSize * (1.2f + lineGap)).sp
    val words = remember(blocks) {
        blocks.sumOf { b ->
            when (b) {
                is Block.Para -> b.runs.sumOf { it.text.split(" ").size }
                is Block.Heading -> b.text.split(" ").size
                is Block.Quote -> b.text.split(" ").size
                else -> 0
            }
        }
    }
    val minutes = maxOf(1, Math.round(words / 180.0).toInt())
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(key) { listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onTap() } },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 88.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy((fontSize * (0.5f + lineGap)).dp),
    ) {
        item {
            Text(title, color = colors.fg, fontFamily = family, fontWeight = FontWeight.Bold, fontSize = (fontSize + 8).sp, lineHeight = (fontSize + 16).sp)
        }
        if (!loading && blocks.isNotEmpty()) {
            item { Text("≈ $minutes мин чтения", color = colors.fg.copy(alpha = 0.55f), style = MaterialTheme.typography.labelMedium) }
        }
        if (loading) {
            item { Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.fg) } }
        }
        items(blocks.size) { i ->
            when (val b = blocks[i]) {
                is Block.Para -> Text(
                    buildAnnotatedString {
                        for (r in b.runs) {
                            withStyle(
                                SpanStyle(
                                    fontWeight = if (r.bold) FontWeight.Bold else null,
                                    fontStyle = if (r.italic) FontStyle.Italic else null,
                                ),
                            ) { append(r.text) }
                        }
                    },
                    color = colors.fg, fontFamily = family, fontSize = fontSize.sp, lineHeight = lh,
                )
                is Block.Heading -> Text(b.text, color = colors.fg, fontFamily = family, fontWeight = FontWeight.Bold, fontSize = (fontSize + 4).sp)
                is Block.Quote -> {
                    val accent = MaterialTheme.colorScheme.primary
                    Text(
                        b.text,
                        Modifier.padding(start = 12.dp).drawBehind {
                            drawRect(accent, topLeft = Offset(-12.dp.toPx(), 0f), size = Size(3.dp.toPx(), size.height))
                        },
                        color = colors.fg, fontFamily = family, fontStyle = FontStyle.Italic, fontSize = fontSize.sp, lineHeight = lh,
                    )
                }
                is Block.Image -> AsyncImage(
                    model = b.url,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                )
                is Block.Rule -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(colors.fg.copy(alpha = 0.2f)).padding(0.5.dp))
            }
        }
        if (error != null) item { Text(error, color = Color(0xFFFFA726)) }
        if (!loading && hasNext) {
            item {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(50)) {
                    Text("Следующая глава")
                }
            }
        }
    }
}

/** Кнопка скачивания главы в списке глав. */
@Composable
fun ChapterDownloadButton(title: Title, chapter: Chapter, all: List<Chapter>) {
    val saved = OfflineStore.isDownloaded(title.id, chapter.id)
    val queued = chapter.id in DownloadManager.pending
    val failed = DownloadManager.failed.containsKey(chapter.id)
    val label = when {
        saved -> "✓"
        queued -> if (DownloadManager.current == chapter.id) "…" else "⏳"
        failed -> "!"
        else -> "⬇"
    }
    TextButton(onClick = {
        if (queued) DownloadManager.cancel(chapter.id)
        else if (!saved) DownloadManager.enqueue(title, all, listOf(chapter))
    }) { Text(label) }
}
