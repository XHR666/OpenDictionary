package io.github.xhr666.opendictionary.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

/**
 * 悬浮液态玻璃导航条：独立胶囊、半透明分层、柔光/亮边/折射高光。
 * 四个主页面共用；内容在下方滚动时透过玻璃层被弱化。
 */
@Composable
fun FloatingGlassDock(current: Int, backdrop: Backdrop? = null, onNavigate: (Route) -> Unit) {
    val glassMode = UiState.glassMode
    val animLevel = UiState.animationLevel
    val items = listOf(Ic.Home to "首页", Ic.Book to "词典", Ic.Headphone to "学习", Ic.Grid to "我的")
    val shape = RoundedCornerShape(34.dp)
    val base = Modifier
        .fillMaxWidth()
        .padding(horizontal = 26.dp)
        .padding(bottom = 10.dp)
        .navigationBarsPadding()
        .height(66.dp)
        .shadow(22.dp, shape, ambientColor = Color(0x332B3236), spotColor = Color(0x442B3236))
    val glass = if (backdrop != null && glassMode >= 1) {
        base.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(saturation = 1.7f)
                blur(if (glassMode >= 2) 28.dp.toPx() else 20.dp.toPx())
                if (glassMode >= 2) lens(10.dp.toPx(), 4.dp.toPx())
            },
            highlight = { Highlight(width = 1f.dp, blurRadius = .5f.dp, alpha = .5f, style = HighlightStyle.Plain) },
            shadow = { null },
            onDrawSurface = { drawRect(Color.White.copy(alpha = if (glassMode >= 2) 0.10f else 0.24f)) }
        )
    } else {
        base.clip(shape).background(Color(0xFAFFFFFF)).border(1.dp, Color(0xE6FFFFFF), shape)
    }
    Box(glass.padding(horizontal = 8.dp)) {
        // 选中项的青色光晕以 tab 中心为圆心，向外扩散到相邻 tab（玻璃内发光）
        Row(
            Modifier.fillMaxWidth().align(Alignment.Center).drawWithContent {
                if (items.isNotEmpty()) {
                    val cell = size.width / items.size
                    val cx = cell * (current + .5f)
                    val brush = Brush.radialGradient(
                        listOf(OD.Cyan.copy(alpha = .26f), OD.Cyan.copy(alpha = .08f), Color.Transparent),
                        center = Offset(cx, size.height / 2f),
                        radius = cell * 0.95f
                    )
                    drawCircle(brush, radius = cell * 0.95f, center = Offset(cx, size.height / 2f))
                }
                drawContent()
            },
            verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, (icon, label) ->
                val selected = i == current
                val colorSpec: androidx.compose.animation.core.AnimationSpec<Color> = when (animLevel) {
                    0 -> androidx.compose.animation.core.tween(0)
                    1 -> androidx.compose.animation.core.tween(120)
                    else -> spring(dampingRatio = .72f)
                }
                val dpSpec: androidx.compose.animation.core.AnimationSpec<androidx.compose.ui.unit.Dp> = when (animLevel) {
                    0 -> androidx.compose.animation.core.tween(0)
                    1 -> androidx.compose.animation.core.tween(120)
                    else -> spring(dampingRatio = .72f)
                }
                val iconColor by animateColorAsState(if (selected) OD.CyanDeep else OD.Muted, animationSpec = colorSpec, label = "dockIcon")
                val pillWidth by animateDpAsState(if (selected) 46.dp else 0.dp, animationSpec = dpSpec, label = "dockPill")
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Pressable(onClick = {
                        when (i) {
                            0 -> onNavigate(Route.Home)
                            1 -> onNavigate(Route.Search(""))
                            2 -> onNavigate(Route.Learning)
                            3 -> onNavigate(Route.Profile)
                        }
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.height(26.dp).width(46.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.height(24.dp).width(pillWidth).clip(RoundedCornerShape(12.dp)).background(OD.CyanSoft.copy(alpha = .85f)))
                                OIcon(icon, 20.dp, iconColor, contentDescription = label)
                            }
                            Spacer(Modifier.height(1.dp))
                            Text(label, style = OD.Micro.copy(fontSize = 9.sp, color = iconColor, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
                        }
                    }
                }
            }
        }
    }
}