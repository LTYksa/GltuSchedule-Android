package com.ltyksa.gltuschedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教室房号 → 教学楼/楼层 解析测试。
 * 规则（学校公布）：4 位数 ABCD，A=楼（1旅勤/2旅思/3旅博/4旅齐），B=楼层，CD=教室号。
 */
class ClassroomTest {

    @Test
    fun `首位数字映射到对应教学楼`() {
        assertEquals("旅勤楼", Classroom.find("雁山校区 1226（阶4教室）")?.building)
        assertEquals("旅思楼", Classroom.find("2510 教室")?.building)
        assertEquals("旅博楼", Classroom.find("雁山校区 3217 教室")?.building)
        assertEquals("旅齐楼", Classroom.find("4102")?.building)
    }

    @Test
    fun `次位是楼层后两位是教室号`() {
        // 3217 → 3=旅博楼、2=二楼、17=教室号
        val r = Classroom.find("3217")!!
        assertEquals("3217", r.room)
        assertEquals("旅博楼", r.building)
        assertEquals(2, r.floor)
        assertEquals(17, r.roomNo)

        // 1510 → 1=旅勤楼、5=五楼、10=教室号
        val r2 = Classroom.find("1510教室")!!
        assertEquals("旅勤楼", r2.building)
        assertEquals(5, r2.floor)
        assertEquals(10, r2.roomNo)
    }

    @Test
    fun `短标签把校区换成楼栋`() {
        assertEquals("旅博楼 3220（阶7教室）", Classroom.shortLabel("雁山校区 3220（阶7教室）"))
        assertEquals("旅博楼 3217 教室", Classroom.shortLabel("雁山校区 3217 教室"))
    }

    @Test
    fun `识别不到房号时原样返回`() {
        assertEquals("虚拟教室009", Classroom.shortLabel("虚拟教室009"))
        assertNull(Classroom.find("虚拟教室009"))
        assertNull(Classroom.find("体育馆"))
    }

    @Test
    fun `详细标签同时给出校区楼栋楼层`() {
        val d = Classroom.detailLabel("雁山校区 3220（阶7教室）")
        assertTrue("应含校区：$d", d.contains("雁山校区"))
        assertTrue("应含楼栋：$d", d.contains("旅博楼"))
        assertTrue("应含楼层：$d", d.contains("2 楼"))
        assertTrue("房号应保留：$d", d.contains("3220"))
    }
}
