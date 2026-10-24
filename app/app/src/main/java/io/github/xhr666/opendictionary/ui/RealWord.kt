package io.github.xhr666.opendictionary.ui

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.Tts
import io.github.xhr666.opendictionary.data.LexEntry
import io.github.xhr666.opendictionary.data.LexiconDb
import io.github.xhr666.opendictionary.data.UserDb
import io.github.xhr666.opendictionary.data.WordData
import io.github.xhr666.opendictionary.media.MediaPlayback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun formLabel(key: String): String = when (key.trim()) {
    "p" -> "过去式"; "d" -> "过去分词"; "i" -> "现在分词"; "3" -> "三单"
    "r" -> "比较级"; "t" -> "最高级"; "s" -> "复数"; "0" -> "原形"; else -> key
}

private fun tagLabel(raw: String): String = when (raw.lowercase()) {
    "zk" -> "中考"; "gk" -> "高考"; "cet4" -> "CET-4"; "cet6" -> "CET-6"; "ky" -> "考研"
    "toefl" -> "TOEFL"; "ielts" -> "IELTS"; "gre" -> "GRE"; "gmat" -> "GMAT"; "bec" -> "商务英语"
    "tem4" -> "专四"; "tem8" -> "专八"; else -> raw.uppercase()
}

private val sections = listOf("双语例句", "音乐例句", "视频例句", "网络释义", "专业释义", "用户贡献", "英英释义", "同近义词", "同根词", "词组短语", "词源", "百科")

@Composable
fun SearchScreen(query: String, onNavigate: (Route) -> Unit) {
    val context = LocalContext.current
    val db = remember { LexiconDb.get(context) }
    val userDb = remember { io.github.xhr666.opendictionary.data.UserDb.get(context) }
    val recent = remember { userDb.recentQueries(12) }
    var results by remember { mutableStateOf<List<LexEntry>>(emptyList()) }
    var chinese by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(query) {
        withContext(Dispatchers.IO) {
            chinese = query.any { it.code in 0x4E00..0x9FFF }
            results = if (chinese) db.searchByChinese(query) else {
                val compact = io.github.xhr666.opendictionary.data.WordForms.compact(query)
                (db.searchByWord(query) + userDb.searchCustom(query))
                    .distinctBy { it.word }
                    .sortedWith(compareBy(
                        { if (it.word.equals(query, true)) 0 else 1 },
                        { if (io.github.xhr666.opendictionary.data.WordForms.compact(it.word) == compact) 0 else 1 },
                        { it.word.length }
                    ))
                    .take(30)
            }
        }
        loading = false
    }
    Backdrop {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                TopBar("词典", onBack = { onNavigate(Route.Home) })
                // 只读搜索条：点击进入全屏输入框。避免在结果页出现不可编辑的 BasicTextField，输入法预览会残留已删除字符。
                Pressable(onClick = { onNavigate(Route.SearchInput) }) {
                    Row(Modifier.fillMaxWidth().height(46.dp).liquidGlass(RoundedCornerShape(13.dp), Color.White.copy(alpha = .18f))
                        .border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(13.dp)).padding(horizontal = 12.dp).semantics { contentDescription = "搜索词条" },
                        verticalAlignment = Alignment.CenterVertically) {
                        OIcon(Ic.Search, 16.dp, OD.Muted)
                        Spacer(Modifier.width(8.dp))
                        Text(if (query.isBlank()) "输入单词、词组或中文" else query, style = OD.Body.copy(fontSize = 13.5.sp, color = if (query.isBlank()) OD.Muted else OD.Ink), modifier = Modifier.weight(1f))
                        OIcon(Ic.Chevron, 12.dp, OD.Hairline)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(if (query.isBlank()) "搜索单词、词组，或输入中文反查英文" else if (chinese) "中文 → 英文候选（含常用语义映射）；点一条查看完整词条" else "单词与前缀匹配；点一条查看完整词条", style = OD.Micro)
            }
            if (query.isBlank()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("最近查词", style = OD.H3)
                    Spacer(Modifier.height(8.dp))
                    if (recent.isEmpty()) {
                        Text("还没有查询记录。上方输入一个词开始。", style = OD.Cap)
                    } else {
                        recent.forEach { r ->
                            Pressable(onClick = { onNavigate(Route.Word(r.word)) }) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.word, style = OD.H3.copy(fontSize = 13.5.sp), modifier = Modifier.weight(1f))
                                    Text(android.text.format.DateFormat.format("MM-dd HH:mm", r.ts).toString(), style = OD.Micro)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .55f)))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("每日一句", style = OD.H3)
                    Spacer(Modifier.height(6.dp))
                    Text("Keep your face to the sunshine and you cannot see a shadow.", style = OD.ReadBody)
                    Text("— Helen Keller", style = OD.Cap)
                }
                return@Backdrop
            }
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("查询中…", style = OD.Cap) }
                results.isEmpty() -> Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(40.dp))
                    OIcon(Ic.Search, 30.dp, OD.Hairline)
                    Spacer(Modifier.height(10.dp))
                    Text("没有找到“$query”", style = OD.H3)
                    Spacer(Modifier.height(4.dp))
                    Text("可以试试更短的词根、换成中文，或检查拼写；新词/专业术语可在线查询。", style = OD.Cap)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ODButton("在线查询/补词", onClick = { onNavigate(Route.Word(query)) }, primary = true, modifier = Modifier.weight(1f))
                        ODButton("回到首页", onClick = { onNavigate(Route.Home) }, primary = false, modifier = Modifier.weight(1f))
                    }
                }
                else -> {
                    val queryCompact = io.github.xhr666.opendictionary.data.WordForms.compact(query)
                    val hasExact = results.any { it.word.equals(query, true) || io.github.xhr666.opendictionary.data.WordForms.compact(it.word) == queryCompact }
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    if (!hasExact) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(12.dp)).background(OD.CyanSoft.copy(alpha = .55f)).padding(12.dp)) {
                            Text("本地词库未收录“$query”", style = OD.H3.copy(fontSize = 13.sp))
                            Spacer(Modifier.height(4.dp))
                            Text("下面是相近结果；新词/专业术语可在线上查询并保存为本地词条。", style = OD.Cap.copy(fontSize = 10.5.sp, lineHeight = 15.sp))
                            Spacer(Modifier.height(8.dp))
                            ODButton("在线查询“$query”", { onNavigate(Route.Word(query)) }, primary = false)
                        }
                    }
                    item { Text("${results.size} 个结果", style = OD.Micro.copy(color = OD.Muted), modifier = Modifier.padding(vertical = 8.dp)) }
                    items(results) { e ->
                        Pressable(onClick = { onNavigate(Route.Word(e.word)) }) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(e.word, style = OD.H3.copy(fontSize = 15.sp))
                                    if (e.phonetic.isNotBlank()) { Spacer(Modifier.width(6.dp)); Text("/${e.phonetic}/", style = OD.Micro) }
                                    Spacer(Modifier.weight(1f))
                                    e.tag.split(" ").filter { it.isNotBlank() }.take(2).forEach { t -> Box(Modifier.padding(start = 3.dp).clip(RoundedCornerShape(4.dp)).background(OD.CyanSoft).padding(horizontal = 4.dp, vertical = 1.dp)) { Text(tagLabel(t), style = OD.Micro.copy(fontSize = 8.sp, color = OD.CyanDeep)) } }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(e.translation.replace("\n", "  ").take(90), style = OD.Cap.copy(fontSize = 11.sp), maxLines = 2)
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
                    }
                    }
                }
            }
        }
    }
}

