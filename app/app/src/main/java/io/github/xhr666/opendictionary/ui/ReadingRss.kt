package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL

data class RssItem(val title: String, val link: String, val summary: String, val date: String, val source: String)

private data class Feed(val name: String, val url: String)

/** 时文阅读：内置英语正向内容的公开 RSS；只展示标题/摘要/链接，正文在外链打开。 */
@Composable
fun ReadingRssScreen(onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val feeds = remember { listOf(
        Feed("Good News Network", "https://www.goodnewsnetwork.org/feed/"),
        Feed("Positive News", "https://www.positive.news/feed/"),
        Feed("NASA Breaking News", "https://www.nasa.gov/rss/dyn/breaking_news.rss"),
    ) }
    var items by remember { mutableStateOf<List<RssItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedFeed by rememberSaveable { mutableIntStateOf(-1) }
    var attemptedFeed by remember { mutableStateOf<Feed?>(null) }
    var clozeItem by remember { mutableStateOf<RssItem?>(null) }
    var showClozeAnswer by remember { mutableStateOf(false) }
    var exerciseType by remember { mutableStateOf("cloze") }

    fun load(feed: Feed) {
        attemptedFeed = feed
        loading = true; error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchFeed(feed) }
            }
            result.onSuccess { items = it; selectedFeed = feeds.indexOf(feed) }.onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load(feeds[selectedFeed.coerceIn(0, feeds.lastIndex)]) }

    PageScaffold("时文阅读", { onNavigate(Route.Home) }) {
        Text("内置公开 RSS：英语、正向内容。应用只缓存标题/摘要/链接，正文由原站提供。", style = OD.Cap.copy(lineHeight = 16.sp))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton("导入文档（txt/md）", { onNavigate(Route.DocReader(-1L)) }, primary = false, modifier = Modifier.weight(1f))
            ODButton("PDF", { onNavigate(Route.PdfReader) }, primary = false, modifier = Modifier.weight(1f))
            ODButton("EPUB", { onNavigate(Route.EpubReader) }, primary = false, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            feeds.forEachIndexed { i, f -> Pill(f.name, selectedFeed == i) { load(f) } }
        }
        Spacer(Modifier.height(10.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Text("加载中…", style = OD.Cap) }
            error != null -> Column {
                Text("加载失败：${error}", style = OD.Cap.copy(color = OD.Warm))
                Spacer(Modifier.height(8.dp)); ODButton("重试", { load(attemptedFeed ?: feeds[0]) }, primary = false)
            }
            else -> Column {
                items.forEach { it ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(it.source, style = OD.Micro.copy(color = OD.CyanDeep, fontWeight = FontWeight.SemiBold))
                            Spacer(Modifier.weight(1f)); Text(it.date.take(16), style = OD.Micro)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(it.title, style = OD.H3.copy(fontSize = 13.5.sp))
                        if (it.summary.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(it.summary, style = OD.Cap.copy(fontSize = 10.5.sp, lineHeight = 16.sp), maxLines = 3) }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pressable(onClick = {
                                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it.link)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }) { Text("打开原文 →", style = OD.Micro.copy(color = OD.CyanDeep)) }
                            Spacer(Modifier.width(12.dp))
                            Pressable(onClick = { clozeItem = it; showClozeAnswer = false; exerciseType = "cloze" }) { Text("生成练习", style = OD.Micro.copy(color = OD.Warm)) }
                        }
                    }
                }
                Spacer(Modifier.height(90.dp))
            }
        }
    }
    clozeItem?.let { item ->
        DialogCard(visible = true, onDismiss = { clozeItem = null }) {
            Text("阅读练习（自动生成）", style = OD.H2)
            Spacer(Modifier.height(4.dp))
            Text("基于文章标题/摘要离线生成，不是原题；仅用于阅读练习。", style = OD.Micro)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("cloze" to "填空", "choice" to "完形选择", "seven" to "句际衔接（2空·4选）", "grammar" to "语法填空").forEach { (k, label) ->
                    Pill(label, exerciseType == k) { exerciseType = k; showClozeAnswer = false }
                }
            }
            Spacer(Modifier.height(10.dp))
            val raw = (item.title + ". " + item.summary)
            val exercise = remember(exerciseType, item) { ExerciseGenerator.build(raw, exerciseType) }
            Text(exercise.passage, style = OD.ReadBody.copy(fontSize = 13.sp, lineHeight = 21.sp))
            Spacer(Modifier.height(10.dp))
            if (exercise.questions.isEmpty()) Text("摘要太短，暂时无法生成练习。", style = OD.Cap)
            if (showClozeAnswer) {
                exercise.questions.forEachIndexed { i, q ->
                    Text("${i + 1}. ${q.prompt}", style = OD.Cap.copy(fontSize = 11.sp))
                    if (q.options.isNotEmpty()) Text("选项：${q.options.joinToString(" / ")}", style = OD.Micro)
                    Text("答案：${q.answer}", style = OD.Cap.copy(color = OD.CyanDeep))
                    Spacer(Modifier.height(3.dp))
                }
            } else Text("共 ${exercise.questions.size} 题", style = OD.Cap)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton(if (showClozeAnswer) "隐藏答案" else "显示答案", { showClozeAnswer = !showClozeAnswer }, primary = false, modifier = Modifier.weight(1f))
                ODButton("关闭", { clozeItem = null }, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun fetchFeed(feed: Feed): List<RssItem> {
    val conn = URL(feed.url).openConnection() as HttpURLConnection
    conn.connectTimeout = 12000; conn.readTimeout = 15000
    conn.setRequestProperty("User-Agent", "OpenDictionary-Android/0.5 (+https://github.com/XHR666/OpenDictionary)")
    val body = conn.inputStream.bufferedReader().use { it.readText() }
    conn.disconnect()
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(body.reader())
    val out = mutableListOf<RssItem>()
    var title = ""; var link = ""; var desc = ""; var date = ""; var insideItem = false
    val text = StringBuilder()
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                text.clear()
                if (parser.name.equals("item", true) || parser.name.equals("entry", true)) { insideItem = true; title = ""; link = ""; desc = ""; date = "" }
                if (insideItem && parser.name.equals("link", true)) {
                    parser.getAttributeValue(null, "href")?.let { link = it.trim() }
                }
            }
            XmlPullParser.TEXT -> text.append(parser.text ?: "")
            XmlPullParser.END_TAG -> {
                val n = parser.name?.lowercase()
                if (insideItem) {
                    when (n) {
                        "title" -> title = clean(text.toString())
                        "link" -> if (text.isNotBlank()) link = text.toString().trim()
                        "description", "summary", "content" -> if (desc.isBlank()) desc = clean(text.toString())
                        "pubdate", "published", "updated" -> date = text.toString().trim()
                        "item", "entry" -> {
                            if (title.isNotBlank()) out.add(RssItem(title, link, desc, date, feed.name))
                            insideItem = false
                        }
                    }
                }
                text.clear()
            }
        }
        event = parser.next()
    }
    return out.take(40)
}

private fun clean(s: String): String = s.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(300)
