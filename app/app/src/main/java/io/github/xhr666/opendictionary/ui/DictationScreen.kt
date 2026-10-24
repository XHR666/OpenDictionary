package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.Tts
import io.github.xhr666.opendictionary.data.LexEntry
import io.github.xhr666.opendictionary.data.LexiconDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 听写/默写：TTS 读词或显示中文，输入英文并即时判分。 */
@Composable
fun DictationScreen(onNavigate: (Route) -> Unit) {
    DisposableEffect(Unit) { onDispose { Tts.stop() } }
    val ctx = LocalContext.current
    val lex = remember { LexiconDb.get(ctx) }
    val userDb = remember { io.github.xhr666.opendictionary.data.UserDb.get(ctx) }
    var sourceName by rememberSaveable { mutableStateOf("高考词汇") }
    val prefs = remember { ctx.getSharedPreferences("od_prefs", android.content.Context.MODE_PRIVATE) }
    var bookId by rememberSaveable { mutableLongStateOf(prefs.getLong("recite_book_id", -1L)) }
    var books by remember { mutableStateOf(userDb.wordbooks()) }
    var showBookSheet by remember { mutableStateOf(false) }
    var pendingBook by remember { mutableLongStateOf(-1L) }
    var showBookConfirm by remember { mutableStateOf(false) }
    var showModeConfirm by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableIntStateOf(0) }
    var words by remember { mutableStateOf<List<LexEntry>>(emptyList()) }
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 听写 1 默写
    var answer by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf(false) }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    var wrong by rememberSaveable { mutableIntStateOf(0) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var rate by remember { mutableFloatStateOf(prefs.getFloat("tts_rate", 1f)) }
    LaunchedEffect(bookId) {
        Tts.setRate(rate)
        books = withContext(Dispatchers.IO) { userDb.wordbooks() }
        if (bookId > 0) {
            sourceName = books.firstOrNull { it.id == bookId }?.name ?: "词书"
            words = withContext(Dispatchers.IO) { lex.entriesByWords(userDb.wordsInBook(bookId).take(30)) }
        } else {
            sourceName = "高考词汇"
            words = withContext(Dispatchers.IO) { lex.gkWords(30) }
        }
        mode = prefs.getInt("recite_mode_$bookId", mode).coerceIn(0, 1)
        idx = 0; correct = 0; wrong = 0; answer = ""; checked = false; revealed = false
        if (words.isNotEmpty() && mode == 0) Tts.speak(words[0].word, false)
    }
    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            TopBar("背诵", onBack = { onNavigate(Route.Learning) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("音→词", mode == 0) { if (mode != 0) { if (idx > 0 || answer.isNotBlank()) { pendingMode = 0; showModeConfirm = true } else { mode = 0; prefs.edit().putInt("recite_mode_$bookId", 0).apply(); answer = ""; checked = false } } }
                Pill("中→词", mode == 1) { if (mode != 1) { if (idx > 0 || answer.isNotBlank()) { pendingMode = 1; showModeConfirm = true } else { mode = 1; prefs.edit().putInt("recite_mode_$bookId", 1).apply(); answer = ""; checked = false } } }
                Pill("词书", false) { showBookSheet = true }
                Spacer(Modifier.weight(1f))
                Pill("IELTS 听力练习", false) { onNavigate(Route.ListeningPractice) }
            }
            Text("来源：$sourceName · 同一词书可用两种方式背诵", style = OD.Micro)
            if (idx < words.size) Text("${idx + 1} / ${words.size}", style = OD.Micro)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("语速", style = OD.Micro)
                listOf(0.8f to "慢", 1f to "正常", 1.2f to "快").forEach { (r, label) ->
                    Pill(label, kotlin.math.abs(rate - r) < 0.01f) {
                        rate = r; prefs.edit().putFloat("tts_rate", r).apply(); Tts.setRate(r)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (words.isEmpty()) {
                Text("高考词汇为空，请稍后重试。", style = OD.Cap)
            } else if (idx >= words.size) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(60.dp)); Text("本轮完成", style = OD.H2)
                    Spacer(Modifier.height(6.dp)); Text("正确 $correct · 错误 $wrong", style = OD.Cap)
                    Spacer(Modifier.height(16.dp)); ODButton("再来一轮", { idx = 0; correct = 0; wrong = 0; answer = ""; checked = false })
                }
            } else {
                val e = words[idx]
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(20.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (mode == 0) {
                        Pressable(onClick = { Tts.speak(e.word, false) }) {
                            Box(Modifier.size(64.dp).clip(CircleShape).background(OD.CyanSoft), contentAlignment = Alignment.Center) { OIcon(Ic.Play, 26.dp, OD.CyanDeep) }
                        }
                        Spacer(Modifier.height(8.dp)); Text("听发音，写出单词（音→词）", style = OD.Cap)
                    } else {
                        Text(e.translation.split("\n").first().take(80), style = OD.ReadBody.copy(fontSize = 15.sp, lineHeight = 23.sp), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp)); Text("根据中文写出英文（中→词）", style = OD.Cap)
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(OD.Parchment).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        BasicTextField(value = answer, onValueChange = { answer = it; checked = false }, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = OD.Ink), decorationBox = { inner -> if (answer.isEmpty()) Text("输入英文…", style = OD.Cap) else inner() }, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(10.dp))
                    when {
                        !checked -> ODButton("检查", {
                            val ok = answer.trim().equals(e.word, ignoreCase = true)
                            checked = true; revealed = true
                            if (ok) correct++ else wrong++
                        }, modifier = Modifier.fillMaxWidth())
                        revealed -> {
                            Text(if (answer.trim().equals(e.word, ignoreCase = true)) "正确：${e.word}" else "答案：${e.word}（你写的是 ${answer.ifBlank { "空" }}）",
                                style = OD.H3.copy(fontSize = 14.sp, color = if (answer.trim().equals(e.word, ignoreCase = true)) OD.Good else OD.Warm))
                            Spacer(Modifier.height(10.dp))
                            ODButton(if (idx + 1 >= words.size) "查看结果" else "下一词", {
                                idx++; answer = ""; checked = false; revealed = false
                                if (idx < words.size) Tts.speak(words[idx].word, false)
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
    if (showModeConfirm) {
        DialogCard(visible = true, onDismiss = { showModeConfirm = false }) {
            Text("切换背诵方式？", style = OD.H2)
            Spacer(Modifier.height(6.dp))
            Text("同一词书可以用「音→词」和「中→词」两种方式背诵。切换会重新开始本轮，确定继续？", style = OD.Cap.copy(lineHeight = 17.sp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("继续本轮", { showModeConfirm = false }, primary = false, modifier = Modifier.weight(1f))
                ODButton("重新开始", {
                    mode = pendingMode; prefs.edit().putInt("recite_mode_$bookId", pendingMode).apply()
                    idx = 0; correct = 0; wrong = 0; answer = ""; checked = false; revealed = false
                    showModeConfirm = false
                    if (words.isNotEmpty() && pendingMode == 0) Tts.speak(words[0].word, false)
                }, modifier = Modifier.weight(1f))
            }
        }
    }
    if (showBookSheet) {
        Sheet(showBookSheet, { showBookSheet = false }) {
            Text("选择背诵词书", style = OD.H2)
            Spacer(Modifier.height(4.dp))
            Text("当前：$sourceName；每个词书会记住上次使用的背诵方式。", style = OD.Cap)
            Spacer(Modifier.height(8.dp))
            Pressable(onClick = {
                if (idx > 0 || answer.isNotBlank()) { pendingBook = -1L; showBookConfirm = true } else { bookId = -1; prefs.edit().putLong("recite_book_id", -1L).apply(); showBookSheet = false }
            }) { Text("高考词汇", style = OD.H3.copy(fontSize = 13.sp, color = if (bookId == -1L) OD.CyanDeep else OD.Ink), modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) }
            books.forEach { b ->
                Pressable(onClick = {
                    if (idx > 0 || answer.isNotBlank()) { pendingBook = b.id; showBookConfirm = true } else { bookId = b.id; prefs.edit().putLong("recite_book_id", b.id).apply(); showBookSheet = false }
                }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, style = OD.H3.copy(fontSize = 13.sp, color = if (bookId == b.id) OD.CyanDeep else OD.Ink), modifier = Modifier.weight(1f))
                        Text("${b.count} 词", style = OD.Micro)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            ODButton("关闭", { showBookSheet = false }, primary = false, modifier = Modifier.fillMaxWidth())
        }
    }
    if (showBookConfirm) {
        DialogCard(visible = true, onDismiss = { showBookConfirm = false }) {
            Text("切换背诵词书？", style = OD.H2)
            Spacer(Modifier.height(6.dp))
            Text("切换词书会重新开始本轮，确定继续？", style = OD.Cap)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("继续本轮", { showBookConfirm = false }, primary = false, modifier = Modifier.weight(1f))
                ODButton("重新开始", {
                    bookId = pendingBook; prefs.edit().putLong("recite_book_id", pendingBook).apply()
                    showBookConfirm = false; showBookSheet = false
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}
