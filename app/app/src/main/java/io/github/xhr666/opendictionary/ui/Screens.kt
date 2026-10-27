package io.github.xhr666.opendictionary.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import io.github.xhr666.opendictionary.R
import io.github.xhr666.opendictionary.Tts
import io.github.xhr666.opendictionary.data.UserDb
import io.github.xhr666.opendictionary.llm.ModelStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

// ---------- 通用脚手架 ----------
val LocalPageScrollStates = androidx.compose.runtime.compositionLocalOf<MutableMap<String, androidx.compose.foundation.ScrollState>?> { null }

@Composable
fun PageScaffold(title: String, onBack: (() -> Unit)?, trailing: @Composable RowScope.() -> Unit = {}, contentScroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val pageScrollStore = LocalPageScrollStates.current
    val pageScroll = remember(title) { pageScrollStore?.getOrPut(title) { androidx.compose.foundation.ScrollState(0) } ?: androidx.compose.foundation.ScrollState(0) }
    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 8.dp)) {
            TopBar(title, onBack, trailing)
            Column(Modifier.weight(1f).then(if (contentScroll) Modifier.verticalScroll(pageScroll) else Modifier)) {
                content()
                if (contentScroll) Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun HomeBottomNav(current: Int, onNav: (Int) -> Unit) {
    GlassNav(Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp).padding(top = 8.dp)) {
        val items = listOf(Ic.Home to "首页", Ic.Book to "词典", Ic.Headphone to "学习", Ic.Grid to "我的")
        items.forEachIndexed { i, (icon, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Pressable(onClick = { onNav(i) }) {
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

// ---------- 首页 ----------
@Composable
fun HomeScreen(onNavigate: (Route) -> Unit) {
    val homeCtx = androidx.compose.ui.platform.LocalContext.current
    val homeDb = remember { UserDb.get(homeCtx) }
    var showDaily by remember { mutableStateOf(false) }
    var learnBookName by remember { mutableStateOf("高考核心词汇") }
    var learnTotal by remember { mutableIntStateOf(0) }
    var learnDone by remember { mutableIntStateOf(0) }
    var dueCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val book = homeDb.wordbooks().firstOrNull { it.isDefault }
            learnBookName = book?.name ?: "高考核心词汇"
            learnTotal = book?.count ?: 0
            learnDone = book?.let { homeDb.learnedInBook(it.id) } ?: 0
            dueCount = homeDb.reviewDueCount()
        }
    }
    Backdrop {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_logo), null, Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("OpenDictionary", style = OD.H3.copy(fontSize = 15.sp))
                        Text("离线词典 · 学习", style = OD.Micro.copy(fontSize = 9.5.sp))
                    }
                    Pressable(onClick = { onNavigate(Route.Settings) }) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White).border(1.dp, OD.Hairline, CircleShape), contentAlignment = Alignment.Center) { OIcon(Ic.Settings, 16.dp, OD.InkSoft) }
                    }
                }
                Spacer(Modifier.height(26.dp))
                // 搜索入口：点击进入全屏搜索（输入框上移动画）
                Pressable(onClick = { onNavigate(Route.SearchInput) }) {
                    Row(Modifier.fillMaxWidth().height(56.dp).liquidGlass(RoundedCornerShape(18.dp), Color.White.copy(alpha = .20f)).border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        OIcon(Ic.Search, 18.dp, OD.Muted)
                        Spacer(Modifier.width(10.dp))
                        Text("输入单词、词组或中文", style = OD.Body.copy(fontSize = 14.sp, color = OD.Muted), modifier = Modifier.weight(1f))
                        Box(Modifier.size(30.dp).clip(CircleShape).background(OD.Cyan), contentAlignment = Alignment.Center) { OIcon(Ic.Search, 14.dp, Color.White) }
                    }
                }
                Spacer(Modifier.height(18.dp))
                // 功能宫格
                val functions = listOf(
                    Triple(Ic.Search, "查词", Route.Search("")),
                    Triple(Ic.Book, "单词本", Route.Learning),
                    Triple(Ic.Sparkle, "每日一句", Route.Daily),
                    Triple(Ic.Check, "复习", Route.Review),
                    Triple(Ic.Headphone, "听力", Route.Listening),
                    Triple(Ic.Book, "写作", Route.Writing),
                    Triple(Ic.Book, "阅读", Route.Reading),
                    Triple(Ic.Book, "真题", Route.ExamReading),
                    Triple(Ic.Headphone, "音乐", Route.Media),
                    Triple(Ic.Check, "卡片", Route.CardMode(-1L)),
                    Triple(Ic.Info, "笔记", Route.Notes),
                    Triple(Ic.Sparkle, "AI", Route.Llm)
                )
                functions.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { (icon, label, route) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Pressable(onClick = { if (route == null) showDaily = true else onNavigate(route) }) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                                            OIcon(icon, 21.dp, OD.CyanDeep)
                                        }
                                        Spacer(Modifier.height(5.dp))
                                        Text(label, style = OD.Cap.copy(fontSize = 10.5.sp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Spacer(Modifier.height(4.dp))
                // 每日一句
                Pressable(onClick = { onNavigate(Route.Daily) }) {
                    Column(Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(18.dp), Color.White.copy(alpha = .18f)).border(1.dp, Color.White.copy(alpha = .5f), RoundedCornerShape(18.dp)).padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(OD.WarmSoft), contentAlignment = Alignment.Center) { OIcon(Ic.Sparkle, 13.dp, OD.Warm) }
                            Spacer(Modifier.width(8.dp))
                            Text("每日一句", style = OD.H3.copy(fontSize = 13.sp))
                            Spacer(Modifier.weight(1f))
                            Text("查看", style = OD.Micro.copy(color = OD.CyanDeep))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(DailyLibrary.today().en, style = OD.ReadBody.copy(fontSize = 13.5.sp, lineHeight = 20.sp))
                        Spacer(Modifier.height(4.dp))
                        Text("— ${DailyLibrary.today().source}", style = OD.Cap.copy(fontSize = 10.5.sp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("继续学习", style = OD.H3)
                    Spacer(Modifier.weight(1f))
                    Pressable(onClick = { onNavigate(Route.Learning) }) { Text("全部词汇", style = OD.Cap.copy(color = OD.CyanDeep)) }
                }
                Spacer(Modifier.height(8.dp))
                Pressable(onClick = { onNavigate(Route.Learning) }) {
                    Row(Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(16.dp), Color.White.copy(alpha = .18f)).border(1.dp, Color.White.copy(alpha = .5f), RoundedCornerShape(16.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(OD.CyanSoft), contentAlignment = Alignment.Center) { OIcon(Ic.Book, 18.dp, OD.CyanDeep) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(learnBookName, style = OD.H3.copy(fontSize = 13.5.sp))
                            Text(
                                if (learnTotal > 0) "已学 $learnDone / $learnTotal · 今日待复习 $dueCount" else "还没有生词本 · 点此开始学习",
                                style = OD.Cap.copy(fontSize = 10.5.sp)
                            )
                            Spacer(Modifier.height(5.dp))
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(OD.Hairline)) {
                                Box(Modifier.fillMaxWidth(if (learnTotal > 0) (learnDone.toFloat() / learnTotal).coerceIn(0f, 1f) else 0f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(OD.Cyan))
                            }
                        }
                        if (learnTotal > 0) { Spacer(Modifier.width(8.dp)); Text("${(learnDone * 100 / learnTotal.coerceAtLeast(1))}%", style = OD.Cap.copy(color = OD.Muted)) }
                    }
                }
                Spacer(Modifier.height(96.dp))
            }
        }
    }
    if (showDaily) {
        DialogCard(visible = true, onDismiss = { showDaily = false }) {
            Text("每日一句", style = OD.H2)
            Spacer(Modifier.height(8.dp))
            Text("Keep your face to the sunshine and you cannot see a shadow.", style = OD.ReadBody.copy(fontSize = 15.sp, lineHeight = 23.sp))
            Spacer(Modifier.height(6.dp))
            Text("— Helen Keller", style = OD.Cap)
            Spacer(Modifier.height(8.dp))
            Text("把脸朝向阳光，你就看不到阴影。", style = OD.Cap.copy(fontSize = 11.5.sp))
            Spacer(Modifier.height(14.dp))
            ODButton("知道了", onClick = { showDaily = false })
        }
    }
}

@Composable
private fun FeatureRow(icon: Ic, title: String, desc: String, onClick: () -> Unit) {
    Pressable(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                OIcon(icon, 16.dp, OD.InkSoft)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, style = OD.H3.copy(fontSize = 14.sp)); Text(desc, style = OD.Cap.copy(fontSize = 11.sp)) }
            OIcon(Ic.Chevron, 12.dp, OD.Hairline)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
}

// 旧演示用 WordScreen（硬编码 reputation 假数据/假点赞）已移除；真实词条页见 ui/RealWord.kt

