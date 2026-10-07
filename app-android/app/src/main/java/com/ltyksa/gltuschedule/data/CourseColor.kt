// GLTU 课表 App —— 课程颜色（超级课程表式：同名同色、不同名尽量不同色）
package com.ltyksa.gltuschedule.data

import androidx.compose.ui.graphics.Color

/**
 * 课程配色（超级课程表风格）：
 * - 同一门课（课程名相同）永远用同一个颜色；
 * - 不同课程通过课程名 hash 从调色板取色，尽量错开。
 * 这样即使不导入教务系统、手动添加课程，也能自动得到稳定、可区分的颜色。
 */
object CourseColor {

    /** 超级课程表式柔和调色板（16 色，深浅模式下都清晰）。 */
    private val palette = listOf(
        Color(0xFFE57373), // 红
        Color(0xFFF06292), // 粉
        Color(0xFFBA68C8), // 紫
        Color(0xFF9575CD), // 深紫
        Color(0xFF7986CB), // 靛
        Color(0xFF64B5F6), // 蓝
        Color(0xFF4FC3F7), // 天蓝
        Color(0xFF4DD0E1), // 青
        Color(0xFF4DB6AC), // 蓝绿
        Color(0xFF81C784), // 绿
        Color(0xFFAED581), // 浅绿
        Color(0xFFDCE775), // 黄绿
        Color(0xFFFFD54F), // 黄
        Color(0xFFFFB74D), // 橙
        Color(0xFFFF8A65), // 深橙
        Color(0xFFA1887F), // 棕
    )

    /** 按课程名稳定取色（同名永远同色）。 */
    fun of(courseName: String): Color {
        if (courseName.isBlank()) return palette[0]
        val h = courseName.hashCode()
        return palette[((h % palette.size) + palette.size) % palette.size]
    }

    /** 格子背景色（柔和，保证文字可读）。 */
    fun background(courseName: String): Color = of(courseName).copy(alpha = 0.28f)

    /** 格子左侧色条 / 强调色。 */
    fun accent(courseName: String): Color = of(courseName)

    /** 用于格子内文字的深色版本（在浅色背景上可读）。 */
    fun textColor(courseName: String): Color = of(courseName).copy(alpha = 1f)
}
