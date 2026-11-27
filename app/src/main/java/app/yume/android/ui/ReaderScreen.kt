@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.yume.android.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import app.yume.android.data.MangaTitlePrefs
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.yume.android.Prefs
import app.yume.android.Route
import app.yume.android.data.Api
import app.yume.android.data.HistoryStore
import app.yume.android.data.KV
import app.yume.android.data.MangaPage
import app.yume.android.data.ReadStore
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private fun prefetch(ctx: Context, urls: List<String>) {
    val loader = SingletonImageLoader.get(ctx)
    for (u in urls) {
        loader.enqueue(
            ImageRequest.Builder(ctx)
                .data(u)
                .size(64, 64)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build(),
        )
    }
}

@Composable
fun ReaderScreen(route: Route.Reader, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val title = route.title
    val chapters = route.chapters

    var index by remember { mutableIntStateOf(route.start.coerceIn(0, maxOf(chapters.size - 1, 0))) }
    var pages by remember { mutableStateOf<List<MangaPage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var chromeHidden by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var shown by remember(index) { mutableIntStateOf(0) }
    var titleMode by remember { mutableStateOf(KV.getString("manga.mode.${title.id}", "auto") ?: "auto") }

    val chapter = chapters.getOrNull(index)

    var tp by remember { mutableStateOf(MangaTitlePrefs.load(title.id)) }
    fun updTp(n: MangaTitlePrefs) { tp = n; n.save(title.id) }
    val own = tp.enabled
    val effAnim = if (own) tp.anim else Prefs.mangaAnim
    val effAutoBg = if (own) tp.autoBG else Prefs.mangaAutoBg
    val effAutoNext = if (own) tp.autoNext else Prefs.mangaAutoNext
    val effSpeed = if (own) tp.speed else Prefs.autoSpeed
    val effDim = if (own) tp.dim else Prefs.fDim
    val effWarm = if (own) tp.warm else Prefs.fWarm
    val effGray = if (own) tp.gray else Prefs.fGray
    var autoOn by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }

    val autoVertical = run {
        val t = (title.type?.title ?: "").lowercase()
        if (t.contains("манхв") || t.contains("маньхуа") || t.contains("веб")) {
            true
        } else {
            val r = pages.map { it.ratio }.filter { it > 0f }.sorted()
            if (r.isEmpty()) false else r[r.size / 2] > 1.9f
        }
    }
    val vertical = when (if (titleMode == "auto") Prefs.mangaMode else titleMode) {
        "scroll" -> true
        "pages" -> false
        else -> autoVertical
    }

    LaunchedEffect(index, reloadTick) {
        val ch = chapters.getOrNull(index) ?: return@LaunchedEffect
        val slug = title.slugUrl ?: return@LaunchedEffect
        loading = true
        error = null
        pages = emptyList()
        ReadStore.mark(title.readKey, ch.id)
        app.yume.android.data.SiteSync.view(title.id, ch.id)
        HistoryStore.recordChapter(title, ch.id, ch.title)
        try {
            val result = Api.pages(slug, ch)
            pages = result
            if (result.isEmpty()) error = "У главы нет страниц или она недоступна."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить главу"
        }
        loading = false
    }

    // фоновая предзагрузка: несколько страниц вперёд от текущей
    LaunchedEffect(shown, pages) {
        if (pages.isEmpty()) return@LaunchedEffect
        val from = shown.coerceIn(0, pages.size - 1)
        val urls = (from until minOf(from + 5, pages.size)).map { pages[it].urls.first() }
        prefetch(ctx, urls)
    }

    // системные панели скрываются вместе с интерфейсом читалки
    DisposableEffect(chromeHidden) {
        val window = ctx.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeHidden) controller?.hide(WindowInsetsCompat.Type.systemBars())
        else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    DisposableEffect(Unit) {
        val window = ctx.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars ?: false
        val oldNav = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        view.keepScreenOn = Prefs.keepAwake
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            controller?.isAppearanceLightStatusBars = oldStatus
            controller?.isAppearanceLightNavigationBars = oldNav
        }
    }

    fun go(delta: Int) {
        val n = index + delta
        if (n in chapters.indices) index = n
    }

    val toggleChrome = { chromeHidden = !chromeHidden }

    val endCard: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (index + 1 < chapters.size) "Глава закончилась" else "Это последняя глава",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium,
            )
            if (index + 1 < chapters.size) {
                Button(onClick = { go(1) }, modifier = Modifier.padding(top = 14.dp).fillMaxWidth()) {
                    Text("Следующая глава")
                }
            }
            TextButton(onClick = { showComments = true }, modifier = Modifier.padding(top = 6.dp)) {
                Text("Комментарии к главе", color = Color.White.copy(alpha = 0.85f))
            }
        }
    }

    val readerBg = if (effAutoBg && MaterialTheme.colorScheme.background.luminance() > 0.5f) Color.White else Color.Black
    Box(Modifier.fillMaxSize().background(readerBg)) {
        if (pages.isNotEmpty()) {
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    if (effGray) {
                        drawIntoCanvas { c ->
                            val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
                            c.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
                            drawContent()
                            c.restore()
                        }
                    } else {
                        drawContent()
                    }
                },
            ) {
                key(index, vertical, effAnim) {
                    if (vertical) {
                        ScrollReader(
                            pages, startPage = shown, endCard = endCard, onPage = { shown = it }, onTap = toggleChrome,
                            auto = autoOn, speed = effSpeed,
                            onAutoEnd = { if (effAutoNext && index + 1 < chapters.size) go(1) else autoOn = false },
                        )
                    } else {
                        PagedReader(
                            pages,
                            startPage = shown,
                            anim = effAnim,
                            endCard = endCard,
                            onPage = { shown = it },
                            onCenterTap = toggleChrome,
                        )
                    }
                }
            }
            if (effWarm > 0f) Box(Modifier.fillMaxSize().background(Color(0xFFFF9500).copy(alpha = effWarm * 0.35f)))
            if (effDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = effDim * 0.75f)))
        }
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        error?.let { e ->
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(e, color = Color(0xFFFF8A80), textAlign = TextAlign.Center)
                Button(onClick = { reloadTick++ }) { Text("Повторить") }
            }
        }

        AnimatedVisibility(
            visible = !chromeHidden,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Закрыть", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(title.displayTitle, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    chapter?.let {
                        Text(
                            "Том ${it.volume} · Глава ${it.number}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (vertical) {
                    IconButton(onClick = { autoOn = !autoOn; if (autoOn) chromeHidden = true }) {
                        Icon(if (autoOn) Icons.Filled.Close else Icons.Filled.PlayArrow, "Автопрокрутка", tint = if (autoOn) Color(0xFFFFB74D) else Color.White)
                    }
                }
                IconButton(onClick = { showChapters = true }) { Icon(Icons.AutoMirrored.Filled.List, "Главы", tint = Color.White) }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.MoreVert, "Режим чтения", tint = Color.White) }
                IconButton(onClick = { showComments = true }) { Icon(Icons.Filled.Send, "Комментарии", tint = Color.White) }
            }
        }

        AnimatedVisibility(
            visible = !chromeHidden,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { go(-1) }, enabled = index > 0) {
                    Icon(Icons.Filled.KeyboardArrowLeft, "Предыдущая глава", tint = Color.White)
                }
                Text(
                    if (pages.isEmpty()) "…" else "${minOf(shown + 1, pages.size)} / ${pages.size}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
                IconButton(onClick = { go(1) }, enabled = index + 1 < chapters.size) {
                    Icon(Icons.Filled.KeyboardArrowRight, "Следующая глава", tint = Color.White)
                }
            }
        }
    }

    if (showSettings) {
        val state = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showSettings = false }, sheetState = state) {
            Column(
                Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Режим чтения", style = MaterialTheme.typography.titleLarge)
                Text("Для этого тайтла", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, label) in listOf("auto" to "Авто", "pages" to "Страницы", "scroll" to "Лента")) {
                        FilterChip(
                            selected = titleMode == k,
                            onClick = {
                                titleMode = k
                                KV.putString("manga.mode.${title.id}", k)
                            },
                            label = { Text(label) },
                        )
                    }
                }
                Text(
                    "«Страницы» для манги: листаешь свайпом. «Лента» для манхвы: прокручиваешь вниз. " +
                        "«Авто»: сейчас выбрано «${if (autoVertical) "лента" else "страницы"}».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Свои настройки для этого тайтла", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    androidx.compose.material3.Switch(
                        checked = own,
                        onCheckedChange = { on ->
                            if (on && !tp.enabled) {
                                updTp(MangaTitlePrefs(true, Prefs.mangaAnim, Prefs.mangaAutoBg, Prefs.mangaAutoNext, Prefs.autoSpeed, Prefs.fDim, Prefs.fWarm, Prefs.fGray))
                            } else updTp(tp.copy(enabled = on))
                        },
                    )
                }
                Text("Листание страниц", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, label) in listOf("slide" to "Сдвиг", "fade" to "Плавно", "book" to "Как в книге")) {
                        FilterChip(
                            selected = effAnim == k,
                            onClick = { if (own) updTp(tp.copy(anim = k)) else Prefs.selectMangaAnim(k) },
                            label = { Text(label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Фон под страницу по теме", Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = effAutoBg, onCheckedChange = { if (own) updTp(tp.copy(autoBG = it)) else Prefs.updateMangaAutoBg(it) })
                }
                Text("Фильтры", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Чёрно-белое", Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = effGray, onCheckedChange = { if (own) updTp(tp.copy(gray = it)) else Prefs.updateFGray(it) })
                }
                Text("Затемнение", style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.Slider(value = effDim, onValueChange = { if (own) updTp(tp.copy(dim = it)) else Prefs.updateFDim(it) })
                Text("Тёплый свет", style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.Slider(value = effWarm, onValueChange = { if (own) updTp(tp.copy(warm = it)) else Prefs.updateFWarm(it) })
                Text("Автопрокрутка ленты", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Скорость: ${effSpeed.toInt()}", Modifier.weight(1f))
                }
                androidx.compose.material3.Slider(
                    value = effSpeed, valueRange = 15f..240f,
                    onValueChange = { v -> val r = (v / 5).toInt() * 5f; if (own) updTp(tp.copy(speed = r)) else Prefs.updateAutoSpeed(r) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("После главы открывать следующую", Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = effAutoNext, onCheckedChange = { if (own) updTp(tp.copy(autoNext = it)) else Prefs.updateMangaAutoNext(it) })
                }
                Text("Кнопка ▶ вверху включает автопрокрутку в режиме ленты.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showChapters) {
        MangaChapterPicker(chapters, index, title.readKey, onPick = { index = it; showChapters = false }, onClose = { showChapters = false })
    }

    if (showComments && chapter != null) {
        CommentsSheet(
            title = "Глава ${chapter.number}",
            postId = chapter.id,
            postType = "chapter",
            onDismiss = { showComments = false },
        )
    }
}

@Composable
private fun PagedReader(
    pages: List<MangaPage>,
    startPage: Int,
    anim: String,
    endCard: @Composable () -> Unit,
    onPage: (Int) -> Unit,
    onCenterTap: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = startPage.coerceIn(0, pages.size), pageCount = { pages.size + 1 })
    val scope = rememberCoroutineScope()
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            onPage(it)
            zoomed = false
        }
    }

    fun onTap(x: Float) {
        if (!Prefs.tapZones) {
            onCenterTap()
            return
        }
        val cur = pagerState.currentPage
        when {
            x < 0.3f -> if (cur > 0) scope.launch { pagerState.animateScrollToPage(cur - 1) }
            x > 0.7f -> if (cur < pages.size) scope.launch { pagerState.animateScrollToPage(cur + 1) }
            else -> onCenterTap()
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !zoomed,
        beyondViewportPageCount = 1,
    ) { page ->
        val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(if (anim == "book" && offset > 0f) 1f else 0f)
                .graphicsLayer {
                    if (anim == "fade") {
                        translationX = offset * size.width
                        alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f)
                    } else if (anim == "book") {
                        // страницы стоят на месте, уходящая «переворачивается» вокруг левого края
                        translationX = offset * size.width
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        if (offset > 0f) {
                            scaleX = (1f - offset).coerceIn(0f, 1f)
                            alpha = 1f - 0.35f * offset.coerceIn(0f, 1f)
                        } else if (offset < -1f) {
                            alpha = 0f
                        }
                    }
                },
        ) {
            if (page < pages.size) {
                ZoomablePage(pages[page], onZoom = { zoomed = it }, onTap = ::onTap)
            } else {
                endCard()
            }
        }
    }
}

@Composable
private fun ZoomablePage(page: MangaPage, onZoom: (Boolean) -> Unit, onTap: (Float) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (page.ratio > 2.2f) {
            // длинная полоса: прокручивается внутри страницы
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .pointerInput(Unit) { detectTapGestures(onTap = { onTap(it.x / size.width.coerceAtLeast(1)) }) },
            ) {
                MangaPageImage(page, Modifier.fillMaxWidth(), ContentScale.FillWidth, flow = true)
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offX = 0f
                                    offY = 0f
                                } else {
                                    scale = 2.4f
                                }
                                onZoom(scale > 1f)
                            },
                            onTap = { onTap(it.x / size.width.coerceAtLeast(1)) },
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val multi = event.changes.size > 1
                                if (multi || scale > 1f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                    if (scale > 1f) {
                                        val maxX = (scale - 1f) * size.width / 2f
                                        val maxY = (scale - 1f) * size.height / 2f
                                        offX = (offX + pan.x).coerceIn(-maxX, maxX)
                                        offY = (offY + pan.y).coerceIn(-maxY, maxY)
                                    } else {
                                        offX = 0f
                                        offY = 0f
                                    }
                                    onZoom(scale > 1.01f)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offX
                        translationY = offY
                    },
            ) {
                MangaPageImage(page, Modifier.fillMaxSize(), ContentScale.Fit, flow = false, sharp = true)
            }
        }
    }
}