@Composable
fun SettingsScreen(onNavigate: (Route) -> Unit, onCheckUpdate: () -> Unit) {
    val settingsCtx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { settingsCtx.getSharedPreferences("od_prefs", android.content.Context.MODE_PRIVATE) }
    var wifi by remember { mutableStateOf(prefs.getBoolean("dl_wifi_only", true)) }
    var autoUpdate by remember { mutableStateOf(prefs.getBoolean("auto_update", true)) }
    val table = listOf("阅读", "下载", "隐私", "高级", "更新与支持")
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 780.dp
        if (wide) {
            var tab by remember { mutableStateOf(0) }
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(190.dp).fillMaxHeight().padding(horizontal = 10.dp, vertical = 16.dp)) {
                    Text("设置", style = OD.H2, modifier = Modifier.padding(start = 8.dp, bottom = 14.dp))
                    table.forEachIndexed { i, name ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).clickable { tab = i }.background(if (tab == i) OD.CyanSoft.copy(alpha = .8f) else Color.Transparent).padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = OD.H3.copy(fontSize = 13.sp, color = if (tab == i) OD.CyanDeep else OD.InkSoft))
                        }
                    }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(OD.Hairline.copy(alpha = .5f)))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PageScaffold("设置", onBack = { onNavigate(Route.Home) }) {
                        when (tab) {
                            0 -> SettingsReading(onNavigate)
                            1 -> SettingsDownload(wifi, { wifi = it; prefs.edit().putBoolean("dl_wifi_only", it).apply() }, onNavigate)
                            2 -> SettingsPrivacy(onNavigate)
                            3 -> SettingsAdvanced(onNavigate)
                            else -> SettingsUpdate(autoUpdate, { autoUpdate = it; prefs.edit().putBoolean("auto_update", it).apply() }, onCheckUpdate, onNavigate)
                        }
                    }
                }
            }
        } else {
            PageScaffold("设置", onBack = { onNavigate(Route.Home) }) {
                SettingsReading(onNavigate)
                SettingsDownload(wifi, { wifi = it; prefs.edit().putBoolean("dl_wifi_only", it).apply() }, onNavigate)
                SettingsPrivacy(onNavigate)
                SettingsAdvanced(onNavigate)
                SettingsUpdate(autoUpdate, { autoUpdate = it; prefs.edit().putBoolean("auto_update", it).apply() }, onCheckUpdate, onNavigate)
            }
        }
    }
}

@Composable
private fun SettingsReading(onNavigate: (Route) -> Unit) {
    SettingsGroup("阅读")
    SettingItem(Ic.Book, "词典与内容源", "ECDICT · 标签 · 许可与来源") { onNavigate(Route.DictSource) }
    SettingItem(Ic.Book, "自定义词条", "新词/专业术语 · JSON 导入导出") { onNavigate(Route.CustomWords) }
    SettingItem(Ic.Headphone, "原声语料", "演讲/访谈/公开课 · 开放来源链接") { onNavigate(Route.SpeechSources) }
    SettingItem(Ic.Book, "题库与阅读", "高考英语真题 · IELTS 阅读 · GAOKAO-Bench") { onNavigate(Route.ExamBank) }
    SettingItem(Ic.Globe, "学术资源", "开放资源外链 · 未接入自动抓取") { onNavigate(Route.Academic) }
    SettingItem(Ic.Font, "字体与排版", "字号 · 行距 · 预览") { onNavigate(Route.Fonts) }
    SettingItem(Ic.Search, "查词弹窗", "点击单词 · 快捷查词") { onNavigate(Route.DictPopup) }
}

@Composable
private fun SettingsDownload(wifi: Boolean, onWifi: (Boolean) -> Unit, onNavigate: (Route) -> Unit) {
    SettingsGroup("下载")
    SettingItem(Ic.Download, "离线资源", "词典包 · 词书 · 音频 · 模型") { onNavigate(Route.Offline) }
    ToggleSetting("仅 Wi-Fi 下载", "移动网络下暂停大文件（默认开启）", wifi, onWifi)
}

@Composable
private fun SettingsPrivacy(onNavigate: (Route) -> Unit) {
    SettingsGroup("隐私")
    SettingItem(Ic.Shield, "数据与隐私", "本地优先 · 无账号 · 不采集") { onNavigate(Route.Privacy) }
    SettingItem(Ic.Lock, "剪贴板与取词", "系统限制 · 仅在应用内") { onNavigate(Route.Clipboard) }
}

@Composable
private fun SettingsAdvanced(onNavigate: (Route) -> Unit) {
    SettingsGroup("高级")
    SettingItem(Ic.Sparkle, "AI 与本地模型", "提示词 · 最大内存 · 模型管理") { onNavigate(Route.Llm) }
    SettingItem(Ic.Grid, "实验功能", "状态说明 · 原声自动播放") { onNavigate(Route.Experiment) }
    SettingItem(Ic.Bell, "通知", "提醒 · 复习 · 更新") { onNavigate(Route.Notifications) }
    SettingItem(Ic.Info, "无障碍与效果", "字号 · 动画程度 · Liquid Glass") { onNavigate(Route.Accessibility) }
    SettingItem(Ic.Cloud, "备份与恢复", "本地导出 · 导入") { onNavigate(Route.Backup) }
}

@Composable
private fun SettingsUpdate(autoUpdate: Boolean, onAuto: (Boolean) -> Unit, onCheckUpdate: () -> Unit, onNavigate: (Route) -> Unit) {
    SettingsGroup("更新与支持")
    ToggleSetting("自动检查预发布更新", "只检查带 update.json 的 prerelease", autoUpdate, onAuto)
    SettingItem(Ic.Update, "检查更新", "GitHub 预发布 · 仅 APK 资产") { onCheckUpdate() }
    SettingItem(Ic.Info, "帮助与反馈", "常见问题 · 反馈 · 开源许可") { onNavigate(Route.Help) }
    SettingItem(Ic.External, "内容与数据来源", "内置数据/文档/媒体/字体来源与原页面") { onNavigate(Route.Sources) }
    SettingItem(Ic.Info, "诊断日志", "无 adb 时导出崩溃/事件记录") { onNavigate(Route.Diagnostics) }
    SettingItem(Ic.Heart, "支持开发", "爱发电 · 无广告、无账号") { onNavigate(Route.Support) }
    SettingItem(Ic.Info, "关于 OpenDictionary", "版本 ${io.github.xhr666.opendictionary.BuildConfig.VERSION_NAME} · 开源许可") { onNavigate(Route.About) }
    Spacer(Modifier.height(16.dp))
    Pressable(onClick = { onNavigate(Route.Support) }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(OD.Ink, Color(0xFF3C4A4E)))).padding(16.dp)) {
            Text("支持 OpenDictionary 持续更新", style = OD.H3.copy(color = Color.White))
            Spacer(Modifier.height(5.dp))
            Text("完全离线 · 无广告 · 无账号 · 资源开放", style = OD.Cap.copy(color = Color.White.copy(alpha = .68f), fontSize = 11.sp))
            Spacer(Modifier.height(10.dp))
            Text("爱发电 · afdian.com/a/xferoni66 →", style = OD.Cap.copy(color = OD.Cyan, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp))
        }
    }
}

@Composable
private fun SettingsGroup(title: String) {
    Text(title, style = OD.Cap.copy(color = OD.Muted, fontWeight = FontWeight.SemiBold, letterSpacing = .3.sp), modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline))
}

@Composable
private fun SettingItem(icon: Ic, title: String, desc: String, onClick: () -> Unit) {
    Pressable(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { OIcon(icon, 14.dp, OD.InkSoft) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) { Text(title, style = OD.H3.copy(fontSize = 13.5.sp)); Text(desc, style = OD.Cap.copy(fontSize = 10.5.sp)) }
            OIcon(Ic.Chevron, 12.dp, OD.Hairline)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
}

@Composable
private fun ToggleSetting(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = OD.H3.copy(fontSize = 13.5.sp)); Text(desc, style = OD.Cap.copy(fontSize = 10.5.sp)) }
        ODToggle(checked, onChange)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
}

