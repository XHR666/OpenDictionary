package io.github.xhr666.opendictionary.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import io.github.xhr666.opendictionary.data.UserDb
import io.github.xhr666.opendictionary.data.WordData

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 文档阅读：导入 txt/md，点词查义，支持高亮/下划线并按文档保存。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocReaderScreen(docId: Long, onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { UserDb.get(ctx) }
    var docs by remember { mutableStateOf(db.documents()) }
    var currentId by rememberSaveable { mutableStateOf(docId) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var selectedLine by remember { mutableIntStateOf(0) }
    var selectedDef by remember { mutableStateOf<WordData?>(null) }
    var marks by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showMarkPanel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = (runCatching {
                ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment)?.substringAfterLast('/') ?: "导入文档"
            val id = db.addDocument(uri.toString(), name)
            docs = db.documents(); currentId = id
        }
    }

    fun load(id: Long) {
        val doc = docs.firstOrNull { it.id == id } ?: return
        title = doc.title
        loading = true; loadError = ""
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(android.net.Uri.parse(doc.uri))?.bufferedReader()?.use { reader ->
                        reader.lineSequence().take(2000).toList()
                    } ?: emptyList()
                }
            }
            if (id != currentId) return@launch
            read.onFailure { loadError = "读取失败：${it.message}" }
            lines = read.getOrNull().orEmpty()
            marks = db.docMarks(id)
            loading = false
        }
    }
    LaunchedEffect(currentId) { if (currentId > 0) load(currentId) }

    PageScaffold(if (currentId > 0) title.ifBlank { "文档阅读" } else "文档阅读", { onNavigate(Route.Reading) }, contentScroll = false) {
        if (currentId <= 0) {
            Text("导入 txt/md 文档，点按单词查义，长按/选择可高亮或下划线。文档只保存在本机。", style = OD.Cap.copy(lineHeight = 17.sp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ODButton("导入文档", { importer.launch(arrayOf("text/plain", "text/markdown", "text/*")) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            docs.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.title, style = OD.H3.copy(fontSize = 13.sp))
                        Text("点击打开", style = OD.Micro)
                    }
                    Pressable(onClick = { currentId = d.id }) { Text("打开", style = OD.Cap.copy(color = OD.CyanDeep)) }
                    Spacer(Modifier.width(12.dp))
                    Pressable(onClick = {
                        db.deleteDocument(d.id); docs = db.documents()
                        if (docs.isEmpty()) currentId = -1
                    }) { Text("删除", style = OD.Cap.copy(color = OD.Muted)) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .5f)))
            }
            Spacer(Modifier.height(90.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pressable(onClick = { currentId = -1 }) { OIcon(Ic.Back, 18.dp, OD.Ink) }
                Spacer(Modifier.width(8.dp))
                Text(title.ifBlank { "文档阅读" }, style = OD.H3.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                Pressable(onClick = { importer.launch(arrayOf("text/plain", "text/markdown", "text/*")) }) { Text("导入", style = OD.Cap.copy(color = OD.CyanDeep, fontSize = 11.sp)) }
            }
            Spacer(Modifier.height(6.dp))
            if (!loading && loadError.isBlank() && lines.size >= 2000) {
                Text("文档较长，仅显示前 2000 行。", style = OD.Micro.copy(color = OD.Muted)); Spacer(Modifier.height(4.dp))
            }
            if (loading) Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Text("读取中…", style = OD.Cap) }
            else if (loadError.isNotBlank()) Text(loadError, style = OD.Cap.copy(color = OD.Warm))
            else LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(lines) { li, line ->
                    FlowRow(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.Start) {
                        tokenize(line).forEach { token ->
                            val isWord = token.any { it.isLetter() }
                            if (!isWord) { Text(token, style = OD.ReadBody.copy(fontSize = 13.sp, lineHeight = 21.sp)); return@forEach }
                            val key = "$li:$token"
                            val mark = marks[key]
                            Box(
                                Modifier.padding(end = 1.dp).clip(RoundedCornerShape(3.dp))
                                    .background(if (mark == "highlight") OD.WarmSoft else Color.Transparent)
                                    .clickable {
                                        selected = token; selectedLine = li; showMarkPanel = true; selectedDef = null
                                        scope.launch {
                                            selectedDef = withContext(Dispatchers.IO) { io.github.xhr666.opendictionary.data.LexiconDb.get(ctx).detail(token) }
                                        }
                                    }
                                    .padding(horizontal = 1.dp)
                            ) {
                                Text(token, style = OD.ReadBody.copy(fontSize = 13.sp, lineHeight = 21.sp, textDecoration = if (mark == "underline") TextDecoration.Underline else null))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    if (showMarkPanel && selected != null) {
        DialogCard(visible = true, onDismiss = { showMarkPanel = false }) {
            Text(selected!!, style = OD.H2)
            Spacer(Modifier.height(4.dp))
            val def = selectedDef
            when {
                def == null -> Text("本地词典暂无该词释义", style = OD.Cap)
                else -> def.entry.translation.split("\n").filter { it.isNotBlank() }.take(3).forEach { Text(it, style = OD.ReadBody.copy(fontSize = 13.sp, lineHeight = 20.sp)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pressable(onClick = {
                    val w = selected!!
                    db.toggleDocMark(currentId, selectedLine, w, "highlight"); marks = db.docMarks(currentId); showMarkPanel = false
                }) { Text("高亮", style = OD.Cap.copy(color = OD.Warm, fontWeight = FontWeight.SemiBold)) }
                Pressable(onClick = {
                    val w = selected!!
                    db.toggleDocMark(currentId, selectedLine, w, "underline"); marks = db.docMarks(currentId); showMarkPanel = false
                }) { Text("下划线", style = OD.Cap.copy(color = OD.CyanDeep)) }
                Spacer(Modifier.weight(1f))
                Pressable(onClick = {
                    db.addToWordbook(selected!!); showMarkPanel = false
                }) { Text("加入生词本", style = OD.Cap.copy(color = OD.CyanDeep)) }
            }
        }
    }
}

private fun tokenize(line: String): List<String> {
    val out = mutableListOf<String>()
    val m = Regex("[A-Za-z][A-Za-z']*|[^A-Za-z]+").findAll(line)
    m.forEach { out.add(it.value) }
    return out
}

