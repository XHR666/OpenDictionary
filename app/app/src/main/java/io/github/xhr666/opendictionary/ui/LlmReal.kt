package io.github.xhr666.opendictionary.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.llm.LocalLlm
import io.github.xhr666.opendictionary.llm.ModelCatalog
import io.github.xhr666.opendictionary.llm.ModelSpec
import io.github.xhr666.opendictionary.llm.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LlmRealScreen(onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("od_llm", Context.MODE_PRIVATE) }
    var selectedId by remember { mutableStateOf(prefs.getString("model", "qwen3-0.6b") ?: "qwen3-0.6b") }
    val spec = remember(selectedId) { ModelCatalog.byId(selectedId) }
    var installed by remember { mutableStateOf(ModelStore.installed(ctx, spec)) }
    var memory by remember { mutableStateOf(prefs.getFloat("memory_gb", 1.8f)) }
    var prompt by remember { mutableStateOf(prefs.getString("prompt", DEFAULT_PROMPT) ?: DEFAULT_PROMPT) }
    var thinking by remember { mutableStateOf(prefs.getBoolean("thinking", false)) }
    var length by remember { mutableStateOf(prefs.getString("length", "标准") ?: "标准") }
    var status by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val aiPrefs = remember { ctx.getSharedPreferences("od_prefs", Context.MODE_PRIVATE) }
    var aiMode by remember { mutableStateOf(aiPrefs.getString("ai_sense_mode", "ask") ?: "ask") }
    var output by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(LocalLlm.isModelLoaded) }
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                running = true; status = "导入模型…"
                runCatching {
                    withContext(Dispatchers.IO) {
                        LocalLlm.unload()
                        val dest = ModelStore.file(ctx, spec)
                        val tmp = File(dest.path + ".importing")
                        tmp.delete()
                        ModelStore.import(ctx, uri, tmp) { p ->
                            progress = (p.toFloat() / spec.sizeBytes).coerceIn(0f, 1f)
                        }
                        if (tmp.length() != spec.sizeBytes) { tmp.delete(); error("文件大小不符") }
                        if (spec.sha256.isNotBlank() && ModelStore.sha256(tmp) != spec.sha256) { tmp.delete(); error("sha256 校验失败") }
                        if (dest.exists()) dest.delete()
                        if (!tmp.renameTo(dest)) error("无法保存模型文件")
                    }
                }.onSuccess { installed = ModelStore.installed(ctx, spec); loaded = LocalLlm.isModelLoaded; status = "已导入 ${spec.name}" }
                    .onFailure { status = "导入失败：${it.message}" }
                running = false
            }
        }
    }

    PageScaffold("AI 与本地模型", { onNavigate(Route.Settings) }) {
        Text("AI 生成内容提示", style = OD.H3.copy(fontSize = 13.5.sp))
        Text("词条 AI 扩展义项等生成内容是否先询问", style = OD.Cap.copy(fontSize = 10.5.sp))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ask" to "每次询问", "never" to "从不", "direct" to "直接生成").forEach { (k, label) ->
                Pill(label, aiMode == k) { aiMode = k; aiPrefs.edit().putString("ai_sense_mode", k).apply() }
            }
        }
        Spacer(Modifier.height(14.dp))
        // 模型
        Text("模型", style = OD.Cap.copy(color = OD.Muted, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        ModelCatalog.all.forEach { m ->
            val isSel = m.id == selectedId
            val isInstalled = remember(m.id) { ModelStore.installed(ctx, m) }
            Pressable(onClick = { selectedId = m.id; prefs.edit().putString("model", m.id).apply(); installed = isInstalled; loaded = LocalLlm.isModelLoaded }) {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(if (isSel) OD.Cyan else Color.White).border(1.dp, if (isSel) OD.Cyan else OD.Hairline, CircleShape), contentAlignment = Alignment.Center) {
                        if (isSel) Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = OD.H3.copy(fontSize = 13.sp))
                        Text("${m.description} · ${"%.0f".format(m.sizeBytes / 1024.0 / 1024.0)} MB", style = OD.Cap.copy(fontSize = 10.sp))
                    }
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (isInstalled) OD.CyanSoft else OD.Parchment).padding(horizontal = 7.dp, vertical = 3.dp)) {
                        Text(if (isInstalled) "已就绪" else "未下载", style = OD.Micro.copy(fontSize = 8.5.sp, color = if (isInstalled) OD.CyanDeep else OD.Muted))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OD.Hairline.copy(alpha = .6f)))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton(if (installed) "重新导入" else "导入 GGUF", onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) }, primary = false, modifier = Modifier.weight(1f))
            if (!installed) {
                ODButton(if (running) "下载中…" else "下载模型", onClick = {
                    if (!running) {
                        running = true; status = "下载 ${spec.name}"; progress = 0f
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ModelStore.download(ctx, spec) { done, total ->
                                        progress = (done.toFloat() / total.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                    }
                                }
                            }.onSuccess { installed = true; status = "下载完成" }
                                .onFailure { status = "下载失败：${it.message}（可重试，支持断点续传）" }
                            running = false
                        }
                    }
                }, modifier = Modifier.weight(1f))
            } else {
                ODButton("删除模型", onClick = { ModelStore.delete(ctx, spec); installed = false; loaded = false; LocalLlm.unload(); status = "已删除" }, primary = false, modifier = Modifier.weight(1f))
            }
        }
        if (progress > 0f && progress < 1f) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(OD.Hairline)) {
                Box(Modifier.fillMaxWidth(progress).height(6.dp).clip(RoundedCornerShape(3.dp)).background(OD.Cyan))
            }
            Text("${(progress * 100).toInt()}% · 可随时退出，再次点击会从断点续传", style = OD.Micro)
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(status, style = OD.Micro.copy(color = OD.CyanDeep, lineHeight = 14.sp))
        }
        Spacer(Modifier.height(16.dp))

        // 运行限制
        Text("运行限制", style = OD.Cap.copy(color = OD.Muted, fontWeight = FontWeight.SemiBold))
        Row(Modifier.padding(top = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("最大运行内存", style = OD.H3.copy(fontSize = 13.5.sp))
                Text("限制上下文长度与生成长度；替代系统级内存限制。", style = OD.Cap.copy(fontSize = 10.5.sp))
            }
            Text("%.1f GB".format(memory), style = OD.Cap)
        }
        ODSlider(memory, 0.5f..3.0f, { memory = it; prefs.edit().putFloat("memory_gb", it).apply() })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("512 MB", style = OD.Micro); Text("3 GB", style = OD.Micro) }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("上下文", style = OD.H3.copy(fontSize = 13.sp))
                Text(memoryCtx(memory).let { "$it tokens（按内存自动限制）" }, style = OD.Cap.copy(fontSize = 10.sp))
            }
            Text("线程 4", style = OD.Cap)
        }
        Spacer(Modifier.height(10.dp))

        // 回答方式
        Text("回答方式", style = OD.Cap.copy(color = OD.Muted, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(8.dp))
        Text("自定义提示词", style = OD.H3.copy(fontSize = 13.5.sp))
        Text("只改变表达方式，事实仍以词典数据为准。", style = OD.Cap.copy(fontSize = 10.5.sp))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(12.dp)).padding(10.dp)) {
            BasicTextField(value = prompt, onValueChange = { prompt = it }, textStyle = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = OD.InkSoft), modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("{{word}}", "{{dictionary_data}}", "{{language}}", "{{level}}").forEach { v ->
                Box(Modifier.clip(RoundedCornerShape(5.dp)).background(OD.CyanSoft).padding(horizontal = 5.dp, vertical = 2.dp)) { Text(v, style = OD.Micro.copy(fontSize = 8.5.sp, color = OD.CyanDeep)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton("恢复默认", onClick = { prompt = DEFAULT_PROMPT }, primary = false, modifier = Modifier.weight(1f))
            ODButton("保存设置", onClick = {
                prefs.edit().putString("prompt", prompt).putBoolean("thinking", thinking).putString("length", length).apply()
                status = "设置已保存"
            }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("思考模式", style = OD.H3.copy(fontSize = 13.sp)); Text("Qwen3 可关闭以降低延迟", style = OD.Cap.copy(fontSize = 10.sp)) }
            ODToggle(thinking, { thinking = it })
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("回答长度", style = OD.H3.copy(fontSize = 13.sp)); Text("短 / 标准 / 详细", style = OD.Cap.copy(fontSize = 10.sp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("短", "标准", "详细").forEach { t -> Pill(t, t == length) { length = t } } }
        }
        Spacer(Modifier.height(10.dp))

        // 测试
        Text("测试", style = OD.Cap.copy(color = OD.Muted, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ODButton(if (running) "运行中…" else "运行一次测试", onClick = {
                if (!running) {
                    if (!installed) { status = "请先下载或导入模型"; return@ODButton }
                    running = true; output = ""; stats = ""; status = "加载模型…"
                    scope.launch {
                        val t0 = System.currentTimeMillis()
                        val res = withContext(Dispatchers.IO) {
                            runCatching {
                                val path = ModelStore.file(ctx, spec).absolutePath
                                if (!LocalLlm.isModelLoaded) LocalLlm.loadModel(path, 4, memoryCtx(memory))
                                val loadMs = System.currentTimeMillis() - t0
                                val p = buildPrompt(prompt, thinking)
                                val g0 = System.currentTimeMillis()
                                val text = LocalLlm.generate(p, maxTokens(length))
                                Triple(text, loadMs, System.currentTimeMillis() - g0)
                            }
                        }
                        res.onSuccess { (text, loadMs, genMs) ->
                            loaded = LocalLlm.isModelLoaded
                            output = text.ifBlank { "（模型没有输出，可能上下文不足或模型文件异常）" }
                            val approxTok = text.length / 3
                            stats = "加载 ${loadMs} ms · 生成 ${genMs} ms · 约 ${"%.1f".format(approxTok * 1000.0 / genMs.coerceAtLeast(1))} tok/s（估算）"
                            status = if (loaded) "模型已加载" else "加载失败"
                        }.onFailure { status = "运行失败：${it.message}" }
                        running = false
                    }
                }
            }, modifier = Modifier.weight(1f))
            if (loaded) ODButton("卸载", onClick = { LocalLlm.unload(); loaded = false; status = "模型已卸载" }, primary = false, modifier = Modifier.weight(1f))
        }
        if (output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OD.Pearl).border(1.dp, OD.Hairline, RoundedCornerShape(12.dp)).padding(11.dp)) {
                Text("输出", style = OD.Micro.copy(color = OD.CyanDeep, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(4.dp))
                Text(output, style = OD.Cap.copy(lineHeight = 18.sp))
            }
        }
        if (stats.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(stats, style = OD.Micro) }
        Spacer(Modifier.height(8.dp))
        Text("提示：模型文件不随 APK 分发；首次使用需下载（约 0.6–1.1GB）或从本地导入 GGUF。下载优先走 GitHub 分块，失败时自动切换镜像；支持断点续传与 sha256 校验。", style = OD.Micro.copy(lineHeight = 14.sp))
    }
}

private const val DEFAULT_PROMPT = "你是英语词典助手。只依据我提供的词典数据回答；用简明中文解释 {{word}} 的核心义项、易混点和 2 个例句。查不到就说查不到，不要编造。"

private fun memoryCtx(memoryGb: Float): Int = when {
    memoryGb < 0.9f -> 1024
    memoryGb < 1.5f -> 1536
    else -> 2048
}

private fun maxTokens(length: String): Int = when (length) { "短" -> 90; "详细" -> 320; else -> 180 }

private fun buildPrompt(template: String, thinking: Boolean): String {
    val word = "reputation"
    val dict = "reputation n. 名气，名声，名誉；声望；信誉；词形复数为 reputations。"
    val base = template
        .replace("{{word}}", word)
        .replace("{{dictionary_data}}", dict)
        .replace("{{language}}", "中文")
        .replace("{{level}}", "高中")
    return if (thinking) base else "/no_think\n$base"
}
