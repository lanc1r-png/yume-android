package app.yume.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import app.yume.android.data.Chapter
import app.yume.android.data.EpisodeShort
import app.yume.android.data.Sort
import app.yume.android.data.Title
import app.yume.android.ui.AuthorScreen
import app.yume.android.ui.CatalogScreen
import app.yume.android.ui.DetailScreen
import app.yume.android.ui.FavoritesScreen
import app.yume.android.ui.FloatingBarHeight
import app.yume.android.ui.FloatingTabBar
import app.yume.android.ui.HomeScreen
import app.yume.android.ui.NovelReaderScreen
import app.yume.android.ui.PlayerScreen
import app.yume.android.ui.ProfileScreen
import app.yume.android.ui.ReaderScreen
import app.yume.android.ui.SettingsScreen

/** Экран поверх вкладок: карточка тайтла или читалка. */
sealed class Route {
    val uid: Int = Nav.nextId()

    class Detail(val title: Title) : Route()
    class Reader(val title: Title, val chapters: List<Chapter>, val start: Int) : Route()
    class Player(val title: Title, val episodes: List<EpisodeShort>, val start: Int) : Route()
    class Novel(val title: Title, val chapters: List<Chapter>, val start: Int) : Route()
    class Login : Route()
    class Friends : Route()
    class Offline : Route()
    class NewInFavorites : Route()
    class SiteLog : Route()
    class AnySite : Route()
    class Stats : Route()
    class FriendProfile(val friend: app.yume.android.data.Friend) : Route()
    class Author(val authorId: Int, val name: String, val slug: String?) : Route()
}

object Nav {
    private var counter = 0
    fun nextId(): Int = ++counter

    val stack = mutableStateListOf<Route>()

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }
}

object Router {
    var tab by mutableIntStateOf(0)
    var pendingQuery by mutableStateOf<String?>(null)
    var pendingSort by mutableStateOf<Sort?>(null)

    fun openCatalog(query: String = "", sort: Sort? = null) {
        pendingQuery = query
        pendingSort = sort ?: Sort.RATING
        tab = 1
    }
}

private enum class YumeTab(val title: String, val icon: ImageVector) {
    Home("Главная", Icons.Filled.Home),
    Catalog("Каталог", Icons.Filled.Search),
    Favorites("Избранное", Icons.Filled.Favorite),
    Profile("Профиль", Icons.Filled.Person),
    Settings("Настройки", Icons.Filled.Settings),
}

@Composable
fun YumeApp() {
    androidx.compose.runtime.LaunchedEffect(Prefs.platform) { app.yume.android.data.FavUpdater.refreshIfDue() }
    androidx.compose.runtime.LaunchedEffect(app.yume.android.data.AuthStore.user?.id) {
        if (app.yume.android.data.AuthStore.user != null) app.yume.android.data.SiteListSync.syncAll()
    }
    val dark = when (Prefs.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    YumeTheme(dark = dark, accent = Prefs.accent) {
        BackHandler(enabled = Nav.stack.isNotEmpty()) { Nav.pop() }
        Box(Modifier.fillMaxSize()) {
            MainScaffold()
            for (route in Nav.stack.toList()) {
                key(route.uid) {
                    val visible = androidx.compose.runtime.remember {
                        androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visibleState = visible,
                        enter = androidx.compose.animation.slideInHorizontally(
                            androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                        ) { it / 4 } + androidx.compose.animation.fadeIn(),
                    ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        when (route) {
                            is Route.Detail -> DetailScreen(route.title)
                            is Route.Reader -> ReaderScreen(route, onClose = { Nav.pop() })
                            is Route.Player -> PlayerScreen(route, onClose = { Nav.pop() })
                            is Route.SiteLog -> app.yume.android.ui.SiteLogScreen(onClose = { Nav.pop() })
                            is Route.AnySite -> app.yume.android.ui.AnySiteScreen(onClose = { Nav.pop() })
                            is Route.Stats -> app.yume.android.ui.StatsScreen(onClose = { Nav.pop() })
                            is Route.NewInFavorites -> app.yume.android.ui.NewInFavoritesScreen(onClose = { Nav.pop() })
                            is Route.Offline -> app.yume.android.ui.OfflineLibraryScreen(onClose = { Nav.pop() })
                            is Route.Friends -> app.yume.android.ui.FriendsScreen(onClose = { Nav.pop() })
                            is Route.FriendProfile -> app.yume.android.ui.FriendProfileScreen(route, onClose = { Nav.pop() })
                            is Route.Login -> app.yume.android.ui.LoginScreen(onClose = { Nav.pop() })
                            is Route.Novel -> NovelReaderScreen(route, onClose = { Nav.pop() })
                            is Route.Author -> AuthorScreen(route)
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScaffold() {
    val tabs = YumeTab.entries
    val gap = 12.dp
    val glass = Prefs.glass
    val haze = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }
    // В режиме «стекло» контент заходит под панель, чтобы было что размывать.
    val bottomReserve = if (glass) FloatingBarHeight + gap - 28.dp else FloatingBarHeight + gap + 4.dp
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(bottom = bottomReserve).then(if (glass) Modifier.haze(state = haze) else Modifier)) {
                androidx.compose.animation.Crossfade(
                    targetState = Router.tab,
                    animationSpec = androidx.compose.animation.core.tween(180),
                    label = "tabs",
                ) { index ->
                    when (tabs[index]) {
                        YumeTab.Home -> HomeScreen()
                        YumeTab.Catalog -> CatalogScreen()
                        YumeTab.Favorites -> FavoritesScreen()
                        YumeTab.Profile -> ProfileScreen()
                        YumeTab.Settings -> SettingsScreen()
                    }
                }
            }
            FloatingTabBar(
                titles = tabs.map { it.title },
                icons = tabs.map { it.icon },
                selected = Router.tab,
                onSelect = { Router.tab = it },
                glass = glass,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = gap)
                    .then(
                        if (glass) Modifier.hazeChild(
                            state = haze,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            style = HazeStyle(tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)), blurRadius = 24.dp),
                        ) else Modifier,
                    ),
            )
        }
    }
}
