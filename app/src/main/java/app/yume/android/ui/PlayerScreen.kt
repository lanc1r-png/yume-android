@file:OptIn(ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package app.yume.android.ui

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import app.yume.android.Prefs
import app.yume.android.Route
import app.yume.android.data.Api
import app.yume.android.data.HistoryStore
import app.yume.android.data.Kodik
import app.yume.android.data.PlayableVariant
import app.yume.android.data.ProgressStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val kodikHeaders = mapOf(
    "Referer" to "https://kodikplayer.com/",
    "Origin" to "https://kodikplayer.com",
    "User-Agent" to Kodik.UA,
)

private fun qualityNumber(title: String): Int = title.dropLast(1).toIntOrNull() ?: 0

private fun buildPlayer(ctx: android.content.Context, headers: Map<String, String>): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(headers["User-Agent"] ?: Api.UA)
        .setAllowCrossProtocolRedirects(true)
        .setDefaultRequestProperties(headers.filterKeys { it != "User-Agent" })
    return ExoPlayer.Builder(ctx)
        .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(http))
        .build()
}

@Composable
fun PlayerScreen(route: Route.Player, onClose: () -> Unit) {
    val title = route.title
    val episodes = route.episodes
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var index by remember { mutableIntStateOf(route.start.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))) }
    var variants by remember { mutableStateOf<List<PlayableVariant>>(emptyList()) }
    var current by remember { mutableStateOf<PlayableVariant?>(null) }
    var qualities by remember { mutableStateOf<List<PlayableVariant>>(emptyList()) }
    var currentQuality by remember { mutableStateOf<PlayableVariant?>(null) }
    var showWeb by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var voiceMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var lastQualityTitle by remember { mutableStateOf<String?>(null) }
    var lastVoice by remember { mutableStateOf<String?>(null) }
    var lastWebStream by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf<List<Pair<String, Map<String, String>>>>(emptyList()) }
    var attemptIndex by remember { mutableIntStateOf(0) }
    var subtitle by remember { mutableStateOf<String?>(null) }
    var startAt by remember { mutableStateOf(0.0) }

    val episode = episodes.getOrNull(index)

    fun savePosition() {
        val p = player ?: return
        val ep = episode ?: return
        val dur = p.duration
        if (dur > 0 && p.currentPosition >= 0) ProgressStore.update(ep.id, p.currentPosition / 1000.0, dur / 1000.0)
    }

    fun releasePlayer() {
        savePosition()
        player?.release()
        player = null
    }

    var pickRef: (PlayableVariant) -> Unit = {}

    fun startAV(url: String, headers: Map<String, String>) {
        player?.release()
        val p = buildPlayer(ctx, headers)
        val item = MediaItem.Builder().setUri(url).apply {
            subtitle?.let { sub ->
                val mime = if (sub.lowercase().substringBefore("?").endsWith(".srt")) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT
                setSubtitleConfigurations(
                    listOf(MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub)).setMimeType(mime).setLanguage("ru").setSelectionFlags(0).build()),
                )
            }
        }.build()
        p.setMediaItem(item, (startAt * 1000).toLong())
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (player !== p) return
                if (attemptIndex + 1 < attempts.size) {
                    attemptIndex++
                    val (u, h) = attempts[attemptIndex]
                    startAt = p.currentPosition / 1000.0
                    startAV(u, h)
                } else {
                    // пробуем качество пониже
                    val cq = currentQuality
                    val lower = qualities.filter { cq != null && qualityNumber(it.title) < qualityNumber(cq.title) }
                        .maxByOrNull { qualityNumber(it.title) }
                    if (lower != null) {
                        startAt = p.currentPosition / 1000.0
                        pickRef(lower)
                    } else {
                        playbackError = "Видео не открылось: ${error.errorCodeName}"
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) playbackError = null
                if (state == Player.STATE_ENDED && player === p) {
                    val ep = episode
                    val dur = p.duration
                    if (ep != null && dur > 0) ProgressStore.update(ep.id, dur / 1000.0, dur / 1000.0)
                    if (Prefs.autoNext && index + 1 < episodes.size) index++
                }
            }
        })
        p.prepare()
        p.playWhenReady = true
        player = p
    }

    fun playNative(q: PlayableVariant) {
        val sets = listOf(
            Api.playbackHeaders,
            mapOf("Referer" to "https://animelib.org/", "Origin" to "https://animelib.org"),
            emptyMap(),
        )
        val list = ArrayList<Pair<String, Map<String, String>>>()
        for (u in listOf(q.url) + q.alts) for (h in sets) list.add(u to h)
        attempts = list
        attemptIndex = 0
        playbackError = null
        startAV(list[0].first, list[0].second)
    }

    fun pickQuality(q: PlayableVariant) {
        currentQuality = q
        lastQualityTitle = q.title
        if (current?.isWeb == true) {
            attempts = listOf(q.url to kodikHeaders)
            attemptIndex = 0
            startAV(q.url, kodikHeaders)
        } else {
            playNative(q)
        }
    }

    pickRef = ::pickQuality

    fun kodikDirect(v: PlayableVariant) {
        scope.launch {
            resolving = true
            try {
                val links = Kodik.resolve(v.url)
                if (current?.uid != v.uid) return@launch
                showWeb = false
                qualities = links.map { PlayableVariant("${it.first}p", it.second) }
                qualities.firstOrNull()?.let { pickQuality(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (current?.uid == v.uid) {
                    note = "Прямой поток не получен (${e.message}). Открываю веб-плеер."
                    showWeb = true
                }
            } finally {
                resolving = false
            }
        }
    }

    fun select(v: PlayableVariant) {
        note = null
        showWeb = false
        qualities = emptyList()
        current = v
        lastVoice = v.title
        subtitle = v.subtitle
        if (v.kodik) {
            releasePlayer()
            if (Prefs.kodikDirect) kodikDirect(v) else showWeb = true
        } else if (v.isWeb) {
            releasePlayer()
            showWeb = true
        } else if (v.options.isNotEmpty()) {
            qualities = v.options
            // сначала не выше 1080p: 2160p часто в формате, который устройство не показывает
            val q = v.options.firstOrNull { it.title == lastQualityTitle }
                ?: v.options.firstOrNull { qualityNumber(it.title) <= 1080 }
                ?: v.options[0]
            pickQuality(q)
        } else {
            attempts = listOf(v.url to Api.playbackHeaders)
            attemptIndex = 0
            startAV(v.url, Api.playbackHeaders)
        }
    }

    // загрузка серии
    LaunchedEffect(index) {
        val ep = episodes.getOrNull(index) ?: return@LaunchedEffect
        loading = true
        error = null
        releasePlayer()
        variants = emptyList()
        current = null
        qualities = emptyList()
        showWeb = false
        note = null
        playbackError = null
        lastWebStream = null
        ProgressStore.opened(ep.id)
        HistoryStore.recordEpisode(title, ep)
        startAt = if (Prefs.resume) ProgressStore.position(ep.id).let { p ->
            val pr = ProgressStore.get(ep.id)
            if (pr != null && pr.finished) 0.0 else p
        } else 0.0
        try {
            val r = Api.variants(ep.id)
            variants = r.variants
            val first = r.variants.firstOrNull { it.title == lastVoice } ?: r.variants.firstOrNull()
            first?.let { select(it) }
            if (r.note.isNotEmpty()) note = r.note
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить видео"
        }
        loading = false
    }

    // сохраняем позицию каждые 5 секунд
    LaunchedEffect(player) {
        while (player != null) {
            delay(5000)
            savePosition()
        }
    }

    // нет картинки при наличии звука: понижаем качество
    LaunchedEffect(player, currentQuality) {
        val p = player ?: return@LaunchedEffect
        delay(4500)
        if (player === p && p.playbackState == Player.STATE_READY && p.videoSize.width == 0) {
            val cq = currentQuality ?: return@LaunchedEffect
            val lower = qualities.filter { qualityNumber(it.title) < qualityNumber(cq.title) }.maxByOrNull { qualityNumber(it.title) }
            if (lower != null) {
                startAt = p.currentPosition / 1000.0
                pickQuality(lower)
            }
        }
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            savePosition()
            player?.release()
            player = null
            val act = ctx.findActivity()
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            act?.window?.let { WindowCompat.getInsetsController(it, view) }?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(fullscreen) {
        val act = ctx.findActivity()
        val controller = act?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullscreen) {
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }

    BackHandler(enabled = fullscreen) { fullscreen = false }

    @Composable
    fun Video(modifier: Modifier) {
        Box(modifier.background(Color.Black)) {
            val p = player
            val web = current?.takeIf { it.isWeb && showWeb }
            when {
                web != null -> KodikWeb(
                    url = web.url,
                    onStream = { lastWebStream = it },
                    onFullscreen = {
                        val s = lastWebStream
                        if (s != null) {
                            showWeb = false
                            qualities = listOf(PlayableVariant("поток", s))
                            attempts = listOf(s to kodikHeaders)
                            attemptIndex = 0
                            startAt = 0.0
                            startAV(s, kodikHeaders)
                            true
                        } else {
                            false
                        }
                    },
                )
                p != null -> AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            useController = true
                            setShowSubtitleButton(true)
                            setFullscreenButtonClickListener { fullscreen = !fullscreen }
                        }
                    },
                    update = { v -> if (v.player !== p) v.player = p },
                    modifier = Modifier.fillMaxSize(),
                )
                loading || resolving -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            playbackError?.let {
                Text(it, Modifier.align(Alignment.Center).padding(16.dp), color = Color(0xFFFFA726))
            }
        }
    }

    if (fullscreen) {
        Video(Modifier.fillMaxSize())
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Закрыть") }
            Column(Modifier.weight(1f)) {
                Text(title.displayTitle, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                Text(episode?.title ?: "", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Video(Modifier.fillMaxWidth().aspectRatio(16f / 9f))

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            if (variants.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick = { voiceMenu = true }) {
                            Text(current?.title ?: "Озвучка", maxLines = 1)
                        }
                        DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                            for (v in variants) {
                                DropdownMenuItem(
                                    text = { Text((if (v.uid == current?.uid) "✓ " else "") + v.title) },
                                    onClick = {
                                        voiceMenu = false
                                        savePosition()
                                        select(v)
                                    },
                                )
                            }
                        }
                    }
                    if (qualities.size > 1) {
                        Box {
                            OutlinedButton(onClick = { qualityMenu = true }) { Text(currentQuality?.title ?: "Качество") }
                            DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                                for (q in qualities) {
                                    DropdownMenuItem(
                                        text = { Text((if (q.uid == currentQuality?.uid) "✓ " else "") + q.title) },
                                        onClick = {
                                            qualityMenu = false
                                            startAt = (player?.currentPosition ?: 0L) / 1000.0
                                            pickQuality(q)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { if (index > 0) index-- }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Предыдущая") }
                OutlinedButton(onClick = { if (index + 1 < episodes.size) index++ }, enabled = index + 1 < episodes.size, modifier = Modifier.weight(1f)) { Text("Следующая") }
            }
            current?.takeIf { it.kodik && showWeb }?.let { v ->
                OutlinedButton(onClick = { kodikDirect(v) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (resolving) "Получаю поток…" else "Если видео чёрное: открыть прямым потоком")
                }
            }
            OutlinedButton(onClick = { showComments = true }, modifier = Modifier.fillMaxWidth()) { Text("Комментарии к серии") }
        }
    }

    if (showComments && episode != null) {
        CommentsSheet(title = "Комментарии к серии", postId = episode.id, postType = "episode", onDismiss = { showComments = false })
    }
}

/** Веб-плеер: страница Kodik встраивается как iframe на «странице сайта», так же делает сам сайт. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KodikWeb(url: String, onStream: (String) -> Unit, onFullscreen: () -> Boolean) {
    var webError by remember { mutableStateOf<String?>(null) }
    val onStreamState = androidx.compose.runtime.rememberUpdatedState(onStream)
    val onFsState = androidx.compose.runtime.rememberUpdatedState(onFullscreen)
    var custom by remember { mutableStateOf<View?>(null) }
    val web = remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            web.value?.let {
                it.stopLoading()
                it.loadUrl("about:blank")
                it.destroy()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = Kodik.UA
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(v: WebView?, r: WebResourceRequest?, e: android.webkit.WebResourceError?) {
                            if (r?.isForMainFrame == true) webError = "Веб-плеер: ${e?.description}"
                        }

                        override fun onReceivedHttpError(v: WebView?, r: WebResourceRequest?, e: WebResourceResponse?) {
                            if (r?.url?.toString() == url) webError = "Веб-плеер: ответ ${e?.statusCode}"
                        }

                        override fun shouldInterceptRequest(v: WebView?, r: WebResourceRequest?): WebResourceResponse? {
                            val u = r?.url?.toString()
                            if (u != null && (u.contains(".m3u8") || u.contains(".mp4")) && !u.contains(".ts?")) {
                                v?.post { onStreamState.value(u) }
                            }
                            return null
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (onFsState.value()) {
                                callback?.onCustomViewHidden()
                            } else {
                                custom = view
                            }
                        }

                        override fun onHideCustomView() {
                            custom = null
                        }
                    }
                    val html = """
                        <!doctype html><html><head>
                        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                        <style>html,body{margin:0;height:100%;background:#000}
                        iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>
                        </head><body>
                        <iframe src="$url" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen></iframe>
                        </body></html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://animelib.org/", html, "text/html", "utf-8", null)
                    web.value = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        webError?.let {
            Text(it, Modifier.align(Alignment.Center).padding(16.dp), color = Color(0xFFFFA726))
        }
        custom?.let { v ->
            AndroidView(
                factory = { c ->
                    FrameLayout(c).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        (v.parent as? android.view.ViewGroup)?.removeView(v)
                        addView(v)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
