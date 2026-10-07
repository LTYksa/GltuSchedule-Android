// GLTU 课表 App —— 网页课表解析器（针对真实教务系统页面结构重写）
//
// 真实页面（GLTU「个人课表查询」，v.gltu.edu.cn）实际结构：
//   ┌────────┬──────┬──────────────────────────────────────┐
//   │ 星期   │ 节次 │ 课表信息                              │
//   ├────────┼──────┼──────────────────────────────────────┤
//   │ 星期一 │ 1-2  │ 中华民族共同体概论*                    │
//   │        │      │ 周数：4-5周,8周,11-13周(单),14-16周    │
//   │        │      │ 校区:雁山校区  上课地点：1226（阶4教室）│
//   │        │      │ 教师：文娟(讲师)  教学班：TB...        │
//   │        │      │ ……（同一格可有多张课程卡片）           │
//   └────────┴──────┴──────────────────────────────────────┘
// 即「列表视图」：3 列 + 富文本课程卡片（字段带标签）。表格视图则仍是 节次行 × 星期列。
package com.ltyksa.gltuschedule.import

import com.ltyksa.gltuschedule.data.CoursePalette
import com.ltyksa.gltuschedule.model.Course
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.DayOfWeek

object WebTimetableParser {

    data class Outcome(
        val courses: List<Course>,
        val strategy: String,
        val note: String = "",
    )

    // ---------- 正则 ----------
    private const val NL = "\u0001"   // 行分隔哨兵（Jsoup.text() 不会吃掉它）

    private val SEC_RANGE = Regex("""第?\s*(\d{1,2})\s*[~\-—－至]\s*(\d{1,2})\s*节""")
    private val SEC_SINGLE = Regex("""第?\s*(\d{1,2})\s*节""")
    private val SEC_BARE_RANGE = Regex("""(\d{1,2})\s*[~\-—－至]\s*(\d{1,2})""")
    private val SEC_BARE_SINGLE = Regex("""^(\d{1,2})$""")
    /** 形如 [01-02节] / (1-2节) 的节次括号（'[' ']' 用 \u005B \u005D 转义，避免正则字符类报错） */
    private val SEC_BRACKET = Regex("[\\u005B［(（]?\\s*\\d{1,2}\\s*[-~—－至]\\s*\\d{1,2}\\s*节\\s*[\\u005D］)）]?")
    /** 周次片段（含可选括号与单双标记），用于从文本里剥掉周次 */
    private val WEEKS_FRAGMENT = Regex("""\d{1,2}\s*[~\-—－至]?\s*\d{0,2}\s*[（(]?\s*[单双]?\s*[）)]?\s*周""")

    private val DAY_CHAR = mapOf(
        '一' to DayOfWeek.MONDAY, '二' to DayOfWeek.TUESDAY, '三' to DayOfWeek.WEDNESDAY,
        '四' to DayOfWeek.THURSDAY, '五' to DayOfWeek.FRIDAY, '六' to DayOfWeek.SATURDAY,
        '日' to DayOfWeek.SUNDAY, '天' to DayOfWeek.SUNDAY,
    )

    /** 课程卡片里的字段标签（带冒号才算，避免把"教师口语"这类课程名误判成标签）。 */
    private val LABEL_NAMES = listOf(
        "周数", "校区", "上课地点", "上课时间", "上课教室", "教师", "任课教师",
        "教学班组成", "教学班", "考核方式", "考试方式", "选课备注", "课程学时组成",
        "周学时", "总学时", "学分", "课程性质", "开课学院", "选课人数", "容量", "备注",
    )
    private val LABEL_REGEX = Regex("(" + LABEL_NAMES.joinToString("|") + """)\s*[：:]""")

    /** 一个课程卡片的结束标志（用于无换行时兜底切块）。 */
    private val BLOCK_END = Regex("""学分\s*[：:]\s*[\d.]{1,6}""")

