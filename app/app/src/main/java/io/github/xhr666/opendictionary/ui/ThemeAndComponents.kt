package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.compositionLocalOf
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.fontResource
import io.github.xhr666.opendictionary.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- 开源字体（SIL OFL 1.1，见设置·开源许可） ----------
val UiFont = FontFamily(
    Font(R.font.inter, weight = FontWeight.Normal),
    Font(R.font.inter, weight = FontWeight.Medium),
    Font(R.font.inter, weight = FontWeight.SemiBold),
    Font(R.font.inter, weight = FontWeight.Bold),
)
val ReadingFont = FontFamily(Font(R.font.literata, weight = FontWeight.Normal), Font(R.font.literata, weight = FontWeight.SemiBold))
val HandFont = FontFamily(Font(R.font.caveat, weight = FontWeight.Medium), Font(R.font.caveat, weight = FontWeight.Bold))

// ---------- 设计标记（取色自应用图标：青绿 + 深灰） ----------
data class OdPalette(
    val name: String,
    val accent: Color,
    val accentSoft: Color,
    val background: Color,
    val surface: Color,
    val warm: Color,
    val warmSoft: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val hairline: Color,
)

val odPalettes = listOf(
    OdPalette("青瓷", Color(0xFF12C4CB), Color(0xFFE6FAFB), Color(0xFFF4F7F8), Color(0xFFFAFCFC), Color(0xFFD97706), Color(0xFFFFF4E2), Color(0xFF2B3236), Color(0xFF495157), Color(0xFF7B8489), Color(0xFFE6EAEC)),
    OdPalette("雾蓝", Color(0xFF8FA9BF), Color(0xFFE7EEF3), Color(0xFFEAE3D6), Color(0xFFF5F0E7), Color(0xFFB9865A), Color(0xFFF3E4D6), Color(0xFF2F3A44), Color(0xFF4E5964), Color(0xFF7D8892), Color(0xFFDCD7CC)),
    OdPalette("苔绿", Color(0xFF6E8378), Color(0xFFEAF0ED), Color(0xFFF6F5F1), Color(0xFFFBFAF7), Color(0xFFA98467), Color(0xFFF2E8DF), Color(0xFF2D3833), Color(0xFF4B5751), Color(0xFF7B8781), Color(0xFFDFE1DC)),
    OdPalette("石墨", Color(0xFF9CBBC0), Color(0xFFEAF2F3), Color(0xFFF4F5F2), Color(0xFFFAFBF9), Color(0xFFB58A6A), Color(0xFFF3E9E1), Color(0xFF40484E), Color(0xFF525C63), Color(0xFF7C868D), Color(0xFFDFE3E4)),
)

object OD {
    var Ink by mutableStateOf(Color(0xFF2B3236))
    var InkSoft by mutableStateOf(Color(0xFF495157))
    var Muted by mutableStateOf(Color(0xFF7B8489))
    var Hairline by mutableStateOf(Color(0xFFE6EAEC))
    var Parchment by mutableStateOf(Color(0xFFF4F7F8))
    var Pearl by mutableStateOf(Color(0xFFFAFCFC))
    var Card by mutableStateOf(Color(0xFFFFFFFF))
    var Cyan by mutableStateOf(Color(0xFF12C4CB))
    var CyanDeep by mutableStateOf(Color(0xFF079AA1))
    var CyanSoft by mutableStateOf(Color(0xFFE6FAFB))
    var Warm by mutableStateOf(Color(0xFFD97706))
    var WarmSoft by mutableStateOf(Color(0xFFFFF4E2))
    val Good = Color(0xFF138A52)
    val Danger = Color(0xFFB42318)
    val Glass = Color(0xB8FFFFFF)

    fun applyPalette(index: Int) {
        val p = odPalettes.getOrNull(index) ?: return
        Cyan = p.accent
        CyanDeep = darken(p.accent, .42f)
        CyanSoft = p.accentSoft
        Parchment = p.background
        Pearl = p.surface
        Warm = p.warm
        WarmSoft = p.warmSoft
        Ink = p.ink
        InkSoft = p.inkSoft
        Muted = darken(p.muted, .30f)
        Hairline = p.hairline
    }

    private fun darken(c: Color, amount: Float) = Color(
        red = (c.red * (1f - amount)).coerceIn(0f, 1f),
        green = (c.green * (1f - amount)).coerceIn(0f, 1f),
        blue = (c.blue * (1f - amount)).coerceIn(0f, 1f),
        alpha = c.alpha
    )

