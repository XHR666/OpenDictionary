package io.github.xhr666.opendictionary.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import io.github.xhr666.opendictionary.data.LexiconDb
import io.github.xhr666.opendictionary.data.UserDb
import io.github.xhr666.opendictionary.data.WordData
import kotlinx.coroutines.withContext
import java.io.File

/** PDF 阅读：SAF 导入并复制到应用缓存，使用系统 PdfRenderer 逐页渲染；支持翻页/缩放。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfReaderScreen(onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("od_prefs", android.content.Context.MODE_PRIVATE) }
    var file by remember { mutableStateOf(runCatching { prefs.getString("last_pdf", null)?.let { File(it) }?.takeIf { it.exists() } }.getOrNull()) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var textMode by rememberSaveable { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var extracted by remember { mutableStateOf("") }
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var selectedDef by remember { mutableStateOf<WordData?>(null) }
    val lex = remember { LexiconDb.get(ctx) }
    val scope = rememberCoroutineScope()

    fun dirCleanup() {
        runCatching { File(ctx.cacheDir, "pdf").listFiles()?.filter { it != file }?.forEach { it.delete() } }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(ctx.cacheDir, "pdf").apply { mkdirs() }
                        val dest = File(dir, "document-${System.currentTimeMillis()}.pdf")
                        ctx.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                        val count = ParcelFileDescriptor.open(dest, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                            PdfRenderer(pfd).use { renderer -> renderer.pageCount }
                        }
                        val text = runCatching {
                            com.tom_roush.pdfbox.pdmodel.PDDocument.load(dest).use { doc ->
                                com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc).take(2_000_000)
                            }
                        }.getOrDefault("")
                        Triple(dest, count, text)
                    }
                }
                result.onSuccess { (dest, count, text) ->
                    error = null; file = dest; page = 0; pageCount = count; extracted = text
                    prefs.edit().putString("last_pdf", dest.absolutePath).apply(); dirCleanup()
                }.onFailure { error = "打开失败：${it.message}" }
            }
        }
    }

    LaunchedEffect(file) {
        val f = file ?: return@LaunchedEffect
        if (pageCount == 0) {
            pageCount = withContext(Dispatchers.IO) {
                runCatching {
                    ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { it.pageCount }
                    }
                }.getOrDefault(0)
            }
        }
        if (textMode && extracted.isBlank()) {
            extracted = withContext(Dispatchers.IO) {
                runCatching {
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(f).use { doc ->
                        com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc).take(2_000_000)
                    }
                }.getOrDefault("")
            }
        }
    }
    LaunchedEffect(file, page, zoom) {
        val f = file ?: return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        loading = true; error = null
        bitmap = null
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (page >= renderer.pageCount) return@use null
                        renderer.openPage(page).use { p ->
                            var w = (900 * zoom).toInt().coerceIn(480, 1600)
                            var h = (w * p.height / p.width.toFloat()).toInt().coerceAtLeast(480)
                            if (h > 2400) { h = 2400; w = (h * p.width / p.height.toFloat()).toInt() }
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(AndroidColor.WHITE)
                            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            if (!isActive) { bmp.recycle(); null } else bmp
                        }
                    }
                }
            }.getOrNull()
        }
        loading = false
    }

    PageScaffold("PDF 阅读", { onNavigate(Route.Reading) }) {
        if (file == null) {
            Text("导入 PDF 文档（只在本地缓存，不上传）。文字提取与点词查义暂未支持；适合阅读排版类资料。", style = OD.Cap.copy(lineHeight = 17.sp))
            Spacer(Modifier.height(10.dp))
            ODButton("选择 PDF", { importer.launch(arrayOf("application/pdf", "*/*")) }, modifier = Modifier.fillMaxWidth())
            error?.let { Spacer(Modifier.height(8.dp)); Text(it, style = OD.Cap.copy(color = OD.Warm)) }
            Spacer(Modifier.height(90.dp))
        } else {
            error?.let { Text(it, style = OD.Cap.copy(color = OD.Warm)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PDF · 第 ${page + 1} / ${pageCount.coerceAtLeast(1)} 页", style = OD.H3.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                Pressable(onClick = { importer.launch(arrayOf("application/pdf", "*/*")) }) { Text("重新导入", style = OD.Cap.copy(color = OD.CyanDeep, fontSize = 11.sp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("图文", !textMode) { textMode = false }
                Pill("文字/查词", textMode) { textMode = true }
            }
            Spacer(Modifier.height(6.dp))
            if (textMode) {
                val lines = remember(extracted) { extracted.split("\n").filter { it.isNotBlank() }.take(400) }
                Column(Modifier.fillMaxWidth()) {
                    if (extracted.isBlank()) Text("未能提取文字层（扫描版 PDF 需 OCR，暂不支持）。可切换回图文阅读。", style = OD.Cap)
                    lines.forEach { line ->
                        FlowRow(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            tokenizePdf(line).forEach { token ->
                                if (token.any { it.isLetter() }) {
                                    Text(token, style = OD.ReadBody.copy(fontSize = 13.sp, lineHeight = 20.sp, color = OD.CyanDeep), modifier = Modifier.clickable {
                                        selectedWord = token; selectedDef = null
                                        scope.launch {
                                            selectedDef = withContext(Dispatchers.IO) { runCatching { lex.detail(token) }.getOrNull() }
                                        }
                                    })
                                } else Text(token, style = OD.ReadBody.copy(fontSize = 13.sp, lineHeight = 20.sp))
                            }
                        }
                    }
                    Spacer(Modifier.height(90.dp))
                }
            } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ODButton("上一页", { if (page > 0) page-- }, primary = false, modifier = Modifier.weight(1f))
                ODButton("下一页", { if (page < pageCount - 1) page++ }, primary = false, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("缩放", style = OD.Micro)
                ODSlider(zoom, 0.8f..2.2f, { zoom = it }, modifier = Modifier.weight(1f))
                Text("%.0f%%".format(zoom * 100), style = OD.Micro)
            }
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth()) {
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Text("渲染中…", style = OD.Cap) }
                    bitmap != null -> Image(bitmap!!.asImageBitmap(), contentDescription = "PDF 第 ${page + 1} 页", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                    else -> Text("这一页渲染失败。", style = OD.Cap)
                }
            }
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

private fun tokenizePdf(line: String): List<String> = Regex("[A-Za-z][A-Za-z']*|[^A-Za-z]+").findAll(line).map { it.value }.toList()