    // =========================================================================
    // 入口
    // =========================================================================
    fun parse(html: String): Outcome {
        if (html.isBlank()) return Outcome(emptyList(), "无", "抓到的网页内容为空")

        val doc = Jsoup.parse(html)
        val tables = directTables(doc)

        // ① 列表视图（星期 / 节次 / 课表信息）—— GLTU 实际页面
        for (t in tables) {
            val grid = expandTable(t)
            val r = tryListLayout(grid)
            if (r.courses.isNotEmpty()) return r
        }

        // ② 表格视图（表头含 星期一…星期日）
        for (t in tables) {
            val grid = expandTable(t)
            val r = tryGridLayout(grid, useHeaderDays = true)
            if (r.courses.isNotEmpty()) return r
        }

        // ③ 表格视图（无表头，默认第 1..7 列 = 周一..周日）
        for (t in tables) {
            val grid = expandTable(t)
            val r = tryGridLayout(grid, useHeaderDays = false)
            if (r.courses.isNotEmpty()) return r
        }

        // ④ 无表格：从 div 网格里抓
        val divs = tryDivGrid(doc)
        if (divs.courses.isNotEmpty()) return divs

        return Outcome(
            emptyList(), "失败",
            "没有识别到课表。当前页面表格数：${tables.size}。\n" +
                "请确认：\n" +
                "① 已登录成功；\n" +
                "② 停留在『个人课表查询 / 学生课表查询 / 我的课表』页面；\n" +
                "③ 页面上能看到『星期 / 节次 / 课表信息』或完整课表格子。\n" +
                "确认后再点『确定导入』。"
        )
    }

    // =========================================================================
    // ① 列表视图：星期 | 节次 | 课表信息
    // =========================================================================
    private fun tryListLayout(grid: List<List<Element?>>): Outcome {
        if (grid.size < 2) return Outcome(emptyList(), "列表视图")

        // 找表头行：同时出现"星期"列与"节次"列
        var headerIdx = -1
        var dayCol = -1
        var secCol = -1
        for ((idx, row) in grid.withIndex()) {
            var d = -1
            var s = -1
            row.forEachIndexed { i, cell ->
                val t = cell?.text()?.trim() ?: return@forEachIndexed
                if (t.isEmpty()) return@forEachIndexed
                val isDayHeader = (t.contains("星期") || t == "周" || t.contains("礼拜")) && dayOf(t) == null
                when {
                    isDayHeader && d < 0 -> d = i
                    (t.contains("节次")) && s < 0 -> s = i
                }
            }
            if (d >= 0 && s >= 0) { headerIdx = idx; dayCol = d; secCol = s; break }
        }
        if (headerIdx < 0) return Outcome(emptyList(), "列表视图")

        val out = ArrayList<Course>()
        for (idx in (headerIdx + 1) until grid.size) {
            val row = grid[idx]
            if (row.size <= maxOf(dayCol, secCol)) continue
            val day = dayOfLoose(row[dayCol]?.text() ?: "") ?: continue
            val section = parseSectionLoose(row[secCol]?.text() ?: "") ?: continue

            // 其余列都当作课程信息列（通常只有一列）
            for (i in row.indices) {
                if (i == dayCol || i == secCol) continue
                val cell = row[i] ?: continue
                for (course in extractCoursesFromInfoCell(cell, day, section.first, section.second)) {
                    out.add(course)
                }
            }
        }
        return Outcome(dedupe(out), "列表视图（星期 / 节次 / 课表信息）")
    }

    /** 从"课表信息"单元格里提取（可能多张）课程卡片。 */
    private fun extractCoursesFromInfoCell(
        cell: Element, day: DayOfWeek, secStart: Int, secEnd: Int,
    ): List<Course> {
        val lines = cellLines(cell)
        if (lines.isEmpty()) return emptyList()

        // 按"非标签行 = 新卡片开始"切块
        val blocks = ArrayList<StringBuilder>()
        for (line in lines) {
            val isLabelLine = LABEL_REGEX.containsMatchIn(line)
            if (!isLabelLine || blocks.isEmpty()) blocks.add(StringBuilder(line))
            else blocks.last().append('\n').append(line)
        }
        var blockTexts = blocks.map { it.toString() }.filter { it.isNotBlank() }

        // 兜底：整格只有一行但有多个"周数："时，按"学分：x"再切
        if (blockTexts.size == 1 && Regex("""周数\s*[：:]""").findAll(blockTexts[0]).count() > 1) {
            blockTexts = splitByBlockEnd(blockTexts[0])
        }

        return blockTexts.mapNotNull { parseCourseBlock(it, day, secStart, secEnd) }
    }