@Composable
fun RealWordScreen(query: String, onNavigate: (Route) -> Unit) {
    DisposableEffect(Unit) { onDispose { Tts.stop() } }
    val ctx = LocalContext.current
    val lex = remember { LexiconDb.get(ctx) }
    val userDb = remember { UserDb.get(ctx) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<WordData?>(null) }
    var suggestions by remember { mutableStateOf<List<LexEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(0) }
    var starred by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showBooks by remember { mutableStateOf(false) }
    var showJson by remember { mutableStateOf(false) }
    var wordbookNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var showWiki by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var audioList by remember { mutableStateOf<List<io.github.xhr666.opendictionary.data.UserDb.WordAudio>>(emptyList()) }
    val audioPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val label = uri.lastPathSegment?.substringAfterLast('/') ?: "本地音频"
            val current = data?.entry?.word ?: query
            runCatching { userDb.addWordAudio(current, uri.toString(), label) }
            audioList = runCatching { userDb.wordAudio(current) }.getOrDefault(emptyList())
        }
    }
    var wikiData by remember { mutableStateOf<io.github.xhr666.opendictionary.data.UserDb.ExternalContent?>(null) }
    var wikiLoading by remember { mutableStateOf(false) }
    var wikiError by remember { mutableStateOf("") }
    var booksVersion by remember { mutableIntStateOf(0) }
    var noteDraft by remember { mutableStateOf("") }
    var notesVersion by remember { mutableStateOf(0) }
    var aiSense by remember { mutableStateOf<String?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf("") }
    var showAiConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        data = null; loading = true; noteDraft = ""; aiSense = null; aiBusy = false; aiError = ""; showAiConfirm = false
        wikiData = null; wikiError = ""; wikiLoading = false; showWiki = false; showAudio = false
        withContext(Dispatchers.IO) {
            data = lex.detail(query) ?: userDb.customEntry(query)?.let { e ->
                WordData(e, emptyList(), emptyList(), emptyList(), caseNote = "自定义词条（保存在本机）")
            }
            if (data == null) suggestions = (lex.searchByWord(query) + userDb.searchCustom(query)).distinctBy { it.word }.take(12)
            val w = data?.entry?.word ?: query
            starred = userDb.inWordbook(w)
            aiSense = runCatching { userDb.aiSense(w) }.getOrNull()
            wordbookNames = runCatching {
                userDb.wordbooks().filter { b -> userDb.wordsInBook(b.id).contains(w) }.map { it.name }
            }.getOrDefault(emptyList())
            audioList = runCatching { userDb.wordAudio(w) }.getOrDefault(emptyList())
        }
        loading = false
    }
    LaunchedEffect(booksVersion) {
        if (booksVersion == 0) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val w = data?.entry?.word ?: query
            wordbookNames = runCatching { userDb.wordbooks().filter { b -> userDb.wordsInBook(b.id).contains(w) }.map { it.name } }.getOrDefault(emptyList())
            starred = userDb.inWordbook(w)
        }
    }
    val word = data?.entry?.word ?: query
    val notes = remember(data?.entry?.word, notesVersion) { userDb.notesFor(data?.entry?.word ?: query) }
    val uiPrefs = remember { ctx.getSharedPreferences("od_ui", android.content.Context.MODE_PRIVATE) }
    var sectionOrder by remember {
        mutableStateOf(uiPrefs.getString("section_order", sections.indices.joinToString(","))!!
            .split(",").mapNotNull { it.toIntOrNull() }.filter { it in sections.indices }
            .ifEmpty { sections.indices.toList() })
    }
    var showOrder by remember { mutableStateOf(false) }
    val aiModePref = ctx.getSharedPreferences("od_prefs", android.content.Context.MODE_PRIVATE).getString("ai_sense_mode", "ask") ?: "ask"
    fun doGenerateAi() {
        if (aiBusy) return
        if (!io.github.xhr666.opendictionary.llm.LocalLlm.isModelLoaded) { aiError = "请先在「AI 与本地模型」下载并加载模型"; return }
        aiBusy = true; aiError = ""
        val wi = word
        val tr = data?.entry?.translation ?: ""
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    io.github.xhr666.opendictionary.llm.LocalLlm.generate(
                        "You are an English learner's dictionary assistant. For the word \"${wi}\" (translation: ${tr.take(120)}), list up to 3 less-common or figurative senses (熟词生义) a Chinese learner should know. Use concise Chinese, one sense per line with a short English example. Do not invent facts.",
                        320
                    )
                }
            }
            if (data?.entry?.word != wi) return@launch
            res.onSuccess { text ->
                if (text.isNotBlank()) { userDb.saveAiSense(wi, text.trim()); aiSense = text.trim() } else aiError = "模型未返回内容（可能未加载）"
            }.onFailure { aiError = it.message ?: "生成失败" }
            aiBusy = false
        }
    }
    fun requestAiGenerate() {
        when (aiModePref) {
            "never" -> aiError = "已关闭 AI 生成内容"
            "direct" -> doGenerateAi()
            else -> showAiConfirm = true
        }
    }


    Backdrop {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                Row(Modifier.fillMaxWidth().padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clickable { onNavigate(Route.Home) }, contentAlignment = Alignment.Center) { OIcon(Ic.Back, 20.dp, OD.Ink) }
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier.weight(1f).height(38.dp).liquidGlass(RoundedCornerShape(12.dp), Color.White.copy(alpha = .25f)).padding(horizontal = 11.dp).semantics { contentDescription = "搜索词条" }.clickable { onNavigate(Route.SearchInput) }, contentAlignment = Alignment.CenterStart) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OIcon(Ic.Search, 14.dp, OD.Muted); Spacer(Modifier.width(7.dp))
                            Text(word, style = OD.Body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = OD.Ink))
                        }
                    }
                }
                if (data != null) LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sectionOrder) { idx ->
                        val name = sections[idx]
                        Pressable(onClick = { selected = idx; scope.launch { listState.animateScrollToItem(sectionOrder.indexOf(idx) + 1) } }) {
                            Box(Modifier.clip(RoundedCornerShape(50)).background(if (idx == selected) OD.Ink else Color.White).border(1.dp, if (idx == selected) OD.Ink else OD.Hairline, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(15.dp).clip(CircleShape).background(if (idx == selected) OD.Cyan else OD.Parchment), contentAlignment = Alignment.Center) { Text("%02d".format(idx + 1), style = OD.Micro.copy(fontSize = 8.sp, color = if (idx == selected) OD.Ink else OD.Muted, fontWeight = FontWeight.Bold)) }
                                    Spacer(Modifier.width(5.dp))
                                    Text(name, style = OD.Cap.copy(fontSize = 10.5.sp, color = if (idx == selected) Color.White else OD.InkSoft, fontWeight = if (idx == selected) FontWeight.SemiBold else FontWeight.Normal))
                                }
                            }
                        }
                    }
                }
            }
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("加载词条…", style = OD.Cap) }
                data == null -> NotFound(query, suggestions, onNavigate)
                else -> {
                    val d = data!!
                    Box(Modifier.weight(1f)) {
                        LazyColumn(Modifier.fillMaxSize(), state = listState) {
                            item { WordHeader(d, starred, onStar = { starred = it; d.entry.word.let { w -> if (starred) userDb.addToWordbook(w) else userDb.removeFromWordbook(w) }; booksVersion++ }, onNotes = { showNotes = true }, onBooks = { showBooks = true }, onJson = { showJson = true }, wordbookNames = wordbookNames, onAudio = {
                            val w = d.entry.word
                            audioList = runCatching { userDb.wordAudio(w) }.getOrDefault(emptyList())
                            showAudio = true
                            if (ctx.getSharedPreferences("od_prefs", android.content.Context.MODE_PRIVATE).getBoolean("auto_play_word_audio", false)) {
                                audioList.firstOrNull()?.let { a ->
                                    MediaPlayback.play(ctx, io.github.xhr666.opendictionary.data.MediaClip(mediaId = -1L, uri = a.uri, title = "原声：$w", word = w, startMs = 0L, endMs = 0L, lineText = a.label))
                                }
                            }
                        }, onWiki = {
                            showWiki = true; wikiError = ""
                            if (wikiData == null) {
                                scope.launch {
                                    wikiLoading = true
                                    val cached = withContext(Dispatchers.IO) { runCatching { userDb.externalContent(d.entry.word) }.getOrNull() }
                                    wikiData = cached
                                    if (cached == null) {
                                        runCatching { io.github.xhr666.opendictionary.data.ExternalContentFetcher.fetch(d.entry.word) }
                                            .onSuccess { x -> withContext(Dispatchers.IO) { userDb.saveExternalContent(x) }; wikiData = x }
                                            .onFailure { wikiError = it.message ?: "抓取失败" }
                                    }
                                    wikiLoading = false
                                }
                            }
                        }, onNavigate, aiSense = aiSense, aiBusy = aiBusy, aiError = aiError, aiMode = aiModePref, onAiGenerate = { requestAiGenerate() }) }
                            items(sectionOrder) { idx ->
                                when (idx) {
                                    0 -> S01(d, onNavigate); 1 -> S02(d, onNavigate); 2 -> S03(d, onNavigate); 3 -> S04(d)
                                    4 -> S05(d); 5 -> S06(notes); 6 -> S07(d); 7 -> S08(d, onNavigate)
                                    8 -> S09(d); 9 -> S10(d, onNavigate); 10 -> S11(d); 11 -> S12(d, onNavigate)
                                }
                            }
                            item { Spacer(Modifier.height(120.dp)) }
                        }
                        // 右侧分区索引
                        Column(Modifier.align(Alignment.CenterEnd).padding(end = 2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            sectionOrder.forEach { idx ->
                                Box(Modifier.size(width = 24.dp, height = 20.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .72f))
                                    .semantics { contentDescription = "跳转到分区 ${sections[idx]}" }.clickable {
                                    selected = idx
                                    scope.launch { listState.animateScrollToItem(sectionOrder.indexOf(idx) + 1) }
                                }, contentAlignment = Alignment.Center) {
                                    Text("%02d".format(idx + 1), style = OD.Micro.copy(fontSize = 7.sp, color = if (idx == selected) OD.CyanDeep else OD.Muted))
                                }
                            }
                        }
                        // 顶部分区条与右侧索引的交叠区：打开半屏排序面板
                        Pressable(onClick = { showOrder = true }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp)) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = .92f)).border(1.dp, OD.Hairline, CircleShape), contentAlignment = Alignment.Center) {
                                OIcon(Ic.Grid, 14.dp, OD.InkSoft)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAiConfirm) {
        DialogCard(visible = true, onDismiss = { showAiConfirm = false }) {
            Text("使用本机 AI 生成义项？", style = OD.H2)
            Spacer(Modifier.height(6.dp))
            Text("内容由本地模型生成，可能出现错误或不适合的表述；不会联网、不会上传。生成结果缓存在本机，可在设置→AI 中调整提示方式。", style = OD.Cap.copy(lineHeight = 17.sp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("取消", { showAiConfirm = false }, primary = false, modifier = Modifier.weight(1f))
                ODButton("生成", { showAiConfirm = false; doGenerateAi() }, modifier = Modifier.weight(1f))
            }
        }
    }
    Sheet(showOrder, { showOrder = false }) {
        Text("词条分区排序", style = OD.H2)
        Spacer(Modifier.height(4.dp))
        Text("长列表分区可调整顺序；点击右侧编号可跳转，排序会保存在本机。", style = OD.Cap)
        Spacer(Modifier.height(8.dp))
        sectionOrder.forEachIndexed { pos, idx ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("%02d".format(idx + 1), style = OD.Micro, modifier = Modifier.width(28.dp))
                Text(sections[idx], style = OD.H3.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                Pressable(onClick = {
                    if (pos > 0) {
                        val n = sectionOrder.toMutableList(); val t = n[pos]; n[pos] = n[pos - 1]; n[pos - 1] = t
                        sectionOrder = n; uiPrefs.edit().putString("section_order", n.joinToString(",")).apply()
                    }
                }) { Text("↑", style = OD.Cap.copy(fontSize = 15.sp, color = if (pos > 0) OD.InkSoft else OD.Hairline), modifier = Modifier.padding(horizontal = 7.dp)) }
                Pressable(onClick = {
                    if (pos < sectionOrder.size - 1) {
                        val n = sectionOrder.toMutableList(); val t = n[pos]; n[pos] = n[pos + 1]; n[pos + 1] = t
                        sectionOrder = n; uiPrefs.edit().putString("section_order", n.joinToString(",")).apply()
                    }
                }) { Text("↓", style = OD.Cap.copy(fontSize = 15.sp, color = if (pos < sectionOrder.size - 1) OD.InkSoft else OD.Hairline), modifier = Modifier.padding(horizontal = 7.dp)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton("恢复默认", onClick = {
                sectionOrder = sections.indices.toList(); uiPrefs.edit().putString("section_order", sectionOrder.joinToString(",")).apply()
            }, primary = false, modifier = Modifier.weight(1f))
            ODButton("完成", onClick = { showOrder = false }, modifier = Modifier.weight(1f))
        }
    }
    Sheet(showBooks, { showBooks = false }) {
        @Suppress("UNUSED_VARIABLE") val booksVer = booksVersion
        Text("加入词书 · $word", style = OD.H2)
        Spacer(Modifier.height(4.dp))
        Text("可同时加入多个词书；默认词书用于快速复习。", style = OD.Cap)
        Spacer(Modifier.height(8.dp))
        userDb.wordbooks().forEach { b ->
            val has = userDb.wordsInBook(b.id).contains(word)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(b.name + if (b.isDefault) "（默认）" else "", style = OD.H3.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                ODToggle(has, { on ->
                    if (on) userDb.addWordToBook(b.id, word) else userDb.removeWordFromBook(b.id, word)
                    booksVersion++
                })
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .5f)))
        }
        Spacer(Modifier.height(10.dp))
        ODButton("完成", { showBooks = false }, modifier = Modifier.fillMaxWidth())
    }
    Sheet(showAudio, { showAudio = false }) {
        Text("原声例句 · $word", style = OD.H2)
        Spacer(Modifier.height(4.dp))
        Text("可导入你有权使用的本地音频片段（影视/播客/听力材料）；也可在外部站点查找该词的原声。", style = OD.Cap.copy(lineHeight = 16.sp))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("YouGlish" to "https://youglish.com/pronounce/$word/english", "VOA 搜索" to "https://learningenglish.voanews.com/search?q=$word", "TED 搜索" to "https://www.ted.com/search?q=$word").forEach { (label, url) ->
                Pressable(onClick = { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                    Text(label, style = OD.Cap.copy(color = OD.CyanDeep, fontSize = 11.sp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (audioList.isEmpty()) Text("还没有本地原声，点下方导入。", style = OD.Micro)
        audioList.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = OD.Cap.copy(fontSize = 11.5.sp), maxLines = 1)
                    Text(if (a.uri.startsWith("http")) "网络链接 · 需网络" else "本机导入 · 仅你自己可见", style = OD.Micro)
                }
                Pressable(onClick = {
                    MediaPlayback.play(ctx, io.github.xhr666.opendictionary.data.MediaClip(mediaId = -1L, uri = a.uri, title = "原声：$word", word = word, startMs = 0L, endMs = 0L, lineText = a.label))
                }) { OIcon(Ic.Play, 16.dp, OD.CyanDeep) }
                Spacer(Modifier.width(12.dp))
                Pressable(onClick = {
                    userDb.removeWordAudio(word, a.uri)
                    if (!a.uri.startsWith("http")) runCatching { ctx.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(a.uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    audioList = userDb.wordAudio(word)
                }) { OIcon(Ic.Trash, 14.dp, OD.Muted) }
            }
        }
        Spacer(Modifier.height(8.dp))
        var audioUrl by remember { mutableStateOf("") }
        ODField(audioUrl, { audioUrl = it }, "https://… 音频直链（可选）", leading = Ic.Link)
        Spacer(Modifier.height(6.dp))
        ODButton("添加网络音频链接", {
            val u = audioUrl.trim()
            if (u.startsWith("https://")) {
                runCatching { userDb.addWordAudio(word, u, "网络音频") }
                audioList = runCatching { userDb.wordAudio(word) }.getOrDefault(emptyList())
                audioUrl = ""
            }
        }, primary = false, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton("导入本地音频", { audioPicker.launch(arrayOf("audio/*")) }, primary = false, modifier = Modifier.weight(1f))
            ODButton("关闭", { showAudio = false }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text("外部站点内容受各自版权与服务条款限制，应用只打开链接、不抓取音频；影视片段请自行确认使用权。", style = OD.Micro.copy(lineHeight = 14.sp))
    }
    Sheet(showWiki, { showWiki = false }) {
        val wx = wikiData
        Text("外链百科 · $word", style = OD.H2)
        Spacer(Modifier.height(4.dp))
        if (wikiLoading) Text("抓取中…", style = OD.Cap)
        if (wikiError.isNotBlank()) Text(wikiError, style = OD.Cap.copy(color = OD.Warm))
        if (wx != null) {
            Text("${wx.source} · 抓取于 ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(wx.fetchedAt))}", style = OD.Micro)
            Spacer(Modifier.height(6.dp))
            Text(wx.content, style = OD.ReadBody.copy(fontSize = 13.sp, lineHeight = 20.sp))
            Spacer(Modifier.height(6.dp))
            Text("内容来自维基百科（CC BY-SA 4.0），仅本地缓存；可在来源页打开原条目，或重新抓取更新。", style = OD.Micro.copy(lineHeight = 14.sp))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton(if (wikiLoading) "刷新中…" else "重新抓取", {
                if (!wikiLoading) {
                    val wi = word
                    wikiLoading = true; wikiError = ""
                    scope.launch {
                        runCatching { io.github.xhr666.opendictionary.data.ExternalContentFetcher.fetch(wi) }
                            .onSuccess { x -> withContext(Dispatchers.IO) { userDb.saveExternalContent(x) }; if (data?.entry?.word == wi) wikiData = x }
                            .onFailure { e -> if (data?.entry?.word == wi) wikiError = e.message ?: "抓取失败" }
                        if (data?.entry?.word == wi) wikiLoading = false
                    }
                }
            }, primary = false, modifier = Modifier.weight(1f))
            if (wx?.url?.isNotBlank() == true) ODButton("打开原页面", { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(wx.url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }, primary = false, modifier = Modifier.weight(1f))
            ODButton("关闭", { showWiki = false }, modifier = Modifier.weight(1f))
        }
    }
    Sheet(showJson, { showJson = false }) {
        val d = data
        Text("词条 JSON · ${d?.entry?.word ?: word}", style = OD.H2)
        Spacer(Modifier.height(4.dp))
        Text("每个单词/短语均可导出结构化 JSON（离线字段）。", style = OD.Cap)
        Spacer(Modifier.height(8.dp))
        val jsonText = remember(d) { if (d == null) "" else wordJson(d) }
        Box(Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(12.dp)).background(OD.Parchment).padding(10.dp)) {
            Text(jsonText, style = OD.Micro.copy(fontSize = 9.5.sp, lineHeight = 14.sp), modifier = Modifier.verticalScroll(rememberScrollState()))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton("复制 JSON", {
                runCatching {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("OpenDictionary JSON", jsonText))
                }
            }, primary = false, modifier = Modifier.weight(1f))
            ODButton("关闭", { showJson = false }, modifier = Modifier.weight(1f))
        }
    }
    Sheet(showNotes, { showNotes = false }) {
        Text("笔记 · $word", style = OD.H2)
        Spacer(Modifier.height(6.dp))
        notes.forEach { n -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(9.dp)).background(OD.Pearl).padding(10.dp)) { Text(n, style = OD.Cap) } }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(11.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(11.dp)).padding(10.dp)) {
            BasicTextField(value = noteDraft, onValueChange = { noteDraft = it }, textStyle = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = OD.InkSoft), modifier = Modifier.fillMaxSize(),
                decorationBox = { inner -> if (noteDraft.isEmpty()) Text("写点什么，保存在本机…", style = OD.Cap) else inner() })
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton("取消", onClick = { showNotes = false }, primary = false, modifier = Modifier.weight(1f))
            ODButton("保存", onClick = { if (noteDraft.isNotBlank()) { userDb.saveNote(word, noteDraft.trim()); noteDraft = ""; notesVersion++; showNotes = false } }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun NotFound(query: String, suggestions: List<LexEntry>, onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { UserDb.get(ctx) }
    val lex = remember { LexiconDb.get(ctx) }
    val scope = rememberCoroutineScope()
    var online by remember(query) { mutableStateOf<io.github.xhr666.opendictionary.data.OnlineDictionary.OnlineResult?>(null) }
    var onlineZh by remember(query) { mutableStateOf("") }
    var onlineZhSource by remember(query) { mutableStateOf("") }
    var busy by remember(query) { mutableStateOf(false) }
    var error by remember(query) { mutableStateOf("") }
    var aiText by remember(query) { mutableStateOf("") }
    var aiBusy by remember(query) { mutableStateOf(false) }

    fun plain(s: String): String = s
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("[*_`#>]+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun fetchOnline() {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            runCatching { io.github.xhr666.opendictionary.data.OnlineDictionary.lookup(query) }
                .onSuccess { r ->
                    online = r
                    val localZh = withContext(Dispatchers.IO) { runCatching { io.github.xhr666.opendictionary.data.ChineseGloss.describe(query, r, lex) }.getOrDefault("") }
                    if (localZh.isNotBlank()) {
                        onlineZh = localZh; onlineZhSource = "本地词库/组合词/近义词推导"
                    } else {
                        val wordZh = withContext(Dispatchers.IO) { runCatching { io.github.xhr666.opendictionary.data.ZhTranslator.translateWord(query) }.getOrNull() }
                        val machine = wordZh ?: withContext(Dispatchers.IO) {
                            runCatching { io.github.xhr666.opendictionary.data.ZhTranslator.translate(r.defs.take(2).joinToString("; ") { it.text }) }.getOrNull()
                        }
                        if (machine != null) { onlineZh = machine.text; onlineZhSource = machine.source }
                    }
                    runCatching {
                        db.saveExternalContent(
                            io.github.xhr666.opendictionary.data.UserDb.ExternalContent(
                                word = query, source = r.source + " · " + r.license, title = r.word,
                                content = (if (onlineZh.isNotBlank()) "中文：$onlineZh\n" else "") + r.asText(), url = r.url, fetchedAt = r.fetchedAt
                            )
                        )
                    }
                }
                .onFailure { error = it.message ?: "查询失败，请检查网络" }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Text(query, style = OD.H1.copy(fontSize = 34.sp, lineHeight = 38.sp))
        Spacer(Modifier.height(4.dp))
        Text("本地词库未收录 · 新词/专业术语可在线查询", style = OD.Cap)
        Spacer(Modifier.height(12.dp))
        if (!busy && online == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("在线查询", { fetchOnline() }, modifier = Modifier.weight(1f))
                if (io.github.xhr666.opendictionary.llm.LocalLlm.isModelLoaded) {
                    ODButton(if (aiBusy) "AI 解释中…" else "本地 AI 解释", {
                        if (!aiBusy) {
                            aiBusy = true; error = ""
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    runCatching {
                                        io.github.xhr666.opendictionary.llm.LocalLlm.generate(
                                            "用语料库之外的专业/新词“$query”：用一句简短中文解释它的含义（如果它是组合词，解释各部分），再给一个简短英文例句。不要编造不确定的事实；不确定就说明不确定。", 320
                                        )
                                    }
                                }
                                res.onSuccess { aiText = it }.onFailure { error = it.message ?: "AI 解释失败" }
                                aiBusy = false
                            }
                        }
                    }, primary = false, modifier = Modifier.weight(1f))
                }
            }
        }
        if (busy) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(14.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("正在联网查询…", style = OD.H3.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                Text("在线", style = OD.Micro.copy(color = OD.CyanDeep))
            }
            Spacer(Modifier.height(4.dp))
            Text("优先词典数据，失败会自动降级到其他来源；中文释义可能稍后返回。", style = OD.Micro.copy(lineHeight = 15.sp))
        }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(error, style = OD.Cap.copy(color = OD.Warm, lineHeight = 17.sp))
        }
        if (online != null) {
            val r = online!!
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.word, style = OD.H2.copy(fontSize = 22.sp))
                val ph = r.phonetic.trim().trim('/').trim()
                if (ph.isNotBlank()) { Spacer(Modifier.width(8.dp)); Text("/$ph/", style = OD.Cap) }
                Spacer(Modifier.weight(1f))
                Pressable(onClick = { Tts.speak(r.word, british = true); if (!Tts.isReady()) Tts.reportNotReady() }) { Text("英音", style = OD.Cap.copy(fontSize = 11.sp, color = OD.CyanDeep)) }
                Spacer(Modifier.width(10.dp))
                Pressable(onClick = { Tts.speak(r.word, british = false); if (!Tts.isReady()) Tts.reportNotReady() }) { Text("美音", style = OD.Cap.copy(fontSize = 11.sp, color = OD.CyanDeep)) }
            }
            Spacer(Modifier.height(3.dp))
            Text("${r.source} · ${r.license}", style = OD.Micro)
            Tts.lastError?.let { err -> Spacer(Modifier.height(4.dp)); Text(err, style = OD.Micro.copy(color = OD.Warm)) }
            if (onlineZh.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("中文释义", style = OD.H3.copy(fontSize = 13.sp))
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OD.CyanSoft.copy(alpha = .45f)).padding(12.dp)) {
                    Text(plain(onlineZh), style = OD.BodySm.copy(lineHeight = 21.sp))
                }
                Text("来源：$onlineZhSource · 仅供参考", style = OD.Micro.copy(fontSize = 8.5.sp))
            }
            Spacer(Modifier.height(12.dp))
            Text("英文释义（在线）", style = OD.H3.copy(fontSize = 13.sp))
            Spacer(Modifier.height(5.dp))
            r.defs.take(8).forEachIndexed { i, d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    Text("${i + 1}.", style = OD.Cap.copy(fontSize = 11.sp, color = OD.CyanDeep, fontWeight = FontWeight.SemiBold), modifier = Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text((if (d.pos.isBlank()) "" else "${d.pos} ") + plain(d.text), style = OD.BodySm.copy(lineHeight = 20.sp))
                        if (d.example.isNotBlank()) Text("例：${plain(d.example)}", style = OD.Cap.copy(fontSize = 11.sp, lineHeight = 16.sp))
                    }
                }
            }
            if (r.synonyms.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("近义词：${r.synonyms.joinToString("、")}", style = OD.Cap.copy(fontSize = 11.sp))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("保存为本地词条", {
                    runCatching {
                        db.saveCustomEntry(
                            LexEntry(query, r.phonetic, onlineZh.ifBlank { r.defs.firstOrNull()?.text ?: r.asText().take(80) }, (if (onlineZh.isNotBlank()) "中文：$onlineZh\n" else "") + r.asText() + "\n来源：" + r.source + " · " + r.license + " · " + r.url, r.defs.firstOrNull()?.pos ?: "", "custom", "", 9999),
                            r.source
                        )
                        db.addToWordbook(query)
                    }
                    error = "已保存为本地词条，返回重新查词即可离线查看"
                }, modifier = Modifier.weight(1f))
                ODButton("打开原页面", { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(r.url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }, primary = false, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("保存到笔记", { runCatching { db.addToWordbook(query) }; runCatching { db.saveNote(query, r.asText() + "\n来源：" + r.source + " · " + r.license + " · " + r.url) } }, primary = false, modifier = Modifier.weight(1f))
                ODButton("重新查询", { fetchOnline() }, primary = false, modifier = Modifier.weight(1f))
            }
        }
        if (aiText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("本地 AI 解释（需自行核验）", style = OD.H3.copy(fontSize = 13.sp))
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OD.Parchment).padding(12.dp)) {
                Text(plain(aiText), style = OD.BodySm.copy(lineHeight = 20.sp))
            }
        }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("相近结果", style = OD.H3.copy(fontSize = 13.sp))
            suggestions.forEach { e ->
                Pressable(onClick = { onNavigate(Route.Word(e.word)) }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.word, style = OD.H3.copy(fontSize = 14.sp), modifier = Modifier.width(110.dp))
                        Text(e.translation.replace("\n", " ").take(40), style = OD.Cap.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
                        OIcon(Ic.Chevron, 12.dp, OD.Hairline)
                    }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

internal fun wordJson(d: WordData): String {
    val e = d.entry
    val o = org.json.JSONObject()
    o.put("word", e.word); o.put("phonetic", e.phonetic); o.put("translation", e.translation)
    o.put("definition", e.definition); o.put("pos", e.pos); o.put("tag", e.tag); o.put("exchange", e.exchange); o.put("frequency", e.frq)
    o.put("tags", org.json.JSONArray(d.tags))
    o.put("phrases", org.json.JSONArray(d.phrases.map { org.json.JSONObject().put("phrase", it.phrase).put("translation", it.translation).put("kind", it.kind).put("license", it.license) }))
    o.put("lemmas", org.json.JSONArray(d.lemmas.map { org.json.JSONObject().put("form", it.form).put("lemma", it.lemma).put("kind", it.kind) }))
    o.put("related", org.json.JSONArray(d.related.map { org.json.JSONObject().put("word", it.first).put("translation", it.second) }))
    o.put("senses", org.json.JSONArray(d.senses.map { org.json.JSONObject().put("pos", it.pos).put("meaning", it.meaning).put("example", it.example).put("source", it.source) }))
    o.put("source", "ECDICT/OpenDictionary offline lexicon")
    return o.toString(2)
}

@Composable
private fun WordHeader(d: WordData, starred: Boolean, onStar: (Boolean) -> Unit, onNotes: () -> Unit, onBooks: () -> Unit, onJson: () -> Unit, wordbookNames: List<String>, onAudio: () -> Unit, onWiki: () -> Unit, onNavigate: (Route) -> Unit, aiSense: String?, aiBusy: Boolean, aiError: String, aiMode: String, onAiGenerate: () -> Unit) {
    val e = d.entry
    val ctx = LocalContext.current
    Column(Modifier.padding(horizontal = 18.dp).padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.word, style = OD.H1.copy(fontSize = 36.sp, lineHeight = 40.sp))
            Spacer(Modifier.width(10.dp))
            Pressable(onClick = { onStar(!starred) }) {
                Box(Modifier.size(34.dp).semantics { stateDescription = if (starred) "已收藏" else "未收藏" }, contentAlignment = Alignment.Center) { OIcon(if (starred) Ic.StarFill else Ic.Star, 22.dp, if (starred) OD.Warm else OD.Muted, contentDescription = "收藏") }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (e.phonetic.isNotBlank()) {
                Text("英 /${e.phonetic}/", style = OD.Cap.copy(fontSize = 12.sp))
                Spacer(Modifier.width(8.dp))
                Pressable(onClick = { Tts.speak(e.word, british = true) }) { OIcon(Ic.Play, 13.dp, OD.CyanDeep) }
                Spacer(Modifier.width(12.dp))
                Text("美 /${e.phonetic}/", style = OD.Cap.copy(fontSize = 12.sp))
                Spacer(Modifier.width(8.dp))
                Pressable(onClick = { Tts.speak(e.word, british = false) }) { OIcon(Ic.Play, 13.dp, OD.CyanDeep) }
            } else {
                Pressable(onClick = { Tts.speak(e.word, false) }) { OIcon(Ic.Play, 13.dp, OD.CyanDeep) }
            }
        }
        if (d.caseNote != null) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(OD.Parchment).padding(10.dp)) {
                Text(d.caseNote, style = OD.Cap.copy(fontSize = 11.5.sp))
                d.alternate?.let { alt -> Pressable(onClick = { onNavigate(Route.Word(alt)) }) { Text("也可能想找 $alt →", style = OD.Cap.copy(fontSize = 11.5.sp, color = OD.CyanDeep)) } }
            }
        } else if (d.alternate != null) {
            Spacer(Modifier.height(6.dp))
            Pressable(onClick = { onNavigate(Route.Word(d.alternate!!)) }) { Text("也可能想找 ${d.alternate} →", style = OD.Cap.copy(fontSize = 11.5.sp, color = OD.CyanDeep)) }
        }
        Spacer(Modifier.height(10.dp))
        val allTags = (e.tag.split(" ") + d.tags).filter { it.isNotBlank() }.distinct()
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            allTags.take(8).forEachIndexed { i, t -> Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (i == 0) OD.WarmSoft else OD.Parchment).padding(horizontal = 6.dp, vertical = 3.dp)) { Text(tagLabel(t), style = OD.Micro.copy(color = if (i == 0) OD.Warm else OD.Muted, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal)) } }
        }
        if (allTags.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text("分级/考试词表标签来自开源词表（ECDICT / 词书资源包）", style = OD.Micro.copy(fontSize = 8.5.sp))
        }
        if (wordbookNames.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("所在词书：${wordbookNames.take(4).joinToString("、")}", style = OD.Micro.copy(fontSize = 9.sp, color = OD.CyanDeep))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionChip(if (starred) "已在生词本" else "加入生词本", Ic.Heart) { onStar(!starred) }
            ActionChip("笔记", Ic.Book) { onNotes() }
            ActionChip("加入词书", Ic.Book) { onBooks() }
            ActionChip("英音", Ic.Play) { Tts.speak(e.word, true) }
            ActionChip("美音", Ic.Play) { Tts.speak(e.word, false) }
            ActionChip("JSON", Ic.Info) { onJson() }
            ActionChip("百科", Ic.Info) { onWiki() }
            ActionChip("原声", Ic.Headphone) { onAudio() }
        }
        Spacer(Modifier.height(12.dp))
        e.translation.split("\n").filter { it.isNotBlank() }.take(6).forEach { line ->
            Text(line, style = OD.ReadBody)
        }
        Tts.lastError?.let { err ->
            Spacer(Modifier.height(6.dp))
            Text(err, style = OD.Micro.copy(color = OD.Warm, lineHeight = 14.sp))
        }
        PronunciationCard(e.word)
        if (e.exchange.isNotBlank()) {
            val forms = e.exchange.split("/").mapNotNull { part ->
                val bits = part.split(":", limit = 2)
                if (bits.size == 2 && bits[1].isNotBlank()) formLabel(bits[0]) to bits[1] else null
            }.filter { it.second.isNotBlank() && !it.second.equals(e.word, true) }
            if (forms.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    forms.take(8).forEach { (label, form) ->
                        Column(Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp)).background(OD.Pearl).border(1.dp, OD.Hairline, RoundedCornerShape(10.dp)).padding(horizontal = 9.dp, vertical = 6.dp)) {
                            Text(label, style = OD.Micro.copy(fontSize = 8.sp))
                            Text(form, style = OD.Cap.copy(fontSize = 11.5.sp, color = OD.Ink, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
        if (d.senses.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OD.WarmSoft.copy(alpha = .45f)).border(1.dp, OD.Warm.copy(alpha = .35f), RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text("熟词生义", style = OD.H3.copy(fontSize = 12.5.sp, color = OD.Warm))
                d.senses.take(3).forEach { s ->
                    Spacer(Modifier.height(5.dp))
                    Text("${s.pos}  ${s.meaning}", style = OD.Body.copy(fontSize = 12.5.sp, color = OD.Ink))
                    if (s.example.isNotBlank()) Text(s.example, style = OD.ReadBody.copy(fontSize = 11.5.sp, lineHeight = 17.sp))
                    Text(s.source, style = OD.Micro.copy(fontSize = 8.5.sp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OD.CyanSoft.copy(alpha = .28f)).border(1.dp, OD.Hairline, RoundedCornerShape(14.dp)).padding(12.dp)) {
            Text("AI 扩展义项（本机生成）", style = OD.H3.copy(fontSize = 12.5.sp, color = OD.CyanDeep))
            Spacer(Modifier.height(3.dp))
            Text("由本地模型补充熟词生义/引申义，可能不准确，仅供学习参考。", style = OD.Micro.copy(fontSize = 9.sp, lineHeight = 14.sp))
            aiSense?.let { content ->
                Spacer(Modifier.height(6.dp))
                Text(content, style = OD.ReadBody.copy(fontSize = 12.sp, lineHeight = 18.sp))
            }
            if (aiMode != "never") {
                Spacer(Modifier.height(8.dp))
                Pressable(onClick = onAiGenerate) { Text(if (aiBusy) "生成中…" else if (aiSense == null) "生成扩展义项" else "重新生成", style = OD.Cap.copy(color = OD.CyanDeep, fontSize = 11.sp)) }
            } else Text("已在设置中关闭 AI 生成内容", style = OD.Micro)
            if (aiError.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(aiError, style = OD.Micro.copy(color = OD.Warm)) }
        }
    }
}

@Composable
private fun ActionChip(text: String, icon: Ic, onClick: () -> Unit) {
    Pressable(onClick = onClick) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            OIcon(icon, 11.dp, OD.InkSoft); Spacer(Modifier.width(4.dp)); Text(text, style = OD.Micro.copy(fontSize = 9.5.sp))
        }
    }
}

@Composable
private fun SectionBlock(index: Int, title: String, accent: Color = OD.Cyan, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        SectionHead(index, title, accent)
        content()
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun EmptySection(text: String) {
    Text(text, style = OD.Cap.copy(fontSize = 11.5.sp))
}

// 01 双语例句：暂无开放例句时给出学术来源入口
@Composable
private fun S01(d: WordData, onNavigate: (Route) -> Unit) = SectionBlock(1, "双语例句") {
    Text("“${d.entry.word}”的开放例句暂未收录。", style = OD.Cap.copy(fontSize = 11.5.sp))
    Spacer(Modifier.height(6.dp))
    Pressable(onClick = { onNavigate(Route.Academic) }) { Text("去学术资源查看可用来源 →", style = OD.Cap.copy(fontSize = 11.5.sp, color = OD.CyanDeep)) }
}

@Composable
private fun S02(d: WordData, onNavigate: (Route) -> Unit) = SectionBlock(2, "音乐例句", OD.Warm) {
    val ctx = LocalContext.current
    val userDb = remember { io.github.xhr666.opendictionary.data.UserDb.get(ctx) }
    val clips = remember(d.entry.word) { userDb.findMediaClips(d.entry.word) }
    LaunchedEffect(MediaPlayback.playing) {
        while (MediaPlayback.playing) { MediaPlayback.poll(); kotlinx.coroutines.delay(80) }
    }
    if (clips.isEmpty()) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OD.WarmSoft.copy(alpha = .5f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { OIcon(Ic.Headphone, 15.dp, OD.Warm) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("从本地音乐中找到这个单词", style = OD.H3.copy(fontSize = 12.5.sp))
                Text("导入音乐 + 同名 .lrc，自动匹配歌词片段", style = OD.Cap.copy(fontSize = 10.sp))
            }
            Pressable(onClick = { onNavigate(Route.Media) }) { Text("导入", style = OD.Cap.copy(color = OD.Warm, fontWeight = FontWeight.SemiBold)) }
        }
    } else {
        clips.take(3).forEach { clip ->
            val active = MediaPlayback.clip?.let { it.mediaId == clip.mediaId && it.startMs == clip.startMs && it.word == clip.word } == true
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(13.dp)).background(Color.White).border(1.dp, if (active) OD.Warm else OD.Hairline, RoundedCornerShape(13.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Pressable(onClick = {
                    if (active && MediaPlayback.playing) MediaPlayback.stop() else MediaPlayback.play(ctx, clip)
                }) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(OD.WarmSoft), contentAlignment = Alignment.Center) { OIcon(if (active && MediaPlayback.playing) Ic.Close else Ic.Play, 14.dp, OD.Warm) }
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(clip.title, style = OD.H3.copy(fontSize = 12.sp))
                    Spacer(Modifier.height(2.dp))
                    val idx = clip.lineText.indexOf(clip.word, ignoreCase = true)
                    if (idx >= 0) {
                        androidx.compose.ui.text.buildAnnotatedString {
                            append(clip.lineText.substring(0, idx))
                            withStyle(androidx.compose.ui.text.SpanStyle(background = OD.WarmSoft, color = OD.Warm, fontWeight = FontWeight.SemiBold)) { append(clip.lineText.substring(idx, idx + clip.word.length)) }
                            append(clip.lineText.substring(idx + clip.word.length))
                        }.let { Text(it, style = OD.Cap.copy(fontSize = 10.5.sp), maxLines = 2) }
                    } else Text(clip.lineText, style = OD.Cap.copy(fontSize = 10.5.sp), maxLines = 2)
                    if (active) Text("${MediaPlayback.positionMs / 1000}s / ${clip.endMs / 1000}s · 片段", style = OD.Micro.copy(fontSize = 8.5.sp))
                }
            }
        }
        Pressable(onClick = { onNavigate(Route.Media) }) { Text("管理本地音乐 →", style = OD.Micro.copy(fontSize = 10.sp, color = OD.Warm)) }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun S03(d: WordData, onNavigate: (Route) -> Unit) = SectionBlock(3, "视频例句", OD.Warm) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp, 40.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(listOf(OD.Ink, OD.CyanDeep))), contentAlignment = Alignment.Center) { OIcon(Ic.Play, 15.dp, Color.White) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("TED-Ed · 词义讲解视频（外链）", style = OD.H3.copy(fontSize = 12.sp))
            Text("只做外链与时间戳，不下载、不二次分发", style = OD.Cap.copy(fontSize = 10.sp))
        }
    }
    Spacer(Modifier.height(6.dp))
    val q = java.net.URLEncoder.encode(d.entry.word, "UTF-8")
    LinkRow("在 TED 搜索“${d.entry.word}”（外链）", "https://www.ted.com/search?q=$q", onNavigate)
}

