package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import io.github.xhr666.opendictionary.data.UserDb
import io.github.xhr666.opendictionary.llm.LocalLlm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TranslationItem(val zh: String, val ref: String, val level: String)

private val translationItems = listOf(
    TranslationItem("中国的传统节日承载着丰富的文化内涵。", "Traditional Chinese festivals carry rich cultural connotations.", "CET-4 自编"),
    TranslationItem("随着科技的发展，人们的生活方式发生了巨大变化。", "With the development of technology, people's way of life has changed greatly.", "CET-4 自编"),
    TranslationItem("越来越多的人意识到保护环境的重要性。", "More and more people are aware of the importance of protecting the environment.", "CET-4 自编"),
    TranslationItem("这座古城吸引了许多来自世界各地的游客。", "This ancient city attracts many tourists from all over the world.", "CET-4 自编"),
    TranslationItem("读书不仅能增长知识，还能开阔视野。", "Reading can not only increase knowledge but also broaden our horizons.", "CET-6 自编"),
    TranslationItem("面对困难时，保持积极的心态尤为重要。", "It is particularly important to keep a positive attitude when facing difficulties.", "CET-6 自编"),
    TranslationItem("政府采取了一系列措施以改善公共交通。", "The government has taken a series of measures to improve public transportation.", "CET-6 自编"),
    TranslationItem("互联网让信息传播变得更加迅速和便捷。", "The Internet has made the spread of information faster and more convenient.", "CET-6 自编"),
    TranslationItem("只有通过不断练习，才能真正掌握一门外语。", "Only through constant practice can one truly master a foreign language.", "自编练习"),
    TranslationItem("尊重不同的文化有助于促进国际交流。", "Respecting different cultures helps promote international communication.", "自编练习"),
)

/** CET 翻译练习：自编题目与参考译文；可直接使用本地 LLM 批改。 */
@Composable
fun TranslationPracticeScreen(onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { UserDb.get(ctx) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }
    var showRef by rememberSaveable { mutableStateOf(false) }
    var grading by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val item = translationItems[index]
    PageScaffold("翻译练习", { onNavigate(Route.Writing) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1} / ${translationItems.size}", style = OD.Micro)
            Spacer(Modifier.weight(1f))
            Text("自编练习，非官方真题", style = OD.Micro)
        }
        Spacer(Modifier.height(10.dp))
        Text(item.zh, style = OD.H2.copy(fontSize = 18.sp, lineHeight = 27.sp))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(14.dp)).padding(12.dp)) {
            BasicTextField(value = input, onValueChange = { input = it }, textStyle = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, color = OD.InkSoft), modifier = Modifier.fillMaxSize(),
                decorationBox = { inner -> if (input.isEmpty()) Text("写下你的英文翻译…", style = OD.Cap) else inner() })
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton(if (showRef) "隐藏参考" else "参考译文", { showRef = !showRef }, primary = false, modifier = Modifier.weight(1f))
            ODButton(if (grading) "批改中…" else "本地 AI 批改", {
                if (!grading) {
                    grading = true; feedback = ""
                    val gradeIndex = index
                    val gradeItem = item
                    val gradeInput = input
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            runCatching {
                                if (!LocalLlm.isModelLoaded) error("请先在 AI 与本地模型页加载模型")
                                if (gradeInput.isBlank()) error("先写译文再批改")
                                LocalLlm.generate("你是英语翻译老师。中文：${gradeItem.zh}\n学生译文：${gradeInput.take(800)}\n参考译文：${gradeItem.ref}\n请用中文指出错误、给出更自然表达，简短。", 240)
                            }
                        }
                        if (index == gradeIndex) {
                            res.onSuccess { feedback = it.ifBlank { "模型无输出" } }.onFailure { feedback = it.message ?: "批改失败" }
                            grading = false
                        }
                    }
                }
            }, modifier = Modifier.weight(1f))
        }
        if (showRef) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OD.Pearl).padding(12.dp)) {
                Text("参考译文", style = OD.Micro); Spacer(Modifier.height(4.dp)); Text(item.ref, style = OD.ReadBody.copy(fontSize = 13.sp, lineHeight = 20.sp)); Spacer(Modifier.height(4.dp)); Text(item.level, style = OD.Micro)
            }
        }
        if (feedback.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()).clip(RoundedCornerShape(12.dp)).background(OD.CyanSoft.copy(alpha = .35f)).padding(12.dp)) {
                Text(feedback, style = OD.Cap.copy(lineHeight = 18.sp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton("上一题", { index = (index - 1 + translationItems.size) % translationItems.size; input = ""; showRef = false; feedback = "" }, primary = false, modifier = Modifier.weight(1f))
            ODButton("保存笔记", { db.saveNote("翻译练习", "${item.zh}\n我的译文：$input"); feedback = "已保存到我的笔记" }, primary = false, modifier = Modifier.weight(1f))
            ODButton("下一题", { index = (index + 1) % translationItems.size; input = ""; showRef = false; feedback = "" }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(90.dp))
    }
}