    private fun splitByBlockEnd(text: String): List<String> {
        val out = ArrayList<String>()
        var cursor = 0
        for (m in BLOCK_END.findAll(text)) {
            out.add(text.substring(cursor, m.range.last + 1))
            cursor = m.range.last + 1
        }
        if (cursor < text.length) out.add(text.substring(cursor))
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 解析一张课程卡片（标签式字段）。
     * 文本形如：
     *   中华民族共同体概论*
     *   周数：4-5周,8周,11-13周(单),14-16周
     *   校区:雁山校区  上课地点：1226（阶4教室）
     *   教师：文娟(讲师)  教学班：TB29129006-...
     *   学分：2.0
     */
    private fun parseCourseBlock(
        block: String, day: DayOfWeek, secStart: Int, secEnd: Int,
    ): Course? {
        val text = block.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim()
        if (text.isEmpty()) return null

        val matches = LABEL_REGEX.findAll(text).toList()

        // 课程名 = 第一个标签之前的内容（去掉末尾的 * 等标记）
        val rawName = if (matches.isEmpty()) text else text.substring(0, matches.first().range.first)
        val name = rawName.trim().trim('*', '＊', '·', ' ', '\u00a0').trim()
        if (name.isEmpty() || name.length > 60) return null

        fun valueOf(vararg labels: String): String? {
            for (label in labels) {
                val m = matches.firstOrNull { it.groupValues[1] == label } ?: continue
                val start = m.range.last + 1
                val next = matches.firstOrNull { it.range.first > m.range.first }
                val end = next?.range?.first ?: text.length
                val v = text.substring(start, end).trim().trim('；', ';', ',', '，', '|').trim()
                if (v.isNotEmpty()) return v
            }
            return null
        }

        val weeksRaw = valueOf("周数")
        val weeks = weeksRaw?.let { parseWeekSet(it) } ?: emptyList()
        val campus = valueOf("校区")?.let { it.replace(Regex("""\s+"""), "").take(12) }
        val room = valueOf("上课地点", "上课教室")?.let(::cleanLocation).orEmpty()
        // 拼接成"雁山校区 3220（阶7教室）"，与教务系统/超级课程表的展示一致
        val location = when {
            campus.isNullOrBlank() -> room
            room.isBlank() -> campus
            room.contains(campus) -> room
            else -> "$campus $room"
        }
        val teacher = valueOf("教师", "任课教师")?.let(::cleanTeacher).orEmpty()
        val credit = valueOf("学分")?.let { Regex("""\d+(?:\.\d+)?""").find(it)?.value?.toDoubleOrNull() }

        // 既没周次也没地点 → 不像一张课程卡片
        if (weeks.isEmpty() && location.isEmpty()) return null

        val sw = weeks.minOrNull() ?: 1
        val ew = weeks.maxOrNull() ?: 1
        // 若整个周次集合全是奇数/偶数，顺便记录单双周（便于展示）
        val oddEven = when {
            weeks.isEmpty() -> 0
            weeks.all { it % 2 == 1 } -> 1
            weeks.all { it % 2 == 0 } -> 2
            else -> 0
        }

        return Course(
            id = 0,
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = day,
            startIndex = secStart,
            endIndex = secEnd,
            startWeek = sw,
            endWeek = ew,
            oddEven = oddEven,
            category = CoursePalette.inferCategory(name, location),
            credit = credit,
            weeksText = weeksRaw.orEmpty(),
            weeks = weeks,
            raw = text,
        )
    }

    /**
     * 解析复杂周次集合。
     * 例：`4-5周,8周,11-13周(单),14-16周` → [4,5,8,11,13,14,15,16]
     * 支持：区间、单周、逗号分隔、每段带 (单)/(双) 标记。
     */
    private fun parseWeekSet(raw: String): List<Int> {
        if (raw.isBlank()) return emptyList()
        val weeks = sortedSetOf<Int>()
        for (seg in raw.split(',', '，', '、', ';', '；')) {
            val s = seg.trim()
            if (s.isEmpty()) continue
            val odd = s.contains("单")
            val even = s.contains("双")
            val range = Regex("""(\d{1,2})\s*[~\-—－至]\s*(\d{1,2})""").find(s)
            if (range != null) {
                val a = range.groupValues[1].toIntOrNull() ?: continue
                val b = range.groupValues[2].toIntOrNull() ?: continue
                for (w in minOf(a, b)..maxOf(a, b)) {
                    if (odd && w % 2 == 0) continue
                    if (even && w % 2 == 1) continue
                    if (w in 1..30) weeks.add(w)
                }
            } else {
                val n = Regex("""(\d{1,2})""").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                if (n in 1..30) weeks.add(n)
            }
        }
        return weeks.toList()
    }

    private fun cleanLocation(v: String): String =
        v.replace(Regex("""\s+"""), " ").trim().take(30)

    /** 教师：去掉职称括号，"文娟(讲师)" → "文娟"。 */
    private fun cleanTeacher(v: String): String =
        v.replace(Regex("""[（(][^）)]{0,10}[）)]"""), "").trim().take(20)

    // =========================================================================
    // ② ③ 表格视图：节次行 × 星期列
    // =========================================================================
    private fun tryGridLayout(grid: List<List<Element?>>, useHeaderDays: Boolean): Outcome {
        if (grid.size < 2) return Outcome(emptyList(), "表格视图")

        var dayCols: Map<Int, DayOfWeek>? = null
        var secCol = 0
        if (useHeaderDays) {
            for (row in grid) {
                val map = HashMap<Int, DayOfWeek>()
                row.forEachIndexed { i, c -> dayOf(c?.text() ?: "")?.let { map[i] = it } }
                if (map.size >= 5) {
                    dayCols = map
                    secCol = (0 until (map.keys.max() + 1)).firstOrNull { it !in map.keys } ?: 0
                    break
                }
            }
            if (dayCols == null) return Outcome(emptyList(), "表格视图")
        }

        val out = ArrayList<Course>()
        for (row in grid) {
            if (row.size < 3) continue
            val mapping = dayCols ?: buildDefaultDayCols(row.size)
            val section = parseSectionLoose(row[secCol]?.text() ?: "") ?: continue
            for ((colIdx, day) in mapping) {
                if (colIdx >= row.size) continue
                val cell = row[colIdx] ?: continue
                for (entry in extractEntriesFromGridCell(cell)) {
                    parseGridEntry(entry, day, section.first, section.second)?.let { out.add(it) }
                }
            }
        }
        return Outcome(
            dedupe(out),
            if (useHeaderDays) "表格视图（按表头星期）" else "表格视图（默认列序）",
        )
    }

    private fun buildDefaultDayCols(cellCount: Int): Map<Int, DayOfWeek> {
        val map = HashMap<Int, DayOfWeek>()
        for (i in 1..7) if (i < cellCount) map[i] = DayOfWeek.of(i)
        return map
    }

    /** 表格视图单元格：有 div 用 div，否则按行合并（同一门课的课程名/教师/周次/地点可能分多行）。 */
    private fun extractEntriesFromGridCell(cell: Element): List<String> {
        val divs = cell.select("div").filter { it.text().isNotBlank() }
        if (divs.isNotEmpty()) {
            val leaf = divs.filter { it.select("div").isEmpty() }
            val chosen = (if (leaf.isNotEmpty()) leaf else divs).map { it.text().trim() }
            val withWeeks = chosen.filter { Regex("""周""").containsMatchIn(it) }
            return (if (withWeeks.isNotEmpty()) withWeeks else chosen).distinct()
        }
        val lines = cellLines(cell)
        if (lines.isEmpty()) return emptyList()
        if (lines.size == 1) return lines
        val totalHits = lines.sumOf { Regex("""\d\s*[~\-—－至]?\s*\d*\s*周""").findAll(it).count() }
        if (totalHits <= 1) return listOf(lines.joinToString(" "))
        // 多段：按"含周次"行分块
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var curWeeks = 0
        for (line in lines) {
            val w = Regex("""\d\s*[~\-—－至]?\s*\d*\s*周""").findAll(line).count()
            if (curWeeks > 0 && w > 0) { out.add(cur.toString().trim()); cur.clear(); curWeeks = 0 }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(line)
            curWeeks += w
        }
        if (cur.isNotBlank()) out.add(cur.toString().trim())
        return out
    }

    /** 表格视图单元格：优先用标签式解析，退化到通用启发式。 */
    private fun parseGridEntry(
        raw: String, day: DayOfWeek, secStart: Int, secEnd: Int,
    ): Course? {
        // 含标签 → 走标签解析
        if (LABEL_REGEX.containsMatchIn(raw)) {
            parseCourseBlock(raw, day, secStart, secEnd)?.let { return it }
        }
        return parseHeuristic(raw, day, secStart, secEnd)
    }

    /** 通用启发式：`高等数学(分组A)张三 1-16周[01-02节]明德楼B104` 这类纯文本。 */
    private fun parseHeuristic(
        raw: String, day: DayOfWeek, secStart: Int, secEnd: Int,
    ): Course? {
        val text = raw.replace('\u00a0', ' ').replace(Regex("""\s+"""), " ").trim()
        val weeks = parseWeekSet(text)
        if (weeks.isEmpty()) return null

        // 原始周次片段与单双周标记（用于展示）
        val weekFragments = WEEKS_FRAGMENT.findAll(text).map { it.value.trim() }.toList()
        val oddEven = when {
            text.contains("单周") || text.contains("(单)") || text.contains("（单）") -> 1
            text.contains("双周") || text.contains("(双)") || text.contains("（双）") -> 2
            else -> 0
        }
        val weeksText = (
            weekFragments.joinToString(",") +
                when (oddEven) { 1 -> "(单)"; 2 -> "(双)"; else -> "" }
            ).trim(',', ' ')

        var rest = text
        WEEKS_FRAGMENT.findAll(rest).toList().forEach { rest = rest.replace(it.value, " ") }
        rest = rest.replace(SEC_BRACKET, " ")
        rest = rest.replace(SEC_RANGE, " ").replace(SEC_SINGLE, " ")
        rest = rest.replace(Regex("""[（(]\s*[单双]\s*[）)]"""), " ")
        rest = rest.replace(Regex("""[（(]\s*[）)]"""), " ")
        rest = rest.replace(Regex("""\s+"""), " ").trim()

        val tokens = rest.split(Regex("""[\s,，;；/|]+""")).filter { it.isNotBlank() }.toMutableList()
        if (tokens.isEmpty()) return null

        var location = ""
        var teacher = ""
        val locIdx = tokens.indexOfFirst {
            Regex("""(楼|教室|馆|场|室|区|机房|报告厅|实训|中心)""").containsMatchIn(it) && it.length in 2..20
        }
        if (locIdx >= 0) location = tokens.removeAt(locIdx)

        val tIdx = tokens.indices.lastOrNull {
            it > 0 && Regex("""^[\u4e00-\u9fa5]{2,4}$""").matches(tokens[it])
        }
        if (tIdx != null) teacher = tokens.removeAt(tIdx)

        var name = tokens.firstOrNull()?.trim().orEmpty()
        if (name.isEmpty()) name = rest.trim().take(30)
        if (name.isEmpty()) return null

        if (teacher.isEmpty()) {
            val m = Regex("""[)）]\s*([\u4e00-\u9fa5]{2,4})$""").find(name)
            if (m != null) {
                teacher = m.groupValues[1]
                name = name.substring(0, m.range.first + 1).trim()
            }
        }
        if (name.isEmpty()) return null

        return Course(
            id = 0, name = name, teacher = teacher, location = location,
            dayOfWeek = day, startIndex = secStart, endIndex = secEnd,
            startWeek = weeks.min(), endWeek = weeks.max(),
            oddEven = oddEven,
            category = CoursePalette.inferCategory(name, location),
            weeksText = weeksText.ifBlank { "${weeks.min()}-${weeks.max()}周" },
            weeks = weeks,
            raw = text,
        )
    }

    // =========================================================================
    // 附加信息：学期名 / 当前周次
    // =========================================================================

    /**
     * 从课表页文本里猜学期名，如 "2026-2027 第1学期"。
     * 实际页面写法：`2026-2027学年第1学期`（可能被其它文字打断，故这里容忍中间的少量非数字噪声）。
     */
    fun guessSemester(html: String): String? {
        if (html.isBlank()) return null
        val text = Jsoup.parse(html).text().replace('\u00a0', ' ')

        val patterns = listOf(
            // 2026-2027学年第1学期 / 2026-2027 学年 第一学期
            Regex("""(20\d{2})\s*[-—~－至]\s*(20\d{2})[^0-9]{0,8}?第\s*([12一二])\s*学期"""),
            // 允许"学年"与"第X学期"之间被打断
            Regex("""(20\d{2})\s*[-—~－至]\s*(20\d{2})\s*学年"""),
            // 2026-2027-1
            Regex("""(20\d{2})\s*[-—~－至]\s*(20\d{2})\s*[-—~－至]\s*([12])(?!\d)"""),
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val year = "${m.groupValues[1]}-${m.groupValues[2]}"
            val term = m.groupValues.getOrNull(3)?.let {
                when (it) {
                    "1", "一" -> "第1学期"
                    "2", "二" -> "第2学期"
                    else -> ""
                }
            } ?: ""
            return listOf(year, term).filter { it.isNotBlank() }.joinToString(" ")
        }
        return null
    }

    /** 从课表页文本里猜"当前第几周"（页面上的"第N周"指示）。 */
    fun guessCurrentWeek(html: String): Int? {
        if (html.isBlank()) return null
        val text = Jsoup.parse(html).text()
        val m = Regex("""第\s*(\d{1,2})\s*周""").find(text) ?: return null
        val w = m.groupValues[1].toIntOrNull() ?: return null
        return if (w in 1..30) w else null
    }

    // =========================================================================
    // ④ div 网格（实验性）
    // =========================================================================
    private fun tryDivGrid(doc: Document): Outcome {
        val out = ArrayList<Course>()
        for (b in doc.select("div, li, p")) {
            val t = b.text().trim()
            if (t.length < 8 || t.length > 600) continue
            val day = dayOf(t) ?: continue
            val section = parseSectionLoose(t) ?: continue
            parseGridEntry(t, day, section.first, section.second)?.let { out.add(it) }
        }
        return Outcome(dedupe(out), "div 网格（实验性）")
    }

    // =========================================================================
    // 工具
    // =========================================================================

    /** 只取顶层表格，避免把课程卡片里嵌套的小表当成课表。 */
    private fun directTables(doc: Document): List<Element> =
        doc.select("table").filter { t -> t.parents().none { it.tagName() == "table" } }

    /** 只取直接子行（排除嵌套表格的行）。 */
    private fun directRows(table: Element): List<Element> {
        val rows = ArrayList<Element>()
        for (child in table.children()) {
            when (child.tagName()) {
                "tr" -> rows.add(child)
                "tbody", "thead", "tfoot" -> rows.addAll(child.children().filter { it.tagName() == "tr" })
            }
        }
        return rows
    }

    private fun directCells(row: Element): List<Element> =
        row.children().filter { it.tagName() == "td" || it.tagName() == "th" }

    /**
     * 展开表格为矩形网格，处理 rowspan / colspan。
     * 被合并覆盖的格子会复用来源元素（因此带 rowspan 的"星期一"在其覆盖的每一行都能读到）。
     */
    private fun expandTable(table: Element): List<List<Element?>> {
        val rows = directRows(table)
        if (rows.isEmpty()) return emptyList()
        val grid = ArrayList<MutableList<Element?>>()
        for (rIdx in rows.indices) {
            while (grid.size <= rIdx) grid.add(ArrayList())
            var cIdx = 0
            for (cell in directCells(rows[rIdx])) {
                // 跳过已被上方 rowspan 占用的位置
                while (cIdx < grid[rIdx].size && grid[rIdx][cIdx] != null) cIdx++
                val rowspan = cell.attr("rowspan").toIntOrNull()?.coerceIn(1, 200) ?: 1
                val colspan = cell.attr("colspan").toIntOrNull()?.coerceIn(1, 20) ?: 1
                for (dr in 0 until rowspan) {
                    val rr = rIdx + dr
                    while (grid.size <= rr) grid.add(ArrayList())
                    for (dc in 0 until colspan) {
                        val cc = cIdx + dc
                        while (grid[rr].size <= cc) grid[rr].add(null)
                        grid[rr][cc] = cell
                    }
                }
                cIdx += colspan
            }
        }
        return grid.map { it.toList() }
    }

    /** 保留行结构的文本行（把 <br> 与块级标签边界转成哨兵再切分）。 */
    private fun cellLines(cell: Element): List<String> {
        val html = cell.html()
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), NL)
            .replace(Regex("""</(div|p|li|tr|h[1-6]|table)>""", RegexOption.IGNORE_CASE), NL)
            .replace(Regex("""<(div|p|li|tr|h[1-6]|table)[^>]*>""", RegexOption.IGNORE_CASE), NL)
        val text = Jsoup.parse(html).text()
        return text.split(NL).map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 严格：表头必须是"星期X / 周X / 礼拜X"。 */
    private fun dayOf(text: String): DayOfWeek? {
        val t = text.trim().replace(" ", "")
        if (t.isEmpty()) return null
        if (!(t.contains("星期") || t.contains("周") || t.contains("礼拜"))) return null
        for ((c, d) in DAY_CHAR) if (t.contains(c)) return d
        return null
    }

