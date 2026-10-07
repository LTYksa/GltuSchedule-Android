package com.gltu.schedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 中国大陆法定节假日解析测试。
 * 覆盖两个数据源：holiday-cn（主源，源自国务院办公厅通知）与 timor.tech（备用源）。
 */
class HolidaySyncTest {

    // ---------------- 主源：holiday-cn ----------------

    private val cnSample = """
    {"year":2026,
     "papers":["https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm"],
     "days":[
       {"name":"元旦","date":"2026-01-01","isOffDay":true},
       {"name":"元旦","date":"2026-01-02","isOffDay":true},
       {"name":"元旦","date":"2026-01-03","isOffDay":true},
       {"name":"元旦","date":"2026-01-04","isOffDay":false},
       {"name":"春节","date":"2026-02-14","isOffDay":false},
       {"name":"春节","date":"2026-02-15","isOffDay":true},
       {"name":"春节","date":"2026-02-16","isOffDay":true},
       {"name":"春节","date":"2026-02-17","isOffDay":true},
       {"name":"春节","date":"2026-02-28","isOffDay":false},
       {"name":"中秋节","date":"2026-09-25","isOffDay":true},
       {"name":"中秋节","date":"2026-09-26","isOffDay":true},
       {"name":"中秋节","date":"2026-09-27","isOffDay":true},
       {"name":"国庆节","date":"2026-09-20","isOffDay":false},
       {"name":"国庆节","date":"2026-10-01","isOffDay":true},
       {"name":"国庆节","date":"2026-10-02","isOffDay":true},
       {"name":"国庆节","date":"2026-10-10","isOffDay":false}
     ]}
    """.trimIndent()

    @Test
    fun `主源-只包含中国大陆法定节日`() {
        val list = HolidaySync.parseHolidayCn(cnSample)
        val names = list.filter { it.type == HolidayType.STATUTORY }.map { it.name }.toSet()
        // 应恰好是中国的法定节日，不含任何其他国家/地区的节日
        assertEquals(setOf("元旦", "春节", "中秋节", "国庆节"), names)
        for (western in listOf("Christmas", "圣诞节", "感恩节", "复活节", "万圣节", "情人节")) {
            assertTrue("不应出现 $western", list.none { it.name.contains(western, ignoreCase = true) })
        }
    }

    @Test
    fun `主源-同名连续放假日合并成一个区间`() {
        val list = HolidaySync.parseHolidayCn(cnSample)
        val newYear = list.first { it.name == "元旦" }
        assertEquals(LocalDate.of(2026, 1, 1), newYear.start)
        assertEquals(LocalDate.of(2026, 1, 3), newYear.end)

        val spring = list.first { it.name == "春节" }
        assertEquals(LocalDate.of(2026, 2, 15), spring.start)
        assertEquals(LocalDate.of(2026, 2, 17), spring.end)

        val national = list.first { it.name == "国庆节" }
        assertEquals(LocalDate.of(2026, 10, 1), national.start)
        assertEquals(LocalDate.of(2026, 10, 2), national.end)
    }

