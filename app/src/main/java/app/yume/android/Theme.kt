package app.yume.android

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightFallback = lightColorScheme(primary = Color(0xFF7A4FD6))
private val DarkFallback = darkColorScheme(primary = Color(0xFFA87AFF))

private fun parseHex(hex: String): Color? = try {
    Color(android.graphics.Color.parseColor("#" + hex.trim().trimStart('#')))
} catch (e: IllegalArgumentException) {
    null
}

/** Подкрашивает схему выбранным акцентным цветом. */
private fun ColorScheme.withAccent(accent: Color, dark: Boolean): ColorScheme {
    val container = if (dark) lerp(accent, Color.Black, 0.55f) else lerp(accent, Color.White, 0.78f)
    val onContainer = if (dark) lerp(accent, Color.White, 0.8f) else lerp(accent, Color.Black, 0.7f)
    val luminance = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
    return copy(
        primary = accent,
        onPrimary = if (luminance > 0.6f) Color.Black else Color.White,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondaryContainer = container,
        onSecondaryContainer = onContainer,
        surfaceTint = accent,
    )
}

/** Крупные скругления в духе One UI. */
val OneUiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val Base = Typography()

/** Заголовки покрупнее и пожирнее, как в One UI. */
private val OneUiTypography = Typography(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

/** Material You: цвета из обоев на Android 12+, либо выбранный акцентный цвет. */
@Composable
fun YumeTheme(dark: Boolean, accent: String = "dynamic", content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkFallback
        else -> LightFallback
    }
    val custom = if (accent == "dynamic") null else parseHex(accent)
    MaterialTheme(
        colorScheme = if (custom != null) base.withAccent(custom, dark) else base,
        shapes = OneUiShapes,
        typography = OneUiTypography,
        content = content,
    )
}
