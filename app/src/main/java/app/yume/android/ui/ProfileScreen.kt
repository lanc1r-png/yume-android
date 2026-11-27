@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.yume.android.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import app.yume.android.data.FolderStyles
import app.yume.android.data.ProfileLook
import app.yume.android.data.BookmarkFolder
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.yume.android.Nav
import app.yume.android.Prefs
import app.yume.android.Route
import app.yume.android.data.ApiException
import app.yume.android.data.AuthStore
import app.yume.android.data.BookmarkFolder
import app.yume.android.data.Site
import app.yume.android.data.Title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class ProfileModel {
    val items = mutableStateListOf<Title>()
    var folders by mutableStateOf<List<BookmarkFolder>>(emptyList())
    var selected by mutableStateOf<Int?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var page = 1
    private var finished = false

    suspend fun loadFolders() {
        try {
            val all = AuthStore.folders()
            val nonEmpty = all.filter { (it.count ?: 1) > 0 }
            folders = nonEmpty.ifEmpty { all }
            if (selected == null || folders.none { it.id == selected }) selected = folders.firstOrNull()?.id
            error = null
            reload()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить списки"
        }
    }

    suspend fun select(id: Int) {
        selected = id
        reload()
    }

    private suspend fun reload() {
        items.clear()
        page = 1
        finished = false
        loadMore()
    }

    suspend fun loadMore() {
        if (loading || finished) return
        loading = true
        try {
            val list = AuthStore.bookmarks(selected, page)
            val known = items.map { it.id }.toSet()
            val fresh = list.filter { it.id !in known }
            items.addAll(fresh)
            page++
            if (fresh.isEmpty()) finished = true
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            finished = true
            error = e.message ?: "Не удалось загрузить список"
        } finally {
            loading = false
        }
    }
}

@Composable
fun ProfileScreen() {
    val token = AuthStore.token
    val user = AuthStore.user
    val site = Site.of(Prefs.platform)
    val scope = rememberCoroutineScope()

    if (token == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Профиль", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Войдите через сайт, чтобы видеть свои списки, добавлять тайтлы и писать комментарии.",
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { Nav.push(Route.Login()) }, shape = RoundedCornerShape(16.dp)) { Text("Войти через сайт") }
            AuthStore.error?.let {
                Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
            }
        }
        return
    }

    val model = remember(site, user?.id) { ProfileModel() }
    LaunchedEffect(token, user?.id) {
        if (user == null) AuthStore.refreshUser()
    }
    LaunchedEffect(model, user?.id) {
        if (user != null) model.loadFolders()
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, model) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (model.items.isNotEmpty() && last >= model.items.size - 3) model.loadMore()
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(20.dp))) {
                    val hm = ProfileLook.activeHeader
                    if (hm != null) ProfileMediaView(hm, Modifier.fillMaxSize()) else RemoteImage(user?.cover, Modifier.fillMaxSize())
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val am = ProfileLook.activeAvatar
                    if (am != null) ProfileMediaView(am, Modifier.size(64.dp).clip(CircleShape))
                    else RemoteImage(user?.avatar, Modifier.size(64.dp).clip(CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(user?.username ?: "Профиль", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val bits = ArrayList<String>()
                        user?.level?.let { bits.add("Уровень $it") }
                        user?.streak?.let { bits.add("Серия входов: $it") }
                        if (user?.premium == true) bits.add("Premium")
                        if (bits.isNotEmpty()) {
                            Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedButton(onClick = { AuthStore.signOut() }) { Text("Выйти") }
                }
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val total = model.folders.sumOf { it.count ?: 0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (total > 0) {
                        Text("Библиотека: $total тайтлов", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    } else {
                        Box(Modifier.weight(1f))
                    }
                    user?.let { u ->
                        OutlinedButton(onClick = {
                            val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "${u.username}: https://animelib.org/ru/user/${u.id}")
                            }
                            ctx.startActivity(android.content.Intent.createChooser(i, "Поделиться профилем"))
                        }) { Text("Поделиться профилем") }
                    }
                }
                var lookSheet by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Nav.push(Route.Friends()) }, modifier = Modifier.weight(1f)) { Text("Друзья") }
                    OutlinedButton(onClick = { lookSheet = true }, modifier = Modifier.weight(1f)) { Text("Оформление") }
                }
                if (lookSheet) ProfileAppearanceSheet(onClose = { lookSheet = false })
                var styleFolder by remember { mutableStateOf<BookmarkFolder?>(null) }
                styleFolder?.let { FolderStyleSheet(it, onClose = { styleFolder = null }) }
                ProfileShowcase(total)
                Text("Мои списки · ${site.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (f in model.folders) {
                        val st = FolderStyles.style(f.id)
                        val base = st?.hex?.let { parseHex(it) }
                        val sel = model.selected == f.id
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        base != null -> base.copy(alpha = if (sel) 0.9f else 0.35f)
                                        sel -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        else -> MaterialTheme.colorScheme.surfaceContainer
                                    },
                                )
                                .combinedClickable(onClick = { scope.launch { model.select(f.id) } }, onLongClick = { styleFolder = f })
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text((st?.emoji?.let { "$it " } ?: "") + f.name + (f.count?.let { " $it" } ?: ""), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Text("Долгое нажатие на список — цвет и эмодзи", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                AuthStore.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        items(model.items, key = { it.id }) { t -> GridCard(t, onClick = { Nav.push(Route.Detail(t)) }) }
        if (model.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        } else if (model.items.isEmpty() && model.error == null && user != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { CenterMessage("В этом списке пока пусто") }
        }
    }
}

/** Вход через сайт: ждём, пока сайт запишет токен в localStorage, и забираем его. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onClose: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(web) {
        val w = web ?: return@LaunchedEffect
        while (!done) {
            delay(1500)
            w.evaluateJavascript("JSON.stringify(Object.assign({}, window.localStorage))") { res ->
                if (done || res == null || res == "null") return@evaluateJavascript
                try {
                    val text = org.json.JSONTokener(res).nextValue() as? String ?: return@evaluateJavascript
                    val tok = AuthStore.findToken(org.json.JSONTokener(text).nextValue()) ?: return@evaluateJavascript
                    done = true
                    kotlinx.coroutines.MainScope().launch {
                        AuthStore.signIn(tok.first, tok.second)
                        onClose()
                    }
                } catch (e: Exception) {
                    // ждём следующей проверки
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Закрыть") }
            Text("Вход через сайт", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Войдите на сайте как обычно. Как только вход завершится, приложение подхватит его само.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = app.yume.android.data.Kodik.UA
                    setBackgroundColor(android.graphics.Color.BLACK)
                    webViewClient = WebViewClient()
                    loadUrl("https://animelib.org/ru/")
                    web = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.destroy() },
        )
    }
}
