// GLTU 课表 App —— 小部件的时间规则单测
package com.ltyksa.gltuschedule.widget

import com.ltyksa.gltuschedule.data.CourseCategory
import com.ltyksa.gltuschedule.model.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetTimingTest {

    private fun course(start: Int, end: Int) = Course(
        id = 0,
        name = "测试课",
        teacher = "",
        location = "",
        dayOfWeek = DayOfWeek.MONDAY,
        startIndex = start,
        endIndex = end,
        startWeek = 1,
        endWeek = 16,
        oddEven = 0,
        category = CourseCategory.MAJOR,
        credit = null,
        weeksText = "1-16周",
        weeks = (1..16).toList(),
        raw = "",
    )

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min)

    // ---------------- 23:30 切换到明天 ----------------

    @Test
    fun `23-29 还算今天`() {
        val now = at(2026, 10, 8, 23, 29)
        assertEquals(LocalDate.of(2026, 10, 8), WidgetTiming.displayDate(now))
    }

    @Test
    fun `23-30 整就算明天`() {
        val now = at(2026, 10, 8, 23, 30)
        assertEquals(LocalDate.of(2026, 10, 9), WidgetTiming.displayDate(now))
    }

    @Test
    fun `23-31 是明天`() {
        val now = at(2026, 10, 8, 23, 31)
        assertEquals(LocalDate.of(2026, 10, 9), WidgetTiming.displayDate(now))
    }

    @Test
    fun `零点之后又变回今天`() {
        val now = at(2026, 10, 9, 0, 5)
        assertEquals(LocalDate.of(2026, 10, 9), WidgetTiming.displayDate(now))
    }

    @Test
    fun `23-30 跨月`() {
        val now = at(2026, 10, 31, 23, 45)
        assertEquals(LocalDate.of(2026, 11, 1), WidgetTiming.displayDate(now))
    }

    @Test
    fun `23-30 跨年`() {
        val now = at(2026, 12, 31, 23, 45)
        assertEquals(LocalDate.of(2027, 1, 1), WidgetTiming.displayDate(now))
    }

    @Test
    fun `早上和下午都是今天`() {
        assertEquals(
            LocalDate.of(2026, 10, 8),
            WidgetTiming.displayDate(at(2026, 10, 8, 7, 0)),
        )
        assertEquals(
            LocalDate.of(2026, 10, 8),
            WidgetTiming.displayDate(at(2026, 10, 8, 18, 30)),
        )
    }

    // ---------------- 最后一节课下课 ----------------

    @Test
    fun `没到最后一节下课不算上完`() {
        // 第13节 20:50-21:30
        val now = at(2026, 10, 8, 21, 0)
        assertFalse(WidgetTiming.allDone(now, listOf(course(1, 2), course(11, 13))))
    }

    @Test
    fun `正好到最后一节下课就算上完`() {
        val now = at(2026, 10, 8, 21, 30)
        assertTrue(WidgetTiming.allDone(now, listOf(course(1, 2), course(11, 13))))
    }

    @Test
    fun `过了最后一节下课算上完`() {
        val now = at(2026, 10, 8, 22, 10)
        assertTrue(WidgetTiming.allDone(now, listOf(course(1, 2), course(11, 13))))
    }

    @Test
    fun `只看到中间某节课的下课时间不算上完`() {
        // 只有第 1-2 节（09:40 下课）时，10:00 就该算上完
        val now = at(2026, 10, 8, 10, 0)
        assertTrue(WidgetTiming.allDone(now, listOf(course(1, 2))))
        // 但如果一天里有晚上的课，10:00 就不算上完
        assertFalse(WidgetTiming.allDone(now, listOf(course(1, 2), course(10, 11))))
    }

    @Test
    fun `没有课时不算上完`() {
        assertFalse(WidgetTiming.allDone(at(2026, 10, 8, 22, 0), emptyList()))
    }

    // ---------------- 下一次翻转时刻 ----------------

    @Test
    fun `白天时下一次翻转是当天 23-30`() {
        val next = WidgetTiming.nextFlipMillis(at(2026, 10, 8, 9, 0))
        val t = java.time.Instant.ofEpochMilli(next)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        assertEquals(LocalDateTime.of(2026, 10, 8, 23, 30), t)
    }

    @Test
    fun `过了 23-30 之后下一次翻转是第二天 23-30`() {
        val next = WidgetTiming.nextFlipMillis(at(2026, 10, 8, 23, 45))
        val t = java.time.Instant.ofEpochMilli(next)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        assertEquals(LocalDateTime.of(2026, 10, 9, 23, 30), t)
    }

    @Test
    fun `正好 23-30 时下一次翻转是第二天`() {
        val next = WidgetTiming.nextFlipMillis(at(2026, 10, 8, 23, 30))
        val t = java.time.Instant.ofEpochMilli(next)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        assertEquals(LocalDateTime.of(2026, 10, 9, 23, 30), t)
    }
}
