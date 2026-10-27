package io.github.xhr666.opendictionary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class BankQuestion(val text: String, val options: List<String>, val answer: String, val explanation: String, val type: String, val instructions: String)
data class BankItem(val source: String, val title: String, val passage: String, val questions: List<BankQuestion>, val meta: String)

private var bankCache: List<BankItem>? = null

@Composable
fun CorpusBankScreen(onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(bankCache ?: emptyList()) }
    var loading by remember { mutableStateOf(items.isEmpty()) }
    var query by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf<BankItem?>(null) }
    var revealAnswer by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (bankCache == null) {
            items = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = ctx.assets.open("corpus/exam_bank.json").bufferedReader().use { it.readText() }
                    val arr = JSONObject(raw).getJSONArray("items")
                    buildList {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val qs = o.optJSONArray("questions")
                            val questions = buildList {
                                if (qs != null) for (j in 0 until qs.length()) {
                                    val q = qs.getJSONObject(j)
                                    val opts = q.optJSONArray("options")
                                    add(BankQuestion(
                                        text = q.optString("text"),
                                        options = buildList { if (opts != null) for (k in 0 until opts.length()) add(opts.optString(k)) },
                                        answer = q.optString("answer"),
                                        explanation = q.optString("explanation"),
                                        type = q.optString("type"),
                                        instructions = q.optString("instructions"),
                                    ))
                                }
                            }
                            add(BankItem(o.optString("source"), o.optString("title"), o.optString("passage"), questions, o.optJSONObject("meta")?.toString() ?: ""))
                        }
                    }
                }.getOrDefault(emptyList())
            }
            bankCache = items; loading = false
        }
    }
    PageScaffold("题库与阅读", { onNavigate(Route.Learning) }, contentScroll = false) {
        val filtered = remember(items, query) {
            val q = query.trim().lowercase()
            if (q.isBlank()) items else items.filter { it.title.lowercase().contains(q) || it.passage.lowercase().contains(q) || it.questions.any { x -> x.text.lowercase().contains(q) } }
        }
        Text("已内置 ${items.size} 篇开放试题/阅读材料（高考英语真题、IELTS 阅读、GAOKAO-Bench）。可按文章/题干关键词搜索。", style = OD.Cap.copy(fontSize = 11.sp, lineHeight = 16.sp))
        Spacer(Modifier.height(8.dp))
        ODField(query, { query = it }, "搜索文章、题干或关键词", Modifier.fillMaxWidth(), leading = Ic.Search)
        Spacer(Modifier.height(8.dp))
        if (loading) Text("正在加载题库…", style = OD.Cap)
        else if (filtered.isEmpty()) Text("没有匹配的材料。", style = OD.Cap)
        else LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(filtered.take(300)) { item ->
                Column(Modifier.fillMaxWidth().clickable { opened = item; revealAnswer = false }.padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.source, style = OD.Micro.copy(fontSize = 8.5.sp, color = OD.CyanDeep))
                        Spacer(Modifier.width(6.dp))
                        Text(item.questions.size.toString() + " 题", style = OD.Micro.copy(fontSize = 8.5.sp))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(item.title, style = OD.H3.copy(fontSize = 13.sp), maxLines = 2)
                    Spacer(Modifier.height(3.dp))
                    Text(item.passage.take(120) + if (item.passage.length > 120) "…" else "", style = OD.Cap.copy(fontSize = 10.sp, lineHeight = 15.sp), maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .5f)))
            }
        }
    }
    if (opened != null) {
        val item = opened!!
        BackHandler { opened = null }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x73000000)).clickable { opened = null })
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).align(Alignment.BottomCenter).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(androidx.compose.ui.graphics.Color.White).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = OD.H2.copy(fontSize = 17.sp), maxLines = 2)
                        Text(item.source, style = OD.Micro.copy(fontSize = 8.5.sp, color = OD.CyanDeep))
                    }
                    Pressable(onClick = { opened = null }) { OIcon(Ic.Close, 18.dp, OD.Muted) }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    item { Text(item.passage, style = OD.ReadBody.copy(fontSize = 12.5.sp, lineHeight = 20.sp)) }
                    if (item.questions.isNotEmpty()) {
                        item { Spacer(Modifier.height(10.dp)); Text("题目", style = OD.H3) }
                        items(item.questions.size) { qi ->
                            val q = item.questions[qi]
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text("${qi + 1}. ${q.text}", style = OD.Body.copy(fontSize = 12.sp, lineHeight = 18.sp))
                                q.options.forEach { Text(it, style = OD.Cap.copy(fontSize = 10.5.sp, lineHeight = 16.sp)) }
                                if (revealAnswer && q.answer.isNotBlank()) Text("答案：${q.answer}", style = OD.Cap.copy(fontSize = 10.5.sp, color = OD.Good))
                                if (revealAnswer && q.explanation.isNotBlank()) Text(q.explanation.take(500), style = OD.Micro.copy(fontSize = 8.5.sp, lineHeight = 13.sp))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ODButton(if (revealAnswer) "隐藏答案" else "显示答案", { revealAnswer = !revealAnswer }, primary = false, modifier = Modifier.weight(1f))
                    ODButton("关闭", { opened = null }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
