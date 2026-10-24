package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.Tts
import io.github.xhr666.opendictionary.data.LexEntry
import io.github.xhr666.opendictionary.data.LexiconDb
import io.github.xhr666.opendictionary.data.UserDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReviewScreen(onNavigate: (Route) -> Unit) {
    DisposableEffect(Unit) { onDispose { Tts.stop() } }
    val ctx = LocalContext.current
    val lex = remember { LexiconDb.get(ctx) }
    val userDb = remember { UserDb.get(ctx) }
    var words by remember { mutableStateOf<List<LexEntry>>(emptyList()) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var savedWord by rememberSaveable { mutableStateOf<String?>(null) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var c0 by rememberSaveable { mutableIntStateOf(0) }
    var c1 by rememberSaveable { mutableIntStateOf(0) }
    var c2 by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        words = withContext(Dispatchers.IO) {
            val book = userDb.wordbookWords()
            val ordered = if (book.isNotEmpty()) userDb.dueReviewWords(book).take(30) else userDb.dueReviewWords(lex.gkWords(30).map { it.word })
            if (ordered.isNotEmpty()) lex.entriesByWords(ordered) else lex.gkWords(20)
        }
        val sw = savedWord
        if (sw != null) {
            val pos = words.indexOfFirst { it.word == sw }
            index = if (pos >= 0) pos else 0
            revealed = false
        }
    }
    LaunchedEffect(index, words) { savedWord = words.getOrNull(index)?.word }
    Backdrop {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp)) { TopBar("复习", onBack = { onNavigate(Route.Learning) }) }
            if (words.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(60.dp)); OIcon(Ic.Book, 36.dp, OD.Hairline)
                    Spacer(Modifier.height(12.dp)); Text("还没有可复习的词", style = OD.H2)
                    Spacer(Modifier.height(6.dp)); Text("先查几个词并加入生词本，或直接复习高考词汇。", style = OD.Cap)
                    Spacer(Modifier.height(16.dp)); ODButton("去学习页", { onNavigate(Route.Learning) }, primary = false)
                }
            } else if (index >= words.size) {
                Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(50.dp))
                    Box(Modifier.size(64.dp).clip(CircleShape).background(OD.CyanSoft), contentAlignment = Alignment.Center) { OIcon(Ic.Check, 28.dp, OD.CyanDeep) }
                    Spacer(Modifier.height(14.dp)); Text("本轮复习完成", style = OD.H2)
                    Spacer(Modifier.height(6.dp)); Text("认识 $c2 · 模糊 $c1 · 不认识 $c0", style = OD.Cap)
                    Spacer(Modifier.height(18.dp)); ODButton("返回学习页", { onNavigate(Route.Learning) })
                }
            } else {
                val w = words[index]
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(10.dp))
                    Text("${index + 1} / ${words.size}", style = OD.Micro.copy(color = OD.Muted))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(OD.Hairline)) {
                        Box(Modifier.fillMaxWidth((index + 1f) / words.size).height(4.dp).background(OD.Cyan))
                    }
                    Spacer(Modifier.height(26.dp))
                    Text(w.word, style = OD.H1.copy(fontSize = 38.sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    if (w.phonetic.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("/${w.phonetic}/", style = OD.Cap.copy(fontSize = 13.sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(12.dp))
                    Pressable(onClick = { Tts.speak(w.word, false) }) {
                        Row(Modifier.clip(RoundedCornerShape(50)).background(OD.CyanSoft).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OIcon(Ic.Play, 12.dp, OD.CyanDeep); Spacer(Modifier.width(4.dp)); Text("朗读", style = OD.Cap.copy(fontSize = 11.sp, color = OD.CyanDeep))
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    if (!revealed) {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(16.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("先回忆它的意思", style = OD.H3)
                                Spacer(Modifier.height(5.dp)); Text("然后再看答案", style = OD.Cap)
                                Spacer(Modifier.height(12.dp)); ODButton("显示释义", { revealed = true }, primary = false)
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(16.dp)).padding(16.dp)) {
                            w.translation.split("\n").filter { it.isNotBlank() }.take(6).forEach { line -> Text(line, style = OD.ReadBody) }
                            if (w.definition.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(w.definition.split("\n").first().take(160), style = OD.Cap.copy(lineHeight = 17.sp))
                            }
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                    if (revealed) {
                        Text("这个词你记得怎样？", style = OD.Cap.copy(color = OD.Muted), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ODButton("不认识", onClick = { c0++; userDb.recordReview(w.word, 0); nextWord(++index) },
                                primary = false, modifier = Modifier.weight(1f))
                            ODButton("模糊", onClick = { c1++; userDb.recordReview(w.word, 1); nextWord(++index) },
                                primary = false, modifier = Modifier.weight(1f))
                            ODButton("认识", onClick = { c2++; userDb.recordReview(w.word, 2); nextWord(++index) },
                                modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Pressable(onClick = { revealed = false; index++ }) { Text("跳过", style = OD.Cap.copy(color = OD.Muted), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

private fun nextWord(n: Int) { /* index handled by caller state */ }