    @Test
    fun `主源-调休补班日被识别为MAKEUP`() {
        val list = HolidaySync.parseHolidayCn(cnSample)
        val makeups = list.filter { it.type == HolidayType.MAKEUP }
        // sample 里有 5 个 isOffDay=false 的调休补班日：
        // 元旦后(1/4)、春节前(2/14)、春节后(2/28)、国庆前(9/20)、国庆后(10/10)
        assertEquals(5, makeups.size)
        for (d in listOf(
            LocalDate.of(2026, 1, 4),
            LocalDate.of(2026, 2, 14),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 10, 10),
        )) {
            assertTrue("补班日 $d 应被收录", makeups.any { it.contains(d) })
        }
        // 补班日不能被当成放假
        assertTrue(
            list.none { it.type == HolidayType.STATUTORY && it.contains(LocalDate.of(2026, 1, 4)) },
        )
        assertTrue(
            list.none { it.type == HolidayType.STATUTORY && it.contains(LocalDate.of(2026, 10, 10)) },
        )
    }

    @Test
    fun `主源-年份未公布时返回空且不崩溃`() {
        // 2027 年国务院尚未发文时，接口返回 days 为空数组
        assertTrue(HolidaySync.parseHolidayCn("""{"year":2027,"papers":[],"days":[]}""").isEmpty())
        assertTrue(HolidaySync.parseHolidayCn("""{"year":2027}""").isEmpty())
        assertTrue(HolidaySync.parseHolidayCn("not a json").isEmpty())
    }

    // ---------------- 备用源：timor.tech ----------------

    private val backupSample = """
    {"code":0,"holiday":{
      "01-01":{"holiday":true,"name":"元旦","date":"2026-01-01"},
      "01-02":{"holiday":true,"name":"元旦","date":"2026-01-02"},
      "01-03":{"holiday":true,"name":"元旦","date":"2026-01-03"},
      "01-04":{"holiday":false,"name":"元旦后补班","date":"2026-01-04"},
      "02-15":{"holiday":true,"name":"春节","date":"2026-02-15"},
      "02-16":{"holiday":true,"name":"除夕","date":"2026-02-16"},
      "02-17":{"holiday":true,"name":"初一","date":"2026-02-17"},
      "02-28":{"holiday":false,"name":"春节后补班","date":"2026-02-28"},
      "10-01":{"holiday":true,"name":"国庆节","date":"2026-10-01"},
      "10-02":{"holiday":true,"name":"国庆节","date":"2026-10-02"},
      "10-10":{"holiday":false,"name":"国庆节后补班","date":"2026-10-10"}
    }}
    """.trimIndent()

    @Test
    fun `备用源-连续放假日合并成一个区间`() {
        val list = HolidaySync.parseTimor(2026, backupSample)
        val newYear = list.filter { it.type == HolidayType.STATUTORY && it.name == "元旦" }
        assertEquals(1, newYear.size)
        assertEquals(LocalDate.of(2026, 1, 1), newYear[0].start)
        assertEquals(LocalDate.of(2026, 1, 3), newYear[0].end)
    }

    @Test
    fun `备用源-跨多天且逐日改名也合并成一条-春节`() {
        val list = HolidaySync.parseTimor(2026, backupSample)
        val spring = list.filter {
            it.type == HolidayType.STATUTORY && it.contains(LocalDate.of(2026, 2, 17))
        }
        assertEquals("春节应合并为一条", 1, spring.size)
        assertEquals(LocalDate.of(2026, 2, 15), spring[0].start)
        assertEquals(LocalDate.of(2026, 2, 17), spring[0].end)
    }

    @Test
    fun `备用源-补班日识别且不与假期混淆`() {
        val list = HolidaySync.parseTimor(2026, backupSample)
        val makeups = list.filter { it.type == HolidayType.MAKEUP }
        assertEquals(3, makeups.size)
        assertTrue(
            list.none { it.type == HolidayType.STATUTORY && it.contains(LocalDate.of(2026, 1, 4)) },
        )
    }

    @Test
    fun `备用源-接口返回空或不合法时不崩溃`() {
        assertTrue(HolidaySync.parseTimor(2026, """{"code":0,"holiday":{}}""").isEmpty())
        assertTrue(HolidaySync.parseTimor(2026, """{"code":-1}""").isEmpty())
        assertTrue(HolidaySync.parseTimor(2026, "not a json").isEmpty())
    }

    // ---------------- 自动同步时机 ----------------

    private val NOW = 1_800_000_000_000L // 固定时间戳，避免测试受当前时间影响
    private fun daysAgo(d: Long) = NOW - d * 24L * 3600 * 1000

    @Test
    fun `从未同步过时需要同步`() {
        assertTrue(
            HolidaySync.shouldSyncPure(0L, NOW, LocalDate.of(2026, 10, 7), coversNextYear = false),
        )
    }

    @Test
    fun `常规情况下7天检查一次`() {
        val now = LocalDate.of(2026, 10, 7)
        assertFalse(
            "6 天前同步过，不该重复拉",
            HolidaySync.shouldSyncPure(daysAgo(6), NOW, now, coversNextYear = true),
        )
        assertTrue(
            "7 天前同步过，该拉了",
            HolidaySync.shouldSyncPure(daysAgo(7), NOW, now, coversNextYear = true),
        )
    }

    @Test
    fun `11月还缺次年数据时改为3天重试一次`() {
        val nov = LocalDate.of(2026, 11, 15) // 国务院通常 11 月发布次年安排
        assertFalse(
            "才过 2 天，先不打扰",
            HolidaySync.shouldSyncPure(daysAgo(2), NOW, nov, coversNextYear = false),
        )
        assertTrue(
            "过了 3 天，该再探一次次年安排有没有发布",
            HolidaySync.shouldSyncPure(daysAgo(3), NOW, nov, coversNextYear = false),
        )
    }

    @Test
    fun `已拿到次年数据后不再加密重试`() {
        val nov = LocalDate.of(2026, 11, 15)
        assertFalse(
            HolidaySync.shouldSyncPure(daysAgo(3), NOW, nov, coversNextYear = true),
        )
    }

    @Test
    fun `非11和12月不会因为缺次年数据而加密重试`() {
        assertFalse(
            HolidaySync.shouldSyncPure(daysAgo(3), NOW, LocalDate.of(2026, 10, 15), coversNextYear = false),
        )
        assertFalse(
            HolidaySync.shouldSyncPure(daysAgo(3), NOW, LocalDate.of(2026, 6, 1), coversNextYear = false),
        )
    }

    // ---------------- 屏蔽判定 ----------------

    @Test
    fun `补班日语义-周末补班不算放假`() {
        val makeup = Holiday("国庆节后补班", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10), HolidayType.MAKEUP)
        assertEquals(HolidayType.MAKEUP, makeup.type)
        // 2026-10-10 是周六，但属于补班日，业务上不应被"节假日屏蔽"拦掉
        assertEquals(java.time.DayOfWeek.SATURDAY, LocalDate.of(2026, 10, 10).dayOfWeek)
    }
}