@Composable
private fun ScrollReader(
    pages: List<MangaPage>,
    startPage: Int,
    endCard: @Composable () -> Unit,
    onPage: (Int) -> Unit,
    onTap: () -> Unit,
    auto: Boolean = false,
    speed: Float = 60f,
    onAutoEnd: () -> Unit = {},
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage.coerceIn(0, pages.size))
    val speedNow by androidx.compose.runtime.rememberUpdatedState(speed)
    LaunchedEffect(auto) {
        if (!auto) return@LaunchedEffect
        var last = 0L
        var acc = 0f
        var stuck = 0
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            if (last != 0L) {
                acc += speedNow * (now - last) / 1_000_000_000f
                val px = acc.toInt()
                if (px >= 1) {
                    acc -= px
                    val moved = listState.scrollBy(px.toFloat())
                    if (moved < 0.5f) stuck++ else stuck = 0
                    if (stuck > 30) { onAutoEnd(); return@LaunchedEffect }
                }
            }
            last = now
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { onPage(it) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
    ) {
        items(pages, key = { it.id }) { p ->
            MangaPageImage(p, Modifier.fillMaxWidth(), ContentScale.FillWidth, flow = true)
        }
        item { Box(Modifier.fillMaxWidth().height(360.dp)) { endCard() } }
    }
}

