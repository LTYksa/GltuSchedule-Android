// GLTU 课表 App —— 节假日数据（本地内置，全程本地运行，无网络）
package com.ltyksa.gltuschedule.data

import java.time.LocalDate

/**
 * 节假日（用于"节假日屏蔽课程"功能）。
 * [start] / [end] 为闭区间日期；[type] 用于分类显示。
 */
data class Holiday(
    val name: String,
    val start: LocalDate,
    val end: LocalDate,
    val type: HolidayType,
) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

enum class HolidayType(val label: String) {
    STATUTORY("法定节假日"),
    SCHOOL("学校假期"),
    MAKEUP("补班日"),
    USER_DEFINED("自定义"),
}

/**
 * 内置节假日数据（离线兜底用；联网同步成功后会被权威数据覆盖）。
 * 数据来源：
 * 1. **中国大陆法定节假日**（元旦 / 春节 / 清明节 / 劳动节 / 端午节 / 中秋节 / 国庆节，
 *    国务院办公厅发布，含调休后的实际放假区间）；
 * 2. 桂林旅游学院校历标注的学校假期（校运会、寒暑假等）。
 *
 * ⚠️ 本表只包含中国大陆的节假日，不含任何其他国家或地区的节日。
 *
 * 说明：调休周末（补班日）的"上哪天的课"由学校教务另行安排，
 * 本表只负责"哪些日期不上课"。日期后续可持续维护。
 */
object GltuHolidays {
    // 2026-2027 学年（GLTU 校历 OCR）
    val schoolHolidays: List<Holiday> = listOf(
        // 校运会
        Holiday("校运会", LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 31), HolidayType.SCHOOL),
        // 寒假（第一学期结束~第二学期报到前；以校历"寒假5周"估算，2027-02-06 春节）
        Holiday("寒假", LocalDate.of(2027, 1, 18), LocalDate.of(2027, 2, 28), HolidayType.SCHOOL),
        // 暑假（第二学期结束起，9 周）
        Holiday("暑假", LocalDate.of(2027, 7, 5), LocalDate.of(2027, 9, 5), HolidayType.SCHOOL),
    )

    // 中国法定节假日（2026-2027 学年内，公开安排；无国务院精确文件时按惯例估算，标注 estimate）
    val statutoryHolidays: List<Holiday> = listOf(
        Holiday("国庆节", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), HolidayType.STATUTORY), // estimate
        Holiday("中秋节", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 25), HolidayType.STATUTORY), // estimate，2026 中秋为 9/25
        Holiday("元旦", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3), HolidayType.STATUTORY), // estimate
        Holiday("春节", LocalDate.of(2027, 2, 6), LocalDate.of(2027, 2, 12), HolidayType.STATUTORY), // estimate
        Holiday("清明节", LocalDate.of(2027, 4, 5), LocalDate.of(2027, 4, 7), HolidayType.STATUTORY), // estimate
        Holiday("劳动节", LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 5), HolidayType.STATUTORY), // estimate
        Holiday("端午节", LocalDate.of(2027, 6, 9), LocalDate.of(2027, 6, 11), HolidayType.STATUTORY), // estimate
    )

    val all: List<Holiday> = schoolHolidays + statutoryHolidays

    fun isHoliday(date: LocalDate): Boolean = all.any { it.contains(date) }
}
