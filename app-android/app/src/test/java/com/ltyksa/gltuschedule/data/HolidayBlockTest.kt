package com.ltyksa.gltuschedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 节假日屏蔽「三个独立开关」的判定测试。
 * 法定节假日 / 学校假期 / 周末 三类互不影响，可任意组合；补班日永远照常上课。
 */
class HolidayBlockTest {

    private val nationalDay = Holiday(
        "国庆节", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), HolidayType.STATUTORY,
    )
    private val sportsMeet = Holiday(
        "校运会", LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 31), HolidayType.SCHOOL,
    )
    private val makeup = Holiday(
        "国庆节后补班", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10), HolidayType.MAKEUP,
    )
    private val all = listOf(nationalDay, sportsMeet, makeup)

    // 2026 年 10 月：1 日周四 → 3 日周六、5 日周一、10 日周六、17 日周六、29 日周四、31 日周六
    private val nationalDayWeekday = LocalDate.of(2026, 10, 5)   // 周一，国庆假期（非周末）
    private val nationalDayWeekend = LocalDate.of(2026, 10, 3)   // 周六，国庆假期（同时是周末）
    private val sportsMeetWeekday = LocalDate.of(2026, 10, 29)   // 周四，校运会
    private val sportsMeetSaturday = LocalDate.of(2026, 10, 31)  // 周六，校运会最后一天
    private val makeupDay = LocalDate.of(2026, 10, 10)           // 周六，但补班
    private val normalWeekend = LocalDate.of(2026, 10, 17)       // 周六，无假期
    private val normalWeekday = LocalDate.of(2026, 10, 14)       // 周三，无假期

    @Test
    fun `只开法定节假日-只屏蔽国庆不屏蔽校运会和周末`() {
        val s = HolidayManager.BlockSettings(statutory = true, school = false, weekend = false)
        assertTrue(HolidayManager.isBlockedPure(nationalDayWeekday, s, all))
        assertFalse(HolidayManager.isBlockedPure(sportsMeetWeekday, s, all))
        assertFalse(HolidayManager.isBlockedPure(normalWeekend, s, all))
        assertFalse(HolidayManager.isBlockedPure(normalWeekday, s, all))
    }

    @Test
    fun `只开学校假期-只屏蔽校运会`() {
        val s = HolidayManager.BlockSettings(statutory = false, school = true, weekend = false)
        assertTrue(HolidayManager.isBlockedPure(sportsMeetWeekday, s, all))
        assertFalse(HolidayManager.isBlockedPure(nationalDayWeekday, s, all))
        assertFalse(HolidayManager.isBlockedPure(normalWeekend, s, all))
    }

    @Test
    fun `只开周末-只屏蔽周六周日`() {
        val s = HolidayManager.BlockSettings(statutory = false, school = false, weekend = true)
        assertTrue(HolidayManager.isBlockedPure(normalWeekend, s, all))
        assertTrue(HolidayManager.isBlockedPure(sportsMeetSaturday, s, all)) // 校运会最后一天是周六
        assertFalse(HolidayManager.isBlockedPure(sportsMeetWeekday, s, all)) // 校运会里的周四不受影响
        assertFalse(HolidayManager.isBlockedPure(normalWeekday, s, all))
    }

    @Test
    fun `三个都关-什么都不屏蔽`() {
        val s = HolidayManager.BlockSettings(false, false, false)
        for (d in listOf(
            nationalDayWeekday, nationalDayWeekend, sportsMeetWeekday,
            sportsMeetSaturday, makeupDay, normalWeekend, normalWeekday,
        )) {
            assertFalse("$d 不该被屏蔽", HolidayManager.isBlockedPure(d, s, all))
        }
        assertEquals("不屏蔽", s.summary)
        assertFalse(s.anyEnabled)
    }

    @Test
    fun `三个都开-法定节假日和校运会都屏蔽`() {
        val s = HolidayManager.BlockSettings(true, true, true)
        assertTrue(HolidayManager.isBlockedPure(nationalDayWeekday, s, all))
        assertTrue(HolidayManager.isBlockedPure(sportsMeetWeekday, s, all))
        assertTrue(HolidayManager.isBlockedPure(normalWeekend, s, all))
        assertFalse(HolidayManager.isBlockedPure(normalWeekday, s, all))
    }

    @Test
    fun `补班日即使三个开关全开也照常上课`() {
        val s = HolidayManager.BlockSettings(true, true, true)
        assertFalse(
            "2026-10-10 是周六但是补班日，必须照常上课",
            HolidayManager.isBlockedPure(makeupDay, s, all),
        )
        assertEquals(java.time.DayOfWeek.SATURDAY, makeupDay.dayOfWeek)
    }

    @Test
    fun `组合开关互不干扰`() {
        // 只开「学校假期 + 周末」，法定节假日关掉
        val s = HolidayManager.BlockSettings(statutory = false, school = true, weekend = true)
        assertTrue(HolidayManager.isBlockedPure(sportsMeetWeekday, s, all))    // 校运会
        assertTrue(HolidayManager.isBlockedPure(normalWeekend, s, all))        // 周末
        assertTrue(HolidayManager.isBlockedPure(nationalDayWeekend, s, all))   // 周末（顺带是国庆）
        assertFalse(HolidayManager.isBlockedPure(nationalDayWeekday, s, all))  // 国庆工作日：法定那档关了
    }

    @Test
    fun `摘要文案随开关变化`() {
        assertEquals(
            "法定节假日+学校假期",
            HolidayManager.BlockSettings(true, true, false).summary,
        )
        assertEquals("周末", HolidayManager.BlockSettings(false, false, true).summary)
        assertEquals(
            "法定节假日+学校假期+周末",
            HolidayManager.BlockSettings(true, true, true).summary,
        )
    }
}
