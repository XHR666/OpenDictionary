package io.github.xhr666.opendictionary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(onDone: () -> Unit, onDecline: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 26.dp).padding(top = 26.dp, bottom = 26.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (page < 2) {
                    Pressable(onClick = { page = 2 }) {
                        Text("跳过", style = OD.Cap.copy(fontSize = 12.sp, color = OD.Muted))
                    }
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when (page) {
                    0 -> HelloPage()
                    1 -> IntroPage()
                    else -> DisclaimerPage(onAgree = onDone, onDecline = onDecline)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(3) { i ->
                    Box(Modifier.padding(horizontal = 4.dp).size(if (i == page) 18.dp else 6.dp, 6.dp)
                        .clip(CircleShape).background(if (i == page) OD.Ink else OD.Hairline))
                }
            }
            Spacer(Modifier.height(18.dp))
            if (page < 2) {
                ODButton(if (page == 0) "开始使用" else "继续", onClick = { page++ })
            }
        }
    }
}

@Composable
private fun HelloPage() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 2400)) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(140.dp), contentAlignment = Alignment.CenterStart) {
            Handwriting("Hello", progress = progress.value, size = 104.sp, color = OD.Ink)
            if (progress.value >= 1f) {
                // 手写末尾的光标
                Box(Modifier.padding(start = 250.dp).width(3.dp).height(52.dp).background(OD.Cyan))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("OpenDictionary", style = OD.H2.copy(letterSpacing = (-0.4).sp))
        Spacer(Modifier.height(10.dp))
        Text("离线优先的英语学习应用", style = OD.BodySm.copy(color = OD.Muted))
        Spacer(Modifier.height(38.dp))
        Text("第一次见面，先带你熟悉一下。", style = OD.Cap.copy(fontSize = 12.sp))
    }
}

@Composable
private fun IntroPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("把单词放回它的世界里", style = OD.H1.copy(fontSize = 24.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("不是只给一条中文释义。", style = OD.BodySm.copy(color = OD.Muted))
        Spacer(Modifier.height(26.dp))
        listOf(
            Triple(Ic.Book, "12 个信息分区", "例句、词源、百科、同根词 —— 一个词的面貌完整展开"),
            Triple(Ic.Sparkle, "本地 AI，离线可用", "模型在手机上运行；不联网也能解释词义、改写例句"),
            Triple(Ic.Shield, "完全离线 · 无广告 · 无账号", "词典与学习记录都在本机；资源从公开仓库按需更新")
        ).forEach { (icon, title, desc) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(OD.CyanSoft), contentAlignment = Alignment.Center) {
                    OIcon(icon, 20.dp, OD.CyanDeep)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, style = OD.H3)
                    Spacer(Modifier.height(4.dp))
                    Text(desc, style = OD.Cap.copy(lineHeight = 17.sp))
                }
            }
        }
    }
}

@Composable
private fun DisclaimerPage(onAgree: () -> Unit, onDecline: () -> Unit) {
    val scroll = rememberScrollState()
    val reachedBottom by remember { derivedStateOf { scroll.maxValue <= 0 || scroll.value >= scroll.maxValue - 2 } }
    Column(Modifier.fillMaxSize()) {
        Text("使用前请阅读", style = OD.H1.copy(fontSize = 24.sp))
        Spacer(Modifier.height(6.dp))
        Text("请向下滑动阅读完整说明；滑到底部后即可同意并继续。", style = OD.Cap.copy(color = OD.Muted))
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White)
                .verticalScroll(scroll).padding(18.dp)
        ) {
            DisclaimerContent()
        }
        Spacer(Modifier.height(12.dp))
        Text(if (reachedBottom) "已阅读完整说明" else "请滑到底部", style = OD.Micro.copy(color = if (reachedBottom) OD.Good else OD.Muted))
        Spacer(Modifier.height(8.dp))
        ODButton(if (reachedBottom) "同意并继续" else "滑到底部后可继续", onClick = onAgree, enabled = reachedBottom)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Pressable(onClick = onDecline) { Text("不同意并退出", style = OD.Cap.copy(color = OD.Muted)) }
        }
    }
}