    /** 宽松：已知该列是"星期"列时，允许只写"一/周一/星期一"。 */
    private fun dayOfLoose(text: String): DayOfWeek? {
        dayOf(text)?.let { return it }
        val t = text.trim().replace(" ", "").replace("\n", "")
        if (t.isEmpty() || t.length > 4) return null
        var found: DayOfWeek? = null
        var count = 0
        for ((c, d) in DAY_CHAR) if (t.contains(c)) { found = d; count++ }
        return if (count == 1) found else null
    }

    /** 节次：兼容 "第1~2节" / "1-2" / "3" / "第一大节"。 */
    private fun parseSectionLoose(text: String): Pair<Int, Int>? {
        val t = text.trim()
        if (t.isEmpty()) return null
        SEC_RANGE.find(t)?.let {
            val a = it.groupValues[1].toIntOrNull() ?: return@let
            val b = it.groupValues[2].toIntOrNull() ?: return@let
            return minOf(a, b) to maxOf(a, b)
        }
        SEC_SINGLE.find(t)?.let {
            val a = it.groupValues[1].toIntOrNull() ?: return@let
            return a to a
        }
        SEC_BARE_RANGE.find(t)?.let {
            val a = it.groupValues[1].toIntOrNull() ?: return@let
            val b = it.groupValues[2].toIntOrNull() ?: return@let
            if (a in 1..20 && b in 1..20) return minOf(a, b) to maxOf(a, b)
        }
        SEC_BARE_SINGLE.find(t)?.let {
            val a = it.groupValues[1].toIntOrNull() ?: return@let
            if (a in 1..20) return a to a
        }
        return null
    }

    private fun dedupe(list: List<Course>): List<Course> {
        val seen = LinkedHashMap<String, Course>()
        for (c in list) {
            val key = "${c.name}|${c.teacher}|${c.location}|${c.dayOfWeek}|" +
                "${c.startIndex}-${c.endIndex}|${c.weeks.joinToString(",")}"
            if (!seen.containsKey(key)) seen[key] = c
        }
        return seen.values.toList()
    }
}
