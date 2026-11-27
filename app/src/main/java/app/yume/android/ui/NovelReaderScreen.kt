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
import app.yume.android.data.Bookmark
import app.yume.android.data.BookmarkStore
import app.yume.android.data.Highlight
import app.yume.android.data.HighlightStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
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

private fun paintOf(fontPx: Float, serif: Boolean, color: Int, bold: Boolean = false) = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
    textSize = fontPx
    this.color = color
    val base = if (serif) Typeface.SERIF else Typeface.SANS_SERIF
    typeface = if (bold && android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(base, 600, false) else base
}

private fun layoutOf(
    text: CharSequence, widthPx: Int, fontPx: Float, serif: Boolean, lineGap: Float, color: Int,
    bold: Boolean = false, justify: Boolean = false,
): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paintOf(fontPx, serif, color, bold), widthPx)
        .setLineSpacing(fontPx * lineGap, 1f)
        .setIncludePad(false)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setJustificationMode(if (justify) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
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
    bold: Boolean = false,
    justify: Boolean = false,
    indent: Boolean = false,
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
        val layout = layoutOf(sb, widthPx, fontPx, serif, lineGap, color, bold, justify)
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
                val ps = sb.length
                for (r in b.runs) append(r.text, r.bold, r.italic)
                if (indent && sb.length > ps) sb.setSpan(LeadingMarginSpan.Standard((fontPx * 1.5f).toInt(), 0), ps, sb.length, flags)
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
    var bold by remember { mutableStateOf(KV.getBool("reader.bold", false)) }
    var margin by remember { mutableFloatStateOf(KV.getFloat("reader.margin", 24f)) }
    var justify by remember { mutableStateOf(KV.getBool("reader.justify", false)) }
    var indent by remember { mutableStateOf(KV.getBool("reader.indent", false)) }
    var dimV by remember { mutableFloatStateOf(KV.getFloat("reader.dim", 0f)) }
    var warmV by remember { mutableFloatStateOf(KV.getFloat("reader.warm", 0f)) }
    var visibleBlock by remember { mutableIntStateOf(0) }
    var pendingBlock by remember { mutableStateOf<Int?>(null) }
    var showBookmarks by remember { mutableStateOf(false) }
    var hlBlock by remember { mutableStateOf<Int?>(null) }
    val hlTick = HighlightStore.items.size + HighlightStore.items.sumOf { it.color + it.note.length }
    val bmTick = BookmarkStore.items.size

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
        app.yume.android.data.SiteSync.view(title.id, ch.id)
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
                bold = bold, justify = justify, indent = indent, margin = margin,
                highlights = remember(hlTick, index) {
                    HighlightStore.items.filter { it.titleKey == title.readKey && it.chapterID == chapters.getOrNull(index)?.id }
                        .associate { it.start to it.color }
                },
                scrollTo = pendingBlock,
                onScrolled = { pendingBlock = null },
                onVisible = { visibleBlock = it },
                onLongBlock = { hlBlock = it },
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val widthPx = with(density) { (maxWidth - (margin * 2).dp).roundToPx() }
                val heightPx = with(density) { (maxHeight - 96.dp).roundToPx() }
                val fontPx = with(density) { fontSize.sp.toPx() }
                val fgInt = colors.fg.toArgb()

                LaunchedEffect(blocks, fontSize, serif, lineGap, fgInt, widthPx, heightPx, index, bold, justify, indent) {
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
                        paginate(blocks, t, widthPx, heightPx, fontPx, serif, lineGap, fgInt, bold, justify, indent)
                    }
                    pages = result
                    pagesFor = index
                    val pb = pendingBlock
                    val target = if (pb != null && blocks.isNotEmpty()) {
                        pendingBlock = null
                        (pb.toFloat() / blocks.size * result.size).toInt()
                    } else if (oldCount > 0) (oldIndex.toFloat() / oldCount * result.size).toInt() else 0
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
                                        val layout = remember(pg, widthPx, fontPx, serif, lineGap, fgInt, bold, justify) {
                                            layoutOf(pg.text, widthPx, fontPx, serif, lineGap, fgInt, bold, justify)
                                        }
                                        Canvas(Modifier.fillMaxSize()) {
                                            drawIntoCanvas { c ->
                                                c.nativeCanvas.save()
                                                c.nativeCanvas.translate(margin.dp.toPx(), 48.dp.toPx())
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
                                            modifier = Modifier.fillMaxSize().padding(horizontal = margin.dp, vertical = 48.dp),
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

        if (warmV > 0f) Box(Modifier.fillMaxSize().background(Color(0xFFFF9500).copy(alpha = warmV * 0.3f)))
        if (dimV > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimV * 0.7f)))

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
                    val curCh = chapters.getOrNull(index)
                    val posNow = if (mode == "scroll") visibleBlock else if (pages.isNotEmpty() && blocks.isNotEmpty()) (pagerState.currentPage.toFloat() / pages.size * blocks.size).toInt().coerceIn(0, blocks.size - 1) else 0
                    val here = if (curCh != null) BookmarkStore.find(title.readKey, curCh.id, posNow - 3, posNow + 4) else null
                    @Suppress("UNUSED_VARIABLE") val tick = bmTick
                    IconButton(onClick = {
                        if (curCh == null) return@IconButton
                        if (here != null) BookmarkStore.remove(here.id) else {
                            val snip = (blocks.getOrNull(posNow) as? Block.Para)?.runs?.joinToString("") { it.text }?.take(120) ?: ""
                            BookmarkStore.add(Bookmark(titleKey = title.readKey, chapterID = curCh.id, chapterLabel = curCh.title, position = posNow, isText = true, snippet = snip))
                        }
                    }) { Text(if (here != null) "★" else "☆", style = MaterialTheme.typography.titleLarge) }
                    IconButton(onClick = { showBookmarks = true }) { Icon(Icons.AutoMirrored.Filled.List, "Закладки и заметки") }
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
        MangaChapterPicker(chapters, index, title.readKey, onPick = { index = it; showChapters = false }, onClose = { showChapters = false })
    }

    if (showBookmarks) {
        BookmarksSheet(
            titleKey = title.readKey, chapters = chapters,
            onJump = { chId, pos ->
                val ci = chapters.indexOfFirst { it.id == chId }
                if (ci >= 0) {
                    pendingBlock = pos
                    if (ci != index) index = ci
                    else if (mode == "scroll") { /* ScrollBody подхватит pendingBlock */ } else pendingBlock = null
                }
                showBookmarks = false
            },
            onClose = { showBookmarks = false },
        )
    }

    hlBlock?.let { b ->
        val ch = chapters.getOrNull(index)
        if (ch != null) HighlightDialog(
            titleKey = title.readKey, chapter = ch, block = b, blocks = blocks, onClose = { hlBlock = null },
        )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Жирный шрифт", Modifier.weight(1f))
                    Switch(checked = bold, onCheckedChange = { bold = it; KV.putBool("reader.bold", it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Выравнивание по ширине", Modifier.weight(1f))
                    Switch(checked = justify, onCheckedChange = { justify = it; KV.putBool("reader.justify", it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Красная строка", Modifier.weight(1f))
                    Switch(checked = indent, onCheckedChange = { indent = it; KV.putBool("reader.indent", it) })
                }
                Text("Поля: ${margin.toInt()}")
                androidx.compose.material3.Slider(value = margin, valueRange = 8f..48f, onValueChange = { margin = (it / 2).toInt() * 2f; KV.putFloat("reader.margin", margin) })
                Text("Затемнение")
                androidx.compose.material3.Slider(value = dimV, onValueChange = { dimV = it; KV.putFloat("reader.dim", it) })
                Text("Тёплый свет")
                androidx.compose.material3.Slider(value = warmV, onValueChange = { warmV = it; KV.putFloat("reader.warm", it) })
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
    bold: Boolean = false,
    justify: Boolean = false,
    indent: Boolean = false,
    margin: Float = 16f,
    highlights: Map<Int, Int> = emptyMap(),
    scrollTo: Int? = null,
    onScrolled: () -> Unit = {},
    onVisible: (Int) -> Unit = {},
    onLongBlock: (Int) -> Unit = {},
) {
    val family = if (serif) FontFamily.Serif else FontFamily.SansSerif
    val baseWeight = if (bold) FontWeight.SemiBold else null
    val textAlign = if (justify) androidx.compose.ui.text.style.TextAlign.Justify else androidx.compose.ui.text.style.TextAlign.Start
    val paraIndent = if (indent) androidx.compose.ui.text.style.TextIndent(firstLine = (fontSize * 1.5f).sp) else androidx.compose.ui.text.style.TextIndent.None
    fun hl(i: Int): Modifier = highlights[i]?.let { c ->
        Modifier.background(Color(Highlight.PALETTE[c.coerceIn(0, 3)]).copy(alpha = 0.35f), RoundedCornerShape(6.dp))
    } ?: Modifier
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
    LaunchedEffect(scrollTo, loading, blocks.size) {
        val t = scrollTo ?: return@LaunchedEffect
        if (loading || blocks.isEmpty()) return@LaunchedEffect
        listState.scrollToItem((t + 2).coerceAtMost(blocks.size + 1))
        onScrolled()
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { onVisible(maxOf(0, it - 2)) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onTap() } },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = margin.dp, end = margin.dp, top = 88.dp, bottom = 130.dp),
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
                    fontWeight = baseWeight, textAlign = textAlign,
                    style = androidx.compose.ui.text.TextStyle(textIndent = paraIndent),
                    modifier = Modifier.fillMaxWidth().then(hl(i)).pointerInput(i) { detectTapGestures(onLongPress = { onLongBlock(i) }, onTap = { onTap() }) },
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


/** Закладки, выделения и заметки к этому тайтлу. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheet(titleKey: Int, chapters: List<Chapter>, onJump: (Int, Int) -> Unit, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val bms = BookmarkStore.items.filter { it.titleKey == titleKey }.sortedByDescending { it.created }
    val hls = HighlightStore.items.filter { it.titleKey == titleKey }.sortedByDescending { it.created }
    var noteFor by remember { mutableStateOf<String?>(null) }
    var noteText by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Закладки и заметки", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Закладки ${bms.size}") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Выделения ${hls.size}") })
            }
            LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tab == 0) {
                    if (bms.isEmpty()) item { Text("Закладок пока нет. Нажмите значок закладки вверху в читалке.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(bms, key = { it.id }) { b ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { onJump(b.chapterID, b.position) }.padding(12.dp)) {
                            Text(b.chapterLabel, fontWeight = FontWeight.SemiBold)
                            if (b.snippet.isNotEmpty()) Text(b.snippet, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            if (b.note.isNotEmpty()) Text("✎ ${b.note}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { noteFor = b.id; noteText = b.note }) { Text("Заметка") }
                                TextButton(onClick = { BookmarkStore.remove(b.id) }) { Text("Удалить") }
                            }
                        }
                    }
                } else {
                    if (hls.isEmpty()) item { Text("Выделений пока нет. Нажмите и удерживайте абзац в режиме прокрутки.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(hls, key = { it.id }) { h ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(Highlight.PALETTE[h.color.coerceIn(0, 3)]).copy(alpha = 0.18f)).clickable { onJump(h.chapterID, h.start) }.padding(12.dp)) {
                            Text(h.chapterLabel, fontWeight = FontWeight.SemiBold)
                            Text(h.snippet, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            if (h.note.isNotEmpty()) Text("✎ ${h.note}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { HighlightStore.remove(h.id) }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }
    noteFor?.let { id ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { noteFor = null },
            title = { Text("Заметка") },
            text = { androidx.compose.material3.OutlinedTextField(noteText, { noteText = it }, Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { BookmarkStore.setNote(id, noteText); noteFor = null }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { noteFor = null }) { Text("Отмена") } },
        )
    }
}

/** Цвет и заметка для абзаца, на котором держали палец. */
@Composable
private fun HighlightDialog(titleKey: Int, chapter: Chapter, block: Int, blocks: List<Block>, onClose: () -> Unit) {
    val existing = HighlightStore.block(titleKey, chapter.id, block)
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val snippet = (blocks.getOrNull(block) as? Block.Para)?.runs?.joinToString("") { it.text } ?: ""
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Выделить абзац") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(snippet.take(160), style = MaterialTheme.typography.bodySmall, maxLines = 4)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((i, c) in Highlight.PALETTE.withIndex()) {
                        Box(
                            Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(c))
                                .clickable { HighlightStore.set(titleKey, chapter.id, chapter.title, block, snippet, i, note.ifEmpty { null }); onClose() },
                        )
                    }
                }
                androidx.compose.material3.OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Заметка") })
            }
        },
        confirmButton = {
            TextButton(onClick = { HighlightStore.set(titleKey, chapter.id, chapter.title, block, snippet, existing?.color ?: 0, note); onClose() }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { HighlightStore.remove(existing.id); onClose() }) { Text("Убрать") }
                TextButton(onClick = onClose) { Text("Отмена") }
            }
        },
    )
}
