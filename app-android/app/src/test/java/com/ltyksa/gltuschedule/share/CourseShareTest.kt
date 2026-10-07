package com.ltyksa.gltuschedule.share

import com.ltyksa.gltuschedule.model.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.util.Base64

/**
 * 课表分享码 导出/导入 往返测试。
 *
 * 回归防护：导出必须带显式周次集合，否则同学导入后
 * `4-5周,8周,11-13周(单)` 会被展开成连续的 4-16 周。
 */
class CourseShareTest {

    private val irregular = Course(
        id = 3,
        name = "中华民族共同体概论",
        teacher = "文娟",
        location = "1226（阶4教室）",
        dayOfWeek = DayOfWeek.MONDAY,
        startIndex = 1,
        endIndex = 2,
        startWeek = 4,
        endWeek = 16,
        oddEven = 0,
        credit = 2.0,
        weeksText = "4-5周,8周,11-13周(单),14-16周",
        weeks = listOf(4, 5, 8, 11, 13, 14, 15, 16),
    )

    private val simple = Course(
        id = 9,
        name = "写作与沟通",
        teacher = "马景峰",
        location = "3220（阶7教室）",
        dayOfWeek = DayOfWeek.TUESDAY,
        startIndex = 1,
        endIndex = 2,
        startWeek = 1,
        endWeek = 16,
        oddEven = 0,
    )

    @Test
    fun `往返后不规则周次不丢失`() {
        val code = CourseShare.export(listOf(irregular))
        val back = CourseShare.import(code)
        assertNotNull("应能导入", back)
        assertEquals(1, back!!.size)

        val c = back[0]
        assertEquals(
            "显式周次必须原样还原",
            listOf(4, 5, 8, 11, 13, 14, 15, 16),
            c.weeks,
        )
        assertEquals("4-5周,8周,11-13周(单),14-16周", c.weeksText)
        assertEquals(2.0, c.credit)
        // 关键断言：第 6、7 周本来没课，不能被展开出来
        assertTrue("第 6 周不该有课", !c.occursOnWeek(6))
        assertTrue("第 7 周不该有课", !c.occursOnWeek(7))
        assertTrue("第 5 周有课", c.occursOnWeek(5))
        assertTrue("第 8 周有课", c.occursOnWeek(8))
    }

    @Test
    fun `往返后基本字段一致`() {
        val back = CourseShare.import(CourseShare.export(listOf(simple, irregular)))!!
        assertEquals(2, back.size)

        val a = back[0]
        assertEquals("写作与沟通", a.name)
        assertEquals("马景峰", a.teacher)
        assertEquals("3220（阶7教室）", a.location)
        assertEquals(DayOfWeek.TUESDAY, a.dayOfWeek)
        assertEquals(1, a.startIndex)
        assertEquals(2, a.endIndex)
        assertEquals("没有显式周次时保持为空", emptyList<Int>(), a.weeks)
        assertNull("没有学分时保持 null", a.credit)
    }

    @Test
    fun `导入时粘贴带入换行和空格也能解析`() {
        val code = CourseShare.export(listOf(simple))
        val messy = "  " + code.chunked(20).joinToString("\n  ") + "  "
        val back = CourseShare.import(messy)
        assertNotNull("带换行空格的分享码应能解析", back)
        assertEquals("写作与沟通", back!![0].name)
    }

    @Test
    fun `兼容v1老分享码-没有w字段时退化成起止周`() {
        // 手工构造一个 v1 分享码（没有 w / wt / cr 字段）
        val json = """{"app":"GLTU-SCHEDULE","v":1,"c":[
            {"n":"生理学","t":"孙七","l":"实训楼B402","d":3,"s":3,"e":4,"sw":1,"ew":16,"o":1}
        ]}"""
        val code = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val back = CourseShare.import(code)
        assertNotNull("v1 分享码应能继续导入", back)
        val c = back!![0]
        assertEquals("生理学", c.name)
        assertEquals(1, c.oddEven)
        assertTrue("v1 没有显式周次", c.weeks.isEmpty())
        assertTrue("单周：第3周有课", c.occursOnWeek(3))
        assertTrue("单周：第4周没课", !c.occursOnWeek(4))
    }

    @Test
    fun `非法分享码返回null并给出原因`() {
        assertNull(CourseShare.import(""))
        assertEquals("分享码为空", CourseShare.lastError)

        assertNull(CourseShare.import("这不是base64!!!"))
        assertNotNull(CourseShare.lastError)

        val notOurs = Base64.getEncoder().encodeToString("{\"app\":\"X\"}".toByteArray())
        assertNull(CourseShare.import(notOurs))
        assertEquals("这不是 GLTU 课表的分享码", CourseShare.lastError)
    }

    @Test
    fun `显式周次会被限制在1到30并去重排序`() {
        val json = """{"app":"GLTU-SCHEDULE","v":2,"c":[
            {"n":"测试","d":1,"s":1,"e":1,"sw":1,"ew":16,"o":0,"w":"5,3,3,0,99,7"}
        ]}"""
        val code = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val c = CourseShare.import(code)!![0]
        assertEquals(listOf(3, 5, 7), c.weeks)
    }
}
