package app.yume.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Высота плавающей панели вкладок без внешних отступов. */
val FloatingBarHeight: Dp = 64.dp

/**
 * Плавающая панель-капсула в стиле One UI: одна «таблетка», активная вкладка подсвечена своей таблеткой.
 * [glass] — полупрозрачный фон (под ним может быть размытие, его вешает вызывающий через [modifier]).
 */
@Composable
fun FloatingTabBar(
    titles: List<String>,
    icons: List<ImageVector>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
) {
    val view = LocalView.current
    val scheme = MaterialTheme.colorScheme
    val container = if (glass) scheme.surfaceContainerHigh.copy(alpha = 0.55f) else scheme.surfaceContainerHigh
    Surface(
        modifier = modifier.fillMaxWidth().height(FloatingBarHeight),
        shape = CircleShape,
        color = container,
        tonalElevation = if (glass) 0.dp else 3.dp,
        shadowElevation = if (glass) 4.dp else 10.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            titles.forEachIndexed { i, title ->
                TabItem(
                    title = title,
                    icon = icons[i],
                    selected = selected == i,
                    glass = glass,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (selected != i) Haptics.tap(view)
                        onSelect(i)
                    },
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    glass: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tabScale",
    )
    val pill by animateColorAsState(
        if (selected) scheme.primaryContainer.copy(alpha = if (glass) 0.8f else 1f) else Color.Transparent,
        label = "tabPill",
    )
    val tint by animateColorAsState(
        if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        label = "tabTint",
    )
    Box(
        modifier
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(pill)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(22.dp))
            Text(
                title,
                color = tint,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp),
            )
        }
    }
}
