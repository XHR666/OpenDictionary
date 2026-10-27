package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.Tts
import java.time.LocalDate

data class DailySentence(val en: String, val zh: String, val source: String)

/** 内置每日一句/谚语：公版名言与常见谚语，译文为项目自编。 */
object DailyLibrary {
    val all = listOf(
        DailySentence("Keep your face to the sunshine and you cannot see a shadow.", "把脸朝向阳光，你就看不到阴影。", "Helen Keller"),
        DailySentence("The only way to do great work is to love what you do.", "做出伟大工作的唯一方法，是热爱你所做的事。", "Steve Jobs"),
        DailySentence("It always seems impossible until it's done.", "事情在完成之前，看起来总是 impossible。", "常被误引（出处存疑）"),
        DailySentence("A journey of a thousand miles begins with a single step.", "千里之行，始于足下。", "Lao Tzu / 中国谚语"),
        DailySentence("Where there is a will, there is a way.", "有志者，事竟成。", "英语谚语"),
        DailySentence("Practice makes perfect.", "熟能生巧。", "英语谚语"),
        DailySentence("Better late than never.", "迟做总比不做好。", "英语谚语"),
        DailySentence("Actions speak louder than words.", "行动胜于言语。", "英语谚语"),
        DailySentence("Every cloud has a silver lining.", "黑暗中总有一线光明。", "英语谚语"),
        DailySentence("The best time to plant a tree was twenty years ago. The second best time is now.", "种树最好的时间是二十年前，其次是现在。", "中国谚语"),
        DailySentence("Success is not final, failure is not fatal: it is the courage to continue that counts.", "成功不是终点，失败也非末日，重要的是继续前行的勇气。", "常被误引（出处存疑）"),
        DailySentence("Life is like riding a bicycle. To keep your balance you must keep moving.", "生活像骑自行车，要保持平衡就得不停前行。", "常被误引（出处存疑）"),
        DailySentence("Do what you can, with what you have, where you are.", "在你能做的地方，用你拥有的，做你能做的事。", "Theodore Roosevelt"),
        DailySentence("The future belongs to those who believe in the beauty of their dreams.", "未来属于那些相信梦想之美的人。", "Eleanor Roosevelt"),
        DailySentence("In the middle of every difficulty lies opportunity.", "每一个困难中都藏着机会。", "常被误引（出处存疑）"),
        DailySentence("Little by little, one travels far.", "积跬步，致千里。", "常被误引（出处存疑）"),
        DailySentence("A friend in need is a friend indeed.", "患难见真情。", "英语谚语"),
        DailySentence("What we think, we become.", "我们想什么，就会成为什么。", "Buddha"),
        DailySentence("Time and tide wait for no man.", "岁月不待人。", "英语谚语"),
        DailySentence("Grasp all, lose all.", "贪多必失。", "英语谚语"),
        DailySentence("No pain, no gain.", "不劳无获。", "英语谚语"),
        DailySentence("Knowledge is power.", "知识就是力量。", "Francis Bacon"),
    )
    fun today(): DailySentence = all[(LocalDate.now().toEpochDay() % all.size).toInt()]
}

@Composable
fun DailyScreen(onNavigate: (Route) -> Unit) {
    DisposableEffect(Unit) { onDispose { Tts.stop() } }
    var index by remember { mutableIntStateOf((LocalDate.now().toEpochDay() % DailyLibrary.all.size).toInt()) }
    val cur = DailyLibrary.all[index]
    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            TopBar("每日一句", onBack = { onNavigate(Route.Home) })
            Spacer(Modifier.height(30.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(OD.WarmSoft), contentAlignment = Alignment.Center) { OIcon(Ic.Sparkle, 20.dp, OD.Warm) }
                Spacer(Modifier.height(16.dp))
                Text(cur.en, style = OD.ReadBody.copy(fontSize = 20.sp, lineHeight = 30.sp, color = OD.Ink), textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text(cur.zh, style = OD.Body.copy(fontSize = 14.sp, lineHeight = 22.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("— ${cur.source}", style = OD.Cap)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ODButton("朗读", { Tts.speak(cur.en, false); if (!Tts.isReady()) Tts.reportNotReady() }, primary = false, modifier = Modifier.weight(1f))
                    ODButton("下一条", {
                        index = (index + 1) % DailyLibrary.all.size
                    }, modifier = Modifier.weight(1f))
                }
                Tts.lastError?.let { err -> Spacer(Modifier.height(8.dp)); Text(err, style = OD.Cap.copy(color = OD.Warm, lineHeight = 16.sp), textAlign = TextAlign.Center) }
            }
            Spacer(Modifier.height(18.dp))
            Text("每天一句，慢慢积累语感与表达。部分引文为广泛流传文本，归属可能存争议，仅作学习示例。", style = OD.Cap.copy(color = OD.Muted), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}