    val ReadBody: TextStyle get() = TextStyle(fontFamily = UiState.customFontFamily ?: ReadingFont, fontSize = 13.5.sp, lineHeight = 21.sp, color = InkSoft)
    val H1: TextStyle get() = TextStyle(fontFamily = UiFont, fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = (-0.7).sp)
    val H2: TextStyle get() = TextStyle(fontFamily = UiFont, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = (-0.3).sp)
    val H3: TextStyle get() = TextStyle(fontFamily = UiFont, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = (-0.1).sp)
    val Body: TextStyle get() = TextStyle(fontFamily = UiFont, fontSize = 13.sp, lineHeight = 20.sp, color = InkSoft)
    val BodySm: TextStyle get() = TextStyle(fontFamily = UiFont, fontSize = 12.sp, lineHeight = 18.sp, color = InkSoft)
    val Cap: TextStyle get() = TextStyle(fontFamily = UiFont, fontSize = 10.5.sp, lineHeight = 14.sp, color = Muted)
    val Micro: TextStyle get() = TextStyle(fontFamily = UiFont, fontSize = 9.5.sp, lineHeight = 13.sp, color = Muted)
}

/** 页面背景层（用于页面内搜索框/卡片的液态玻璃采样，避免与内容层递归）。 */
val LocalPageBackdrop = compositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

private object BgCache {
    var uri: String? = null
    var bitmap: ImageBitmap? = null
}

@Composable
fun Modifier.liquidGlass(shape: Shape, tint: Color = Color.White.copy(alpha = .18f)): Modifier {
    val bd = LocalPageBackdrop.current
    val mode = UiState.glassMode
    return if (bd != null && mode >= 1) {
        this.drawBackdrop(
            backdrop = bd,
            shape = { shape },
            effects = {
                colorControls(saturation = 1.7f)
                blur(if (mode >= 2) 28.dp.toPx() else 20.dp.toPx())
                if (mode >= 2) lens(8.dp.toPx(), 4.dp.toPx())
            },
            highlight = { Highlight(width = 1f.dp, blurRadius = .5f.dp, alpha = .45f, style = HighlightStyle.Plain) },
            shadow = { null },
            onDrawSurface = { drawRect(tint) }
        )
    } else {
        this.clip(shape).background(Color.White.copy(alpha = .74f)).border(1.dp, Color.White.copy(alpha = .55f), shape)
    }
}

@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bgPrefs = ctx.getSharedPreferences("od_prefs", android.content.Context.MODE_PRIVATE)
    val bg = bgPrefs.getString("bg_uri", null)
    val bgMode = bgPrefs.getString("bg_mode", if (bg != null) "image" else "gradient") ?: "gradient"
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(bg) {
        if (bg == null) { bitmap = null; return@LaunchedEffect }
        if (BgCache.uri == bg && BgCache.bitmap != null) { bitmap = BgCache.bitmap; return@LaunchedEffect }
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val uri = android.net.Uri.parse(bg)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap() }
            }.getOrNull().also { BgCache.uri = bg; BgCache.bitmap = it }
        }
    }
    Box(Modifier.fillMaxSize()) {
        val pageBackdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
        val bmp = bitmap
        if (bgMode == "image" && bmp != null) {
            Image(bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .70f)))
        } else if (bgMode == "auto") {
            val palettes = listOf(
                listOf(Color(0xFFEAF7F5), Color(0xFFFDF3E7), Color(0xFFF3F0FF)),
                listOf(Color(0xFFE8F4FF), Color(0xFFF7F9FC), Color(0xFFEAF7F1)),
                listOf(Color(0xFFFFF4E8), Color(0xFFFBEFF0), Color(0xFFFDF9EC)),
                listOf(Color(0xFFEDF7EE), Color(0xFFF4FAF7), Color(0xFFF8F3E9)),
                listOf(Color(0xFFF2EEFF), Color(0xFFF9F0F7), Color(0xFFFDF6EA)),
            )
            val pick = palettes[((java.time.LocalDate.now().toEpochDay() % palettes.size).toInt())]
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(pick)))
        } else {
            Box(Modifier.fillMaxSize().background(OD.Parchment))
        }
        Box(
            Modifier.fillMaxSize().drawWithContent {
                drawCircle(Brush.radialGradient(listOf(OD.Cyan.copy(alpha = .16f), Color.Transparent), center = Offset(size.width * .9f, size.height * .05f), radius = size.width * .7f), radius = size.width * .7f, center = Offset(size.width * .9f, size.height * .05f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE6C9).copy(alpha = .35f), Color.Transparent), center = Offset(size.width * .05f, size.height * .35f), radius = size.width * .55f), radius = size.width * .55f, center = Offset(size.width * .05f, size.height * .35f))
                drawContent()
            }
        )
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalPageBackdrop provides pageBackdrop) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), content = content)
        }
    }
}