/** Страница манги: перебирает запасные адреса и даёт повторить загрузку нажатием. */
@Composable
private fun MangaPageImage(
    page: MangaPage,
    modifier: Modifier,
    contentScale: ContentScale,
    flow: Boolean,
    sharp: Boolean = false,
) {
    var idx by remember(page.id) { mutableIntStateOf(0) }
    var attempt by remember(page.id) { mutableIntStateOf(0) }
    val ctx = LocalPlatformContext.current
    val request = remember(page.urls, idx, attempt, sharp) {
        val b = ImageRequest.Builder(ctx).data(page.urls[idx.coerceAtMost(page.urls.lastIndex)])
        // для зума страница декодируется в большом разрешении, иначе при приближении она «мылится»
        if (sharp) b.size(2000, 3200)
        b.build()
    }
    val loadingBox = Modifier.let {
        if (flow && page.ratio > 0f) it.fillMaxWidth().aspectRatio(1f / page.ratio) else it.fillMaxSize()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        onError = { if (idx < page.urls.lastIndex) idx++ },
        loading = {
            Box(loadingBox, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            }
        },
        error = {
            if (idx < page.urls.lastIndex) {
                Box(loadingBox, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                idx = 0
                                attempt++
                            })
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = Color.White.copy(alpha = 0.8f))
                    Text(
                        "Страница ${page.id + 1} не загрузилась.\nНажми, чтобы повторить.",
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}