@Composable
private fun S04(d: WordData) = SectionBlock(4, "网络释义", OD.CyanDeep) {
    val lines = d.entry.translation.split("\n").filter { it.isNotBlank() }
    if (lines.isEmpty()) EmptySection("暂无网络释义。")
    lines.take(4).forEachIndexed { i, l ->
        Row(Modifier.padding(bottom = 6.dp)) {
            Text("${i + 1}.", style = OD.Micro.copy(fontSize = 9.sp)); Spacer(Modifier.width(6.dp))
            Text(l, style = OD.Body.copy(fontSize = 12.sp))
        }
    }
    Text("来源：开放词典数据（ECDICT）；网络释义可能滞后。", style = OD.Micro)
}

@Composable
private fun S05(d: WordData) = SectionBlock(5, "专业释义", OD.InkSoft) {
    val domains = d.entry.translation.split("；", ";", "\n").map { it.trim() }.filter { it.contains("[") || it.contains("【") }.take(6)
    if (domains.isEmpty()) EmptySection("暂无学科标注的专业释义。")
    domains.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { t -> Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(OD.Pearl).border(1.dp, OD.Hairline, RoundedCornerShape(10.dp)).padding(9.dp)) { Text(t, style = OD.Cap.copy(fontSize = 11.sp)) } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun S06(notes: List<String>) = SectionBlock(6, "用户贡献") {
    if (notes.isEmpty()) EmptyStateCard("还没有用户贡献；在词条页写笔记会显示在这里。")
    else notes.forEach { n -> Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(11.dp)).background(OD.Pearl).padding(10.dp)) { Text(n, style = OD.Cap.copy(lineHeight = 18.sp)) } }
}

