package io.github.xhr666.opendictionary.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.data.MediaClip
import io.github.xhr666.opendictionary.data.MediaTrack
import io.github.xhr666.opendictionary.data.UserDb
import io.github.xhr666.opendictionary.media.MediaImporter
import io.github.xhr666.opendictionary.media.MediaPlayback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 本地音乐与歌词：SAF 文件夹导入（同名 .lrc 自动配对）、播放、词条片段。 */
@Composable
fun MediaScreen(onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { UserDb.get(ctx) }
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<MediaTrack>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var progressText by remember { mutableStateOf("") }

    fun reload() { tracks = db.mediaTracks() }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(MediaPlayback.playing) {
        while (isActive && MediaPlayback.playing) { MediaPlayback.poll(); delay(80) }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            importing = true; status = "扫描文件夹…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { MediaImporter.importTree(ctx, uri) { progressText = it } }
                }
                result.onSuccess { status = it.message; reload() }.onFailure { status = "导入失败：${it.message}" }
                importing = false; progressText = ""
            }
        }
    }

    PageScaffold("本地音乐与歌词", { onNavigate(Route.Offline) }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text("选择一个文件夹，音乐/视频与其同名 .lrc 歌词会被自动配对。歌词逐词时间轴用于片段播放与高亮；普通 LRC 会按字符比例估算时间。", style = OD.Cap.copy(lineHeight = 17.sp))
            Spacer(Modifier.height(12.dp))
            ODButton(if (importing) "导入中…" else "选择文件夹导入", onClick = {
                if (!importing) folderPicker.launch(null)
            }, modifier = Modifier.fillMaxWidth())
            if (progressText.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(progressText, style = OD.Micro) }
            if (status.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(status, style = OD.Micro.copy(color = OD.CyanDeep)) }
            Spacer(Modifier.height(16.dp))
            if (MediaPlayback.playing || MediaPlayback.title.isNotBlank()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text("正在播放 · ${MediaPlayback.title}", style = OD.H3.copy(fontSize = 13.sp))
                    Spacer(Modifier.height(4.dp))
                    val clip = MediaPlayback.clip
                    if (clip != null) Text(clip.lineText, style = OD.Cap, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${MediaPlayback.positionMs / 1000}s", style = OD.Micro)
                        Spacer(Modifier.weight(1f))
                        ODButton("停止", { MediaPlayback.stop() }, primary = false, modifier = Modifier.width(90.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Text("已导入 ${tracks.size} 个媒体", style = OD.H3)
            Spacer(Modifier.height(6.dp))
            if (tracks.isEmpty()) {
                Text("还没有导入。把音乐和同名 .lrc 放在同一文件夹，然后选择该文件夹。", style = OD.Cap)
            }
            tracks.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp).clickable { onNavigate(Route.MediaPlayer(t.id)) }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(OD.CyanSoft), contentAlignment = Alignment.Center) { OIcon(Ic.Headphone, 16.dp, OD.CyanDeep) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = OD.H3.copy(fontSize = 13.sp))
                        Text((if (t.hasLrc) "含歌词" else "无歌词") + " · ${t.duration / 1000}s", style = OD.Cap.copy(fontSize = 10.sp))
                    }
                    Pressable(onClick = { db.deleteMedia(t.id); reload() }) { OIcon(Ic.Trash, 14.dp, OD.Muted) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .5f)))
            }
        }
    }
}
