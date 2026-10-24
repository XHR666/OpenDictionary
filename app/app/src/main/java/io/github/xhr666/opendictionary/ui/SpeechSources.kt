package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class SpeechSource(
    val name: String,
    val license: String,
    val usage: String,
    val url: String,
)

/** 原声语料：只提供开放/公共领域来源的外部链接；不内置受版权保护的演讲音频。 */
@Composable
fun SpeechSourcesScreen(onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    fun open(url: String) = runCatching {
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val sources = listOf(
        SpeechSource("LibriVox", "公共领域（录音）", "公共领域有声书与著名演讲录音；可按作品下载 MP3 并在本地播放", "https://librivox.org/search?q=speeches"),
        SpeechSource("Internet Archive · Audio", "逐条标注（多为公共领域/CC）", "演讲、访谈、历史广播等音频档案；只在原页面确认许可后再使用", "https://archive.org/details/audio"),
        SpeechSource("Wikimedia Commons · Speeches", "逐文件 CC/公共领域", "开放许可的演讲音频与历史录音；页面标注作者和许可", "https://commons.wikimedia.org/wiki/Category:Audio_files_of_speeches"),
        SpeechSource("VOA Learning English", "美国联邦政府作品（公共领域为主）", "新闻、慢速英语与访谈音频；适合听力练习", "https://learningenglish.voanews.com/"),
        SpeechSource("NASA Audio", "公共领域（NASA 作品）", "发布会、任务录音与太空相关音频", "https://www.nasa.gov/audio-and-ringtones/"),
        SpeechSource("TED Talks", "CC BY-NC-ND 4.0", "演讲音频/视频；仅在线观看或按 TED 条款下载音频，不可二次改编", "https://www.ted.com/talks"),
        SpeechSource("Open Yale Courses", "CC BY-NC-SA 3.0", "公开课音频/视频；适合学术听力和讲座语料", "https://oyc.yale.edu/"),
    )
    PageScaffold("原声语料", { onNavigate(Route.Settings) }) {
        InfoBlock("这里只列出开放许可/公共领域的演讲、访谈与公开课来源，并给出原页面链接；应用不抓取、不二次分发受版权保护的音频。下载前请在该页面确认许可。")
        Spacer(Modifier.height(10.dp))
        sources.forEach { src ->
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(src.name, style = OD.H3.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                    Text(src.license, style = OD.Micro.copy(color = OD.CyanDeep, fontSize = 8.5.sp))
                }
                Spacer(Modifier.height(3.dp))
                Text(src.usage, style = OD.Cap.copy(fontSize = 10.5.sp, lineHeight = 15.sp))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pressable(onClick = { open(src.url) }) {
                        Row(Modifier.clip(RoundedCornerShape(8.dp)).background(OD.CyanSoft.copy(alpha = .7f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            OIcon(Ic.External, 11.dp, OD.CyanDeep)
                            Spacer(Modifier.width(4.dp))
                            Text("打开来源", style = OD.Cap.copy(fontSize = 10.5.sp, color = OD.CyanDeep, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .55f)))
        }
        Spacer(Modifier.height(12.dp))
        Text("也可以先把音频下载到本机，再用「原声」面板按词关联；已授权音频可放入本机媒体库，通过 LRC/时间点做逐词高亮。", style = OD.Micro.copy(lineHeight = 15.sp))
        Spacer(Modifier.height(80.dp))
    }
}