// ---------- 图标（全部 Canvas 自绘） ----------
enum class Ic { Back, Search, Close, Star, StarFill, Home, Book, Play, Plus, Check, Chevron, Settings, Download, Heart, Info, Shield, Link, Sparkle, Mic, Globe, Clock, External, Update, Wifi, Font, Bell, Trash, Cloud, Lock, Grid, Headphone }

@Composable
fun OIcon(icon: Ic, size: Dp = 18.dp, tint: Color = OD.InkSoft, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val desc = contentDescription ?: when (icon) {
        Ic.Back -> "返回"; Ic.Search -> "搜索"; Ic.Star -> "收藏"; Ic.Play -> "播放"
        Ic.Trash -> "删除"; Ic.Chevron -> "更多"; Ic.Settings -> "设置"; Ic.Info -> "信息"
        Ic.Grid -> "排序"; Ic.Close -> "关闭"; else -> null
    }
    Canvas(if (desc != null) modifier.size(size).semantics { this.contentDescription = desc } else modifier.size(size)) {
        val w = size.toPx(); val s = w / 24f
        val stroke = Stroke(width = 1.8f * s, cap = StrokeCap.Round)
        fun p(block: Path.() -> Unit) = Path().apply(block)
        when (icon) {
            Ic.Back -> drawPath(p { moveTo(15f * s, 5f * s); lineTo(8f * s, 12f * s); lineTo(15f * s, 19f * s) }, tint, style = stroke)
            Ic.Chevron -> drawPath(p { moveTo(9f * s, 5f * s); lineTo(16f * s, 12f * s); lineTo(9f * s, 19f * s) }, tint, style = stroke)
            Ic.Search -> { drawCircle(tint, 6.2f * s, Offset(10.5f * s, 10.5f * s), style = stroke); drawLine(tint, Offset(15.2f * s, 15.2f * s), Offset(20f * s, 20f * s), 1.8f * s, StrokeCap.Round) }
            Ic.Close -> { drawLine(tint, Offset(6f * s, 6f * s), Offset(18f * s, 18f * s), 1.8f * s, StrokeCap.Round); drawLine(tint, Offset(18f * s, 6f * s), Offset(6f * s, 18f * s), 1.8f * s, StrokeCap.Round) }
            Ic.Star, Ic.StarFill -> {
                val path = p {
                    moveTo(12f * s, 3f * s); lineTo(14.7f * s, 9f * s); lineTo(21f * s, 9.7f * s)
                    lineTo(16.3f * s, 14f * s); lineTo(17.7f * s, 20.3f * s); lineTo(12f * s, 17f * s)
                    lineTo(6.3f * s, 20.3f * s); lineTo(7.7f * s, 14f * s); lineTo(3f * s, 9.7f * s)
                    lineTo(9.3f * s, 9f * s); close()
                }
                if (icon == Ic.StarFill) drawPath(path, tint) else drawPath(path, tint, style = stroke)
            }
            Ic.Home -> drawPath(p { moveTo(4f * s, 11f * s); lineTo(12f * s, 4f * s); lineTo(20f * s, 11f * s); lineTo(20f * s, 20f * s); lineTo(4f * s, 20f * s); close() }, tint, style = stroke)
            Ic.Book -> { drawRoundRect(tint, Offset(4f * s, 5f * s), Size(6.5f * s, 15f * s), CornerRadius(1.5f * s), style = stroke); drawRoundRect(tint, Offset(13.5f * s, 5f * s), Size(6.5f * s, 15f * s), CornerRadius(1.5f * s), style = stroke) }
            Ic.Play -> drawPath(p { moveTo(8f * s, 5f * s); lineTo(19f * s, 12f * s); lineTo(8f * s, 19f * s); close() }, tint)
            Ic.Plus -> { drawLine(tint, Offset(12f * s, 5f * s), Offset(12f * s, 19f * s), 1.9f * s, StrokeCap.Round); drawLine(tint, Offset(5f * s, 12f * s), Offset(19f * s, 12f * s), 1.9f * s, StrokeCap.Round) }
            Ic.Check -> drawPath(p { moveTo(5f * s, 12.5f * s); lineTo(10f * s, 17.5f * s); lineTo(19f * s, 6.5f * s) }, tint, style = Stroke(2.2f * s, cap = StrokeCap.Round))
            Ic.Settings -> {
                drawCircle(tint, 3.2f * s, Offset(12f * s, 12f * s), style = stroke)
                for (i in 0 until 8) {
                    val a = Math.PI * i / 4
                    val x1 = 12f * s + (9f * s * Math.cos(a)).toFloat(); val y1 = 12f * s + (9f * s * Math.sin(a)).toFloat()
                    val x2 = 12f * s + (11f * s * Math.cos(a)).toFloat(); val y2 = 12f * s + (11f * s * Math.sin(a)).toFloat()
                    drawLine(tint, Offset(x1, y1), Offset(x2, y2), 1.6f * s, StrokeCap.Round)
                }
            }
            Ic.Download -> { drawLine(tint, Offset(12f * s, 4f * s), Offset(12f * s, 16f * s), 1.8f * s, StrokeCap.Round); drawPath(p { moveTo(7f * s, 11f * s); lineTo(12f * s, 16f * s); lineTo(17f * s, 11f * s) }, tint, style = stroke); drawLine(tint, Offset(5f * s, 20f * s), Offset(19f * s, 20f * s), 1.8f * s, StrokeCap.Round) }
            Ic.Heart -> drawPath(p { moveTo(12f * s, 20f * s); cubicTo(4f * s, 14f * s, 3f * s, 8f * s, 12f * s, 5f * s); cubicTo(21f * s, 8f * s, 20f * s, 14f * s, 12f * s, 20f * s) }, tint, style = stroke)
            Ic.Info -> { drawCircle(tint, 8f * s, Offset(12f * s, 12f * s), style = stroke); drawLine(tint, Offset(12f * s, 11f * s), Offset(12f * s, 16.5f * s), 1.8f * s, StrokeCap.Round); drawCircle(tint, 1.1f * s, Offset(12f * s, 7.6f * s)) }
            Ic.Shield, Ic.Lock -> { drawPath(p { moveTo(12f * s, 3f * s); lineTo(19f * s, 6f * s); lineTo(19f * s, 12f * s); cubicTo(19f * s, 17f * s, 15f * s, 20f * s, 12f * s, 21f * s); cubicTo(9f * s, 20f * s, 5f * s, 17f * s, 5f * s, 12f * s); lineTo(5f * s, 6f * s); close() }, tint, style = stroke) }
            Ic.Link -> { drawRoundRect(tint, Offset(4f * s, 12f * s), Size(9f * s, 5f * s), CornerRadius(2.5f * s), style = stroke); drawRoundRect(tint, Offset(11f * s, 7f * s), Size(9f * s, 5f * s), CornerRadius(2.5f * s), style = stroke) }
            Ic.Sparkle -> drawPath(p { moveTo(12f * s, 3f * s); lineTo(14f * s, 10f * s); lineTo(21f * s, 12f * s); lineTo(14f * s, 14f * s); lineTo(12f * s, 21f * s); lineTo(10f * s, 14f * s); lineTo(3f * s, 12f * s); lineTo(10f * s, 10f * s); close() }, tint, style = stroke)
            Ic.Mic -> { drawRoundRect(tint, Offset(9.5f * s, 3f * s), Size(5f * s, 10f * s), CornerRadius(2.5f * s), style = stroke); drawArc(tint, 180f, 180f, false, topLeft = Offset(6f * s, 8f * s), size = Size(12f * s, 8f * s), style = stroke); drawLine(tint, Offset(12f * s, 17f * s), Offset(12f * s, 21f * s), 1.8f * s, StrokeCap.Round) }
            Ic.Globe -> { drawCircle(tint, 8f * s, Offset(12f * s, 12f * s), style = stroke); drawOval(tint, Offset(8f * s, 4f * s), Size(8f * s, 16f * s), style = stroke); drawLine(tint, Offset(4f * s, 12f * s), Offset(20f * s, 12f * s), 1.4f * s, StrokeCap.Round) }
            Ic.Clock -> { drawCircle(tint, 8f * s, Offset(12f * s, 12f * s), style = stroke); drawLine(tint, Offset(12f * s, 8f * s), Offset(12f * s, 12f * s), 1.8f * s, StrokeCap.Round); drawLine(tint, Offset(12f * s, 12f * s), Offset(15.5f * s, 14f * s), 1.8f * s, StrokeCap.Round) }
            Ic.External -> { drawPath(p { moveTo(14f * s, 4f * s); lineTo(20f * s, 4f * s); lineTo(20f * s, 10f * s) }, tint, style = stroke); drawLine(tint, Offset(20f * s, 4f * s), Offset(11f * s, 13f * s), 1.8f * s, StrokeCap.Round); drawPath(p { moveTo(18f * s, 14f * s); lineTo(18f * s, 20f * s); lineTo(4f * s, 20f * s); lineTo(4f * s, 6f * s); lineTo(10f * s, 6f * s) }, tint, style = stroke) }
            Ic.Update -> { drawArc(tint, -60f, 300f, false, topLeft = Offset(4f * s, 4f * s), size = Size(16f * s, 16f * s), style = stroke); drawPath(p { moveTo(18f * s, 3f * s); lineTo(21f * s, 8f * s); lineTo(15.5f * s, 8f * s) }, tint, style = stroke) }
            Ic.Wifi -> { drawArc(tint, 215f, 110f, false, topLeft = Offset(3f * s, 5f * s), size = Size(18f * s, 18f * s), style = stroke); drawArc(tint, 215f, 110f, false, topLeft = Offset(7f * s, 9f * s), size = Size(10f * s, 10f * s), style = stroke); drawCircle(tint, 1.2f * s, Offset(12f * s, 18f * s)) }
            Ic.Font -> { drawLine(tint, Offset(6f * s, 19f * s), Offset(11f * s, 5f * s), 1.8f * s, StrokeCap.Round); drawLine(tint, Offset(11f * s, 5f * s), Offset(16f * s, 19f * s), 1.8f * s, StrokeCap.Round); drawLine(tint, Offset(8f * s, 13f * s), Offset(14f * s, 13f * s), 1.6f * s, StrokeCap.Round) }
            Ic.Bell -> { drawPath(p { moveTo(6f * s, 17f * s); lineTo(6f * s, 10f * s); cubicTo(6f * s, 5f * s, 18f * s, 5f * s, 18f * s, 10f * s); lineTo(18f * s, 17f * s) }, tint, style = stroke); drawLine(tint, Offset(4f * s, 17f * s), Offset(20f * s, 17f * s), 1.8f * s, StrokeCap.Round); drawCircle(tint, 1.5f * s, Offset(12f * s, 21f * s)) }
            Ic.Trash -> { drawRect(tint, Offset(6f * s, 7f * s), Size(12f * s, 13f * s), style = stroke); drawLine(tint, Offset(4f * s, 7f * s), Offset(20f * s, 7f * s), 1.8f * s, StrokeCap.Round); drawLine(tint, Offset(9.5f * s, 4f * s), Offset(14.5f * s, 4f * s), 1.8f * s, StrokeCap.Round) }
            Ic.Cloud -> { drawCircle(tint, 5f * s, Offset(9f * s, 14f * s), style = stroke); drawCircle(tint, 6f * s, Offset(15f * s, 13f * s), style = stroke); drawLine(tint, Offset(9f * s, 19f * s), Offset(15f * s, 19f * s), 1.8f * s, StrokeCap.Round) }
            Ic.Grid -> { for (i in 0..1) for (j in 0..1) drawRoundRect(tint, Offset((5 + i * 8) * s, (5 + j * 8) * s), Size(6f * s, 6f * s), CornerRadius(1.5f * s), style = stroke) }
            Ic.Headphone -> { drawArc(tint, 180f, 180f, false, topLeft = Offset(4f * s, 5f * s), size = Size(16f * s, 16f * s), style = stroke); drawRoundRect(tint, Offset(4f * s, 13f * s), Size(4f * s, 7f * s), CornerRadius(1.8f * s), style = stroke); drawRoundRect(tint, Offset(16f * s, 13f * s), Size(4f * s, 7f * s), CornerRadius(1.8f * s), style = stroke) }
        }
    }
}

