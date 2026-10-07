// GLTU 课表 App —— 日历同步的日期映射测试
//
// 覆盖调休（补课）带来的三类修正：
//   ① 课被搬到调休日（10/8 周三 → 10/11 周六）
//   ② 名义日期本身是调休上班日 → 这节课不上（本来就排在周六的课，撞上补周三的那天）
//   ③ 名义日期放假且没有补课安排 → 这节课不上（10/1 周四）
package com.ltyksa.gltuschedule.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarClassDatesTest {

    /** 2025 秋季学期第一周周一（用于让 10/8 落在第 6 周）。 */
    private val firstMonday: LocalDate = LocalDate.parse("2025-09-01")

    /** 国庆：10/1–10/8 放假，9/28 与 10/11 调休上班；9/28 补 10/7、10/11 补 10/8。 */
    private val nationalDayOff: Set<LocalDate> = (0..7)
        .map { LocalDate.parse("2025-10-01").plusDays(it.toLong()) }
        .toSet()
    private val makeupDays: Set<LocalDate> = setOf(
        LocalDate.parse("2025-09-28"),
        LocalDate.parse("2025-10-11"),
    )
    private val sourceToMakeup: Map<LocalDate, LocalDate> = mapOf(
        LocalDate.parse("2025-10-07") to LocalDate.parse("2025-09-28"),
        LocalDate.parse("2025-10-08") to LocalDate.parse("2025-10-11"),
    )
    private val isBlocked: (LocalDate) -> Boolean = { it in nationalDayOff }

    // ---------------- ① 课被搬到调休日 ----------------

    @Test
    fun `第6周周三的课被搬到10月11日周六`() {
        // 2025-09-01 是周一 → 第 6 周周一 = 10/6，周三 = 10/8
        val out = CalendarSync.classDatesOf(
            firstMonday, listOf(6), DayOfWeek.WEDNESDAY, sourceToMakeup, makeupDays, isBlocked,
        )
        assertEquals(1, out.size)
        assertEquals(LocalDate.parse("2025-10-11"), out[0].first)
        assertTrue("应标记为调休补课", out[0].second)
    }

    @Test
    fun `第6周周二的课被搬到9月28日周日`() {
        // 第 6 周周二 = 10/7
        val out = CalendarSync.classDatesOf(
            firstMonday, listOf(6), DayOfWeek.TUESDAY, sourceToMakeup, makeupDays, isBlocked,
        )
        assertEquals(1, out.size)
        assertEquals(LocalDate.parse("2025-09-28"), out[0].first)
        assertTrue(out[0].second)
    }

    @Test
    fun `调休日可以早于被补的那一天`() {
        val out = CalendarSync.classDatesOf(
            firstMonday, listOf(6), DayOfWeek.TUESDAY, sourceToMakeup, makeupDays, isBlocked,
        )
        assertTrue("9/28 应早于 10/7", out[0].first.isBefore(LocalDate.parse("2025-10-07")))
    }

    // ---------------- ② 名义日期本身是调休上班日 ----------------

    @Test
    fun `本来就排在周六的课遇到补班日不上`() {
        // 第 6 周周六 = 10/11，那天是调休上班日、按周三的课表走 → 周六的课停一次
        val out = CalendarSync.classDatesOf(
            firstMonday, listOf(6), DayOfWeek.SATURDAY, sourceToMakeup, makeupDays, isBlocked,
        )
        assertTrue("补班日的周六课应被剔除", out.isEmpty())
    }

    @Test
    fun `周日的课在国庆期间停课`() {
        // 第 6 周周日 = 10/12，不在放假区间也不在调休日 → 正常
        val out = CalendarSync.classDatesOf(
            firstMonday, listOf(6), DayOfWeek.SUNDAY, sourceToMakeup, makeupDays, isBlocked,
        )
        assertEquals(1, out.size)
        assertEquals(LocalDate.parse("2025-10-12"), out[0].first)
        assertFalse(out[0].second)
    }

    // ---------------- ③ 放假且无补课安排 ----------------

    @Test
    fun `第5周周四10月1日放假停课`() {
        // 第 5 周周一 = 9/29，周四 = 10/2；10/1 是周三
        val out = CalendarSync.classDatesOf(
            firstMonday, listOf(5), DayOfWeek.WEDNESDAY, sourceToMakeup, makeupDays, isBlocked,
        )
        // 10/1 在放假区间里，且不是任何调休规则的源日期 → 停课
        assertTrue("10/1 放假又无补课安排，应停课", out.isEmpty())
    }

    @Test
    fun `第5周周二的课9月30日照常上`() {
        // 9/30 不在放假区间（10/1 起放假）
        val out = CalendarSync.classDatesOf(
            firstMonday, listOf(5), DayOfWeek.TUESDAY, sourceToMakeup, makeupDays, isBlocked,
        )
        assertEquals(1, out.size)
        assertEquals(LocalDate.parse("2025-09-30"), out[0].first)
        assertFalse(out[0].second)
    }

    // ---------------- 不受影响的周次 ----------------

    @Test
    fun `调休前几周完全不受影响`() {
        val out = CalendarSync.classDatesOf(
            firstMonday, listOf(1, 2, 3, 4), DayOfWeek.WEDNESDAY, sourceToMakeup, makeupDays, isBlocked,
        )
        assertEquals("1-4 周应原样保留", 4, out.size)
        assertTrue("都不应标记调休", out.none { it.second })
        assertEquals(LocalDate.parse("2025-09-03"), out[0].first)
    }

    @Test
    fun `受影响的周次会少一条`() {
        // 第 6 周周六撞调休 → 被剔除，所以结果比入参短
        val weeks = listOf(5, 6, 7)
        val out = CalendarSync.classDatesOf(
            firstMonday, weeks, DayOfWeek.SATURDAY, sourceToMakeup, makeupDays, isBlocked,
        )
        assertTrue("应少于 3 条", out.size < weeks.size)
    }

    // ---------------- 没有调休数据时的退化行为 ----------------

    @Test
    fun `没有调休数据时退化为原样输出`() {
        val out = CalendarSync.classDatesOf(
            firstMonday, listOf(1, 2, 3), DayOfWeek.MONDAY, emptyMap(), emptySet(),
        ) { false }
        assertEquals(3, out.size)
        assertEquals(LocalDate.parse("2025-09-01"), out[0].first)
        assertEquals(LocalDate.parse("2025-09-15"), out[2].first)
        assertTrue(out.none { it.second })
    }

    @Test
    fun `同一周多次调用结果稳定`() {
        val a = CalendarSync.classDatesOf(
            firstMonday, listOf(6), DayOfWeek.WEDNESDAY, sourceToMakeup, makeupDays, isBlocked,
        )
        val b = CalendarSync.classDatesOf(
            firstMonday, listOf(6), DayOfWeek.WEDNESDAY, sourceToMakeup, makeupDays, isBlocked,
        )
        assertEquals(a, b)
    }

    @Test
    fun `周次为空时返回空`() {
        val out = CalendarSync.classDatesOf(
            firstMonday, emptyList(), DayOfWeek.MONDAY, sourceToMakeup, makeupDays, isBlocked,
        )
        assertTrue(out.isEmpty())
    }
}
