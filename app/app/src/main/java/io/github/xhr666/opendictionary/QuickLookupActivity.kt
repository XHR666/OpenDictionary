package io.github.xhr666.opendictionary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import io.github.xhr666.opendictionary.data.LexiconDb
import io.github.xhr666.opendictionary.data.UserDb
import io.github.xhr666.opendictionary.data.WordData
import io.github.xhr666.opendictionary.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 外部文本选择/分享快速查词：半屏卡片，可加入生词本或打开完整词条。 */
class QuickLookupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT_READONLY)
            ?: ""
        Tts.init(this)
        io.github.xhr666.opendictionary.ui.UiState.load(this)
        setContent { QuickLookup(text) }
    }

    @Composable
    private fun QuickLookup(raw: String) {
        val ctx = LocalContext.current
        val query = remember { raw.trim().substringBefore(' ').substringBefore('\n').trim() }
        var data by remember { mutableStateOf<WordData?>(null) }
        var loading by remember { mutableStateOf(true) }
        var added by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            data = withContext(Dispatchers.IO) { runCatching { LexiconDb.get(ctx).detail(query) }.getOrNull() }
            loading = false
        }
        Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable { finish() }, contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).clickable { }.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (query.isBlank()) "快速查词" else query, style = OD.H2.copy(fontSize = 24.sp), modifier = Modifier.weight(1f))
                    Pressable(onClick = { finish() }) { OIcon(Ic.Close, 18.dp, OD.Muted) }
                }
                Spacer(Modifier.height(4.dp))
                when {
                    loading -> Text("查询中…", style = OD.Cap)
                    data == null -> Text("没有找到这个单词", style = OD.Cap)
                    else -> {
                        val d = data!!
                        if (d.entry.phonetic.isNotBlank()) Text("/${d.entry.phonetic}/", style = OD.Cap)
                        Spacer(Modifier.height(8.dp))
                        d.entry.translation.split("\n").filter { it.isNotBlank() }.take(4).forEach { Text(it, style = OD.ReadBody.copy(fontSize = 13.5.sp, lineHeight = 21.sp)) }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ODButton(if (added) "已加入" else "加入生词本", {
                                UserDb.get(ctx).addToWordbook(d.entry.word); added = true
                            }, primary = false, modifier = Modifier.weight(1f))
                            ODButton("打开完整词条", {
                                startActivity(Intent(ctx, MainActivity::class.java).putExtra("word", d.entry.word).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                finish()
                            }, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
