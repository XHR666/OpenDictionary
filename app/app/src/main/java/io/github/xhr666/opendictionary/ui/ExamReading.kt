package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import org.json.JSONObject

private data class ExamSet(val year: String, val category: String, val text: String, val answers: String, val analysis: String)

/** 真题阅读精读：内置开放许可样例（GAOKAO-Bench），展示原文/答案/解析。 */
@Composable
fun ExamReadingScreen(onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val sets = remember {
        runCatching {
            val raw = ctx.assets.open("quiz/gaokao_reading_sample.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(raw).getJSONArray("items")
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(ExamSet(o.optString("year"), o.optString("category"), o.optString("text"), o.optString("answers"), o.optString("analysis")))
                }
            }
        }.getOrDefault(emptyList())
    }
    var selected by remember { mutableIntStateOf(-1) }
    var reveal by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val ielts = remember {
        runCatching {
            val raw = ctx.assets.open("quiz/ielts_reading_sample.json").bufferedReader().use { it.readText() }
            JSONObject(raw).getJSONArray("tests")
        }.getOrNull()
    }
    var ieltsSelection by remember { mutableIntStateOf(-1) }
    PageScaffold("真题阅读", { onNavigate(Route.Home) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("高考阅读", tab == 0) { tab = 0; selected = -1 }
            Pill("IELTS 阅读", tab == 1) { tab = 1; ieltsSelection = -1 }
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 1) {
            if (ielts == null) Text("IELTS 样例加载失败。", style = OD.Cap)
            else if (ieltsSelection < 0) {
                Text("AI 生成练习（LuchoBazz/ielts-ai-dataset，CC BY 4.0；非官方真题）。点开查看文章与题目答案。", style = OD.Cap.copy(lineHeight = 16.sp))
                Spacer(Modifier.height(8.dp))
                for (i in 0 until ielts.length()) {
                    val t = ielts.getJSONObject(i)
                    Pressable(onClick = { ieltsSelection = i; reveal = false }) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.optString("title"), style = OD.H3.copy(fontSize = 13.5.sp))
                                Text("Band ${t.optString("difficulty")} · ${t.getJSONArray("passages").length()} 篇", style = OD.Cap.copy(fontSize = 10.5.sp))
                            }
                            OIcon(Ic.Chevron, 12.dp, OD.Hairline)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .55f)))
                }
                Spacer(Modifier.height(90.dp))
            } else {
                val t = ielts.getJSONObject(ieltsSelection)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pressable(onClick = { ieltsSelection = -1 }) { OIcon(Ic.Back, 18.dp, OD.Ink) }
                    Spacer(Modifier.width(8.dp)); Text(t.optString("title"), style = OD.H2.copy(fontSize = 16.sp))
                }
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    val passages = t.getJSONArray("passages")
                    for (pi in 0 until passages.length()) {
                        val p = passages.getJSONObject(pi)
                        Text("Passage ${pi + 1} · ${p.optString("title")}", style = OD.H3.copy(fontSize = 13.sp, color = OD.CyanDeep))
                        Spacer(Modifier.height(4.dp))
                        Text(p.optString("content"), style = OD.ReadBody.copy(fontSize = 12.5.sp, lineHeight = 20.sp))
                        Spacer(Modifier.height(8.dp))
                        val groups = p.optJSONArray("groups")
                        if (groups != null) for (gi in 0 until groups.length()) {
                            val g = groups.getJSONObject(gi)
                            Text(g.optString("instructions"), style = OD.Cap.copy(fontSize = 10.5.sp, color = OD.Muted))
                            Spacer(Modifier.height(3.dp))
                            // collect answers for the whole test reveal
                            Spacer(Modifier.height(2.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (!reveal) ODButton("显示题目答案", { reveal = true }, modifier = Modifier.fillMaxWidth())
                    else {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OD.Pearl).padding(12.dp)) {
                            val passages = t.getJSONArray("passages")
                            var n = 0
                            for (pi in 0 until passages.length()) {
                                val groups = passages.getJSONObject(pi).optJSONArray("groups") ?: continue
                                for (gi in 0 until groups.length()) {
                                    val qs = groups.getJSONObject(gi).optJSONArray("questions") ?: continue
                                    for (qi in 0 until qs.length()) {
                                        val q = qs.getJSONObject(qi); n++
                                        Text("${n}. ${q.optString("text")}", style = OD.Cap.copy(fontSize = 11.sp, lineHeight = 17.sp))
                                        Text("答案：${q.optString("answer")}", style = OD.Micro.copy(color = OD.CyanDeep))
                                        Spacer(Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(90.dp))
                }
            }
            return@PageScaffold
        }
        if (selected < 0) {
            Text("开放许可样例：GAOKAO-Bench（Apache-2.0）。原文版权归原考试机构，仅个人学习，不用于商业分发。", style = OD.Cap.copy(lineHeight = 16.sp))
            Spacer(Modifier.height(10.dp))
            sets.forEachIndexed { i, s ->
                Pressable(onClick = { selected = i; reveal = false }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${s.year} 高考英语 · ${s.category}", style = OD.H3.copy(fontSize = 13.5.sp))
                            Text("阅读理解 · 点开精读原文与解析", style = OD.Cap.copy(fontSize = 10.5.sp))
                        }
                        OIcon(Ic.Chevron, 12.dp, OD.Hairline)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .55f)))
            }
            if (sets.isEmpty()) Text("没有找到真题样例。", style = OD.Cap)
            Spacer(Modifier.height(90.dp))
        } else {
            val s = sets[selected]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pressable(onClick = { selected = -1 }) { OIcon(Ic.Back, 18.dp, OD.Ink) }
                Spacer(Modifier.width(8.dp))
                Text("${s.year} · ${s.category}", style = OD.H2.copy(fontSize = 18.sp))
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Text(s.text, style = OD.ReadBody.copy(fontSize = 13.sp, lineHeight = 21.sp))
                Spacer(Modifier.height(12.dp))
                if (!reveal) ODButton("显示答案与解析", { reveal = true }, modifier = Modifier.fillMaxWidth())
                else {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OD.Pearl).padding(12.dp)) {
                        Text("答案：${s.answers}", style = OD.H3.copy(fontSize = 13.sp, color = OD.CyanDeep))
                        Spacer(Modifier.height(6.dp))
                        Text(s.analysis, style = OD.Cap.copy(fontSize = 11.5.sp, lineHeight = 18.sp))
                    }
                }
                Spacer(Modifier.height(90.dp))
            }
        }
    }
}
