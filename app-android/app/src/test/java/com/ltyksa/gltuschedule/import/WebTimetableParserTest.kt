package com.ltyksa.gltuschedule.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * 网页课表解析器测试。
 * 覆盖三种真实页面结构：
 *  1. GLTU「个人课表查询」列表视图（星期 / 节次 / 课表信息 + 富文本卡片）—— 本次重点
 *  2. 强智表格视图（第X~Y节 行 + 星期列 + div 单元格）
 *  3. 通用/正方表格视图（表头 周一…周日，单元格用 <br> 分行）
 */
class WebTimetableParserTest {

    // =====================================================================
    // 1) GLTU 列表视图：完全按截图结构复刻
    // =====================================================================
    private val gltuListHtml = """
        <html><body>
        <div>2026-2027学年第1学期&nbsp;&nbsp;注：红色斜体为待筛选，蓝色为已选上&nbsp;&nbsp;学号：202605630126</div>
        <table border="1">
          <tr><th>星期</th><th>节次</th><th>课表信息</th></tr>
          <tr>
            <td rowspan="2">星期一</td>
            <td rowspan="2">1-2</td>
            <td>
              <div>
                <div>中华民族共同体概论*</div>
                <div>周数：4-5周,8周,11-13周(单),14-16周</div>
                <div>校区:雁山校区&nbsp;&nbsp;上课地点：1226（阶4教室）</div>
                <div>教师：文娟(讲师)</div>
                <div>教学班：TB29129006-中华民族共同体概论-0014</div>
                <div>教学班组成：2026汉语1;2026媒体1</div>
                <div>考核方式：考试</div>
                <div>学分：2.0</div>
              </div>
            </td>
          </tr>
          <tr>
            <td>
              <div>
                <div>中华民族共同体概论*</div>
                <div>周数：6-7周,9-10周,12周</div>
                <div>校区:雁山校区&nbsp;&nbsp;上课地点：虚拟教室009</div>
                <div>教师：文娟(讲师)</div>
                <div>学分：2.0</div>
              </div>
            </td>
          </tr>
          <tr>
            <td>星期二</td>
            <td>3-4</td>
            <td>
              <div>
                <div>大学英语（一）</div>
                <div>周数：1-16周</div>
                <div>校区:雁山校区&nbsp;&nbsp;上课地点：3305</div>
                <div>教师：李明</div>
                <div>学分：3.0</div>
              </div>
            </td>
          </tr>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `GLTU列表视图-能识别星期节次与课程卡片`() {
        val out = WebTimetableParser.parse(gltuListHtml)
        println("策略=${out.strategy} 课程数=${out.courses.size}")
        out.courses.forEach { println("  $it") }

        assertEquals("应解析出 3 门课", 3, out.courses.size)

        val c1 = out.courses.first { it.location.contains("1226") }
        assertEquals("中华民族共同体概论", c1.name)
        assertEquals(DayOfWeek.MONDAY, c1.dayOfWeek)
        assertEquals(1, c1.startIndex)
        assertEquals(2, c1.endIndex)
        assertEquals("文娟", c1.teacher)
        assertEquals("雁山校区 1226（阶4教室）", c1.location)
        assertEquals(2.0, c1.credit ?: 0.0, 0.001)
    }

    @Test
    fun `GLTU复杂周次-混合区间单周奇偶能正确展开`() {
        val out = WebTimetableParser.parse(gltuListHtml)

        val c1 = out.courses.first { it.location.contains("1226") }
        // 4-5周,8周,11-13周(单),14-16周 → 4,5,8,11,13,14,15,16
        assertEquals(listOf(4, 5, 8, 11, 13, 14, 15, 16), c1.weeks)
        assertEquals(4, c1.startWeek)
        assertEquals(16, c1.endWeek)
        assertEquals("原始周次文本应保留", "4-5周,8周,11-13周(单),14-16周", c1.weeksText)

        // occursOnWeek 必须按集合判断（不是简单区间）
        assertTrue("第4周有课", c1.occursOnWeek(4))
        assertTrue("第5周有课", c1.occursOnWeek(5))
        assertTrue("第8周有课", c1.occursOnWeek(8))
        assertTrue("第11周有课(单)", c1.occursOnWeek(11))
        assertTrue("第13周有课(单)", c1.occursOnWeek(13))
        assertTrue("第14周有课", c1.occursOnWeek(14))
        assertTrue("第6周没课", !c1.occursOnWeek(6))
        assertTrue("第7周没课", !c1.occursOnWeek(7))
        assertTrue("第9周没课", !c1.occursOnWeek(9))
        assertTrue("第12周没课(单周标记已排除偶数)", !c1.occursOnWeek(12))

        val c2 = out.courses.first { it.location.contains("虚拟教室009") }
        assertEquals(listOf(6, 7, 9, 10, 12), c2.weeks)
        assertTrue("第6周有课", c2.occursOnWeek(6))
        assertTrue("第8周没课", !c2.occursOnWeek(8))
    }

    @Test
    fun `GLTU同一格两张卡片都能识别`() {
        val out = WebTimetableParser.parse(gltuListHtml)
        val mon = out.courses.filter { it.dayOfWeek == DayOfWeek.MONDAY }
        assertEquals("周一 1-2 节应有 2 张卡片", 2, mon.size)
        assertTrue(mon.all { it.startIndex == 1 && it.endIndex == 2 })
        assertTrue("两张卡片教室不同", mon.map { it.location }.distinct().size == 2)
    }

    // =====================================================================
    // 2) 强智表格视图
    // =====================================================================
    private val qiangzhiHtml = """
        <html><body>
        <table border="1">
          <tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
          <tr>
            <td>第1~2节</td>
            <td><div>高等数学(分组A)张三 1-16周[01-02节]明德楼B104</div></td>
            <td></td><td></td><td></td><td></td><td></td><td></td>
          </tr>
          <tr>
            <td>第3~4节</td>
            <td></td>
            <td><div>大学英语 李四 1-16周[03-04节]教二楼203</div></td>
            <td></td><td></td><td></td><td></td><td></td>
          </tr>
          <tr>
            <td>第5~6节</td>
            <td></td><td></td>
            <td><div>体育(二) 王五 1-18周[05-06节]体育馆</div></td>
            <td></td><td></td><td></td><td></td>
          </tr>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `强智表格视图-课程星期节次周次`() {
        val out = WebTimetableParser.parse(qiangzhiHtml)
        println("策略=${out.strategy} 课程数=${out.courses.size}")
        out.courses.forEach { println("  $it") }

        assertEquals(3, out.courses.size)

        val math = out.courses.first { it.name.contains("高等数学") }
        assertEquals(DayOfWeek.MONDAY, math.dayOfWeek)
        assertEquals(1, math.startIndex)
        assertEquals(2, math.endIndex)
        assertEquals(1, math.startWeek)
        assertEquals(16, math.endWeek)
        assertTrue("地点=${math.location}", math.location.contains("明德楼"))

        val english = out.courses.first { it.name.contains("大学英语") }
        assertEquals(DayOfWeek.TUESDAY, english.dayOfWeek)
        assertEquals(3, english.startIndex)
        assertEquals(4, english.endIndex)

        val sport = out.courses.first { it.name.contains("体育") }
        assertEquals(DayOfWeek.WEDNESDAY, sport.dayOfWeek)
        assertEquals(18, sport.endWeek)
    }

