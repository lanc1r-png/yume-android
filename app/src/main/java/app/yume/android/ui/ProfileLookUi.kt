@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.yume.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.yume.android.data.BookmarkFolder
import app.yume.android.data.FolderStyles
import app.yume.android.data.ProfileLook
import app.yume.android.data.ProfileMedia
import coil3.compose.AsyncImage

/** Картинка, GIF или беззвучное видео по кругу с заданным кадром (масштаб и сдвиг). */
@Composable
fun ProfileMediaView(media: ProfileMedia, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val d = LocalDensity.current
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val a = maxOf(media.aspect, 0.05).toFloat()
        val bw = if (a > w / h) h * a else w
        val bh = if (a > w / h) h else w / a
        Box(
            Modifier
                .align(Alignment.Center)
                .size(with(d) { (bw * media.scale.toFloat()).toDp() }, with(d) { (bh * media.scale.toFloat()).toDp() })
                .offset(with(d) { (media.x * w).toFloat().toDp() }, with(d) { (media.y * h).toFloat().toDp() }),
        ) {
            val f = ProfileLook.file(media)
            if (media.isVideo) {
                LoopingVideo(f.absolutePath)
            } else {
                AsyncImage(model = f, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun LoopingVideo(path: String) {
    val ctx = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(path))))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    androidx.compose.runtime.DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Двигать и увеличивать двумя пальцами. */
@Composable
private fun CropEditor(title: String, aspect: Float, round: Boolean, initial: ProfileMedia, onSave: (ProfileMedia) -> Unit, onClose: () -> Unit) {
    var media by remember { mutableStateOf(initial) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Отмена") }
                Text(title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { onSave(media); onClose() }) { Text("Готово") }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }
                ProfileMediaView(
                    media,
                    Modifier
                        .fillMaxWidth(if (round) 0.6f else 1f)
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(if (round) 40.dp else 20.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(if (round) 40.dp else 20.dp))
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                media = media.copy(
                                    scale = media.scale * zoom,
                                    x = media.x + pan.x / size.width,
                                    y = media.y + pan.y / size.height,
                                ).clamped(aspect.toDouble())
                            }
                        },
                )
            }
            Text("Двигайте и увеличивайте двумя пальцами", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { media = media.copy(scale = 1.0, x = 0.0, y = 0.0) }) { Text("Сбросить") }
        }
    }
}

/** Шапка и аватарка профиля: свои (фото, GIF, видео) или с сайта. */
@Composable
fun ProfileAppearanceSheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var pickHeader by remember { mutableStateOf(true) }
    var cropHeader by remember { mutableStateOf<Boolean?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val m = ProfileLook.import(ctx, uri, if (pickHeader) "header" else "avatar")
            ProfileLook.set(pickHeader, m)
            cropHeader = pickHeader
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Не получилось подготовить файл"
        }
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Шапка и аватарка", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            for (isHeader in listOf(true, false)) {
                val m = if (isHeader) ProfileLook.header else ProfileLook.avatar
                val on = if (isHeader) ProfileLook.useHeader else ProfileLook.useAvatar
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isHeader) "Шапка" else "Аватарка", fontWeight = FontWeight.SemiBold)
                    if (m != null) {
                        ProfileMediaView(
                            m,
                            Modifier.then(if (isHeader) Modifier.fillMaxWidth().aspectRatio(3f) else Modifier.size(80.dp).clip(CircleShape))
                                .clip(RoundedCornerShape(if (isHeader) 16.dp else 40.dp)),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Показывать свою", Modifier.weight(1f))
                            Switch(checked = on, onCheckedChange = { ProfileLook.setUse(isHeader, it) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            pickHeader = isHeader
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }) { Text(if (m == null) "Выбрать фото, GIF или видео" else "Заменить") }
                        if (m != null) {
                            OutlinedButton(onClick = { cropHeader = isHeader }) { Text("Кадр") }
                            TextButton(onClick = { ProfileLook.set(isHeader, null) }) { Text("Убрать") }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Свои шапка и аватарка видны только в приложении. GIF до 20 МБ, видео до 60 МБ — оно крутится без звука.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    cropHeader?.let { isHeader ->
        val m = if (isHeader) ProfileLook.header else ProfileLook.avatar
        if (m != null) {
            CropEditor(
                title = if (isHeader) "Шапка" else "Аватарка",
                aspect = if (isHeader) 3f else 1f,
                round = !isHeader,
                initial = m,
                onSave = { ProfileLook.updateCrop(isHeader, it) },
                onClose = { cropHeader = null },
            )
        }
    }
}

private val StyleColors = listOf("#A78BFA", "#F472B6", "#FB7185", "#F59E0B", "#FACC15", "#34D399", "#2DD4BF", "#38BDF8", "#60A5FA", "#94A3B8")
private val StyleEmojis = listOf("⭐", "🔥", "💜", "❤️", "🌸", "🎮", "⚔️", "🗡️", "🐉", "👑", "😂", "😭", "🥀", "🌙", "☀️", "🍜", "📚", "🎧", "💤", "✨", "🧩", "🏆", "💀", "👻")

fun parseHex(h: String): Color = Color(android.graphics.Color.parseColor(h))

/** Свой цвет или эмодзи для списка в профиле. */
@Composable
fun FolderStyleSheet(folder: BookmarkFolder, onClose: () -> Unit) {
    val cur = FolderStyles.style(folder.id) ?: FolderStyles.Style()
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(folder.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Цвет", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (h in StyleColors) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(parseHex(h))
                            .then(if (cur.hex == h) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { FolderStyles.set(folder.id, cur.copy(hex = if (cur.hex == h) null else h)) },
                    )
                }
            }
            Text("Эмодзи вместо значка", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (e in StyleEmojis) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (cur.emoji == e) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { FolderStyles.set(folder.id, cur.copy(emoji = if (cur.emoji == e) null else e)) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, style = MaterialTheme.typography.titleLarge) }
                }
            }
            TextButton(onClick = { FolderStyles.set(folder.id, null) }) { Text("Сбросить", color = MaterialTheme.colorScheme.error) }
        }
    }
}