@Composable
private fun S07(d: WordData) = SectionBlock(7, "英英释义") {
    val defs = d.entry.definition.split("\n").filter { it.isNotBlank() }
    if (defs.isEmpty()) EmptyStateCard("该词暂无开放英英释义。")
    defs.take(6).forEach { line ->
        Row(Modifier.padding(bottom = 7.dp)) {
            Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(OD.InkSoft.copy(alpha = .35f)))
            Spacer(Modifier.width(9.dp))
            Text(line, style = OD.ReadBody.copy(fontSize = 12.5.sp, lineHeight = 19.sp))
        }
    }
    Text("来源：ECDICT 英文释义（开放数据）", style = OD.Micro)
}

@Composable
private fun S08(d: WordData, onNavigate: (Route) -> Unit) = SectionBlock(8, "同近义词") {
    if (d.related.isEmpty()) { EmptyStateCard("该词暂无同近义候选；后续接入 Open English WordNet。"); return@SectionBlock }
    Text("释义相近（按中文释义相似度生成，仅供参考）", style = OD.Micro)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        d.related.take(12).forEach { (w, _) ->
            Pressable(onClick = { onNavigate(Route.Word(w)) }) {
                Box(Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(w, style = OD.Cap.copy(fontSize = 11.5.sp, color = OD.InkSoft))
                }
            }
        }
    }
}

