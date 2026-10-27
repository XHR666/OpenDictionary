package io.github.xhr666.opendictionary.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.xhr666.opendictionary.data.LexiconDb
import io.github.xhr666.opendictionary.data.UserDb
import io.github.xhr666.opendictionary.data.WordData
import java.io.File
import java.util.zip.ZipFile

private data class EpubChapter(val title: String, val text: String)

/** EPUB 阅读：SAF 导入，解压解析 OPF/spine，按章节显示纯文本；暂不支持点词查义。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpubReaderScreen(onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("od_prefs", android.content.Context.MODE_PRIVATE) }
    var bookPath by rememberSaveable { mutableStateOf(prefs.getString("last_epub", null)) }
    var chapters by remember { mutableStateOf<List<EpubChapter>>(emptyList()) }
    var current by rememberSaveable { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var selectedDef by remember { mutableStateOf<WordData?>(null) }
    val lex = remember { LexiconDb.get(ctx) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(bookPath) {
        val path = bookPath ?: return@LaunchedEffect
        if (chapters.isNotEmpty()) return@LaunchedEffect
        loading = true
        val parsed = withContext(Dispatchers.IO) { runCatching { parseEpub(File(path)) } }
        parsed.onSuccess { list -> chapters = list; title = File(path).name; if (list.isEmpty()) error = "没有解析到可读章节" }
            .onFailure { error = "恢复失败：${it.message}" }
        loading = false
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            loading = true; error = null
            scope.launch {
                var importedPath: String? = null
                val parsed = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(ctx.cacheDir, "epub").apply { mkdirs() }
                        dir.listFiles()?.forEach { it.delete() }
                        val dest = File(dir, "book-${System.currentTimeMillis()}.epub")
                        ctx.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                        importedPath = dest.absolutePath
                        parseEpub(dest)
                    }
                }
                parsed.onSuccess { list ->
                    chapters = list; current = 0
                    val path = importedPath
                    if (path != null) { bookPath = path; prefs.edit().putString("last_epub", path).apply() }
                    title = File(path ?: "book.epub").name
                    if (list.isEmpty()) error = "没有解析到可读章节"
                }.onFailure { error = "解析失败：${it.message}" }
                loading = false
            }
        }
    }
    PageScaffold("EPUB 阅读", { onNavigate(Route.Reading) }) {
        if (chapters.isEmpty()) {
            Text("导入 EPUB 电子书（仅本地缓存，不上传）。解析章节文本阅读，点单词可查义。请仅导入你有权阅读的文件。", style = OD.Cap.copy(lineHeight = 17.sp))
            Spacer(Modifier.height(10.dp))
            ODButton(if (loading) "解析中…" else "选择 EPUB", { if (!loading) importer.launch(arrayOf("application/epub+zip", "application/zip", "*/*")) }, modifier = Modifier.fillMaxWidth())
            error?.let { Spacer(Modifier.height(8.dp)); Text(it, style = OD.Cap.copy(color = OD.Warm)) }
            Spacer(Modifier.height(90.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title.take(30), style = OD.H3.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                Pressable(onClick = { chapters = emptyList(); selectedWord = null; bookPath = null; prefs.edit().remove("last_epub").apply() }) { Text("换一本", style = OD.Cap.copy(color = OD.CyanDeep, fontSize = 11.sp)) }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chapters.forEachIndexed { i, c ->
                    Pressable(onClick = { current = i }) { Text("${i + 1}. ${c.title.take(18)}", style = OD.Micro.copy(color = if (i == current) OD.CyanDeep else OD.Muted, fontSize = 10.sp)) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(chapters.getOrNull(current)?.title ?: "", style = OD.H2.copy(fontSize = 17.sp))
                Spacer(Modifier.height(6.dp))
                val chapterText = chapters.getOrNull(current)?.text ?: ""
                val lines = remember(current, chapterText) { chapterText.split("\n").filter { it.isNotBlank() }.take(200) }
                lines.forEach { line ->
                    FlowRow(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                        tokenizeEpub(line).forEach { token ->
                            if (token.any { it.isLetter() }) {
                                Text(token, style = OD.ReadBody.copy(fontSize = 13.5.sp, lineHeight = 22.sp, color = OD.CyanDeep), modifier = Modifier.clickable {
                                    selectedWord = token; selectedDef = null
                                    scope.launch {
                                        selectedDef = withContext(Dispatchers.IO) { runCatching { lex.detail(token) }.getOrNull() }
                                    }
                                })
                            } else {
                                Text(token, style = OD.ReadBody.copy(fontSize = 13.5.sp, lineHeight = 22.sp))
                            }
                        }
                    }
                }
                if (chapterText.length > 6000) Text("（本章较长，仅显示前 200 段；点单词可查义）", style = OD.Micro)
                Spacer(Modifier.height(90.dp))
            }
        }
    }
    selectedWord?.let { word ->
        DialogCard(visible = true, onDismiss = { selectedWord = null }) {
            Text(word, style = OD.H2)
            Spacer(Modifier.height(4.dp))
            val def = selectedDef
            if (def == null) Text("查询中…", style = OD.Cap)
            else def.entry.translation.split("\n").filter { it.isNotBlank() }.take(4).forEach { Text(it, style = OD.ReadBody.copy(fontSize = 13.sp, lineHeight = 20.sp)) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("加入生词本", { word.let { UserDb.get(ctx).addToWordbook(it) }; selectedWord = null }, primary = false, modifier = Modifier.weight(1f))
                ODButton("关闭", { selectedWord = null }, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun tokenizeEpub(line: String): List<String> = Regex("[A-Za-z][A-Za-z']*|[^A-Za-z]+").findAll(line).map { it.value }.toList()

private fun normalizeEpubPath(base: String, href: String): String {
    val noFrag = href.substringBefore('#')
    val decoded = runCatching { java.net.URLDecoder.decode(noFrag, "UTF-8") }.getOrDefault(noFrag)
    val combined = if (base.isBlank()) decoded else "$base/$decoded"
    val out = mutableListOf<String>()
    combined.split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
            else -> out.add(part)
        }
    }
    return out.joinToString("/")
}

private fun parseEpub(file: File): List<EpubChapter> {
    if (file.length() > 120L * 1024 * 1024) error("EPUB 超过 120MB，已阻止解析以避免内存溢出")
    var totalChars = 0
    ZipFile(file).use { zip ->
        val container = zip.getEntry("META-INF/container.xml") ?: error("不是有效的 EPUB")
        val rootPath = zip.getInputStream(container).bufferedReader().use { it.readText() }
            .let { Regex("full-path=\\\"([^\\\"]+)\\\"").find(it)?.groupValues?.get(1) } ?: error("找不到 OPF")
        val opf = zip.getEntry(rootPath) ?: error("找不到 OPF 文件")
        val opfText = zip.getInputStream(opf).bufferedReader().use { it.readText() }
        val base = rootPath.substringBeforeLast('/', "")
        val manifest = mutableMapOf<String, String>()
        Regex("<item\\b[^>]*>").findAll(opfText).forEach { m ->
            val id = Regex("id=\\\"([^\\\"]+)\\\"").find(m.value)?.groupValues?.get(1) ?: return@forEach
            val href = Regex("href=\\\"([^\\\"]+)\\\"").find(m.value)?.groupValues?.get(1) ?: return@forEach
            if (m.value.contains("xhtml", true) || m.value.contains("html", true)) manifest[id] = href
        }
        val spine = Regex("<itemref\\b[^>]*idref=\\\"([^\\\"]+)\\\"[^>]*>").findAll(opfText).map { it.groupValues[1] }.toList()
        val files = if (spine.isNotEmpty()) spine.mapNotNull { manifest[it] } else manifest.values.toList()
        return files.take(300).mapIndexed { i, href ->
            if (totalChars > 4_000_000) return@mapIndexed null
            val path = normalizeEpubPath(base, href)
            val entry = zip.getEntry(path) ?: zip.getEntry(normalizeEpubPath("", href)) ?: return@mapIndexed null
            if (entry.size > 8L * 1024 * 1024) return@mapIndexed null
            val html = zip.getInputStream(entry).bufferedReader().use { it.readText().take(400_000) }
            val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: "第 ${i + 1} 节"
            val body = Regex("<body[^>]*>(.*?)</body>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1) ?: html
            val text = body.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</p>"), "\n\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
            if (text.length < 10) null else {
                val capped = text.take(220_000)
                totalChars += capped.length
                if (totalChars > 4_000_000) null else EpubChapter(title, capped)
            }
        }.filterNotNull()
    }
}
