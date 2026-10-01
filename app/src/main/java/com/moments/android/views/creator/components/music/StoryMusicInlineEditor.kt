package com.moments.android.views.creator.components.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import com.moments.android.views.shared.MomentsBrandColors
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.StoryMusicSelection
import kotlinx.coroutines.*
import kotlin.math.*

@Composable
fun StoryMusicInlineEditor(selection: StoryMusicSelection, allowsDuration: Boolean, onChange: (StoryMusicSelection) -> Unit, onChangeTrack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audio = rememberStoryMusicAudio()
    var resolved by remember(selection.track.id) { mutableStateOf<StoryMusicSelection?>(null) }
    var loading by remember(selection.track.id) { mutableStateOf(true) }
    var failed by remember(selection.track.id) { mutableStateOf(false) }
    var waves by remember(selection.track.id) { mutableStateOf<List<Float>>(emptyList()) }
    var waveformFailed by remember(selection.track.id) { mutableStateOf(false) }
    var durationPicker by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val latest by rememberUpdatedState(selection)
    LaunchedEffect(selection.track.id, retry) {
        loading = true; failed = false; waveformFailed = false
        try {
            val track = StoryMusicCatalog.resolve(selection.track.id)
            val value = latest.copy(track = track).clamped()
            resolved = value; audio.load(value, true); loading = false
            waves = try { StoryMusicWaveformLoader.load(context, track) } catch(c: CancellationException) { throw c } catch (_: Exception) { waveformFailed = true; emptyList() }
        } catch(c: CancellationException) { throw c } catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(selection.start, selection.duration) { resolved?.let { audio.update(selection.copy(track = it.track)) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            val styles = listOf("hidden", "title", "card", "cover", "record")
            val icons = listOf(R.drawable.story_music_hidden, R.drawable.story_music_title, R.drawable.story_music_card, R.drawable.story_music_cover, R.drawable.story_music_record)
            val labels = listOf(R.string.story_music_style_hidden, R.string.story_music_style_title, R.string.story_music_style_card, R.string.story_music_style_cover, R.string.story_music_style_record)
            styles.forEachIndexed { index, style ->
                val selected = selection.style == style
                Surface(Modifier.padding(horizontal = 4.dp).size(44.dp).clickable { onChange(selection.copy(style = style)) }, shape = CircleShape, color = if(selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(icons[index]), stringResource(labels[index]),
                            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp).then(
                                if (selected) Modifier
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithCache {
                                        val gradient = MomentsBrandColors.storyRingBrush
                                        onDrawWithContent {
                                            drawContent()
                                            drawRect(gradient, blendMode = BlendMode.SrcIn)
                                        }
                                    }
                                else Modifier
                            ),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(48.dp), contentAlignment = Alignment.Center) {
            Text(musicTime(selection.start) + " – " + musicTime(selection.start + selection.duration), color = Color.White, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if(allowsDuration) durationPicker = true }, enabled = allowsDuration, colors = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Color.White)) { Text(musicTime(selection.duration)) }
                FilledIconButton(onClick = { if(audio.playing) audio.pause() else audio.play() }, enabled = !loading && !failed, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) { Icon(if(audio.playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(R.string.story_music_preview)) }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(44.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val total = selection.track.duration.toFloat().coerceAtLeast(.1f)
                val left = size.width * selection.start.toFloat() / total
                val width = max(6.dp.toPx(), size.width * selection.duration.toFloat() / total)
                drawLine(Color.White.copy(.2f), Offset(0f, size.height/2), Offset(size.width, size.height/2), 3.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White.copy(.85f), Offset(left, size.height/2), Offset(min(size.width,left+width), size.height/2), 7.dp.toPx(), StrokeCap.Round)
                drawCircle(Color.White, 5.dp.toPx(), Offset(size.width * (selection.start+audio.elapsed).toFloat()/total, size.height/2))
            }
            Slider(selection.start.toFloat(), { onChange(selection.copy(start = it.toDouble()).clamped()) }, valueRange = 0f..max(.1f, (selection.track.duration - selection.duration).toFloat()), modifier = Modifier.fillMaxSize().graphicsLayer { alpha = .01f }, onValueChangeFinished = { audio.play() }, enabled = !loading && !failed)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("0:00", color = Color.White.copy(.65f), fontSize = 11.sp); Text(musicTime(selection.track.duration), color = Color.White.copy(.65f), fontSize = 11.sp) }
        if(loading) Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Color.White) }
        else if(failed || waveformFailed || audio.failed) TextButton(onClick = { retry++ }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.story_music_retry)) }
        else if(waves.isEmpty()) Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Color.White) }
        else MusicWaveform(selection, waves, audio.elapsed, onChange, { if(it) audio.pause() else audio.play() })
    }
    if(durationPicker) AlertDialog(onDismissRequest = { durationPicker = false }, title = { Text(stringResource(R.string.story_music_fragment)) }, text = {
        LazyColumn(Modifier.height(216.dp)) { items((15..minOf(60, selection.track.duration.toInt()).coerceAtLeast(15)).toList()) { seconds ->
            Text(musicTime(seconds.toDouble()), Modifier.fillMaxWidth().clickable { onChange(selection.clamped(seconds.toDouble())); durationPicker = false }.padding(12.dp), color = MaterialTheme.colorScheme.onSurface)
        } }
    }, confirmButton = { TextButton(onClick = { durationPicker = false }) { Text(stringResource(R.string.story_text_editor_done)) } })
}