@Composable
private fun S09(d: WordData) = SectionBlock(9, "同根词") {
    val hints = io.github.xhr666.opendictionary.data.WordForms.pluralHints(d.entry.word)
    if (d.lemmas.isEmpty() && hints.isEmpty()) EmptyStateCard("词形与派生数据暂未收录。")
    else Row(Modifier.horizontalScroll(rememberScrollState())) {
        d.lemmas.forEach { l ->
            Column(Modifier.padding(end = 8.dp).width(116.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(12.dp)).padding(10.dp)) {
                Text(l.form, style = OD.H3.copy(fontSize = 12.5.sp)); Spacer(Modifier.height(3.dp)); Text(kindLabel(l.kind), style = OD.Micro)
            }
        }
        hints.forEach { (form, kind) ->
            Column(Modifier.padding(end = 8.dp).width(126.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(12.dp)).padding(10.dp)) {
                Text(form, style = OD.H3.copy(fontSize = 12.5.sp)); Spacer(Modifier.height(3.dp)); Text(kind, style = OD.Micro.copy(fontSize = 8.5.sp))
            }
        }
    }
}

@Composable
private fun S10(d: WordData, onNavigate: (Route) -> Unit) = SectionBlock(10, "词组短语") {
    if (d.phrases.isEmpty()) EmptyStateCard("暂无关联短语。")
    else d.phrases.take(12).forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.phrase, style = OD.Body.copy(fontSize = 12.5.sp, color = OD.Ink, fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
            Text(p.translation.ifBlank { p.kind }, style = OD.Cap.copy(fontSize = 11.sp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .55f)))
    }
}

