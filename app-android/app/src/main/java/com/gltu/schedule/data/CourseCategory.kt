// GLTU 课表 App —— 课程颜色分类
package com.gltu.schedule.data

import androidx.compose.ui.graphics.Color

/**
 * 课程分类。用于"给每个课程按颜色分类"。
 * 支持：按课程属性自动归类（理论/实验/体育/公共课/专业课/实践…），
 * 也支持用户自定义分类。
 */
enum class CourseCategory(val displayName: String) {
    THEORY("理论课"),
    LAB("实验/实践"),
    SPORT("体育"),
    PUBLIC("公共课"),
    MAJOR("专业课"),
    ELECTION("选修课"),
    OTHER("其他"),
}

/**
 * 分类 → 颜色映射（Material 3 柔和配色，深/浅模式都清晰）。
 * 使用 0xFF 不透明 ARGB；主题层可再通过 alpha 微调。
 */
object CoursePalette {
    private val colors = mapOf(
        CourseCategory.THEORY to Color(0xFF4E6E9E),   // 蓝
        CourseCategory.LAB to Color(0xFF2E8B6E),      // 绿
        CourseCategory.SPORT to Color(0xFFD97706),    // 琥珀
        CourseCategory.PUBLIC to Color(0xFF7C5CBF),   // 紫
        CourseCategory.MAJOR to Color(0xFFC24444),    // 红
        CourseCategory.ELECTION to Color(0xFF0E8A8A), // 青
        CourseCategory.OTHER to Color(0xFF5B6472),    // 灰蓝
    )

    /** 每个课程必须有确定颜色：默认 OTHER。 */
    fun colorOf(category: CourseCategory): Color =
        colors[category] ?: colors.getValue(CourseCategory.OTHER)

    /** 从课程名/属性推断分类（导入时用，可按关键字扩展）。 */
    fun inferCategory(courseName: String, location: String = ""): CourseCategory {
        val n = courseName
        return when {
            n.contains("实验") || n.contains("实训") || n.contains("实践") ||
                n.contains("上机") || n.contains("课程设计") || n.contains("实习") ->
                CourseCategory.LAB
            n.contains("体育") || n.contains("游泳") || n.contains("健美") || n.contains("武术") ->
                CourseCategory.SPORT
            n.contains("大学英语") || n.contains("高等数学") || n.contains("思想道德") ||
                n.contains("形势与政策") || n.contains("心理健康") || n.contains("创新创业") ||
                n.contains("职业生涯") || n.contains("计算机基础") || n.contains("军事理论") ->
                CourseCategory.PUBLIC
            n.contains("选修") -> CourseCategory.ELECTION
            else -> CourseCategory.MAJOR
        }
    }
}
