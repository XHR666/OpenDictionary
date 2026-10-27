package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.Tts
import io.github.xhr666.opendictionary.data.LexEntry
import io.github.xhr666.opendictionary.data.LexiconDb
import io.github.xhr666.opendictionary.data.UserDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 快速卡片刷词：整词书/生词本随机过一遍，翻面自评。 */
@Composable
fun CardModeScreen(bookId: Long, onNavigate: (Route) -> Unit) {
    DisposableEffect(Unit) { onDispose { Tts.stop() } }
    val ctx = LocalContext.current
    val lex = remember { LexiconDb.get(ctx) }
    val db = remember { UserDb.get(ctx) }
    var words by remember { mutableStateOf<List<LexEntry>>(emptyList()) }
    var queue by remember { mutableStateOf<List<LexEntry>>(emptyList()) }
    var seed by rememberSaveable { mutableIntStateOf((0..Int.MAX_VALUE).random()) }
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var known by rememberSaveable { mutableIntStateOf(0) }
    var unknown by rememberSaveable { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("卡片刷词") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = if (bookId > 0) {
                val book = db.wordbooks().firstOrNull { it.id == bookId }
                title = book?.name ?: "卡片刷词"
                lex.entriesByWords(db.wordsInBook(bookId))
            } else {
                val def = db.wordbooks().firstOrNull { it.isDefault }
                if (def != null) { title = def.name; lex.entriesByWords(db.wordsInBook(def.id)) }
                else lex.gkWords(30)
            }
            words = list.shuffled(kotlin.random.Random(seed))
            queue = words
        }
    }
    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            TopBar(title, onBack = { onNavigate(Route.Learning) })
            if (queue.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(80.dp)); OIcon(Ic.Book, 34.dp, OD.Hairline)
                    Spacer(Modifier.height(10.dp)); Text("词书里还没有单词", style = OD.H2)
                    Spacer(Modifier.height(6.dp)); Text("先在词条页加入生词本，或使用高考词汇。", style = OD.Cap)
                    Spacer(Modifier.height(14.dp)); ODButton("去词典", { onNavigate(Route.Search("")) }, primary = false)
                }
            } else if (idx >= queue.size) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(60.dp))
                    Box(Modifier.size(64.dp).clip(CircleShape).background(OD.CyanSoft), contentAlignment = Alignment.Center) { OIcon(Ic.Check, 28.dp, OD.CyanDeep) }
                    Spacer(Modifier.height(12.dp)); Text("这一轮刷完了", style = OD.H2)
                    Spacer(Modifier.height(6.dp)); Text("认识 $known · 不认识 $unknown", style = OD.Cap)
                    Spacer(Modifier.height(16.dp)); ODButton("再来一轮", { queue = words.shuffled(); idx = 0; revealed = false; known = 0; unknown = 0 })
                    Spacer(Modifier.height(8.dp)); ODButton("返回学习", { onNavigate(Route.Learning) }, primary = false)
                }
            } else {
                val e = queue[idx]
                Text("${idx + 1} / ${queue.size}", style = OD.Micro)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(OD.Hairline)) {
                    Box(Modifier.fillMaxWidth((idx + 1f) / queue.size).height(4.dp).background(OD.Cyan))
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(22.dp)).padding(22.dp)) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(e.word, style = OD.H1.copy(fontSize = 34.sp), textAlign = TextAlign.Center)
                            if (e.phonetic.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("/${e.phonetic}/", style = OD.Cap) }
                            Spacer(Modifier.height(8.dp))
                            Pressable(onClick = { Tts.speak(e.word, false) }) { Text("朗读", style = OD.Cap.copy(color = OD.CyanDeep)) }
                            Spacer(Modifier.height(18.dp))
                            if (revealed) {
                                e.translation.split("\n").filter { it.isNotBlank() }.take(5).forEach { line -> Text(line, style = OD.ReadBody.copy(fontSize = 14.sp, lineHeight = 22.sp), textAlign = TextAlign.Center) }
                            } else {
                                Text("点击显示释义", style = OD.Cap)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (!revealed) {
                        ODButton("显示释义", { revealed = true }, modifier = Modifier.fillMaxWidth())
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ODButton("不认识", { unknown++; db.recordReview(e.word, 0); revealed = false; idx++ }, primary = false, modifier = Modifier.weight(1f))
                            ODButton("认识", { known++; db.recordReview(e.word, 2); revealed = false; idx++ }, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Pressable(onClick = { revealed = false; idx++ }) { Text("跳过", style = OD.Cap.copy(color = OD.Muted), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}


