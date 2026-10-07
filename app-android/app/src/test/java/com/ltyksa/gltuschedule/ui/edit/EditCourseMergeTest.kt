package com.ltyksa.gltuschedule.ui.edit

import com.ltyksa.gltuschedule.data.CourseCategory
import com.ltyksa.gltuschedule.model.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * 「编辑课程」表单合并测试。
 *
 * 回归防护：表单里没有的字段（显式周次、学分、原始周次文本、配色）在编辑时必须保留，
 * 否则"只改个教室"会把不规则周次压成连续区间，凭空多出好几周的课。
 */
class EditCourseMergeTest {

    private val imported = Course(
        id = 7,
        name = "中华民族共同体概论",
        teacher = "文娟",
        location = "1226（阶4教室）",
        dayOfWeek = DayOfWeek.MONDAY,
        startIndex = 1,
        endIndex = 2,
        startWeek = 4,
        endWeek = 16,
        oddEven = 0,
        category = CourseCategory.PUBLIC,
        credit = 2.0,
        weeksText = "4-5周,8周,11-13周(单),14-16周",
        weeks = listOf(4, 5, 8, 11, 13, 14, 15, 16),
        raw = "原始记录",
    )

    private fun formOf(c: Course) = CourseFormState(
        id = c.id,
        name = c.name,
        teacher = c.teacher,
        location = c.location,
        dayOfWeek = c.dayOfWeek,
        startIndex = c.startIndex,
        endIndex = c.endIndex,
        startWeek = c.startWeek,
        endWeek = c.endWeek,
        oddEven = c.oddEven,
        loading = false,
    )

    @Test
    fun `只改地点-显式周次与学分必须保留`() {
        val form = formOf(imported).copy(location = "旅博楼 3220（阶7教室）")
        val merged = mergeCourseForm(form, imported)

        assertEquals("旅博楼 3220（阶7教室）", merged.location)
        assertEquals(
            "不规则周次不能被压成连续区间",
            listOf(4, 5, 8, 11, 13, 14, 15, 16),
            merged.weeks,
        )
        assertEquals("4-5周,8周,11-13周(单),14-16周", merged.weeksText)
        assertEquals(2.0, merged.credit)
        assertEquals(CourseCategory.PUBLIC, merged.category)
        assertEquals("原始记录", merged.raw)
    }

    @Test
    fun `保留周次后-第6周不上课第8周上课`() {
        val merged = mergeCourseForm(formOf(imported), imported)
        assertTrue("第5周有课", merged.occursOnWeek(5))
        assertTrue("第8周有课", merged.occursOnWeek(8))
        assertTrue("第6周不该有课（原周次里没有 6）", !merged.occursOnWeek(6))
        assertTrue("第7周不该有课（原周次里没有 7）", !merged.occursOnWeek(7))
        assertTrue("第12周不该有课（11-13周只上单周）", !merged.occursOnWeek(12))
        assertTrue("第13周有课", merged.occursOnWeek(13))
    }

    @Test
    fun `用户改了周次范围-以表单为准并丢弃显式周次`() {
        val form = formOf(imported).copy(startWeek = 1, endWeek = 18)
        val merged = mergeCourseForm(form, imported)

        assertEquals(1, merged.startWeek)
        assertEquals(18, merged.endWeek)
        assertTrue("改过周次后不应再保留旧的显式周次", merged.weeks.isEmpty())
        assertEquals("", merged.weeksText)
        // 退化成连续区间
        assertTrue(merged.occursOnWeek(6))
        assertTrue(merged.occursOnWeek(7))
        // 学分仍然保留
        assertEquals(2.0, merged.credit)
    }

    @Test
    fun `用户改了单双周-以表单为准`() {
        val form = formOf(imported).copy(oddEven = 1)
        val merged = mergeCourseForm(form, imported)

        assertEquals(1, merged.oddEven)
        assertTrue(merged.weeks.isEmpty())
        assertTrue("单周：第5周上课", merged.occursOnWeek(5))
        assertTrue("单周：第6周不上课", !merged.occursOnWeek(6))
    }

    @Test
    fun `新增课程-没有原课程时用表单推断配色`() {
        val form = CourseFormState(
            name = "篮球",
            teacher = "李四",
            location = "垒球场",
            dayOfWeek = DayOfWeek.WEDNESDAY,
            startIndex = 3,
            endIndex = 4,
            startWeek = 1,
            endWeek = 16,
            loading = false,
        )
        val merged = mergeCourseForm(form, null)

        assertEquals(0L, merged.id)
        assertEquals("篮球", merged.name)
        assertNull("新增时没有学分", merged.credit)
        assertTrue("新增时没有显式周次", merged.weeks.isEmpty())
        assertEquals("", merged.weeksText)
        assertEquals(1, merged.startWeek)
        assertEquals(16, merged.endWeek)
    }

    @Test
    fun `起止节次填反了会自动纠正`() {
        val form = CourseFormState(
            name = "高数",
            dayOfWeek = DayOfWeek.TUESDAY,
            startIndex = 5,
            endIndex = 2,
            startWeek = 3,
            endWeek = 1,
            loading = false,
        )
        val merged = mergeCourseForm(form, null)
        assertEquals(2, merged.startIndex)
        assertEquals(5, merged.endIndex)
        assertEquals(1, merged.startWeek)
        assertEquals(3, merged.endWeek)
    }

    @Test
    fun `周次范围被夹在1到30之间`() {
        val form = CourseFormState(
            name = "测试",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 0,
            endWeek = 99,
            loading = false,
        )
        val merged = mergeCourseForm(form, null)
        assertEquals(1, merged.startWeek)
        assertEquals(30, merged.endWeek)
    }

    @Test
    fun `起止周都超出上限时不能产生隐形课程`() {
        // 回归：只夹 endWeek 不夹 startWeek 会得到 35..30，occursOnWeek 恒 false，
        // 课程会在课表里彻底消失（提醒与日历同步也会跳过它）。
        val form = CourseFormState(
            name = "测试",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 40,
            endWeek = 35,
            loading = false,
        )
        val merged = mergeCourseForm(form, null)
        assertTrue("startWeek 不能大于 endWeek，实际 ${merged.startWeek}..${merged.endWeek}",
            merged.startWeek <= merged.endWeek)
        assertEquals(30, merged.endWeek)
        assertEquals(30, merged.startWeek)
        assertTrue("第30周应该有课，课程不能消失", merged.occursOnWeek(30))
    }

    @Test
    fun `起止周都在上限之外时仍能得到有效区间`() {
        val form = CourseFormState(
            name = "测试",
            dayOfWeek = DayOfWeek.MONDAY,
            startWeek = 99,
            endWeek = 120,
            loading = false,
        )
        val merged = mergeCourseForm(form, null)
        assertTrue(merged.startWeek <= merged.endWeek)
        assertEquals(30, merged.startWeek)
        assertEquals(30, merged.endWeek)
        assertTrue(merged.occursOnWeek(30))
    }
}