@Composable
private fun S11(d: WordData) = SectionBlock(11, "词源", OD.InkSoft) {
    EmptyStateCard("该词暂无开放词源数据；可在外链百科中查看词源说明。")
}

@Composable
private fun S12(d: WordData, onNavigate: (Route) -> Unit) = SectionBlock(12, "百科") {
    val w = d.entry.word
    LinkRow("Wikipedia · $w", "https://en.wikipedia.org/wiki/" + w.replace(" ", "_"), onNavigate)
    Spacer(Modifier.height(6.dp))
    LinkRow("百度百科 · $w", "https://baike.baidu.com/item/" + w, onNavigate)
}

@Composable
private fun LinkRow(title: String, url: String, onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    Pressable(onClick = {
        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(13.dp)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            OIcon(Ic.Globe, 16.dp, OD.CyanDeep); Spacer(Modifier.width(9.dp))
            Text(title, style = OD.H3.copy(fontSize = 12.5.sp), modifier = Modifier.weight(1f))
            OIcon(Ic.External, 12.dp, OD.Muted)
        }
    }
}

@Composable
private fun EmptyStateCard(text: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OD.Pearl).border(1.dp, OD.Hairline, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(text, style = OD.Cap.copy(fontSize = 11.5.sp, lineHeight = 17.sp))
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "p" -> "过去式"; "d" -> "过去分词"; "i" -> "现在分词"; "3" -> "第三人称单数"; "r" -> "比较级"; "t" -> "最高级"; "s" -> "复数"; "0" -> "原形"
    else -> kind
}
