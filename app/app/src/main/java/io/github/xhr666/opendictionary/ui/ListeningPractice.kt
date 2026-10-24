package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.Tts
import org.json.JSONObject

/** IELTS 听力练习：用本地 TTS 朗读 transcript，回答题目后显示答案；AI 生成练习，非官方真题。 */
@Composable
fun ListeningPracticeScreen(onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val data = remember {
        runCatching {
            val raw = ctx.assets.open("quiz/ielts_listening_sample.json").bufferedReader().use { it.readText() }
            JSONObject(raw).getJSONArray("tests")
        }.getOrNull()
    }
    var testIndex by rememberSaveable { mutableIntStateOf(-1) }
    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var checked by rememberSaveable { mutableStateOf(false) }
    var showTranscript by rememberSaveable { mutableStateOf(false) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    DisposableEffect(Unit) { onDispose { Tts.stop() } }
    PageScaffold("IELTS 听力练习", { onNavigate(Route.Listening) }) {
        if (data == null) { Text("听力样例加载失败。", style = OD.Cap); return@PageScaffold }
        if (testIndex < 0) {
            Text("AI 生成练习（LuchoBazz/ielts-ai-dataset，CC BY 4.0；非官方 IELTS 真题）。用本地 TTS 朗读原文，完成后核对答案。", style = OD.Cap.copy(lineHeight = 17.sp))
            Spacer(Modifier.height(10.dp))
            for (i in 0 until data.length()) {
                val t = data.getJSONObject(i)
                Pressable(onClick = { testIndex = i; sectionIndex = 0; answers = emptyMap(); checked = false }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.optString("title"), style = OD.H3.copy(fontSize = 13.5.sp))
                            Text("Band ${t.optString("difficulty").ifBlank { "—" }} · ${t.getJSONArray("sections").length()} 节", style = OD.Cap.copy(fontSize = 10.5.sp))
                        }
                        OIcon(Ic.Chevron, 12.dp, OD.Hairline)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .5f)))
            }
            Spacer(Modifier.height(90.dp))
        } else {
            val test = data.getJSONObject(testIndex)
            val sections = test.getJSONArray("sections")
            val safeSection = sectionIndex.coerceIn(0, sections.length() - 1)
            LaunchedEffect(sections.length()) { if (sectionIndex >= sections.length()) sectionIndex = safeSection }
            val section = sections.getJSONObject(safeSection)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pressable(onClick = { testIndex = -1; Tts.stop() }) { OIcon(Ic.Back, 18.dp, OD.Ink) }
                Spacer(Modifier.width(8.dp))
                Text("第 ${safeSection + 1} / ${sections.length()} 节", style = OD.H3.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                Pressable(onClick = { testIndex = -1; Tts.stop(); answers = emptyMap(); checked = false }) { Text("退出", style = OD.Cap.copy(color = OD.Muted)) }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("朗读本节", { Tts.stop(); Tts.speak(section.optString("transcript").take(3800), false) }, modifier = Modifier.weight(1f))
                ODButton("停止", { Tts.stop() }, primary = false, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton(if (showTranscript) "隐藏原文" else "显示原文", { showTranscript = !showTranscript }, primary = false, modifier = Modifier.fillMaxWidth())
            }
            if (showTranscript) {
                Text(section.optString("transcript"), style = OD.ReadBody.copy(fontSize = 11.5.sp, lineHeight = 18.sp), modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).clip(RoundedCornerShape(10.dp)).background(OD.Pearl).padding(10.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(section.optString("title") + if (section.optString("transcript").length > 3800) "（朗读前 3800 字符）" else "", style = OD.Cap.copy(color = OD.CyanDeep))
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.weight(1f)) {
                val groups = section.optJSONArray("groups")
                if (groups != null) for (gi in 0 until groups.length()) {
                    val g = groups.getJSONObject(gi)
                    item { Text(g.optString("instructions"), style = OD.Cap.copy(fontSize = 10.5.sp, color = OD.Muted), modifier = Modifier.padding(vertical = 4.dp)) }
                    val qs = g.optJSONArray("questions") ?: continue
                    items(qs.length()) { qi ->
                        val q = qs.getJSONObject(qi)
                        val key = "$safeSection-$gi-$qi"
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(q.optString("text"), style = OD.Body.copy(fontSize = 12.5.sp))
                            val missingOptions = g.optString("instructions").contains("choose", true) && q.optString("answer").length <= 1
                            if (missingOptions) {
                                Text("（该 AI 样例未提供选项，可显示原文后核对答案）", style = OD.Micro.copy(color = OD.Warm))
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(OD.Parchment).border(1.dp, OD.Hairline, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                                BasicTextField(value = answers[key] ?: "", onValueChange = { answers = answers + (key to it) }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = OD.InkSoft), decorationBox = { inner -> if ((answers[key] ?: "").isEmpty()) Text("输入答案…", style = OD.Micro) else inner() }, modifier = Modifier.fillMaxWidth())
                            }
                            if (checked) Text("参考答案：${q.optString("answer")}", style = OD.Micro.copy(color = OD.CyanDeep))
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ODButton(if (checked) "隐藏答案" else "核对答案", { checked = !checked }, primary = false, modifier = Modifier.weight(1f))
                        ODButton(if (safeSection + 1 >= sections.length()) "下一套" else "下一节", {
                            if (safeSection + 1 >= sections.length()) { testIndex = -1; sectionIndex = 0 } else { sectionIndex = safeSection + 1; checked = false }
                            Tts.stop()
                        }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(90.dp))
                }
            }
        }
    }
}
