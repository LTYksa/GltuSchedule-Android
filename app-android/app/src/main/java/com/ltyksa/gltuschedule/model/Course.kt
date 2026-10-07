// GLTU 课表 App —— 课程条目领域模型（纯 Kotlin，与存储层解耦）
package com.ltyksa.gltuschedule.model

import com.ltyksa.gltuschedule.data.CourseCategory
import java.time.DayOfWeek

/**
 * 一门课的完整描述。
 * [dayOfWeek] 周几；[startIndex]/[endIndex] 起始/结束节次序号（对应 GltuTimeTable.index）。
 * [weeks] 是**显式周次集合**，用于表达教务系统里不规则的上课周次
 * （如 GLTU 的 `4-5周,8周,11-13周(单),14-16周`），非空时优先于 [startWeek]/[endWeek]/[oddEven]。
 * [startWeek]/[endWeek]/[oddEven] 作为后备（0=每周 1=单周 2=双周）。
 */
data class Course(
    val id: Long = 0,
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: DayOfWeek,
    val startIndex: Int,
    val endIndex: Int,
    val startWeek: Int,
    val endWeek: Int,
    val oddEven: Int = 0,
    val category: CourseCategory = CourseCategory.OTHER,
    val credit: Double? = null,
    val weeksText: String = "",   // 原始周次文本（如 "4-5周,8周,11-13周(单)"）
    val weeks: List<Int> = emptyList(), // 解析后的显式周次集合（升序、去重）
    val raw: String = "",         // 原始记录（调试用）
) {
    /** 该课程覆盖的节次序号列表。 */
    val slotIndices: IntRange get() = startIndex..endIndex

    fun occursOnWeek(week: Int): Boolean {
        if (weeks.isNotEmpty()) return weeks.contains(week)
        if (week < startWeek || week > endWeek) return false
        return when (oddEven) {
            1 -> week % 2 == 1
            2 -> week % 2 == 0
            else -> true
        }
    }

    /** 用于显示：优先原始周次文本，否则用区间拼一个。 */
    val displayWeeks: String
        get() = when {
            weeksText.isNotBlank() -> weeksText
            weeks.isNotEmpty() -> weeks.joinToString(",") + "周"
            oddEven == 1 -> "${startWeek}-${endWeek}周(单)"
            oddEven == 2 -> "${startWeek}-${endWeek}周(双)"
            else -> "${startWeek}-${endWeek}周"
        }
}