@Composable
private fun MusicWaveform(selection: StoryMusicSelection, waves: List<Float>, elapsed: Double, onChange: (StoryMusicSelection) -> Unit, onScrub: (Boolean) -> Unit) {
    val current by rememberUpdatedState(selection)
    val update by rememberUpdatedState(onChange)
    val scrub by rememberUpdatedState(onScrub)
    Canvas(Modifier.fillMaxWidth().height(72.dp).pointerInput(selection.track.id, selection.duration) {
        detectHorizontalDragGestures(onDragStart = { scrub(true) }, onDragEnd = { scrub(false) }, onDragCancel = { scrub(false) }) { change, amount ->
            change.consume()
            val pixelsPerSecond = size.width * .38 / current.duration
            update(current.copy(start = current.start - amount / pixelsPerSecond).clamped())
        }
    }) {
        val span = size.width * .38f
        val left = (size.width - span) / 2
        val pps = span / selection.duration.toFloat()
        val musicGradient = Brush.linearGradient(
            MomentsBrandColors.storyRing,
            start = Offset(left, 0f), end = Offset(left + span, 0f),
        )
        val spacing = 7.dp.toPx()
        val offset = (selection.start.toFloat() * pps) % spacing
        var x = -offset
        while(x < size.width) {
            val seconds = selection.start + (x-left)/pps
            if(seconds >= 0 && seconds <= selection.track.duration) {
                val index = (seconds/selection.track.duration * (waves.size - 1)).toInt().coerceIn(0, waves.lastIndex)
                val height = max(5.dp.toPx(), waves[index] * (size.height-16.dp.toPx()))
                val begin = Offset(x, (size.height-height)/2); val end = Offset(x, (size.height+height)/2)
                if(x in left..left+span) drawLine(musicGradient, begin, end, 3.dp.toPx(), StrokeCap.Round)
                else drawLine(Color.White.copy(.6f), begin, end, 3.dp.toPx(), StrokeCap.Round)
            }
            x += spacing
        }
        drawRoundRect(Color.White, Offset(left, 4.dp.toPx()), Size(span, size.height-8.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = Stroke(3.dp.toPx()))
        drawLine(Color.White, Offset(left + elapsed.toFloat()*pps, 8.dp.toPx()), Offset(left + elapsed.toFloat()*pps, size.height-8.dp.toPx()), 2.dp.toPx())
    }
}