// ---------- 基础交互 ----------
@Composable
fun Pressable(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    Box(modifier.graphicsLayerScale(scale).clickable(src, null, onClick = onClick).semantics { role = androidx.compose.ui.semantics.Role.Button }) { content() }
}

fun Modifier.graphicsLayerScale(scale: Float): Modifier = this.graphicsLayer { scaleX = scale; scaleY = scale }

@Composable
fun ODButton(text: String, onClick: () -> Unit, primary: Boolean = true, enabled: Boolean = true, icon: Ic? = null, modifier: Modifier = Modifier) {
    val bg = when {
        !enabled -> OD.Hairline
        primary -> OD.Ink
        else -> Color.White
    }
    val fg = when {
        !enabled -> OD.Muted
        primary -> Color.White
        else -> OD.Ink
    }
    Pressable(modifier, onClick = { if (enabled) onClick() }) {
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                .then(if (!primary && enabled) Modifier.border(1.dp, OD.Hairline, RoundedCornerShape(14.dp)) else Modifier),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) { OIcon(icon, 16.dp, fg); Spacer(Modifier.width(7.dp)) }
            Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ODToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val t by androidx.compose.animation.core.animateFloatAsState(if (checked) 1f else 0f, label = "toggle")
    Pressable(modifier.semantics {
        role = androidx.compose.ui.semantics.Role.Switch
        stateDescription = if (checked) "已开启" else "已关闭"
    }, { onChange(!checked) }) {
        Canvas(Modifier.width(42.dp).height(24.dp)) {
            val r = size.height / 2
            drawRoundRect(lerp(OD.Hairline, OD.Cyan, t), topLeft = Offset.Zero, size = Size(size.width, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
            drawCircle(Color.White, r - 2.5f, Offset(2.5f + r + (size.width - 2 * r - 5f) * t, r))
        }
    }
}

@Composable
fun ODSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var width by remember { mutableStateOf(1f) }
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Canvas(
        modifier.height(28.dp).fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat() }
            .semantics {
                contentDescription = "滑块"
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, 0)
                setProgress { target -> onChange(target.coerceIn(range.start, range.endInclusive)); true }
            }
            .pointerInput(range) {
                detectTapGestures { off -> onChange((range.start + (range.endInclusive - range.start) * (off.x / size.width).coerceIn(0f, 1f))) }
            }
            .pointerInput(range) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange((range.start + (range.endInclusive - range.start) * (change.position.x / size.width).coerceIn(0f, 1f)))
                }
            }
    ) {
        val cy = size.height / 2
        drawRoundRect(OD.Hairline, topLeft = Offset(0f, cy - 2f), size = Size(size.width, 4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
        drawRoundRect(OD.Cyan, topLeft = Offset(0f, cy - 2f), size = Size(size.width * fraction, 4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
        drawCircle(Color.White, 9.dp.toPx(), Offset(size.width * fraction, cy))
        drawCircle(OD.Cyan, 9.dp.toPx(), Offset(size.width * fraction, cy), style = Stroke(2.dp.toPx()))
    }
}

@Composable
fun ODField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, leading: Ic? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().height(46.dp).liquidGlass(RoundedCornerShape(13.dp), Color.White.copy(alpha = .10f))
            .border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(13.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) { OIcon(leading, 16.dp, OD.Muted); Spacer(Modifier.width(8.dp)) }
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(fontSize = 13.5.sp, color = OD.Ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, style = OD.BodySm.copy(color = OD.Muted)) else inner() }
        )
        if (trailing != null) trailing()
    }
}