// ---------- 通用信息页 ----------
@Composable
private fun InfoPage(title: String, onBack: () -> Unit, blocks: List<Pair<String, String>>) {
    PageScaffold(title, onBack) {
        blocks.forEach { (h, body) ->
            if (h.isNotBlank()) { Text(h, style = OD.H3, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
            Text(body, style = OD.BodySm.copy(lineHeight = 20.sp))
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun DictSourceScreen(onNavigate: (Route) -> Unit) = InfoPage("词典与内容源", { onNavigate(Route.Settings) }, listOf(
    "离线词典" to "ECDICT（MIT）编译的本地词库；中文别名用于汉英反查。",
    "英英/词源/同近义" to "当前仅展示随包本地数据；未联网抓取 Wiktionary 等外部词典。",
    "双语例句/百科" to "暂未接入外部例句/百科内容；词条中对应分区保留为空或说明状态。",
    "考试词表" to "基于 ECDICT 标签整理；资源包从 GitHub Releases 下载并校验 SHA-256/大小/词数。",
    "在线兜底" to "本地未收录的新词/专业术语可由你主动在线查询（Wiktionary 数据），结果可保存为本地词条；不批量抓取。",
    "本地导入阅读/媒体" to "txt/PDF/EPUB 与音频/LRC 仅保存在本机；不上传、不二次分发。"
))

@Composable
fun DictPopupScreen(onNavigate: (Route) -> Unit) = InfoPage("查词弹窗", { onNavigate(Route.Settings) }, listOf(
    "跨应用查词" to "在任意应用中选中文本，通过系统“文本选择/分享”打开 OpenDictionary 半屏查词；是否出现取决于目标 App 是否提供该入口。",
    "应用内点词" to "当前支持 txt/md、PDF 文字层、EPUB 阅读中点按单词；其他页面暂不支持。",
    "内容" to "本地离线词典释义、发音、加入生词本；完整内容进入词条页。"
))

@Composable
fun NotificationsScreen(onNavigate: (Route) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("od_prefs", android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("review_reminder", false)) }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && enabled) io.github.xhr666.opendictionary.notify.ReviewReminder.schedule(ctx, true)
    }
    PageScaffold("通知", { onNavigate(Route.Settings) }) {
        ToggleSetting("每日复习提醒", "每天 20:00 提醒待复习词汇（可随时关闭）", enabled) {
            enabled = it
            prefs.edit().putBoolean("review_reminder", it).apply()
            io.github.xhr666.opendictionary.notify.ReviewReminder.schedule(ctx, it)
        }
        Spacer(Modifier.height(8.dp))
        InfoBlock("提醒使用本地通知渠道，不联网、不上传学习数据。首次启用需要系统通知权限；若系统限制后台任务，提醒可能延迟。")
        Spacer(Modifier.height(8.dp))
        Text("系统通知权限：" + if (android.os.Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) "已授权" else "未授权（可在系统设置中开启）", style = OD.Cap)
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Spacer(Modifier.height(8.dp))
            ODButton("请求通知权限", { permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }, primary = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun AccessibilityScreen(onNavigate: (Route) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var scale by remember { mutableStateOf(io.github.xhr666.opendictionary.ui.UiState.fontScale) }
    var reduce by remember { mutableStateOf(io.github.xhr666.opendictionary.ui.UiState.reduceMotion) }
    PageScaffold("无障碍", { onNavigate(Route.Settings) }) {
        Text("字体大小", style = OD.H3)
        Spacer(Modifier.height(4.dp))
        Text("当前 ${"%.0f".format(scale * 100)}%；系统字体缩放会叠加生效", style = OD.Cap)
        ODSlider(scale, 0.8f..2.0f, { scale = it; io.github.xhr666.opendictionary.ui.UiState.setFontScale(ctx, it) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("80%", style = OD.Micro); Text("200%", style = OD.Micro) }
        Spacer(Modifier.height(16.dp))
        Text("动画程度", style = OD.H3)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "无", 1 to "减弱", 2 to "完整").forEach { (v, label) ->
                Pill(label, io.github.xhr666.opendictionary.ui.UiState.animationLevel == v) {
                    io.github.xhr666.opendictionary.ui.UiState.setAnimationLevel(ctx, v)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Liquid Glass 效果", style = OD.H3)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "无", 1 to "仅模糊", 2 to "完整").forEach { (v, label) ->
                Pill(label, io.github.xhr666.opendictionary.ui.UiState.glassMode == v) {
                    io.github.xhr666.opendictionary.ui.UiState.setGlassMode(ctx, v)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("主题配色", style = OD.H3)
        Spacer(Modifier.height(6.dp))
        odPalettes.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { p ->
                    val idx = odPalettes.indexOf(p)
                    Pressable(onClick = { io.github.xhr666.opendictionary.ui.UiState.setThemePalette(ctx, idx) }) {
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (io.github.xhr666.opendictionary.ui.UiState.themePalette == idx) OD.CyanSoft else Color.White).border(1.dp, if (io.github.xhr666.opendictionary.ui.UiState.themePalette == idx) OD.Cyan else OD.Hairline, RoundedCornerShape(10.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(p.accent))
                            Spacer(Modifier.width(6.dp))
                            Text(p.name, style = OD.Cap.copy(fontSize = 11.sp, color = if (io.github.xhr666.opendictionary.ui.UiState.themePalette == idx) OD.CyanDeep else OD.Ink))
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("系统设置", style = OD.H3)
        Spacer(Modifier.height(6.dp))
        ODButton("打开系统无障碍设置", {
            runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, primary = false, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text("提示：TalkBack 朗读、系统对比度与触控目标以系统设置为准；本页字号缩放会立即应用到界面。", style = OD.Micro.copy(lineHeight = 15.sp))
    }
}

@Composable
fun BackupScreen(onNavigate: (Route) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val db = remember { io.github.xhr666.opendictionary.data.UserDb.get(ctx) }
    var status by remember { mutableStateOf("") }
    val backupScope = rememberCoroutineScope()
    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            backupScope.launch {
                val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.openOutputStream(uri)?.use { it.write(db.exportBackupJson().toByteArray()) } ?: error("无法写入")
                    }
                }
                res.onSuccess { status = "已导出学习记录、笔记与词书" }
                    .onFailure { status = "导出失败：${it.message}" }
            }
        }
    }
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            backupScope.launch {
                status = "导入中…"
                val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("无法读取")
                        if (text.length > 20 * 1024 * 1024) error("备份文件过大（>20MB）")
                        db.importBackupJson(text)
                    }
                }
                res.onSuccess { n -> status = "已导入 $n 条活动记录（词书/历史/笔记已合并）" }
                    .onFailure { status = "导入失败：${it.message}" }
            }
        }
    }
    PageScaffold("备份与恢复", { onNavigate(Route.Settings) }) {
        InfoBlock("备份包含：学习活动、查询历史、生词本/词书、笔记。不包含词典、模型、媒体文件；导入为合并模式，不删除现有数据。")
        Spacer(Modifier.height(14.dp))
        ODButton("导出备份文件（JSON）", { exporter.launch("opendictionary-backup-${System.currentTimeMillis()}.json") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        ODButton("导入备份文件", { importer.launch(arrayOf("application/json", "text/*")) }, primary = false, modifier = Modifier.fillMaxWidth())
        if (status.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(status, style = OD.Cap.copy(color = OD.CyanDeep, lineHeight = 17.sp))
        }
        Spacer(Modifier.height(16.dp))
        Text("提示：导入会按时间合并；重复导入同一份文件可能产生重复的历史/笔记记录，建议先导出留存。", style = OD.Micro.copy(lineHeight = 15.sp))
    }
}

@Composable
fun PrivacyScreen(onNavigate: (Route) -> Unit) = InfoPage("数据与隐私", { onNavigate(Route.Settings) }, listOf(
    "本地优先" to "查询记录、生词本、笔记与设置保存在应用私有目录。",
    "不采集" to "无账号体系，不收集通讯录、位置、设备标识；不接入广告 SDK。",
    "网络请求" to "仅用于：检查预发布更新、按需下载资源与模型、打开你主动点击的外部链接。",
    "卸载" to "卸载应用会删除应用私有数据；已下载到公共下载目录的文件需手动删除。"
))

@Composable
fun ClipboardScreen(onNavigate: (Route) -> Unit) = InfoPage("剪贴板与取词", { onNavigate(Route.Settings) }, listOf(
    "系统限制" to "Android 10 起，后台应用无法持续监听剪贴板；本应用不申请该权限。",
    "应用内取词" to "在应用内阅读页、例句与批改报告中点击单词即可查词。",
    "跨应用取词" to "通过系统“处理文本”菜单（ACTION_PROCESS_TEXT）接收你主动选中的文本。"
))

@Composable
fun ExperimentScreen(onNavigate: (Route) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("od_prefs", android.content.Context.MODE_PRIVATE) }
    var autoPlay by remember { mutableStateOf(prefs.getBoolean("auto_play_word_audio", false)) }
    PageScaffold("实验功能", { onNavigate(Route.Settings) }) {
        InfoBlock("以下为实验/规划能力：未接入的功能只做状态说明，不提供无效开关。")
        Spacer(Modifier.height(10.dp))
        ToggleSetting("进入原声面板自动播放", "词条有本地原声时自动播放第一条", autoPlay) {
            autoPlay = it; prefs.edit().putBoolean("auto_play_word_audio", it).apply()
        }
        Spacer(Modifier.height(10.dp))
        listOf(
            "视频例句" to "未接入：影视片段版权限制，暂只提供外链查找。",
            "音乐例句" to "已支持本地音频+同名 LRC 的逐词高亮；在线音乐不抓取。",
            "原声自动播放" to "已实现：词条“原声”面板可选自动播放第一条本地音频。"
        ).forEach { (name, desc) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Text(name, style = OD.H3.copy(fontSize = 12.5.sp))
                Text(desc, style = OD.Cap.copy(fontSize = 10.5.sp, lineHeight = 15.sp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
        }
    }
}

@Composable
internal fun InfoBlock(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(OD.CyanSoft.copy(alpha = .5f)).padding(12.dp)) {
        Text(text, style = OD.Cap.copy(color = OD.CyanDeep, lineHeight = 17.sp))
    }
}

// ---------- 字体与排版 ----------
@Composable
fun FontsScreen(onNavigate: (Route) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("od_prefs", android.content.Context.MODE_PRIVATE) }
    var size by remember { mutableStateOf(1.0f) }
    var line by remember { mutableStateOf(1.6f) }
    var bgUri by remember { mutableStateOf(prefs.getString("bg_uri", null)) }
    var bgMode by remember { mutableStateOf(prefs.getString("bg_mode", "gradient") ?: "gradient") }
    var fontPath by remember { mutableStateOf(prefs.getString("custom_font_path", null)) }
    var fontError by remember { mutableStateOf("") }
    val fontPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fontError = ""
            runCatching {
                val tmp = java.io.File(ctx.cacheDir, "font-import.tmp")
                ctx.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (tmp.length() < 1024) error("文件太小，不是有效字体")
                android.graphics.Typeface.createFromFile(tmp)
                val dest = java.io.File(ctx.filesDir, "custom-font.ttf")
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
                prefs.edit().putString("custom_font_path", dest.absolutePath).apply()
                fontPath = dest.absolutePath
                io.github.xhr666.opendictionary.ui.UiState.reloadFont(ctx)
            }.onFailure { fontError = "字体无效：${it.message}"; java.io.File(ctx.cacheDir, "font-import.tmp").delete() }
        }
    }
    val bgPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            prefs.edit().putString("bg_uri", uri.toString()).apply()
            bgUri = uri.toString()
        }
    }
    PageScaffold("字体与排版", { onNavigate(Route.Settings) }) {
        Text("预览", style = OD.Cap.copy(color = OD.Muted, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(14.dp)).padding(14.dp)) {
            Text("Reputation", style = OD.H2.copy(fontSize = (19 * size).sp))
            Spacer(Modifier.height(4.dp))
            Text("n. 名气，名声，名誉；声望；信誉", style = OD.Body.copy(fontSize = (13 * size).sp, lineHeight = (20 * size * line).sp))
            Spacer(Modifier.height(6.dp))
            Text("The company has a good reputation for quality.", style = OD.BodySm.copy(fontSize = (12 * size).sp, lineHeight = (18 * size * line).sp))
        }
        Spacer(Modifier.height(18.dp))
        Row { Text("字号", style = OD.H3); Spacer(Modifier.weight(1f)); Text("%.0f%%".format(size * 100), style = OD.Cap) }
        ODSlider(size, 0.8f..1.4f, { size = it })
        Spacer(Modifier.height(12.dp))
        Row { Text("行距", style = OD.H3); Spacer(Modifier.weight(1f)); Text("%.1f".format(line), style = OD.Cap) }
        ODSlider(line, 1.2f..2.0f, { line = it })
        Spacer(Modifier.height(10.dp))
        Text("中文采用系统字体，英文与数字使用同一字族；正文对比度目标 ≥ 4.5:1。", style = OD.Cap)
        Spacer(Modifier.height(18.dp))
        Text("背景", style = OD.H3)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("默认渐变", bgMode == "gradient") { bgMode = "gradient"; prefs.edit().putString("bg_mode", "gradient").apply() }
            Pill("图片", bgMode == "image") { bgMode = "image"; prefs.edit().putString("bg_mode", "image").apply() }
            Pill("Auto 配色", bgMode == "auto") { bgMode = "auto"; prefs.edit().putString("bg_mode", "auto").apply() }
        }
        Spacer(Modifier.height(8.dp))
        Text(if (bgUri == null) "图片模式尚未选择图片；未选择时会回退默认背景" else "已选择本地图片背景", style = OD.Cap)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton("选择图片", { bgPicker.launch(arrayOf("image/*")) }, primary = false, modifier = Modifier.weight(1f))
            ODButton("恢复默认", {
                prefs.edit().remove("bg_uri").putString("bg_mode", "gradient").apply(); bgUri = null; bgMode = "gradient"
            }, primary = false, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Text("Auto 模式按日期轮换内置配色（不联网、不下载图片）；视频背景尚未实现。图片叠加白色蒙层保证可读性。", style = OD.Micro)
        Spacer(Modifier.height(18.dp))
        Text("自定义字体", style = OD.H3)
        Spacer(Modifier.height(4.dp))
        Text(if (fontPath == null) "未导入字体；支持 .ttf/.otf" else "已导入：${java.io.File(fontPath!!).name}", style = OD.Cap)
        if (fontError.isNotBlank()) Text(fontError, style = OD.Micro.copy(color = OD.Warm))
        if (fontPath != null) {
            Spacer(Modifier.height(6.dp))
            androidx.compose.ui.viewinterop.AndroidView(factory = { c ->
                android.widget.TextView(c).apply {
                    text = "Reputation · The company has a good reputation."
                    textSize = 16f
                    setTextColor(0xFF2B3236.toInt())
                    runCatching { typeface = android.graphics.Typeface.createFromFile(java.io.File(fontPath!!)) }
                }
            }, modifier = Modifier.fillMaxWidth().height(64.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton("导入字体", { fontPicker.launch(arrayOf("*/*")) }, primary = false, modifier = Modifier.weight(1f))
            ODButton("移除字体", {
                fontPath?.let { java.io.File(it).delete() }
                prefs.edit().remove("custom_font_path").apply(); fontPath = null
                io.github.xhr666.opendictionary.ui.UiState.reloadFont(ctx)
            }, primary = false, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text("字体文件保存在应用私有目录；已应用到阅读正文（词条释义、文档、时文等），可随时移除恢复默认。", style = OD.Micro)
    }
}

// ---------- 离线资源 ----------
@Composable
fun OfflineScreen(onNavigate: (Route) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val model = remember { ModelStore.installedAny(ctx) }
    PageScaffold("离线资源", { onNavigate(Route.Settings) }) {
        InfoBlock("词典核心数据已内置在安装包中，无需联网或额外下载。本地模型不随 APK 分发，可在 AI 页面按需下载或导入。")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("词典核心包", style = OD.H3.copy(fontSize = 13.sp))
                Text("15 万词条 · 8.6 万短语 · 已内置", style = OD.Cap.copy(fontSize = 10.5.sp))
            }
            Box(Modifier.clip(RoundedCornerShape(50)).background(OD.CyanSoft).padding(horizontal = 9.dp, vertical = 4.dp)) { Text("已安装", style = OD.Micro.copy(color = OD.CyanDeep)) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("本地模型", style = OD.H3.copy(fontSize = 13.sp))
                Text(if (model != null) "${model.name} · 已下载" else "未下载 · 支持断点续传与 sha256", style = OD.Cap.copy(fontSize = 10.5.sp))
            }
            Pressable(onClick = { onNavigate(Route.Llm) }) {
                Row(Modifier.clip(RoundedCornerShape(50)).background(OD.Ink).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OIcon(if (model != null) Ic.Check else Ic.Download, 12.dp, Color.White); Spacer(Modifier.width(4.dp))
                    Text(if (model != null) "管理" else "去下载", style = OD.Micro.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("本地音乐与歌词", style = OD.H3.copy(fontSize = 13.sp))
                Text("导入音乐 + 同名 .lrc，片段播放与逐词高亮", style = OD.Cap.copy(fontSize = 10.5.sp))
            }
            Pressable(onClick = { onNavigate(Route.Media) }) {
                Row(Modifier.clip(RoundedCornerShape(50)).background(OD.Ink).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OIcon(Ic.Headphone, 12.dp, Color.White); Spacer(Modifier.width(4.dp)); Text("管理", style = OD.Micro.copy(color = Color.White))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("提示：模型文件较大（0.5–1.1GB），建议在 Wi-Fi 下下载；下载中断后重新点击可断点续传。", style = OD.Cap.copy(lineHeight = 17.sp))
    }
}

// ---------- 学术资源 ----------
@Composable
fun AcademicScreen(onNavigate: (Route) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var customUrl by remember { mutableStateOf("") }
    fun open(url: String) = runCatching {
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val links = listOf(
        Triple("arXiv", "开放获取论文 · 按单篇许可（CC BY/BY-SA/CC0 等）", "https://arxiv.org/"),
        Triple("PMC Open Access", "生物医学开放获取 · 仅 CC 许可文章", "https://www.ncbi.nlm.nih.gov/pmc/"),
        Triple("OpenAlex", "学术元数据 CC0（摘要按来源许可）", "https://openalex.org/"),
        Triple("CORE / DOAJ", "开放获取聚合，逐篇标注许可", "https://core.ac.uk/"),
        Triple("TED", "视频外链，不缓存/不下载；字幕受 TED 条款限制", "https://www.ted.com/talks")
    )
    PageScaffold("学术资源", { onNavigate(Route.Settings) }) {
        InfoBlock("当前版本尚未接入学术资源自动抓取或例句入库；以下只提供开放资源原站外链，不会解析正文。抓取/入库能力会在有合法 API 与配额后加入。")
        Spacer(Modifier.height(12.dp))
        links.forEach { (name, desc, url) ->
            Pressable(onClick = { open(url) }) {
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(name, style = OD.H3.copy(fontSize = 12.5.sp)); Text(desc, style = OD.Cap.copy(fontSize = 10.5.sp, lineHeight = 15.sp)) }
                    OIcon(Ic.External, 13.dp, OD.Muted)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
        }
        Spacer(Modifier.height(16.dp))
        Text("自定义网址", style = OD.H3)
        Spacer(Modifier.height(6.dp))
        ODField(customUrl, { customUrl = it }, "https://example.org", leading = Ic.Link)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton("打开网址", {
                val u = customUrl.trim()
                if (u.startsWith("http://") || u.startsWith("https://")) open(u)
            }, primary = false, modifier = Modifier.weight(1f))
            ODButton("返回设置", { onNavigate(Route.Settings) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Text("付费墙网站（ScienceDirect / Springer / JSTOR 等）仅支持手动打开外链，不解析正文。", style = OD.Micro.copy(lineHeight = 15.sp))
    }
}


// ---------- 帮助与反馈 / 开源许可 ----------
private data class SourceItem(val name: String, val license: String, val usage: String, val url: String)

@Composable
fun CustomWordsScreen(onNavigate: (Route) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val db = remember { io.github.xhr666.opendictionary.data.UserDb.get(ctx) }
    var entries by remember { mutableStateOf(db.allCustomEntries()) }
    var status by remember { mutableStateOf("") }
    var word by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }
    var definition by remember { mutableStateOf("") }
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("无法读取")
                if (text.length > 5 * 1024 * 1024) error("文件过大")
                val arr = org.json.JSONArray(text)
                var n = 0
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val w = o.optString("word").trim(); if (w.isBlank()) continue
                    db.saveCustomEntry(
                        io.github.xhr666.opendictionary.data.LexEntry(
                            w, o.optString("phonetic"), o.optString("translation"), o.optString("definition"),
                            o.optString("pos"), o.optString("tag", "custom"), o.optString("exchange"), 9999
                        ),
                        o.optString("source", "本地导入")
                    )
                    n++
                }
                entries = db.allCustomEntries(); status = "已导入 $n 条自定义词条"
            }.onFailure { status = "导入失败：${it.message}" }
        }
    }
    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val arr = org.json.JSONArray()
                entries.forEach { e -> arr.put(org.json.JSONObject().put("word", e.word).put("phonetic", e.phonetic).put("translation", e.translation).put("definition", e.definition).put("pos", e.pos).put("tag", e.tag).put("exchange", e.exchange).put("source", "自定义词条")) }
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(arr.toString(2).toByteArray()) } ?: error("无法写入")
                status = "已导出 ${entries.size} 条"
            }.onFailure { status = "导出失败：${it.message}" }
        }
    }
    PageScaffold("自定义词条", { onNavigate(Route.Settings) }) {
        InfoBlock("用于补充新词、专业术语等离线词库未收录内容。可手工添加、从在线查询结果保存，或导入 JSON：[{\"word\":\"rizz\",\"translation\":\"n. 魅力\",\"definition\":\"...\"}]。自定义词条随备份导入导出。")
        Spacer(Modifier.height(10.dp))
        Text("新增词条", style = OD.H3)
        Spacer(Modifier.height(6.dp))
        ODField(word, { word = it }, "word（必填）")
        Spacer(Modifier.height(6.dp))
        ODField(translation, { translation = it }, "中文释义/简短解释")
        Spacer(Modifier.height(6.dp))
        ODField(definition, { definition = it }, "详细释义/例句（可选）")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton("保存词条", {
                val w = word.trim()
                if (w.isNotBlank()) {
                    db.saveCustomEntry(io.github.xhr666.opendictionary.data.LexEntry(w, "", translation.trim(), definition.trim(), "", "custom", "", 9999), "手动添加")
                    entries = db.allCustomEntries(); word = ""; translation = ""; definition = ""; status = "已保存 $w"
                }
            }, modifier = Modifier.weight(1f))
            ODButton("导入 JSON", { importer.launch(arrayOf("application/json", "text/plain")) }, primary = false, modifier = Modifier.weight(1f))
            ODButton("导出 JSON", { exporter.launch("opendictionary-custom-words.json") }, primary = false, modifier = Modifier.weight(1f))
        }
        if (status.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(status, style = OD.Cap.copy(color = OD.CyanDeep)) }
        Spacer(Modifier.height(12.dp))
        Text("已保存 ${entries.size} 条", style = OD.H3)
        Spacer(Modifier.height(6.dp))
        if (entries.isEmpty()) Text("暂无自定义词条。", style = OD.Cap)
        entries.forEach { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.word, style = OD.H3.copy(fontSize = 13.sp))
                    Text(e.translation.ifBlank { e.definition.take(60) }, style = OD.Cap.copy(fontSize = 10.5.sp), maxLines = 2)
                }
                Pressable(onClick = { db.deleteCustomEntry(e.word); entries = db.allCustomEntries() }) { OIcon(Ic.Trash, 14.dp, OD.Muted) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
fun DiagnosticsScreen(onNavigate: (Route) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var files by remember { mutableStateOf(io.github.xhr666.opendictionary.Diagnostics.exports(ctx)) }
    var selected by remember { mutableStateOf<Int>(-1) }
    var diagStatus by remember { mutableStateOf("") }
    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val target = if (selected >= 0) listOfNotNull(files.getOrNull(selected)) else files
            runCatching {
                val text = target.joinToString("\n\n") { "===== ${it.name} =====\n" + io.github.xhr666.opendictionary.Diagnostics.read(it) }
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("无法写入")
                diagStatus = "已导出 ${target.size} 个文件"
            }
        }
    }
    PageScaffold("诊断日志", { onNavigate(Route.Settings) }) {
        Text("应用会记录未捕获崩溃与关键事件到本机私有目录，不上传。无 adb 时可用这里导出文本反馈。", style = OD.Cap.copy(lineHeight = 17.sp))
        if (diagStatus.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(diagStatus, style = OD.Cap.copy(color = OD.CyanDeep)) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton("刷新", { files = io.github.xhr666.opendictionary.Diagnostics.exports(ctx) }, primary = false, modifier = Modifier.weight(1f))
            ODButton("清空", { io.github.xhr666.opendictionary.Diagnostics.clear(ctx); files = emptyList(); selected = -1 }, primary = false, modifier = Modifier.weight(1f))
            ODButton(if (selected >= 0) "导出选中" else "导出全部", { exporter.launch("opendictionary-diagnostics.txt") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        if (files.isEmpty()) Text("暂无诊断记录。", style = OD.Cap)
        files.forEachIndexed { i, f ->
            Pressable(onClick = { selected = if (selected == i) -1 else i }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(f.name, style = OD.H3.copy(fontSize = 12.5.sp, color = if (selected == i) OD.CyanDeep else OD.Ink))
                    Text("${f.length()} B · ${android.text.format.DateFormat.format("MM-dd HH:mm", f.lastModified())}", style = OD.Micro)
                    if (selected == i) {
                        Spacer(Modifier.height(5.dp))
                        Text(io.github.xhr666.opendictionary.Diagnostics.read(f).take(4000), style = OD.Micro.copy(fontSize = 9.sp, lineHeight = 13.sp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
fun SourcesScreen(onNavigate: (Route) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun open(url: String) = runCatching {
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val items = listOf(
        SourceItem("ECDICT", "MIT", "离线英汉词典与考试词表标签（内置）", "https://github.com/skywind3000/ECDICT"),
        SourceItem("CC-CEDICT", "CC BY-SA 4.0", "中文别名辅助（部分内置）", "https://cc-cedict.org/"),
        SourceItem("GAOKAO-Bench 阅读样例", "Apache-2.0", "内置高考阅读样例；试题原文版权归原机构", "https://github.com/OpenLMLab/GAOKAO-Bench"),
        SourceItem("IELTS AI-Generated Dataset", "CC BY 4.0", "内置 IELTS 阅读/听力练习样例（非官方真题）", "https://github.com/LuchoBazz/ielts-ai-dataset"),
        SourceItem("RSS：Good News Network", "站点条款/仅链接与摘要", "时文列表标题、摘要、原站链接", "https://www.goodnewsnetwork.org/feed/"),
        SourceItem("RSS：Positive News", "站点条款/仅链接与摘要", "时文列表标题、摘要、原站链接", "https://www.positive.news/feed/"),
        SourceItem("RSS：NASA Breaking News", "公共领域（NASA）", "时文列表标题、摘要、原站链接", "https://www.nasa.gov/rss/dyn/breaking_news.rss"),
        SourceItem("Inter / Literata / Caveat", "SIL OFL 1.1", "内置界面/阅读/手写字体", "https://github.com/google/fonts"),
        SourceItem("PDFBox-Android", "Apache-2.0", "PDF 文字层提取", "https://github.com/TomRoush/PdfBox-Android"),
        SourceItem("Qwen2.5 / Qwen3 GGUF", "Apache-2.0", "本地 LLM 模型（按需下载，不随包分发）", "https://huggingface.co/Qwen"),
        SourceItem("考试词书资源包", "ECDICT MIT 整理", "GitHub Releases 下载并校验后导入", "https://github.com/XHR666/OpenDictionary-apk/releases/tag/wordbooks-v1"),
        SourceItem("Wiktionary / Free Dictionary API", "CC BY-SA 4.0", "新词/专业术语在线兜底（用户主动触发，不批量抓取）", "https://en.wiktionary.org/"),
        SourceItem("dictionaryapi.dev", "开源 API", "在线查词服务（数据来自 Wiktionary）", "https://dictionaryapi.dev/"),
        SourceItem("机器翻译兜底（Microsoft/MyMemory/Google）", "各自服务条款", "仅在在线查词结果缺中文时按需调用，不缓存服务数据；可失败", "https://www.microsoft.com/translator"),
        SourceItem("有道词典在线 suggest 接口", "非官方网页接口", "在线查词缺中文时按词查询中文解释，仅用户主动触发；接口可能变化", "https://dict.youdao.com/")
    )
    PageScaffold("内容与数据来源", { onNavigate(Route.Settings) }) {
        Text("内置或按需下载的第三方内容来源单独列在这里；条目只做链接跳转，不改变原许可。", style = OD.Cap.copy(lineHeight = 17.sp))
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = OD.H3.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                    Text(item.license, style = OD.Micro.copy(color = OD.CyanDeep))
                }
                Spacer(Modifier.height(3.dp))
                Text(item.usage, style = OD.Cap.copy(fontSize = 10.5.sp, lineHeight = 15.sp))
                Spacer(Modifier.height(5.dp))
                ODButton("打开来源页面", { open(item.url) }, primary = false)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
        }
        Spacer(Modifier.height(14.dp))
        Text("第三方内容的版权与许可以其原页面为准；本应用仅保存必要的离线数据或链接。", style = OD.Micro.copy(lineHeight = 15.sp))
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
fun HelpScreen(onNavigate: (Route) -> Unit, onOpenUrl: (String) -> Unit) {
    var expanded by remember { mutableStateOf(-1) }
    val faqs = listOf(
        "为什么有些词没有音乐/视频例句？" to "音乐与视频例句只接入已授权或允许外链的内容；没有合适来源时该分区显示为空状态。",
        "本地 AI 需要联网吗？" to "不需要。模型下载完成后完全离线运行；下载过程需要网络。",
        "更新提示是怎么来的？" to "只检查公开仓库的预发布版本（prerelease），并要求版本资产包含 update.json 与 APK；普通提交不会触发更新。",
        "数据会上传吗？" to "不会。查询记录、笔记与设置保存在本机。"
    )
    PageScaffold("帮助与反馈", { onNavigate(Route.Settings) }) {
        faqs.forEachIndexed { i, (q, a) ->
            Pressable(onClick = { expanded = if (expanded == i) -1 else i }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(q, style = OD.H3.copy(fontSize = 12.5.sp), modifier = Modifier.weight(1f))
                        OIcon(if (expanded == i) Ic.Chevron else Ic.Chevron, 12.dp, OD.Muted)
                    }
                    if (expanded == i) { Spacer(Modifier.height(5.dp)); Text(a, style = OD.Cap.copy(lineHeight = 18.sp)) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
        }
        Spacer(Modifier.height(18.dp))
        SettingItem(Ic.Info, "开源许可", "第三方项目、协议与仓库链接") { onNavigate(Route.Licenses) }
        SettingItem(Ic.External, "反馈问题", "在代码托管平台提交 issue") { onOpenUrl("https://github.com/XHR666/OpenDictionary-apk/issues") }
        SettingItem(Ic.Link, "项目主页", "源码与发布说明") { onOpenUrl("https://github.com/XHR666/OpenDictionary-apk") }
        SettingItem(Ic.Heart, "支持开发", "爱发电 · 完全自愿") { onNavigate(Route.Support) }
        Spacer(Modifier.height(14.dp))
        Text("应用版本 ${io.github.xhr666.opendictionary.BuildConfig.VERSION_NAME} · 构建 2026.10", style = OD.Micro)
    }
}

data class LicenseEntry(val name: String, val license: String, val repo: String, val licenseUrl: String)

val openSourceLicenses = listOf(
    LicenseEntry("OpenDictionary（本项目代码）", "AGPL-3.0-or-later", "https://github.com/XHR666/OpenDictionary", "https://www.gnu.org/licenses/agpl-3.0.html"),
    LicenseEntry("OpenDictionary 自编内容", "CC BY-NC-SA 4.0", "https://github.com/XHR666/OpenDictionary", "https://creativecommons.org/licenses/by-nc-sa/4.0/"),
    LicenseEntry("Kotlin", "Apache-2.0", "https://github.com/JetBrains/kotlin", "https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt"),
    LicenseEntry("AndroidX / Jetpack Compose", "Apache-2.0", "https://github.com/androidx/androidx", "https://www.apache.org/licenses/LICENSE-2.0"),
    LicenseEntry("Kotlin Coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt"),
    LicenseEntry("PDFBox-Android", "Apache-2.0", "https://github.com/TomRoush/PdfBox-Android", "https://www.apache.org/licenses/LICENSE-2.0"),
    LicenseEntry("AndroidLiquidGlass (backdrop)", "Apache-2.0", "https://github.com/Kyant0/AndroidLiquidGlass", "https://www.apache.org/licenses/LICENSE-2.0"),
    LicenseEntry("GAOKAO-Bench（阅读样例）", "Apache-2.0", "https://github.com/OpenLMLab/GAOKAO-Bench", "https://www.apache.org/licenses/LICENSE-2.0"),
    LicenseEntry("IELTS AI-Generated Dataset（练习样例）", "CC BY 4.0", "https://github.com/LuchoBazz/ielts-ai-dataset", "https://creativecommons.org/licenses/by/4.0/"),
    LicenseEntry("llama.cpp", "MIT", "https://github.com/ggml-org/llama.cpp", "https://github.com/ggml-org/llama.cpp/blob/master/LICENSE"),
    LicenseEntry("Qwen2.5 (模型)", "Apache-2.0", "https://github.com/QwenLM/Qwen2.5", "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/blob/main/LICENSE"),
    LicenseEntry("ECDICT", "MIT（代码）", "https://github.com/skywind3000/ECDICT", "https://github.com/skywind3000/ECDICT/blob/master/LICENSE"),
    LicenseEntry("CC-CEDICT", "CC BY-SA 4.0", "https://cc-cedict.org/", "https://creativecommons.org/licenses/by-sa/4.0/"),
    LicenseEntry("Open English WordNet", "CC BY 4.0", "https://github.com/globalwordnet/english-wordnet", "https://github.com/globalwordnet/english-wordnet/blob/main/LICENSE"),
    LicenseEntry("Wiktionary", "CC BY-SA 4.0", "https://www.wiktionary.org/", "https://creativecommons.org/licenses/by-sa/4.0/"),
    LicenseEntry("Wikipedia", "CC BY-SA 4.0", "https://www.wikipedia.org/", "https://creativecommons.org/licenses/by-sa/4.0/"),
    LicenseEntry("Tatoeba", "CC-BY 2.0 FR", "https://tatoeba.org/", "https://creativecommons.org/licenses/by/2.0/fr/"),
    LicenseEntry("2ndLA / english-phrases", "CC BY-SA 4.0", "https://github.com/2ndLA/english-phrases", "https://github.com/2ndLA/english-phrases/blob/main/LICENSE"),
    LicenseEntry("Inter（界面字体）", "SIL OFL 1.1", "https://github.com/google/fonts/tree/main/ofl/inter", "https://openfontlicense.org/"),
    LicenseEntry("Literata（阅读字体）", "SIL OFL 1.1", "https://github.com/google/fonts/tree/main/ofl/literata", "https://openfontlicense.org/"),
    LicenseEntry("Caveat（手写动画字体）", "SIL OFL 1.1", "https://github.com/google/fonts/tree/main/ofl/caveat", "https://openfontlicense.org/")
)

@Composable
fun LicensesScreen(onNavigate: (Route) -> Unit, onOpenUrl: (String) -> Unit) {
    PageScaffold("开源许可", { onNavigate(Route.Help) }) {
        Text("OpenDictionary 项目的诞生离不开许多开源项目", style = OD.Cap.copy(color = OD.Muted, fontSize = 11.5.sp), modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        openSourceLicenses.forEach { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                Text(entry.name, style = OD.H3.copy(fontSize = 13.sp))
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(OD.Parchment).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(entry.license, style = OD.Micro.copy(color = OD.InkSoft)) }
                    Spacer(Modifier.width(8.dp))
                    LinkText("仓库", onClick = { onOpenUrl(entry.repo) })
                    Spacer(Modifier.width(10.dp))
                    LinkText("LICENSE", onClick = { onOpenUrl(entry.licenseUrl) })
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
        }
        Spacer(Modifier.height(16.dp))
        Text("以上项目版权归各自作者所有，按其许可证使用；完整许可文本见各项目仓库。", style = OD.Micro.copy(lineHeight = 15.sp))
    }
}

// ---------- 支持开发 ----------
@Composable
fun SupportScreen(onNavigate: (Route) -> Unit, onOpenUrl: (String) -> Unit) {
    PageScaffold("支持开发", { onNavigate(Route.Settings) }) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(OD.Ink, Color(0xFF394A4F))))) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(OD.Cyan.copy(alpha = .25f), size.width * .35f, Offset(size.width * .85f, size.height * .25f))
                drawCircle(OD.Cyan.copy(alpha = .12f), size.width * .28f, Offset(size.width * .15f, size.height * .8f))
            }
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Center) {
                Text("查词这件小事，", style = OD.H2.copy(color = Color.White, fontSize = 20.sp))
                Text("值得做得干净一点。", style = OD.H2.copy(color = OD.Cyan, fontSize = 20.sp))
                Spacer(Modifier.height(8.dp))
                Text("完全离线 · 无广告 · 无账号 · 资源开放", style = OD.Cap.copy(color = Color.White.copy(alpha = .7f)))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("如果它帮到了你，可以请开发者喝杯咖啡。所有支持完全自愿，不影响任何功能。", style = OD.BodySm)
        Spacer(Modifier.height(14.dp))
        ODButton("爱发电 · afdian.com/a/xferoni66", onClick = { onOpenUrl("https://afdian.com/a/xferoni66") })
        Spacer(Modifier.height(10.dp))
        Text("支持链接由第三方平台提供；打开后请以页面显示的信息为准。", style = OD.Micro)
        Spacer(Modifier.height(18.dp))
        Text("其他支持方式", style = OD.H3)
        Spacer(Modifier.height(6.dp))
        val supportCtx = androidx.compose.ui.platform.LocalContext.current
        val repo = "https://github.com/XHR666/OpenDictionary-apk"
        Pressable(onClick = { onOpenUrl("$repo/issues") }) {
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("反馈问题", style = OD.H3.copy(fontSize = 12.5.sp)); Text("到 GitHub Issues 提交问题与建议", style = OD.Cap.copy(fontSize = 10.5.sp)) }
                OIcon(Ic.External, 13.dp, OD.Muted)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
        Pressable(onClick = {
            runCatching {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "OpenDictionary 离线英语词典：$repo")
                }
                supportCtx.startActivity(android.content.Intent.createChooser(send, "分享 OpenDictionary").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }) {
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("分享给朋友", style = OD.H3.copy(fontSize = 12.5.sp)); Text("发送下载页链接", style = OD.Cap.copy(fontSize = 10.5.sp)) }
                OIcon(Ic.External, 13.dp, OD.Muted)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
        Pressable(onClick = { onOpenUrl(repo) }) {
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("参与开源", style = OD.H3.copy(fontSize = 12.5.sp)); Text("查看源码与预发布版本", style = OD.Cap.copy(fontSize = 10.5.sp)) }
                OIcon(Ic.External, 13.dp, OD.Muted)
            }
        }
    }
}

// ---------- 关于 ----------
@Composable
fun AboutScreen(onNavigate: (Route) -> Unit, onOpenUrl: (String) -> Unit) {
    PageScaffold("关于", { onNavigate(Route.Settings) }) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(14.dp))
            Column { Text("OpenDictionary", style = OD.H2); Text("版本 ${io.github.xhr666.opendictionary.BuildConfig.VERSION_NAME} · 构建 2026.10", style = OD.Cap) }
        }
        Spacer(Modifier.height(18.dp))
        Text("离线优先的英语学习应用。无广告、无账号、不主动收费；词典与例句以本地数据为主，可选装本地 LLM。", style = OD.BodySm.copy(lineHeight = 21.sp))
        Spacer(Modifier.height(18.dp))
        SettingItem(Ic.Info, "开源许可", "第三方项目、协议与链接") { onNavigate(Route.Licenses) }
        SettingItem(Ic.Shield, "隐私说明", "数据与隐私") { onNavigate(Route.Privacy) }
        SettingItem(Ic.Link, "更新源", "GitHub 预发布") { onOpenUrl("https://github.com/XHR666/OpenDictionary-apk") }
        Spacer(Modifier.height(16.dp))
        Text("AGPL-3.0-or-later · 自编内容 CC BY-NC-SA 4.0 · 第三方保留原许可", style = OD.Micro)
    }
}

// ---------- 学习页 / 占位页 ----------
@Composable
fun LearningScreen(onNavigate: (Route) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lex = remember { io.github.xhr666.opendictionary.data.LexiconDb.get(ctx) }
    val userDb = remember { io.github.xhr666.opendictionary.data.UserDb.get(ctx) }
    var book by remember { mutableStateOf(userDb.wordbookWords()) }
    var gk by remember { mutableStateOf(emptyList<io.github.xhr666.opendictionary.data.LexEntry>()) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var books by remember { mutableStateOf(userDb.wordbooks()) }
    var selectedBook by rememberSaveable { mutableStateOf(books.firstOrNull { it.isDefault }?.id ?: -1L) }
    var showCreate by remember { mutableStateOf(false) }
    var showReadmeEdit by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf("") }
    var showPacks by remember { mutableStateOf(false) }
    var packs by remember { mutableStateOf<List<io.github.xhr666.opendictionary.data.PackInfo>>(emptyList()) }
    var packsLoading by remember { mutableStateOf(false) }
    var packProgress by remember { mutableStateOf<Float?>(null) }
    var downloadingPack by remember { mutableStateOf<String?>(null) }
    var packsError by remember { mutableStateOf("") }
    var listLimit by remember { mutableIntStateOf(200) }
    var showRename by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val wordbookImporter = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val parsed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                        val words = if (text.trimStart().startsWith("[")) {
                            val arr = org.json.JSONArray(text)
                            (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotBlank() } }
                        } else text.split("\n").mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull()?.trim() }.filter { it.isNotBlank() }
                        words.map { it.substringBefore(',').trim() }.filter { it.matches(Regex("[A-Za-z][A-Za-z' -]*")) }.distinct().take(10000)
                    }
                }
                parsed.onSuccess { words ->
                    if (words.isNotEmpty()) {
                        val title = (runCatching {
                            ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                                if (c.moveToFirst()) c.getString(0) else null
                            }
                        }.getOrNull() ?: uri.lastPathSegment)?.substringBeforeLast('.')?.ifBlank { "导入词书" } ?: "导入词书"
                        val id = userDb.wordbooks().firstOrNull { !it.isDefault && it.name == (title.ifBlank { "导入词书" }) }?.id
                            ?: userDb.createWordBook(title.ifBlank { "导入词书" }, "从文件导入")
                        val added = userDb.importWordsToBook(id, words)
                        books = userDb.wordbooks(); selectedBook = id; book = userDb.wordsInBook(id)
                        importStatus = "已导入 $added 词到「$title」"
                    } else importStatus = "没有解析到单词"
                }.onFailure { importStatus = "导入失败：${it.message}" }
            }
        }
    }
    var readmeDraft by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { gk = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { lex.gkWords(40) }; books = userDb.wordbooks(); if (selectedBook <= 0) selectedBook = books.firstOrNull { it.isDefault }?.id ?: -1L; book = if (selectedBook > 0) userDb.wordsInBook(selectedBook) else userDb.wordbookWords() }
    PageScaffold("学习", { onNavigate(Route.Home) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("生词本 (${book.size})", tab == 0) { tab = 0 }
            Pill("高考词汇", tab == 1) { tab = 1 }
            Spacer(Modifier.weight(1f))
            ODButton("卡片", onClick = { onNavigate(Route.CardMode(selectedBook)) }, primary = false, modifier = Modifier.width(58.dp))
            ODButton("折叠法", onClick = { onNavigate(Route.FoldReview) }, primary = false, modifier = Modifier.width(72.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            books.forEach { b ->
                Pill("${b.name} (${b.count})", b.id == selectedBook) {
                    selectedBook = b.id
                    book = userDb.wordsInBook(b.id)
                    tab = 0
                }
            }
            Pill("+ 新建", false) { showCreate = true }
            Pill("导入", false) { wordbookImporter.launch(arrayOf("text/plain", "application/json", "text/*")) }
            Pill("资源包", false) { showPacks = true; packsError = "" }
        }
        val selectedBookObj = books.firstOrNull { it.id == selectedBook }
        if (selectedBookObj != null) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OD.Pearl).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("词书说明", style = OD.Micro.copy(fontSize = 8.5.sp))
                    Text(selectedBookObj.readme.ifBlank { "这本书还没有说明。用来记录分类、使用场景或阅读顺序。" }, style = OD.Cap.copy(fontSize = 10.5.sp, lineHeight = 15.sp), maxLines = 3)
                }
                Pressable(onClick = { readmeDraft = selectedBookObj.readme; showReadmeEdit = true }) { Text("编辑", style = OD.Cap.copy(color = OD.CyanDeep, fontSize = 11.sp)) }
            }
        }
        if (importStatus.isNotBlank()) { Text(importStatus, style = OD.Micro.copy(color = OD.CyanDeep)); Spacer(Modifier.height(6.dp)) }
        Spacer(Modifier.height(12.dp))
        if (tab == 0) {
            if (book.isEmpty()) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(OD.Pearl).border(1.dp, OD.Hairline, RoundedCornerShape(13.dp)).padding(14.dp)) {
                    Column {
                        Text("生词本还是空的", style = OD.H3)
                        Spacer(Modifier.height(4.dp))
                        Text("在词条页点“加入生词本”，就会出现在这里。", style = OD.Cap)
                        Spacer(Modifier.height(8.dp))
                        ODButton("去查词", onClick = { onNavigate(Route.Search("")) }, primary = false)
                    }
                }
            }
            val entries = remember(book) { if (book.isEmpty()) emptyList() else lex.entriesByWords(book) }
            entries.forEach { e ->
                Pressable(onClick = { onNavigate(Route.Word(e.word)) }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(OD.CyanSoft), contentAlignment = Alignment.Center) { Text(e.word.take(1).uppercase(), style = OD.Micro.copy(color = OD.CyanDeep, fontWeight = FontWeight.Bold)) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(e.word, style = OD.H3.copy(fontSize = 13.5.sp)); if (e.phonetic.isNotBlank()) { Spacer(Modifier.width(5.dp)); Text("/${e.phonetic}/", style = OD.Micro) } }
                            Text(e.translation.replace("\n", " ").take(52), style = OD.Cap.copy(fontSize = 10.5.sp))
                        }
                        Pressable(onClick = { if (selectedBook > 0) userDb.removeWordFromBook(selectedBook, e.word) else userDb.removeFromWordbook(e.word); book = if (selectedBook > 0) userDb.wordsInBook(selectedBook) else userDb.wordbookWords(); books = userDb.wordbooks() }) { OIcon(Ic.Trash, 14.dp, OD.Muted) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .55f)))
            }
            if (entries.size > listLimit) {
                Spacer(Modifier.height(8.dp))
                ODButton("显示更多（${(entries.size - listLimit).coerceAtMost(200)}）", { listLimit += 200 }, primary = false, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Text("高考词汇 · 前 ${gk.size} 词", style = OD.Cap.copy(color = OD.Muted))
            Spacer(Modifier.height(6.dp))
            gk.forEachIndexed { i, e ->
                Pressable(onClick = { onNavigate(Route.Word(e.word)) }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("%02d".format(i + 1), style = OD.Micro.copy(fontSize = 9.sp), modifier = Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.word, style = OD.H3.copy(fontSize = 13.5.sp))
                            Text(e.translation.replace("\n", " ").take(48), style = OD.Cap.copy(fontSize = 10.5.sp))
                        }
                        OIcon(Ic.Chevron, 11.dp, OD.Hairline)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .5f)))
            }
        }
        Spacer(Modifier.height(96.dp))
    }
    if (showPacks) {
        DialogCard(visible = true, onDismiss = { showPacks = false }) {
            Text("考试词书资源包", style = OD.H2)
            Spacer(Modifier.height(4.dp))
            Text("从 GitHub Releases 下载词表（ECDICT MIT 整理），SHA-256 校验后导入词书；释义由内置离线词典提供。", style = OD.Micro.copy(lineHeight = 15.sp))
            Spacer(Modifier.height(8.dp))
            LaunchedEffect(showPacks) {
                if (packs.isEmpty() && !packsLoading) {
                    packsLoading = true
                    runCatching { io.github.xhr666.opendictionary.data.WordbookPacks.fetchManifest() }
                        .onSuccess { packs = it }.onFailure { packsError = "获取失败：${it.message}" }
                    packsLoading = false
                }
            }
            if (packsLoading) Text("正在获取清单…", style = OD.Cap)
            else if (packsError.isNotBlank()) Text(packsError, style = OD.Cap.copy(color = OD.Warm))
            else {
                Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    packs.forEach { pack ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pack.name, style = OD.H3.copy(fontSize = 13.sp))
                                Text("${pack.count} 词 · ${pack.size / 1024} KB", style = OD.Micro)
                            }
                            if (books.any { it.name == pack.name }) Text("已导入", style = OD.Micro.copy(color = OD.Muted))
                            else Pressable(onClick = {
                                if (downloadingPack == null) {
                                    downloadingPack = pack.id; packProgress = 0f; packsError = ""
                                    scope.launch {
                                        runCatching {
                                            io.github.xhr666.opendictionary.data.WordbookPacks.downloadPack(pack) { p -> packProgress = p }
                                        }.onSuccess { words ->
                                            val id = userDb.createWordBook(pack.name, pack.description)
                                            val added = userDb.importWordsToBook(id, words)
                                            books = userDb.wordbooks(); selectedBook = id; book = userDb.wordsInBook(id)
                                            importStatus = "已导入「${pack.name}」$added 词"
                                            showPacks = false
                                        }.onFailure { packsError = "下载失败：${it.message}" }
                                        packProgress = null; downloadingPack = null
                                    }
                                }
                            }) { Text(if (downloadingPack == pack.id) "%.0f%%".format((packProgress ?: 0f) * 100) else "下载", style = OD.Cap.copy(color = OD.CyanDeep)) }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .5f)))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("关闭", { showPacks = false }, primary = false, modifier = Modifier.weight(1f))
            }
        }
    }
    if (showReadmeEdit) {
        DialogCard(visible = true, onDismiss = { showReadmeEdit = false }) {
            Text("词书说明 · Read Me", style = OD.H2)
            Spacer(Modifier.height(6.dp))
            Text("可写分类说明、使用建议；支持普通文本。", style = OD.Cap)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(12.dp)).padding(10.dp)) {
                BasicTextField(value = readmeDraft, onValueChange = { readmeDraft = it }, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = OD.InkSoft), modifier = Modifier.fillMaxSize(), decorationBox = { inner -> if (readmeDraft.isEmpty()) Text("写点分类说明…", style = OD.Cap) else inner() })
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("取消", { showReadmeEdit = false }, primary = false, modifier = Modifier.weight(1f))
                ODButton("保存", { if (selectedBook > 0) userDb.updateWordBookReadme(selectedBook, readmeDraft); books = userDb.wordbooks(); showReadmeEdit = false }, modifier = Modifier.weight(1f))
            }
            val thisBook = books.firstOrNull { it.id == selectedBook }
            if (thisBook != null && !thisBook.isDefault) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ODButton("重命名", { renameDraft = thisBook.name; showReadmeEdit = false; showRename = true }, primary = false, modifier = Modifier.weight(1f))
                    ODButton("删除词书", {
                        userDb.deleteWordBook(selectedBook)
                        books = userDb.wordbooks(); selectedBook = books.firstOrNull { it.isDefault }?.id ?: -1L
                        book = if (selectedBook > 0) userDb.wordsInBook(selectedBook) else emptyList()
                        showReadmeEdit = false; importStatus = "已删除词书「${thisBook.name}」"
                    }, primary = false, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (showRename) {
        DialogCard(visible = true, onDismiss = { showRename = false }) {
            Text("重命名词书", style = OD.H2)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(value = renameDraft, onValueChange = { renameDraft = it.take(40) }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = OD.Ink), decorationBox = { inner -> if (renameDraft.isEmpty()) Text("词书名称", style = OD.Cap) else inner() }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("取消", { showRename = false }, primary = false, modifier = Modifier.weight(1f))
                ODButton("保存", {
                    if (selectedBook > 0) userDb.renameWordBook(selectedBook, renameDraft)
                    books = userDb.wordbooks(); showRename = false
                }, modifier = Modifier.weight(1f))
            }
        }
    }
    if (showCreate) {
        DialogCard(visible = true, onDismiss = { showCreate = false }) {
            Text("新建单词本", style = OD.H2)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(value = newName, onValueChange = { newName = it }, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = OD.Ink), singleLine = true, decorationBox = { inner -> if (newName.isEmpty()) Text("名称", style = OD.Cap) else inner() }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("取消", { showCreate = false }, primary = false, modifier = Modifier.weight(1f))
                ODButton("创建", {
                    val id = userDb.createWordBook(newName.trim().ifBlank { "新单词本" })
                    books = userDb.wordbooks(); selectedBook = id; book = emptyList(); newName = ""; showCreate = false
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

