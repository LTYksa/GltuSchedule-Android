// GLTU 课表 App —— 调休规则推导测试
//
// 断言全部基于**国务院办公厅实际发布的通知**，不是凭空构造的日期：
//   2025 春节  ：1月28日至2月4日放假调休；1月26日（周日）、2月8日（周六）上班
//   2025 劳动节：5月1日至5日放假调休；4月27日（周日）上班
//   2025 国庆  ：10月1日至8日放假调休；9月28日（周日）、10月11日（周六）上班
package com.ltyksa.gltuschedule.data

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MakeupResolverTest {

    private fun holiday(name: String, start: String, end: String, type: HolidayType) =
        Holiday(name, LocalDate.parse(start), LocalDate.parse(end), type)

    /** 春节：1/28–2/4 放假，1/26 与 2/8 上班 */
    private fun springFestival2025(): List<Holiday> = listOf(
        holiday("春节", "2025-01-28", "2025-02-04", HolidayType.STATUTORY),
        holiday("春节", "2025-01-26", "2025-01-26", HolidayType.MAKEUP),
        holiday("春节", "2025-02-08", "2025-02-08", HolidayType.MAKEUP),
    )

    /** 劳动节：5/1–5/5 放假，4/27 上班 */
    private fun labourDay2025(): List<Holiday> = listOf(
        holiday("劳动节", "2025-05-01", "2025-05-05", HolidayType.STATUTORY),
        holiday("劳动节", "2025-04-27", "2025-04-27", HolidayType.MAKEUP),
    )

    /** 国庆中秋：10/1–10/8 放假，9/28 与 10/11 上班 */
    private fun nationalDay2025(): List<Holiday> = listOf(
        holiday("国庆节", "2025-10-01", "2025-10-08", HolidayType.STATUTORY),
        holiday("国庆节", "2025-09-28", "2025-09-28", HolidayType.MAKEUP),
        holiday("国庆节", "2025-10-11", "2025-10-11", HolidayType.MAKEUP),
    )

    // ---------------- 自动推导 ----------------

    @Test
    fun `春节 1月26日补2月3日周一`() {
        val rules = MakeupResolver.resolve(springFestival2025()).associateBy { it.date }
        val r = rules[LocalDate.parse("2025-01-26")]
        assertEquals(LocalDate.parse("2025-02-03"), r?.sourceDate)
        assertEquals(DayOfWeek.MONDAY, r?.sourceWeekday)
        assertEquals(false, r?.manual)
    }

    @Test
    fun `春节 2月8日补2月4日周二`() {
        val rules = MakeupResolver.resolve(springFestival2025()).associateBy { it.date }
        val r = rules[LocalDate.parse("2025-02-08")]
        assertEquals(LocalDate.parse("2025-02-04"), r?.sourceDate)
        assertEquals(DayOfWeek.TUESDAY, r?.sourceWeekday)
    }

    @Test
    fun `劳动节 4月27日补5月5日周一`() {
        val rules = MakeupResolver.resolve(labourDay2025()).associateBy { it.date }
        val r = rules[LocalDate.parse("2025-04-27")]
        assertEquals(LocalDate.parse("2025-05-05"), r?.sourceDate)
        assertEquals(DayOfWeek.MONDAY, r?.sourceWeekday)
    }

    @Test
    fun `国庆 9月28日补10月7日周二`() {
        val rules = MakeupResolver.resolve(nationalDay2025()).associateBy { it.date }
        val r = rules[LocalDate.parse("2025-09-28")]
        assertEquals(LocalDate.parse("2025-10-07"), r?.sourceDate)
        assertEquals(DayOfWeek.TUESDAY, r?.sourceWeekday)
    }

    @Test
    fun `国庆 10月11日补10月8日周三`() {
        val rules = MakeupResolver.resolve(nationalDay2025()).associateBy { it.date }
        val r = rules[LocalDate.parse("2025-10-11")]
        assertEquals(LocalDate.parse("2025-10-08"), r?.sourceDate)
        assertEquals(DayOfWeek.WEDNESDAY, r?.sourceWeekday)
        assertTrue(r!!.summary.contains("周三"))
    }

    /** 关键点：源日期与调休日**不在同一周**，周次必须跟着源日期走。 */
    @Test
    fun `国庆 9月28日与其源日期10月7日不同周`() {
        val rules = MakeupResolver.resolve(nationalDay2025()).associateBy { it.date }
        val r = rules[LocalDate.parse("2025-09-28")]!!
        val makeupMonday = r.date.minusDays((r.date.dayOfWeek.value - 1).toLong())
        val sourceMonday = r.sourceDate.minusDays((r.sourceDate.dayOfWeek.value - 1).toLong())
        assertTrue("两者应位于不同周", makeupMonday != sourceMonday)
    }

    // ---------------- 边界 ----------------

    @Test
    fun `没有补班日时不产生规则`() {
        val list = listOf(holiday("元旦", "2025-01-01", "2025-01-01", HolidayType.STATUTORY))
        assertTrue(MakeupResolver.resolve(list).isEmpty())
    }

    @Test
    fun `只有补班日没有假期区间时不崩溃`() {
        val list = listOf(holiday("国庆节", "2025-10-11", "2025-10-11", HolidayType.MAKEUP))
        // 没有可关联的假期区间 → 推导不出源日期，结果应为空（由 UI 提示用户手动设置）
        assertTrue(MakeupResolver.resolve(list).isEmpty())
    }

    @Test
    fun `补班日离假期太远时不关联`() {
        val list = listOf(
            holiday("国庆节", "2025-10-01", "2025-10-08", HolidayType.STATUTORY),
            holiday("国庆节", "2025-12-06", "2025-12-06", HolidayType.MAKEUP),
        )
        assertTrue(MakeupResolver.resolve(list).isEmpty())
    }

    @Test
    fun `越靠近区间末尾的工作日补得越晚`() {
        val rules = MakeupResolver.resolve(nationalDay2025()).sortedBy { it.date }
        // 9/28 在前 → 补 10/7；10/11 在后 → 补 10/8
        assertEquals(LocalDate.parse("2025-10-07"), rules[0].sourceDate)
        assertEquals(LocalDate.parse("2025-10-08"), rules[1].sourceDate)
    }

    @Test
    fun `周末不计入被占用的工作日`() {
        // 区间里含 10/4(六) 10/5(日)，它们不能被当成"需要补的工作日"
        val rules = MakeupResolver.resolve(nationalDay2025())
        for (r in rules) {
            assertTrue(
                "源日期不应是周末：${r.sourceDate}",
                r.sourceDate.dayOfWeek != DayOfWeek.SATURDAY &&
                    r.sourceDate.dayOfWeek != DayOfWeek.SUNDAY,
            )
        }
    }

    // ---------------- 手动覆盖 ----------------

    @Test
    fun `手动覆盖优先于自动推导`() {
        val override = mapOf(LocalDate.parse("2025-10-11") to DayOfWeek.FRIDAY)
        val rules = MakeupResolver.resolve(nationalDay2025(), override).associateBy { it.date }
        val r = rules[LocalDate.parse("2025-10-11")]!!
        assertEquals(DayOfWeek.FRIDAY, r.sourceWeekday)
        assertEquals(true, r.manual)
    }

    @Test
    fun `手动指定后周次不变的那一天仍按自动推导`() {
        val override = mapOf(LocalDate.parse("2025-10-11") to DayOfWeek.FRIDAY)
        val rules = MakeupResolver.resolve(nationalDay2025(), override).associateBy { it.date }
        assertEquals(LocalDate.parse("2025-10-07"), rules[LocalDate.parse("2025-09-28")]?.sourceDate)
        assertEquals(false, rules[LocalDate.parse("2025-09-28")]?.manual)
    }

    @Test
    fun `可以手动为推导不出的补班日指定`() {
        val list = listOf(holiday("国庆节", "2025-10-11", "2025-10-11", HolidayType.MAKEUP))
        val override = mapOf(LocalDate.parse("2025-10-11") to DayOfWeek.WEDNESDAY)
        val rules = MakeupResolver.resolve(list, override)
        assertEquals(1, rules.size)
        assertEquals(DayOfWeek.WEDNESDAY, rules[0].sourceWeekday)
        assertEquals(true, rules[0].manual)
    }

    // ---------------- 与其他假期类型共存 ----------------

    @Test
    fun `校历假期与自定义假期不干扰调休推导`() {
        val list = springFestival2025() + listOf(
            holiday("寒假", "2025-01-18", "2025-02-23", HolidayType.SCHOOL),
            holiday("校运会", "2025-10-29", "2025-10-31", HolidayType.USER_DEFINED),
        )
        val rules = MakeupResolver.resolve(list).associateBy { it.date }
        assertEquals(LocalDate.parse("2025-02-03"), rules[LocalDate.parse("2025-01-26")]?.sourceDate)
        assertEquals(LocalDate.parse("2025-02-04"), rules[LocalDate.parse("2025-02-08")]?.sourceDate)
    }

    @Test
    fun `结果按日期升序`() {
        val rules = MakeupResolver.resolve(springFestival2025() + labourDay2025())
        val dates = rules.map { it.date }
        assertEquals(dates.sorted(), dates)
    }

    @Test
    fun `weekdayLabel 输出中文周几`() {
        assertEquals("周一", weekdayLabel(DayOfWeek.MONDAY))
        assertEquals("周三", weekdayLabel(DayOfWeek.WEDNESDAY))
        assertEquals("周日", weekdayLabel(DayOfWeek.SUNDAY))
    }

    @Test
    fun `空输入返回空结果`() {
        assertTrue(MakeupResolver.resolve(emptyList()).isEmpty())
        assertNull(MakeupResolver.resolve(emptyList()).firstOrNull())
    }
}
