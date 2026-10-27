package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.data.UserDb
import io.github.xhr666.opendictionary.media.MediaPlayback
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 全屏歌词播放：逐行/逐词高亮、偏移校准、进度拖动。 */
@Composable
fun MediaPlayerScreen(mediaId: Long, onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { UserDb.get(ctx) }
    val track = remember(mediaId) { db.mediaById(mediaId) }
    val lyrics = remember(mediaId) { db.lyricsFor(mediaId) }
    val words = remember(mediaId) { db.wordsFor(mediaId) }
    val lyricPrefs = remember { ctx.getSharedPreferences("od_prefs", android.content.Context.MODE_PRIVATE) }
    var offset by remember(mediaId) { mutableIntStateOf(lyricPrefs.getInt("lyric_offset_$mediaId", 0)) }
    var started by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(offset, mediaId) { lyricPrefs.edit().putInt("lyric_offset_$mediaId", offset).apply() }
    LaunchedEffect(Unit) { MediaPlayback.setSpeed(lyricPrefs.getFloat("media_speed", 1f)) }
    var speedVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(mediaId) {
        if (track != null && !started) {
            MediaPlayback.playTrack(ctx, mediaId, track.uri, track.title, track.duration)
            started = true
        }
    }
    LaunchedEffect(MediaPlayback.playing) {
        while (isActive && MediaPlayback.playing) { MediaPlayback.poll(); delay(60) }
    }
    val pos = (MediaPlayback.positionMs - offset).coerceAtLeast(0)
    val currentIndex = lyrics.indexOfLast { pos >= it.first } .coerceAtLeast(0)
    LaunchedEffect(currentIndex) {
        if (lyrics.isNotEmpty()) runCatching { listState.animateScrollToItem(currentIndex.coerceIn(0, lyrics.lastIndex)) }
    }

    PageScaffold("歌词 · ${track?.title ?: ""}", { onNavigate(Route.Media) }, contentScroll = false) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), state = listState) {
                itemsIndexed(lyrics) { i, line ->
                    val active = i == currentIndex
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().clickable { MediaPlayback.seekTo(line.first + offset) }) {
                    val activeWord = if (active) words.firstOrNull { (_, w) -> pos >= w.first && pos < w.second && line.third.contains(w.third) }?.first else null
                    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalAlignment = if (active) Alignment.Start else Alignment.CenterHorizontally) {
                        if (activeWord != null) {
                            val idx = line.third.indexOf(activeWord, ignoreCase = true)
                            val annotated = if (idx >= 0) buildAnnotatedString {
                                append(line.third.substring(0, idx))
                                withStyle(SpanStyle(background = OD.CyanSoft, color = OD.CyanDeep, fontWeight = FontWeight.Bold)) { append(line.third.substring(idx, idx + activeWord.length)) }
                                append(line.third.substring(idx + activeWord.length))
                            } else null
                            if (annotated != null) Text(annotated, style = OD.Body.copy(fontSize = if (active) 15.sp else 13.sp, lineHeight = 22.sp, color = if (active) OD.Ink else OD.Muted))
                            else Text(line.third, style = OD.Body.copy(fontSize = if (active) 15.sp else 13.sp, lineHeight = 22.sp, color = if (active) OD.Ink else OD.Muted))
                        } else {
                            Text(line.third, style = OD.Body.copy(fontSize = if (active) 15.sp else 13.sp, lineHeight = 22.sp, color = if (active) OD.Ink else OD.Muted))
                        }
                    }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(Color.White).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${pos / 1000}s", style = OD.Micro)
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        ODSlider((pos.toFloat() / (track?.duration ?: 1L).coerceAtLeast(1)).coerceIn(0f, 1f), 0f..1f, { f ->
                            MediaPlayback.seekTo((f * (track?.duration ?: 0L)).toLong() + offset)
                        })
                    }
                    Text("${(track?.duration ?: 0L) / 1000}s", style = OD.Micro)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ODButton(if (MediaPlayback.playing) "暂停" else "播放", onClick = {
                        if (!MediaPlayback.playing && MediaPlayback.mediaId != mediaId && track != null) MediaPlayback.playTrack(ctx, mediaId, track.uri, track.title, track.duration)
                        else MediaPlayback.togglePause()
                    }, modifier = Modifier.weight(1f))
                    ODButton("-100ms", onClick = { offset -= 100 }, primary = false, modifier = Modifier.weight(1f))
                    ODButton("+100ms", onClick = { offset += 100 }, primary = false, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (speedVersion >= 0) "播放速度" else "播放速度", style = OD.Micro)
                    listOf(0.75f to "0.75x", 1f to "1x", 1.25f to "1.25x").forEach { (v, label) ->
                        Pill(label, kotlin.math.abs(MediaPlayback.currentSpeed() - v) < 0.01f) { MediaPlayback.setSpeed(v); lyricPrefs.edit().putFloat("media_speed", v).apply(); speedVersion++ }
                    }
                }
                Spacer(Modifier.height(6.dp))
                ODButton("校准：当前行起点对齐播放位置", onClick = {
                    val line = lyrics.getOrNull(currentIndex) ?: return@ODButton
                    offset = (MediaPlayback.positionMs - line.first).toInt()
                }, primary = false, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text("偏移 ${offset}ms · 逐词时间来自增强 LRC；普通 LRC 为估算值", style = OD.Micro)
            }
        }
    }
}
