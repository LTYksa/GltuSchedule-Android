package com.ltyksa.gltuschedule.notification

import com.ltyksa.gltuschedule.model.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 提醒闹钟「请求码」测试。
 *
 * 回归防护：`PendingIntent` 的匹配用 `Intent.filterEquals`，它**不比较 extras**。
 * 如果请求码只有「日期 + 提前分钟数」而不含课程，同一天同一档提醒的多门课会共用
 * 同一个 PendingIntent，后一节课的闹钟会把前一节顶掉 —— 表现为"一天只有最后一节课会响"。
 */
class ReminderRequestCodeTest {

    private val day = LocalDate.of(2026, 11, 3) // 周二

    private fun course(id: Long, name: String = "课程$id", start: Int = 1) = Course(
        id = id,
        name = name,
        teacher = "老师",
        location = "3220",
        dayOfWeek = DayOfWeek.TUESDAY,
        startIndex = start,
        endIndex = start,
        startWeek = 1,
        endWeek = 16,
    )

    private fun code(date: LocalDate, minutes: Int, c: Course) =
        ClassReminderScheduler.requestCode(date, minutes, ClassReminderScheduler.courseSlot(c))

    @Test
    fun `同一天同一档提醒-不同课程必须得到不同请求码`() {
        // 这正是修复前的 bug：三门课 + 提前 30 分钟会得到同一个请求码
        val codes = listOf(
            code(day, 30, course(1, "高数", 1)),
            code(day, 30, course(2, "英语", 3)),
            code(day, 30, course(3, "体育", 6)),
        )
        assertEquals("三门课的请求码必须互不相同", 3, codes.toSet().size)
    }

    @Test
    fun `同一天多门课多档提醒-请求码两两不同`() {
        val courses = (1L..8L).map { course(it, "课程$it", (it % 10).toInt() + 1) }
        val all = ArrayList<Int>()
        for (c in courses) for (m in listOf(30, 10)) all.add(code(day, m, c))
        assertEquals("8 门课 × 2 档 = 16 个互不相同的请求码", 16, all.toSet().size)
    }

    @Test
    fun `同一课程不同提前分钟数请求码不同`() {
        val c = course(5)
        val a = code(day, 30, c)
        val b = code(day, 10, c)
        assertTrue(a != b)
    }

    @Test
    fun `不同日期请求码不同`() {
        val c = course(5)
        val a = code(LocalDate.of(2026, 11, 3), 30, c)
        val b = code(LocalDate.of(2026, 11, 4), 30, c)
        assertTrue(a != b)
    }

    @Test
    fun `滚动窗口内同一天不会因取模而撞车`() {
        // 30 天窗口 + 每天同一门课同一档，请求码应两两不同
        val c = course(7)
        val start = LocalDate.of(2026, 11, 3)
        val codes = (0 until 30).map { code(start.plusDays(it.toLong()), 30, c) }
        assertEquals(30, codes.toSet().size)
    }

    @Test
    fun `极端输入下请求码仍是正数且不溢出`() {
        val far = LocalDate.of(2099, 12, 31)
        val codes = listOf(
            ClassReminderScheduler.requestCode(far, 1, 0),
            ClassReminderScheduler.requestCode(far, 60, 4095),
            ClassReminderScheduler.requestCode(LocalDate.of(1970, 1, 1), 60, 4095),
        )
        for (c in codes) {
            assertTrue("请求码应为正数，实际 $c", c > 0)
        }
    }

    @Test
    fun `未落库的课程用内容哈希兜底且稳定`() {
        val a = ClassReminderScheduler.courseSlot(course(0, "高数", 1))
        val b = ClassReminderScheduler.courseSlot(course(0, "高数", 1))
        val c = ClassReminderScheduler.courseSlot(course(0, "英语", 1))
        assertEquals("同样的课程应得到同样的槽位", a, b)
        assertTrue("不同课程应得到不同槽位", a != c)
        assertTrue("槽位应在 12 位范围内", a in 0..4095)
    }
}