    // =====================================================================
    // 3) 通用表格视图（<br> 分行）
    // =====================================================================
    private val genericHtml = """
        <html><body>
        <table>
          <tr><td>时间</td><td>周一</td><td>周二</td><td>周三</td><td>周四</td><td>周五</td><td>周六</td><td>周日</td></tr>
          <tr>
            <td>第1-2节</td>
            <td>护理学基础<br>赵六<br>1-16周<br>明德楼A109</td>
            <td></td><td></td><td></td><td></td><td></td><td></td>
          </tr>
          <tr>
            <td>第3-4节</td>
            <td></td><td></td>
            <td>生理学(单周)<br>孙七<br>1-16周<br>实训楼B402</td>
            <td></td><td></td><td></td><td></td>
          </tr>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `通用表格视图-多行单元格能合并成一条课程`() {
        val out = WebTimetableParser.parse(genericHtml)
        println("策略=${out.strategy} 课程数=${out.courses.size}")
        out.courses.forEach { println("  $it") }

        assertEquals(2, out.courses.size)

        val nursing = out.courses.first { it.name.contains("护理学基础") }
        assertEquals(DayOfWeek.MONDAY, nursing.dayOfWeek)
        assertEquals(1, nursing.startIndex)
        assertEquals(2, nursing.endIndex)
        assertEquals(16, nursing.endWeek)
        assertEquals("赵六", nursing.teacher)

        val physiology = out.courses.first { it.name.contains("生理学") }
        assertEquals(DayOfWeek.WEDNESDAY, physiology.dayOfWeek)
    }

    // =====================================================================
    // 4) 学期名 / 当前周次识别
    // =====================================================================
    @Test
    fun `学期名识别-兼容多种写法`() {
        assertEquals(
            "2026-2027 第1学期",
            WebTimetableParser.guessSemester("<html><body>2026-2027学年第1学期</body></html>"),
        )
        assertEquals(
            "2026-2027 第1学期",
            WebTimetableParser.guessSemester("<html><body>2026-2027学年第一学期</body></html>"),
        )
        assertEquals(
            "2026-2027 第2学期",
            WebTimetableParser.guessSemester("<html><body>2026-2027 第2学期</body></html>"),
        )
        // 真实课表页里就带这句
        assertEquals("2026-2027 第1学期", WebTimetableParser.guessSemester(gltuListHtml))
        assertNull(
            "无关页面应识别不出",
            WebTimetableParser.guessSemester("<html><body>请先登录</body></html>"),
        )
    }

    @Test
    fun `当前周次识别-只认第N周不认周数区间`() {
        assertEquals(6, WebTimetableParser.guessCurrentWeek("<html><body>当前第6周</body></html>"))
        assertNull(
            "课表的周数区间不应被当成当前周",
            WebTimetableParser.guessCurrentWeek("<html><body>周数：4-5周,8周</body></html>"),
        )
    }

    // =====================================================================
    // 5) 失败诊断
    // =====================================================================
    @Test
    fun `非课表页面给出可读诊断`() {
        val out = WebTimetableParser.parse("<html><body><p>请先登录</p></body></html>")
        assertTrue("没有课程", out.courses.isEmpty())
        assertTrue("诊断应提到课表，实际=${out.note}", out.note.contains("课表"))
    }
}