@Composable
private fun DisclaimerContent() {
    val paras = listOf(
        "欢迎使用 OpenDictionary。这是一款离线优先的英语学习应用，词典、例句、学习记录默认保存在本机；应用不要求注册账号，也不主动收集个人身份信息。",
        "一、数据与词典内容。本应用使用的词典与例句来自多个开放数据源（包括但不限于 ECDICT、CC-CEDICT、Wiktionary、Tatoeba 等），各数据源适用其自身的开放许可。部分释义、例句与百科摘要可能存在错误、过时或不完整的情况，请以权威辞书与原始来源为准。",
        "二、人工智能生成内容。应用内的 AI 解释、改写与建议由本地模型生成，可能存在不准确、遗漏或误导。AI 输出仅作为学习参考，不构成考试答案、翻译结论或专业建议。你不应将 AI 输出直接用于需要准确性的正式场合。",
        "三、版权与引用。应用展示的例句、歌词、视频与百科内容均标注来源与许可；受版权保护的内容仅以链接、合理引用或已获授权的方式呈现。请勿将应用内内容用于超出原许可范围的复制、传播或商业用途。",
        "四、本地模型与资源。模型文件与词典包体积较大，默认不随安装包分发，由你自行选择下载；下载地址为公开的代码托管平台。请确认你所在地区的法律法规允许下载与使用相关模型与数据。",
        "五、更新与网络。检查更新时应用会访问公开的 GitHub Releases 接口，仅用于获取版本号与安装包；不会上传你的学习记录或查询内容。你可以随时在设置中关闭自动检查。",
        "六、隐私与安全。应用不读取剪贴板、不监听其他应用、不上传通讯录与位置信息。学习记录、笔记与设置保存在应用私有目录；卸载应用会删除这些数据。",
        "七、免责声明。本应用按“现状”提供，不对内容的准确性、完整性、可用性作出明示或默示担保。因使用或无法使用本应用造成的任何直接或间接损失，开发者不承担责任。你应在遵守当地法律与相关许可的前提下使用本应用。",
        "八、内容与题库。应用中的考试题、例句与熟词生义标注可能来自用户本地导入、开放许可数据集或生成式练习。受版权保护的试题不会默认抓取或内置分发；用户导入的资料由用户自行确保其来源合法，仅限个人学习使用。",
        "九、媒体与歌词。导入的音乐、视频、歌词与字幕版权归原权利人所有；应用只在本地播放与索引，不提供下载、不二次分发。逐词高亮与片段播放仅用于个人学习。请在导入前确认你拥有相应文件的使用权。",
        "十、敏感内容与年龄。AI 与词典可能包含俚语、俚俗或成人语境释义。设置中可选择年龄提示方式（每次询问/不再询问/直接显示）；未成年人应在监护人指导下使用。应用不会主动推送此类内容。",
        "十一、厂商系统能力。灵动岛/流体云/原子岛/媒体卡片等能力依赖手机厂商系统与系统版本；应用会尽力通过标准媒体会话适配，但无法保证在所有机型上显示或表现一致。",
        "十二、下载与通知。下载模型、词典资源或更新时，应用会申请通知权限并显示下载进度；你可以随时暂停/取消。下载内容来自公开代码托管平台，请遵守其服务条款与许可。",
        "十三、捐赠与商业化。应用当前不含广告、会员或付费解锁；如提供捐赠入口，捐赠为无对价的资源支持，不构成购买服务或商业授权。代码按 AGPL-3.0-or-later、项目自编内容按 CC BY-NC-SA 4.0 发布，第三方内容保留原许可。",
        "十四、变更与重新确认。应用功能、数据来源、免责声明与许可说明可能随大版本更新调整；每个大版本更新后，你需要重新阅读并同意更新后的说明，之后才能继续使用。",
        "十五、免责声明。本应用按“现状”提供，不对内容的准确性、完整性、可用性作出明示或默示担保。因使用或无法使用本应用造成的任何直接或间接损失，开发者不承担责任。你应在遵守当地法律与相关许可的前提下使用本应用。",
        "如果你同意以上说明，请勾选下方按钮继续。你可以随时在设置 → 帮助与反馈 → 开源许可中查看完整的第三方许可列表。"
    )
    paras.forEach { p ->
        Text(p, style = OD.BodySm.copy(lineHeight = 21.sp), modifier = Modifier.padding(bottom = 14.dp))
    }
    Text("OpenDictionary 项目 · 2026", style = OD.Micro.copy(color = OD.Muted))
}
