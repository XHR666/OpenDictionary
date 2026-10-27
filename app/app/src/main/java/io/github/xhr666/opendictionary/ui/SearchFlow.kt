package io.github.xhr666.opendictionary.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.data.UserDb
import kotlinx.coroutines.delay

/** 全屏搜索：首页搜索框上移动画、自动输入法、历史记录。 */
@Composable
fun SearchInputScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var entered by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val history = remember { UserDb.get(context).recentQueries(12) }
    LaunchedEffect(Unit) {
        // 先让最终布局稳定落一帧，再淡入并聚焦；避免输入框从下方“回退再上升”的位移动画。
        withFrameNanos { }
        entered = true
        delay(200)
        runCatching { focus.requestFocus(); keyboard?.show() }
    }
    val contentAlpha by animateFloatAsState(if (entered) 1f else 0f, tween(180), label = "searchFade")
    fun submit() {
        val q = text.trim()
        if (q.isNotEmpty()) { keyboard?.hide(); onNavigate(Route.Search(q)) }
    }
    Backdrop {
        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) { OIcon(Ic.Back, 20.dp, OD.Ink) }
                Text("搜索", style = OD.H2, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(54.dp).alpha(contentAlpha).clip(RoundedCornerShape(17.dp)).background(Color.White)
                    .border(1.dp, OD.Hairline, RoundedCornerShape(17.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OIcon(Ic.Search, 18.dp, OD.Muted)
                Spacer(Modifier.width(9.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("输入单词、词组或中文", style = OD.Body.copy(fontSize = 14.sp, color = OD.Muted))
                    BasicTextField(
                        value = text, onValueChange = { text = it },
                        textStyle = TextStyle(fontSize = 15.sp, color = OD.Ink, fontWeight = FontWeight.Medium),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (text.isNotEmpty()) {
                    Pressable(onClick = { text = "" }) { OIcon(Ic.Close, 16.dp, OD.Muted) }
                    Spacer(Modifier.width(8.dp))
                }
                Pressable(onClick = { submit() }) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(OD.Cyan), contentAlignment = Alignment.Center) { OIcon(Ic.Search, 14.dp, Color.White) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("搜索单词、词组；中文可直接反查英文。点历史记录快速回到词条。", style = OD.Micro, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(18.dp))
            if (history.isNotEmpty()) {
                // 历史列表也保持左右留白，避免贴边
                Text("最近查词", style = OD.H3)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    history.forEach { r ->
                        Pressable(onClick = { onNavigate(Route.Word(r.word)) }) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(26.dp).clip(CircleShape).background(OD.CyanSoft), contentAlignment = Alignment.Center) {
                                    Text(r.word.take(1).uppercase(), style = OD.Micro.copy(color = OD.CyanDeep, fontWeight = FontWeight.Bold))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(r.word, style = OD.H3.copy(fontSize = 13.5.sp), modifier = Modifier.weight(1f))
                                Text(android.text.format.DateFormat.format("MM-dd HH:mm", r.ts).toString(), style = OD.Micro)
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .55f)))
                    }
                    Spacer(Modifier.height(80.dp))
                }
            } else {
                Text("还没有查询记录。输入一个词开始吧。", style = OD.Cap, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
