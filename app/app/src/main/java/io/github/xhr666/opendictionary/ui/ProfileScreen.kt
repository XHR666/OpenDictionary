package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import io.github.xhr666.opendictionary.R
import io.github.xhr666.opendictionary.data.DayDetail
import io.github.xhr666.opendictionary.data.UserDb
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
private val cnFmt = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

private fun heatColor(n: Int): Color = when {
    n <= 0 -> OD.Hairline.copy(alpha = .55f)
    n <= 2 -> OD.Cyan.copy(alpha = .22f)
    n <= 5 -> OD.Cyan.copy(alpha = .42f)
    n <= 9 -> OD.Cyan.copy(alpha = .66f)
    else -> OD.CyanDeep
}

@Composable
fun ProfileScreen(onNavigate: (Route) -> Unit) {
    val context = LocalContext.current
    val db = remember { UserDb.get(context) }
    var stats by remember { mutableStateOf(db.stats()) }
    var map by remember { mutableStateOf(db.activityMap()) }
    var demo by remember { mutableStateOf(db.isDemoSeeded()) }
    var recent by remember { mutableStateOf(db.recentQueries(8)) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val today = remember { LocalDate.now() }
    val start = remember {
        var s = today.minusDays(370)
        s = s.minusDays((s.dayOfWeek.value % 7).toLong())
        s
    }
    LaunchedEffect(Unit) { db.seedDemoIfNeeded(); stats = db.stats(); map = db.activityMap(); demo = db.isDemoSeeded(); recent = db.recentQueries(8) }

    Backdrop {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_logo), null, Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("本地用户", style = OD.H2)
                            if (demo) {
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.clip(RoundedCornerShape(5.dp)).background(OD.WarmSoft).padding(horizontal = 5.dp, vertical = 2.dp)) {
                                    Text("含演示数据", style = OD.Micro.copy(color = OD.Warm, fontSize = 8.5.sp))
                                }
                            }
                        }
                        Text("无需账号 · 数据只保存在本机", style = OD.Cap.copy(fontSize = 10.5.sp))
                    }
                    Pressable(onClick = { onNavigate(Route.Settings) }) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White).border(1.dp, OD.Hairline, CircleShape), contentAlignment = Alignment.Center) {
                            OIcon(Ic.Settings, 17.dp, OD.InkSoft)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("连续学习", "${stats.streak} 天", Modifier.weight(1f))
                    StatCard("累计查询", "${stats.totalQueries}", Modifier.weight(1f))
                    StatCard("已学词", "${stats.learnedWords}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                // 热力图
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(18.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("学习足迹", style = OD.H3)
                        Spacer(Modifier.weight(1f))
                        Text("近 53 周", style = OD.Micro)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("点击方格查看当日详情；每日查询/学习越多，颜色越深。", style = OD.Micro.copy(fontSize = 9.5.sp))
                    Spacer(Modifier.height(10.dp))
                    TooltipCard(selected, map, onOpenDay = { day -> onNavigate(Route.Day(day)) })
                    Heatmap(start = start, today = today, map = map, onSelect = { selected = it })
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("少", style = OD.Micro.copy(fontSize = 9.sp))
                        Spacer(Modifier.width(4.dp))
                        listOf(0, 1, 3, 6, 10).forEach { n ->
                            Box(Modifier.padding(horizontal = 1.dp).size(9.dp).clip(RoundedCornerShape(2.dp)).background(heatColor(n)))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("多", style = OD.Micro.copy(fontSize = 9.sp))
                        Spacer(Modifier.weight(1f))
                        if (demo) {
                            LinkText("清除演示数据", onClick = { db.clearDemoData(); demo = false; map = db.activityMap(); stats = db.stats(); recent = db.recentQueries(8) }, style = OD.Micro.copy(fontSize = 9.sp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("活跃天数", "${stats.activeDays} 天", Modifier.weight(1f))
                    StatCard("笔记", "${stats.noteCount}", Modifier.weight(1f))
                    StatCard("生词本", "详情", Modifier.weight(1f), onClick = { onNavigate(Route.Learning) })
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("最近查询", style = OD.H3)
                    Spacer(Modifier.weight(1f))
                    if (recent.isNotEmpty()) Pressable(onClick = { onNavigate(Route.Learning) }) { Text("全部", style = OD.Cap.copy(color = OD.CyanDeep)) }
                }
                Spacer(Modifier.height(6.dp))
                if (recent.isEmpty()) {
                    Text("还没有查询记录。去首页查第一个词吧。", style = OD.Cap)
                } else {
                    recent.forEach { r ->
                        Pressable(onClick = { onNavigate(Route.Word(r.word)) }) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(26.dp).clip(CircleShape).background(OD.CyanSoft), contentAlignment = Alignment.Center) { Text(r.word.take(1).uppercase(), style = OD.Micro.copy(color = OD.CyanDeep, fontWeight = FontWeight.Bold)) }
                                Spacer(Modifier.width(10.dp))
                                Text(r.word, style = OD.H3.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                                Text(android.text.format.DateFormat.format("MM-dd HH:mm", r.ts).toString(), style = OD.Micro)
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .55f)))
                    }
                }
                Spacer(Modifier.height(90.dp))
            }
            Spacer(Modifier.height(92.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Pressable(modifier, { onClick?.invoke() }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(14.dp)).padding(11.dp)) {
            Text(label, style = OD.Micro.copy(fontSize = 9.5.sp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = OD.H3.copy(fontSize = 15.sp, fontFamily = UiFont))
        }
    }
}

@Composable
private fun TooltipCard(selected: LocalDate?, map: Map<String, Int>, onOpenDay: (String) -> Unit) {
    if (selected == null) {
        Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(9.dp)).background(OD.Pearl).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            Text("点一个方格看看当天做了什么", style = OD.Micro)
        }
        Spacer(Modifier.height(8.dp))
        return
    }
    val key = selected.format(dateFmt)
    val n = map[key] ?: 0
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(OD.CyanSoft.copy(alpha = .55f)).border(1.dp, OD.Cyan.copy(alpha = .25f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(selected.format(cnFmt), style = OD.Cap.copy(fontSize = 11.sp, color = OD.Ink, fontWeight = FontWeight.SemiBold))
            Text(if (n == 0) "当天没有学习记录" else "查询/学习 $n 次", style = OD.Micro.copy(fontSize = 9.5.sp))
        }
        Pressable(onClick = { onOpenDay(key) }) { Text("当天详情 →", style = OD.Micro.copy(fontSize = 9.5.sp, color = OD.CyanDeep, fontWeight = FontWeight.SemiBold)) }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Heatmap(start: LocalDate, today: LocalDate, map: Map<String, Int>, onSelect: (LocalDate) -> Unit) {
    val weeks = remember(start, today) {
        val weeks = mutableListOf<List<LocalDate>>()
        var cur = start
        while (!cur.isAfter(today)) {
            val week = mutableListOf<LocalDate>()
            repeat(7) { week.add(cur); cur = cur.plusDays(1) }
            weeks.add(week)
        }
        weeks
    }
    val cell = 11.dp
    val gap = 2.dp
    val scroll = rememberScrollState()
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(18.dp).padding(top = 16.dp).height((7 * 13 - 2).dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { d -> Text(d, style = OD.Micro.copy(fontSize = 7.sp)) }
        }
        Column(Modifier.horizontalScroll(scroll)) {
            Row {
                weeks.forEach { week ->
                    val first = week.first()
                    Box(Modifier.width(cell + gap)) {
                        if (first.dayOfMonth <= 7) Text("${first.monthValue}月", style = OD.Micro.copy(fontSize = 8.sp, color = OD.Muted), maxLines = 1)
                        else Spacer(Modifier.height(10.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row {
                weeks.forEach { week ->
                    Column(Modifier.width(cell + gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                        week.forEach { day ->
                            val n = map[day.format(dateFmt)] ?: 0
                            Box(Modifier.size(cell).clip(RoundedCornerShape(2.5.dp)).background(heatColor(n))
                                .semantics { contentDescription = "${day.monthValue}月${day.dayOfMonth}日，${if (n == 0) "无学习记录" else "$n 次学习"}" }
                                .clickable { onSelect(day) })
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
fun DayDetailScreen(day: String, onNavigate: (Route) -> Unit) {
    val context = LocalContext.current
    val db = remember { UserDb.get(context) }
    val detail = remember(day) { db.dayDetail(day) }
    PageScaffold("当日详情", { onNavigate(Route.Profile) }) {
        Text(runCatching { LocalDate.parse(day).format(cnFmt) }.getOrDefault(day), style = OD.H2, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(4.dp))
        Text(day, style = OD.Cap)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("查询", "${detail.stat?.queries ?: detail.queries.size}", Modifier.weight(1f))
            StatCard("学习词", "${detail.stat?.words ?: 0}", Modifier.weight(1f))
            StatCard("专注分钟", "${detail.stat?.minutes ?: 0}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Text("当天查询", style = OD.H3)
        Spacer(Modifier.height(4.dp))
        if (detail.queries.isEmpty()) {
            Text("当天没有查询记录。", style = OD.Cap)
        } else {
            detail.queries.forEach { q ->
                Pressable(onClick = { onNavigate(Route.Word(q)) }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(OD.CyanSoft), contentAlignment = Alignment.Center) { Text(q.take(1).uppercase(), style = OD.Micro.copy(color = OD.CyanDeep, fontWeight = FontWeight.Bold)) }
                        Spacer(Modifier.width(10.dp))
                        Text(q, style = OD.H3.copy(fontSize = 13.sp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .55f)))
            }
        }
    }
}

@Composable
private fun HomeFooterNav(current: Int, onNavigate: (Route) -> Unit) {
    GlassNav(Modifier.padding(bottom = 10.dp).padding(top = 8.dp)) {
        val items = listOf(Ic.Home to "首页", Ic.Book to "词典", Ic.Headphone to "学习", Ic.Grid to "我的")
        items.forEachIndexed { i, (icon, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Pressable(onClick = {
                    when (i) {
                        0 -> onNavigate(Route.Home)
                        1 -> onNavigate(Route.Word("reputation"))
                        2 -> onNavigate(Route.Learning)
                        3 -> {}
                    }
                }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OIcon(icon, 20.dp, if (i == current) OD.CyanDeep else OD.Muted)
                        Spacer(Modifier.height(2.dp))
                        Text(label, style = OD.Micro.copy(color = if (i == current) OD.CyanDeep else OD.Muted, fontWeight = if (i == current) FontWeight.SemiBold else FontWeight.Normal))
                    }
                }
            }
        }
    }
}
