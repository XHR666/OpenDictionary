package io.github.xhr666.opendictionary.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/** 全局界面偏好：字号缩放等需要实时影响 Compose 的状态。 */
object UiState {
    var fontScale by mutableFloatStateOf(1f)
        private set
    var reduceMotion by androidx.compose.runtime.mutableStateOf(false)
        private set
    var customFontFamily by androidx.compose.runtime.mutableStateOf<FontFamily?>(null)
        private set
    /** 0=无动画，1=减弱，2=完整 */
    var animationLevel by androidx.compose.runtime.mutableIntStateOf(2)
        private set
    /** 0=无玻璃，1=仅模糊，2=完整玻璃 */
    var glassMode by androidx.compose.runtime.mutableIntStateOf(2)
        private set
    /** 0=青瓷，1=雾蓝，2=苔绿，3=石墨 */
    var themePalette by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences("od_prefs", Context.MODE_PRIVATE)
        fontScale = p.getFloat("font_scale", 1f).coerceIn(0.8f, 2f)
        reduceMotion = p.getBoolean("reduce_motion", false)
        animationLevel = p.getInt("anim_level", if (reduceMotion) 1 else 2).coerceIn(0, 2)
        glassMode = p.getInt("ui_glass_mode", if (p.getBoolean("ui_glass", true)) 2 else 0).coerceIn(0, 2)
        themePalette = p.getInt("theme_palette", 0).coerceIn(0, 3)
        OD.applyPalette(themePalette)
        customFontFamily = loadFamily(p.getString("custom_font_path", null))
    }

    fun reloadFont(context: Context) {
        customFontFamily = loadFamily(context.getSharedPreferences("od_prefs", Context.MODE_PRIVATE).getString("custom_font_path", null))
    }

    private fun loadFamily(path: String?): FontFamily? {
        if (path.isNullOrBlank()) return null
        val f = java.io.File(path)
        if (!f.exists()) return null
        // 先做同步校验，损坏/非字体文件不能进入 Compose（否则可能首次排版崩溃且无法恢复）
        val typeface = runCatching { android.graphics.Typeface.createFromFile(f) }.getOrNull() ?: run { f.delete(); return null }
        if (typeface == null) return null
        return runCatching { FontFamily(Font(f)) }.getOrNull()
    }

    fun setFontScale(context: Context, value: Float) {
        fontScale = value.coerceIn(0.8f, 2f)
        context.getSharedPreferences("od_prefs", Context.MODE_PRIVATE).edit().putFloat("font_scale", fontScale).apply()
    }

    fun setThemePalette(context: Context, value: Int) {
        themePalette = value.coerceIn(0, 3)
        OD.applyPalette(themePalette)
        context.getSharedPreferences("od_prefs", Context.MODE_PRIVATE).edit().putInt("theme_palette", themePalette).apply()
    }

    fun setAnimationLevel(context: Context, value: Int) {
        animationLevel = value.coerceIn(0, 2)
        context.getSharedPreferences("od_prefs", Context.MODE_PRIVATE).edit().putInt("anim_level", animationLevel).apply()
    }

    fun setGlassMode(context: Context, value: Int) {
        glassMode = value.coerceIn(0, 2)
        context.getSharedPreferences("od_prefs", Context.MODE_PRIVATE).edit().putInt("ui_glass_mode", glassMode).apply()
    }

    fun setReduceMotion(context: Context, value: Boolean) {
        reduceMotion = value
        context.getSharedPreferences("od_prefs", Context.MODE_PRIVATE).edit().putBoolean("reduce_motion", value).apply()
    }
}