@Composable
fun GlassNav(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(24.dp), Color.White.copy(alpha = .10f))
            .border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

@Composable
fun Sheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!visible) return
    BackHandler { onDismiss() }
    val sheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Box(Modifier.fillMaxSize()) {
        // 遮罩单独一层，弹窗内容不是遮罩的子节点：在弹窗内点击/长按不会误关
        Box(Modifier.fillMaxSize().background(Color(0x73000000)).clickable(onClickLabel = "关闭弹窗", onClick = onDismiss))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding()
                .clip(sheetShape).background(Color(0xFFFEFEFE)).border(1.dp, Color(0x66FFFFFF), sheetShape)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp)
        ) { Column { content() } }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(52.dp).liquidGlass(RoundedCornerShape(16.dp), Color.White.copy(alpha = .20f)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(Modifier.size(36.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { OIcon(Ic.Back, 20.dp, OD.Ink) }
            Spacer(Modifier.width(2.dp))
        }
        Text(title, style = OD.H2, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun SectionHead(index: Int, title: String, accent: Color = OD.Cyan, trailing: String? = null, onMore: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Text("%02d".format(index), style = OD.Micro.copy(color = accent, fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = OD.H3)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Pressable(onClick = { onMore?.invoke() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(trailing, style = OD.Cap.copy(color = accent)); Spacer(Modifier.width(2.dp)); OIcon(Ic.Chevron, 10.dp, accent)
                }
            }
        }
    }
}

@Composable
fun DialogCard(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!visible) return
    BackHandler { onDismiss() }
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Color(0x73000000)).clickable(onClickLabel = "关闭弹窗", onClick = onDismiss))
        Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp).navigationBarsPadding().imePadding().clip(shape).background(Color(0xFFFEFEFE)).border(1.dp, Color(0x66FFFFFF), shape)
            .pointerInput(Unit) { detectTapGestures { } }.padding(20.dp)) {
            Column { content() }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean = false, accent: Color = OD.Cyan, onClick: (() -> Unit)? = null) {
    Pressable(onClick = { onClick?.invoke() }) {
        Box(Modifier.semantics { stateDescription = if (selected) "已选中" else "未选中" }.clip(RoundedCornerShape(50)).background(if (selected) OD.Ink else Color.White).border(1.dp, if (selected) OD.Ink else OD.Hairline, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(text, style = OD.Cap.copy(color = if (selected) Color.White else OD.InkSoft, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
        }
    }
}

@Composable
fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = OD.H3); Spacer(Modifier.height(2.dp)); Text(desc, style = OD.Cap) }
        ODToggle(checked, onChange)
    }
}

