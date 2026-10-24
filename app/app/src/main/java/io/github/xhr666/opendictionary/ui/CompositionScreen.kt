package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.data.UserDb
import io.github.xhr666.opendictionary.llm.LocalLlm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COMPOSITION_PROMPT = """你是英语写作老师。请针对下面的学生作文，用中文输出：1) 总体评价；2) 语法/搭配错误逐条列出（引用原句+修改）；3) 结构建议；4) 一个更地道的改写片段。不要编造事实。题目类型：%s。作文：%s"""

/** 作文练习：可分考试类型、键盘/手写、缩放旋转、保存与本地 AI 批改。 */
@Composable
fun CompositionScreen(onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { UserDb.get(ctx) }
    val scope = rememberCoroutineScope()
    var examType by rememberSaveable { mutableStateOf("高考") }
    var keyboardMode by rememberSaveable { mutableStateOf(true) }
    var eraser by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var rotation by rememberSaveable { mutableFloatStateOf(0f) }
    var strokes = remember { mutableStateListOf<List<Offset>>() }
    var current = remember { mutableStateListOf<Offset>() }
    var feedback by remember { mutableStateOf("") }
    var grading by remember { mutableStateOf(false) }

    PageScaffold("写作", { onNavigate(Route.Home) }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("高考", "CET-4", "CET-6", "IELTS", "读后续写", "摘要写作").forEach { t -> Pill(t, examType == t) { examType = t } }
            Spacer(Modifier.width(6.dp))
            Pill("翻译练习", false) { onNavigate(Route.TranslationPractice) }
        }
        if (examType == "读后续写" || examType == "摘要写作") {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OD.CyanSoft.copy(alpha = .28f)).padding(10.dp)) {
                Text(if (examType == "读后续写") "读后续写" else "摘要写作", style = OD.H3.copy(fontSize = 12.5.sp, color = OD.CyanDeep))
                Spacer(Modifier.height(3.dp))
                Text(if (examType == "读后续写") "练习：先阅读一段故事开头，再用 150 词左右续写，注意承接人物与情节、使用衔接词；可在下方作文纸写作后交给本地 AI 批改。" else "练习：阅读一篇短文后用 80–120 词概括主旨与关键细节，避免照抄原句；写完后可交给本地 AI 批改结构。", style = OD.Cap.copy(fontSize = 10.5.sp, lineHeight = 16.sp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(if (keyboardMode) "键盘输入" else "键盘输入", keyboardMode) { keyboardMode = true }
            Pill("手写", !keyboardMode) { keyboardMode = false }
            Pill("橡皮擦", eraser && !keyboardMode) { keyboardMode = false; eraser = !eraser }
            Spacer(Modifier.weight(1f))
            Pressable(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) { Text("撤销", style = OD.Cap.copy(color = OD.CyanDeep)) }
            Pressable(onClick = { rotation = (rotation + 90f) % 360f }) { Text("旋转", style = OD.Cap.copy(color = OD.CyanDeep)) }
            Pressable(onClick = { strokes.clear(); text = ""; feedback = "" }) { Text("清空", style = OD.Cap.copy(color = OD.Muted)) }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("缩放", style = OD.Micro); ODSlider(zoom, 0.8f..1.8f, { zoom = it }, modifier = Modifier.weight(1f)); Text("%.0f%%".format(zoom * 100), style = OD.Micro)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(12.dp))) {
            Box(Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, rotationZ = rotation)) {
                Canvas(Modifier.fillMaxSize().pointerInput(keyboardMode, eraser) {
                    if (!keyboardMode) {
                        detectDragGestures(
                            onDragStart = { current.clear(); current.add(it) },
                            onDrag = { change, _ ->
                                if (eraser) {
                                    val p = change.position
                                    strokes.removeAll { pts -> pts.any { (it - p).getDistance() < 20.dp.toPx() } }
                                    current.clear()
                                } else current.add(change.position)
                            },
                            onDragEnd = { if (!eraser) strokes.add(current.toList()); current.clear() }
                        )
                    }
                }) {
                    val line = Color(0xFFE8EFF1)
                    var y = 36.dp.toPx()
                    while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f); y += 30.dp.toPx() }
                    drawLine(OD.Cyan.copy(alpha = .35f), Offset(36.dp.toPx(), 0f), Offset(36.dp.toPx(), size.height), strokeWidth = 1f)
                    strokes.forEach { pts ->
                        if (pts.size > 1) {
                            val p = Path(); p.moveTo(pts[0].x, pts[0].y)
                            pts.drop(1).forEach { p.lineTo(it.x, it.y) }
                            drawPath(p, OD.Ink, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                    if (current.size > 1) {
                        val p = Path(); p.moveTo(current[0].x, current[0].y)
                        current.drop(1).forEach { p.lineTo(it.x, it.y) }
                        drawPath(p, OD.Ink, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                if (keyboardMode) {
                    BasicTextField(
                        value = text, onValueChange = { text = it.take(4000) },
                        textStyle = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = OD.InkSoft),
                        modifier = Modifier.fillMaxSize().padding(44.dp),
                        decorationBox = { inner -> if (text.isEmpty()) Text("在这里写作文…（Ctrl/输入法与手写可切换；缩放/旋转用于看清作文纸）", style = OD.Cap) else inner() }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton(if (grading) "批改中…" else "本地 AI 批改", onClick = {
                if (!grading) {
                    grading = true; feedback = ""
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                if (!LocalLlm.isModelLoaded) error("请先在 AI 与本地模型页加载模型")
                                if (text.isBlank()) error("先写一点内容再批改")
                                LocalLlm.generate(COMPOSITION_PROMPT.format(examType, text.take(1500)), 320)
                            }
                        }
                        result.onSuccess { feedback = it.ifBlank { "模型没有输出，请重试。" } }.onFailure { feedback = it.message ?: "批改失败" }
                        grading = false
                    }
                }
            }, modifier = Modifier.weight(1f))
            ODButton("保存", onClick = {
                db.saveNote("作文 · $examType", if (text.isBlank()) "（手写作答）" else text)
                feedback = "已保存到本机笔记"
            }, primary = false, modifier = Modifier.weight(1f))
        }
        if (feedback.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).clip(RoundedCornerShape(12.dp)).background(OD.Pearl).padding(12.dp)) {
                Text(feedback, style = OD.Cap.copy(lineHeight = 18.sp))
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}
