package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.Tts
import io.github.xhr666.opendictionary.data.LexiconDb
import io.github.xhr666.opendictionary.data.UserDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class FoldStage { EXPOSE, E2C, C2E, DONE }

@Composable
fun FoldReviewScreen(onNavigate: (Route) -> Unit) {
    DisposableEffect(Unit) { onDispose { Tts.stop() } }
    val ctx = LocalContext.current
    val lex = remember { LexiconDb.get(ctx) }
    val userDb = remember { UserDb.get(ctx) }
    var words by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var queue by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var idx by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf(FoldStage.EXPOSE) }
    var answer by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf<Set<String>>(emptySet()) }
    var weakPass by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loadedWords = withContext(Dispatchers.IO) {
            val book = userDb.wordbookWords()
            val entries = if (book.isNotEmpty()) lex.entriesByWords(book) else lex.gkWords(20)
            entries.map { it.word to it.translation.split("\n").first().trim() }.filter { it.first.isNotBlank() }
        }
        words = loadedWords
        userDb.foldItems(loadedWords)
        queue = loadedWords
        total = loadedWords.size
        loaded = true
    }

    fun nextWord() {
        stage = FoldStage.EXPOSE; answer = ""; revealed = false
        if (idx + 1 >= queue.size) {
            if (wrong.isEmpty()) stage = FoldStage.DONE
            else if (weakPass < 2) {
                weakPass++
                queue = queue.filter { it.first in wrong }
                idx = 0; wrong = emptySet(); stage = FoldStage.EXPOSE
            } else stage = FoldStage.DONE
        } else idx++
    }

    fun judgeE2c(ok: Boolean) {
        val w = queue[idx]
        userDb.foldResult(w.first, "e2c", ok)
        if (ok) correct++ else wrong = wrong + w.first
        stage = FoldStage.C2E; answer = ""; revealed = false
    }

    fun judgeC2e(ok: Boolean) {
        val w = queue[idx]
        userDb.foldResult(w.first, "c2e", ok)
        if (ok) correct++ else wrong = wrong + w.first
        nextWord()
    }

    fun display() = if (queue.isEmpty()) "" else queue[idx].first
    fun meaning() = if (queue.isEmpty()) "" else queue[idx].second

    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            TopBar("折叠背单词", onBack = { onNavigate(Route.Learning) })
            when {
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("准备中…", style = OD.Cap) }
                queue.isEmpty() && stage != FoldStage.DONE -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(80.dp)); OIcon(Ic.Book, 34.dp, OD.Hairline)
                    Spacer(Modifier.height(10.dp)); Text("还没有可背的词", style = OD.H2)
                    Spacer(Modifier.height(4.dp)); Text("先加入生词本，或使用高考词汇。", style = OD.Cap)
                    Spacer(Modifier.height(14.dp)); ODButton("去词典", { onNavigate(Route.Search("")) }, primary = false)
                }
                stage == FoldStage.DONE -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(60.dp))
                    Box(Modifier.size(64.dp).clip(CircleShape).background(OD.CyanSoft), contentAlignment = Alignment.Center) { OIcon(Ic.Check, 28.dp, OD.CyanDeep) }
                    Spacer(Modifier.height(14.dp)); Text("本轮折叠完成", style = OD.H2)
                    Spacer(Modifier.height(6.dp)); Text("正确 $correct / ${correct + wrong.size} · 错词已进入复习队列", style = OD.Cap)
                    Spacer(Modifier.height(16.dp)); ODButton("返回学习页", { onNavigate(Route.Learning) })
                }
                else -> {
                    val word = display()
                    val meaningText = meaning()
                    // progress
                    Text("${idx + 1} / ${queue.size}" + if (weakPass > 0) " · 错词第 $weakPass 轮" else "", style = OD.Micro)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(OD.Hairline)) {
                        Box(Modifier.fillMaxWidth((idx + 1f) / queue.size).height(4.dp).background(OD.Cyan))
                    }
                    Spacer(Modifier.height(18.dp))
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(20.dp)).padding(20.dp)) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                when (stage) {
                                    FoldStage.EXPOSE -> {
                                        Text("第 1 轮 · 呈现初记", style = OD.Cap.copy(color = OD.CyanDeep, fontWeight = FontWeight.SemiBold))
                                        Spacer(Modifier.height(14.dp))
                                        Text(word, style = OD.H1.copy(fontSize = 34.sp), textAlign = TextAlign.Center)
                                        Spacer(Modifier.height(6.dp))
                                        Text(meaningText, style = OD.ReadBody.copy(fontSize = 14.sp, lineHeight = 22.sp), textAlign = TextAlign.Center)
                                        Spacer(Modifier.height(10.dp))
                                        Pressable(onClick = { Tts.speak(word, false) }) { Text("朗读", style = OD.Cap.copy(color = OD.CyanDeep)) }
                                    }
                                    FoldStage.E2C -> {
                                        Text("第 2 轮 · 英→汉默写", style = OD.Cap.copy(color = OD.CyanDeep, fontWeight = FontWeight.SemiBold))
                                        Spacer(Modifier.height(12.dp))
                                        Text(meaningText, style = OD.ReadBody.copy(fontSize = 15.sp, lineHeight = 23.sp), textAlign = TextAlign.Center)
                                        Spacer(Modifier.height(14.dp))
                                        Text("请写出对应的英文单词", style = OD.Cap)
                                        Spacer(Modifier.height(8.dp))
                                        FoldAnswerField(answer, { answer = it }, revealed) { if (answer.trim().isNotEmpty()) revealed = true }
                                        if (revealed) {
                                            Spacer(Modifier.height(10.dp))
                                            Text(if (answer.trim().equals(word, ignoreCase = true)) "正确：$word" else "答案：$word（你写的是 ${answer.ifBlank { "空" }}）",
                                                style = OD.H3.copy(fontSize = 13.sp, color = if (answer.trim().equals(word, ignoreCase = true)) OD.Good else OD.Warm))
                                        }
                                    }
                                    FoldStage.C2E -> {
                                        Text("第 3 轮 · 汉→英默写", style = OD.Cap.copy(color = OD.CyanDeep, fontWeight = FontWeight.SemiBold))
                                        Spacer(Modifier.height(12.dp))
                                        Text(word, style = OD.H1.copy(fontSize = 32.sp), textAlign = TextAlign.Center)
                                        Spacer(Modifier.height(6.dp))
                                        Text("请在心中/纸上回想它的中文意思，再显示答案自评", style = OD.Cap, textAlign = TextAlign.Center)
                                        Spacer(Modifier.height(10.dp))
                                        if (!revealed) ODButton("显示释义", { revealed = true }, primary = false)
                                        else Text(meaningText, style = OD.ReadBody.copy(fontSize = 14.sp, lineHeight = 22.sp), textAlign = TextAlign.Center)
                                    }
                                    else -> {}
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        when (stage) {
                            FoldStage.EXPOSE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ODButton("不认识", { wrong = wrong + word; stage = FoldStage.E2C }, primary = false, modifier = Modifier.weight(1f))
                                ODButton("模糊", { stage = FoldStage.E2C }, primary = false, modifier = Modifier.weight(1f))
                                ODButton("认识", { stage = FoldStage.E2C }, modifier = Modifier.weight(1f))
                            }
                            FoldStage.E2C -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ODButton("判错", { judgeE2c(false) }, primary = false, modifier = Modifier.weight(1f))
                                    ODButton("判对", { judgeE2c(true) }, modifier = Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("先写答案再点“显示答案”，也可直接自评。", style = OD.Micro)
                            }
                            FoldStage.C2E -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ODButton("没想起来", { judgeC2e(false) }, primary = false, modifier = Modifier.weight(1f))
                                ODButton("想起来了", { judgeC2e(true) }, modifier = Modifier.weight(1f))
                            }
                            else -> {}
                        }
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldAnswerField(value: String, onValueChange: (String) -> Unit, enabled: Boolean, onDone: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(OD.Parchment).border(1.dp, OD.Hairline, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
        if (!enabled) {
            val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            BasicTextField(
                value = value, onValueChange = onValueChange,
                textStyle = TextStyle(fontSize = 15.sp, color = OD.Ink),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onDone() }),
                decorationBox = { inner -> if (value.isEmpty()) Text("输入英文…", style = OD.Cap) else inner() },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(value.ifBlank { "（空）" }, style = OD.Body.copy(fontSize = 14.sp))
        }
    }
}
