@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.yume.android.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.yume.android.Nav
import app.yume.android.Route
import app.yume.android.data.AuthStore
import app.yume.android.data.BookmarkFolder
import app.yume.android.data.Friend
import app.yume.android.data.FriendParser
import app.yume.android.data.FriendsModel
import app.yume.android.data.LocalFriends
import app.yume.android.data.Site
import app.yume.android.data.Title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun OnlineDot(online: Boolean?, modifier: Modifier = Modifier) {
    Box(
        modifier.size(12.dp).clip(CircleShape)
            .background(if (online == true) Color(0xFF40CC7A) else Color(0xFF8A8A8A)),
    )
}

@Composable
fun FriendsScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var addText by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    val uid = AuthStore.user?.id

    LaunchedEffect(uid) { if (uid != null) FriendsModel.load(uid) }

    if (syncing && uid != null) {
        FriendsSyncScreen(uid, onClose = { syncing = false })
        return
    }

    val list = FriendsModel.merged(FriendsModel.friends.toList(), LocalFriends.items.toList())
    Column(Modifier.fillMaxSize()) {
        OneHeader("Друзья", onClose) {
            TextButton(onClick = { syncing = true }) { Text("Синхронизировать") }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = addText, onValueChange = { addText = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("ID или ссылка на профиль") }, singleLine = true,
            )
            TextButton(onClick = {
                val id = FriendParser.userId(addText)
                if (id == null) {
                    addError = "Не нашёл ID в тексте"
                } else {
                    scope.launch {
                        val f = FriendParser.detail(id)
                        if (f == null) addError = "Профиль не найден" else {
                            if (!LocalFriends.contains(id)) LocalFriends.toggle(f)
                            addText = ""
                            addError = null
                        }
                    }
                }
            }) { Text("Добавить") }
        }
        addError?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (FriendsModel.loading) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        if (list.isEmpty() && !FriendsModel.loading) {
            CenterMessage("Друзей пока нет. Нажмите «Синхронизировать» или добавьте по ID.")
            if (FriendsModel.debug.isNotEmpty()) {
                Text(FriendsModel.debug, Modifier.padding(16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { Nav.push(Route.FriendProfile(f)) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box {
                        RemoteImage(f.avatar, Modifier.size(48.dp).clip(CircleShape))
                        OnlineDot(f.online, Modifier.align(Alignment.BottomEnd))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(f.username, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (f.statusText.isNotEmpty()) Text(f.statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    f.level?.let { Text("ур. $it", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

private const val SNIFF = """
(function(){
  if (window.__hehe2) return; window.__hehe2 = true;
  function take(u, t){
    try {
      if (!/friend/i.test(String(u))) return;
      if (/request|invit|incoming|outgoing|sent/i.test(String(u))) return;
      window.hfriends.post(String(u), String(t).slice(0, 3000000));
    } catch (e) {}
  }
  var of = window.fetch;
  if (of) window.fetch = function(a, o){
    var u = typeof a === 'string' ? a : (a && a.url);
    var p = of.apply(this, arguments);
    p.then(function(r){ try { r.clone().text().then(function(t){ take(u, t); }); } catch (e) {} }).catch(function(){});
    return p;
  };
  var oo = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function(m, u){
    var x = this; x.__u = u;
    x.addEventListener('loadend', function(){
      try {
        var t = (x.responseType === '' || x.responseType === 'text') ? x.responseText : JSON.stringify(x.response);
        take(x.__u, t);
      } catch (e) {}
    });
    return oo.apply(this, arguments);
  };
  var tries = 0, clicked = false;
  var iv = setInterval(function(){
    tries++; if (tries > 30) { clearInterval(iv); return; }
    if (!clicked) {
      var el = document.querySelector('a[href*="friends"]');
      if (!el) {
        var all = document.querySelectorAll('a,button,li,[role=tab],span,div');
        for (var i = 0; i < all.length; i++) {
          var t = (all[i].children.length === 0 ? all[i].textContent : '').trim();
          if (t === 'Список друзей' || t === 'Друзья') { el = all[i]; break; }
        }
      }
      if (el) { el.click(); clicked = true; }
    } else {
      window.scrollTo(0, document.body.scrollHeight);
      var se = document.scrollingElement; if (se) se.scrollTop = se.scrollHeight;
    }
  }, 1000);
})();
"""

/** Синхронизация друзей: открываем профиль на сайте и перехватываем ответ со списком друзей. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FriendsSyncScreen(userId: Int, onClose: () -> Unit) {
    var found by remember { mutableStateOf(0) }
    var lastHit by remember { mutableStateOf(System.currentTimeMillis()) }
    val host = when (Site.current) {
        Site.RANOBE -> "https://ranobelib.me"
        Site.MANGA -> "https://mangalib.me"
        else -> "https://animelib.org"
    }
    LaunchedEffect(Unit) {
        FriendsModel.beginSync()
        while (true) {
            delay(1000)
            if (found > 0 && System.currentTimeMillis() - lastHit > 5000) {
                onClose()
                break
            }
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Column(Modifier.weight(1f)) {
                Text(if (found == 0) "Ищу список друзей на сайте…" else "Найдено друзей: $found", fontWeight = FontWeight.SemiBold)
                Text(
                    if (found == 0) "Если сайт попросит войти, войдите. Если список сам не открылся, нажмите на нём «Друзья»." else "Окно закроется само.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClose) { Text("Готово") }
        }
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = app.yume.android.data.Kodik.UA
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun post(u: String, t: String) {
                            val list = FriendParser.parseList(t)
                            if (list.isNullOrEmpty()) return
                            post {
                                FriendsModel.absorb(list, u)
                                found = FriendsModel.friends.size
                                lastHit = System.currentTimeMillis()
                            }
                        }
                    }, "hfriends")
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(v: WebView?, url: String?, f: android.graphics.Bitmap?) {
                            v?.evaluateJavascript(SNIFF, null)
                        }

                        override fun onPageFinished(v: WebView?, url: String?) {
                            v?.evaluateJavascript(SNIFF, null)
                        }
                    }
                    loadUrl("$host/ru/user/$userId")
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.destroy() },
        )
    }
}

@Composable
fun FriendProfileScreen(route: Route.FriendProfile, onClose: () -> Unit) {
    var friend by remember { mutableStateOf(route.friend) }
    var folders by remember { mutableStateOf<List<BookmarkFolder>>(emptyList()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val items = remember { androidx.compose.runtime.mutableStateListOf<Title>() }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val site = Site.current

    LaunchedEffect(route.friend.id) {
        FriendParser.detail(friend.id)?.let { friend = friend.merge(it) }
        try {
            val all = AuthStore.folders(friend.id)
            folders = all.filter { (it.count ?: 1) > 0 }.ifEmpty { all }
            selected = folders.firstOrNull()?.id
        } catch (e: Exception) {
            error = e.message
        }
    }
    LaunchedEffect(selected) {
        val s = selected ?: return@LaunchedEffect
        items.clear()
        loading = true
        try {
            var page = 1
            while (page <= 3) {
                val got = AuthStore.bookmarks(s, page, friend.id).filter { n -> items.none { it.id == n.id } }
                if (got.isEmpty()) break
                items.addAll(got)
                page++
            }
            error = null
        } catch (e: Exception) {
            error = "Списки недоступны: ${e.message}"
        }
        loading = false
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackCircle(onClose)
                }
                Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(20.dp))) { RemoteImage(friend.cover, Modifier.fillMaxSize()) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box {
                        RemoteImage(friend.avatar, Modifier.size(64.dp).clip(CircleShape))
                        OnlineDot(friend.online, Modifier.align(Alignment.BottomEnd))
                    }
                    Column {
                        Text(friend.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val bits = ArrayList<String>()
                        if (friend.statusText.isNotEmpty()) bits.add(friend.statusText)
                        friend.level?.let { bits.add("Уровень $it") }
                        friend.streak?.let { bits.add("Серия: $it") }
                        if (friend.premium) bits.add("Premium")
                        Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                var inList by remember { mutableStateOf(LocalFriends.contains(friend.id)) }
                OutlinedButton(onClick = { LocalFriends.toggle(friend); inList = !inList }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (inList) "В моём списке друзей" else "Добавить в свой список")
                }
                Text("Списки · ${site.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (f in folders) {
                        FilterChip(selected = selected == f.id, onClick = { selected = f.id }, label = { Text(f.name + (f.count?.let { " $it" } ?: "")) })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        items(items, key = { it.id }) { t -> GridCard(t, onClick = { Nav.push(Route.Detail(t)) }) }
        if (loading) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}