@Composable
fun LinkText(text: String, color: Color = OD.CyanDeep, onClick: () -> Unit, style: TextStyle = OD.Cap) {
    Text(text, style = style.copy(color = color, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), modifier = Modifier.clickable(onClick = onClick))
}

@Composable
fun Handwriting(text: String, progress: Float, size: androidx.compose.ui.unit.TextUnit = 92.sp, color: Color = OD.Ink) {
    val chars = text.toList()
    val n = chars.size.coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        chars.forEachIndexed { i, ch ->
            val p = (progress * n - i).coerceIn(0f, 1f)
            Text(
                ch.toString(),
                style = TextStyle(fontFamily = HandFont, fontSize = size, color = color, fontWeight = FontWeight.Medium),
                modifier = Modifier.graphicsLayer {
                    alpha = p
                    translationY = (1f - p) * 9.dp.toPx()
                    rotationZ = (1f - p) * -7f
                }
            )
        }
    }
}

/** 平板宽屏左侧导航栏；手机/窄窗口不显示。 */
@Composable
fun TabletRail(current: Route, onNavigate: (Route) -> Unit) {
    val items = listOf(
        Triple(Ic.Home, "首页", Route.Home),
        Triple(Ic.Search, "查词", Route.SearchInput),
        Triple(Ic.Headphone, "学习", Route.Learning),
        Triple(Ic.Grid, "我的", Route.Profile),
        Triple(Ic.Settings, "设置", Route.Settings),
    )
    Column(
        Modifier.width(82.dp).fillMaxHeight().background(OD.Pearl.copy(alpha = .92f)).padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(OD.CyanSoft), contentAlignment = Alignment.Center) {
            OIcon(Ic.Book, 20.dp, OD.CyanDeep)
        }
        Spacer(Modifier.height(18.dp))
        items.forEach { (icon, label, route) ->
            val selected = when (route) {
                is Route.Home -> current is Route.Home
                is Route.SearchInput -> current is Route.SearchInput || current is Route.Search
                is Route.Learning -> current is Route.Learning
                is Route.Profile -> current is Route.Profile
                is Route.Settings -> current is Route.Settings
                else -> false
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).clickable { onNavigate(route) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.height(28.dp).width(44.dp).clip(RoundedCornerShape(9.dp)).background(if (selected) OD.CyanSoft.copy(alpha = .9f) else Color.Transparent), contentAlignment = Alignment.Center) {
                    OIcon(icon, 19.dp, if (selected) OD.CyanDeep else OD.Muted)
                }
                Spacer(Modifier.height(2.dp))
                Text(label, style = OD.Micro.copy(fontSize = 9.sp, color = if (selected) OD.CyanDeep else OD.Muted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
            }
        }
        Spacer(Modifier.weight(1f))
        Text("OD", style = OD.Micro.copy(fontSize = 8.sp, color = OD.Muted))
    }
}
