package app.yume.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class YumeTab(val title: String, val icon: ImageVector) {
    Home("Главная", Icons.Filled.Home),
    Catalog("Каталог", Icons.Filled.Search),
    Favorites("Избранное", Icons.Filled.Favorite),
    Profile("Профиль", Icons.Filled.Person),
    Settings("Настройки", Icons.Filled.Settings),
}

@Composable
fun YumeApp(prefs: Prefs) {
    val dark = when (prefs.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    YumeTheme(dark = dark) {
        var tabIndex by rememberSaveable { mutableIntStateOf(0) }
        val tabs = YumeTab.entries
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, tab ->
                        NavigationBarItem(
                            selected = i == tabIndex,
                            onClick = { tabIndex = i },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tabs[tabIndex]) {
                    YumeTab.Settings -> SettingsScreen(prefs)
                    else -> PlaceholderScreen(tabs[tabIndex].title)
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Скоро появится",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private data class PlatformTile(val key: String, val title: String)

@Composable
private fun SettingsScreen(prefs: Prefs) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)

        Text("Платформа", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tiles = listOf(
                PlatformTile("anime", "Аниме"),
                PlatformTile("ranobe", "Ранобэ"),
                PlatformTile("manga", "Манга"),
            )
            for (t in tiles) {
                val enabled = t.key in Prefs.ACTIVE_PLATFORMS
                val selected = enabled && prefs.platform == t.key
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (enabled) 1f else 0.55f)
                        .clickable(enabled = enabled) { prefs.setPlatform(t.key) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(t.title, fontWeight = FontWeight.SemiBold)
                        if (!enabled) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
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

        Text("Тема", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val themes = listOf("light" to "Светлая", "dark" to "Тёмная", "system" to "Как в системе")
            for ((key, label) in themes) {
                FilterChip(
                    selected = prefs.theme == key,
                    onClick = { prefs.setTheme(key) },
                    label = { Text(label) },
                )
            }
        }
    }
}
