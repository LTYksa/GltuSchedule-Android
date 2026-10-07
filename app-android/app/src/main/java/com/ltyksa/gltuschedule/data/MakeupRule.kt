// GLTU 课表 App —— 调休（补课）规则推导
//
// 背景：国务院办公厅的放假通知只说「10月11日（星期六）上班」，
// **不会**说这天补周几的课 ——「补周三」是学校另行通知的。
// 所以软件只能按历年惯例推导，并允许用户手动覆盖。
//
// 推导规则（已用 2025 春节/劳动节/国庆 三组实际通知验证）：
//   假期区间内被占用的「工作日」，从区间**末尾**往前取 N 个，
//   N = 关联到该区间的调休日数量；调休日按时间顺序与它们一一对应。
//
//   国庆 10/1–10/8 放假，调休 9/28(日) 与 10/11(六)
//     区间内工作日 = 10/1三 10/2四 10/3五 10/6一 10/7二 10/8三
//     取末尾 2 个 → [10/7(二), 10/8(三)]
//     9/28 → 补 10/7，10/11 → 补 10/8
//
// **关联方式：优先按「假期名」匹配，其次才按日期邻近。**
// 原因：GLTU 的寒假（1/18–2/23）会把春节（1/28–2/4）整个包住，
// 两块在日期上连成一片；只按邻近关联的话，春节的补班日会被错配到寒假块上。
// 而 holiday-cn 的补班日数据里 name 与所属假期同名
// （如 {"date":"2025-02-08","name":"春节","isOffDay":false}），
// 用名字匹配能稳定还原正确的区间。
//
// **调休日代替的是「具体某一天」，连周次一起搬**：
// 9/28 在第 4 周、10/7 在第 6 周，补的是第 6 周周二的课，
// 而不是第 4 周周二的课（那天的课本来就正常上过了）。
package com.ltyksa.gltuschedule.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 一条调休规则：调休上班日 [date] 上 [sourceDate] 那天的课。
 *
 * @param manual 是否由用户手动指定（false = 自动推导）
 */
data class MakeupRule(
    val date: LocalDate,
    val sourceDate: LocalDate,
    val manual: Boolean = false,
) {
    /** 补的是周几。 */
    val sourceWeekday: DayOfWeek get() = sourceDate.dayOfWeek

    /** 摘要文本，如「补 10/7（周二）的课」。 */
    val summary: String
        get() = "补 ${sourceDate.monthValue}/${sourceDate.dayOfMonth}（${weekdayLabel(sourceWeekday)}）的课"
}

/** 周几 → 「周二」 */
internal fun weekdayLabel(day: DayOfWeek): String = "周" + when (day) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

internal fun isWeekendDay(d: LocalDate): Boolean =
    d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

object MakeupResolver {

    /** 按日期邻近兜底关联时，调休日与假期区间的最大距离（天）。 */
    private const val NEAR_DAYS = 7L

    /**
     * 按名字关联时的最大距离（天）。
     * 真实数据里补班日离假期很近（春节 1/26 与区间 1/28 差 2 天，2/8 与 2/4 差 4 天），
     * 放宽到 21 天既能过滤掉明显异常的数据，又不会误伤真实安排。
     */
    private const val NAME_NEAR_DAYS = 21L

    /** 一个假期区间。 */
    private class Block(val name: String, val dates: List<LocalDate>) {
        val first: LocalDate get() = dates.first()
        val last: LocalDate get() = dates.last()
        val workdays: List<LocalDate> get() = dates.filter { !isWeekendDay(it) }
    }

    /**
     * 推导全部调休规则。
     *
     * @param holidays 全部假期（含 MAKEUP 补班日）
     * @param overrides 用户手动覆盖：调休日 → 要补的星期
     */
    fun resolve(
        holidays: List<Holiday>,
        overrides: Map<LocalDate, DayOfWeek> = emptyMap(),
    ): List<MakeupRule> {
        val blocks = buildBlocks(holidays)
        val makeupHolidays = holidays.filter { it.type == HolidayType.MAKEUP }

        // 调休日 → 源日期
        val assigned = LinkedHashMap<LocalDate, LocalDate>()

        // ① 按假期名关联（主路径）
        val makeupByName = LinkedHashMap<String, MutableList<LocalDate>>()
        for (h in makeupHolidays) {
            val list = makeupByName.getOrPut(h.name) { ArrayList() }
            for (d in expand(h)) if (!list.contains(d)) list.add(d)
        }
        for ((name, dates) in makeupByName) {
            val block = blocks
                .filter { nameMatches(it.name, name) }
                .filter { distanceToBlock(it, dates) <= NAME_NEAR_DAYS }
                .minByOrNull { distanceToBlock(it, dates) }
                ?: continue
            val workdays = block.workdays
            if (workdays.isEmpty()) continue
            val sorted = dates.sorted()
            val n = minOf(sorted.size, workdays.size)
            val sources = workdays.takeLast(n)
            for (i in 0 until n) assigned[sorted[i]] = sources[i]
        }

        // ② 名字匹配不上的调休日 → 按日期邻近兜底
        for (h in makeupHolidays) {
            for (d in expand(h)) {
                if (assigned.containsKey(d)) continue
                val block = blocks.minByOrNull { distanceToBlock(it, listOf(d)) } ?: continue
                if (distanceToBlock(block, listOf(d)) > NEAR_DAYS) continue
                val workdays = block.workdays
                if (workdays.isEmpty()) continue
                assigned[d] = workdays.last()
            }
        }

        // ③ 手动覆盖优先级最高
        for ((date, weekday) in overrides) {
            assigned[date] = sourceFor(date, weekday, blocks)
        }

        return assigned.entries
            .map { MakeupRule(it.key, it.value, overrides.containsKey(it.key)) }
            .sortedBy { it.date }
    }

    /**
     * 给定调休日与目标星期，找出具体的源日期。
     * 优先在最近的假期区间里找同星期的那天；找不到就取 **该调休日所在周** 的对应星期。
     */
    private fun sourceFor(
        date: LocalDate,
        weekday: DayOfWeek,
        blocks: List<Block>,
    ): LocalDate {
        val block = blocks.minByOrNull { distanceToBlock(it, listOf(date)) }
        if (block != null) {
            block.dates.lastOrNull { it.dayOfWeek == weekday }?.let { return it }
        }
        // 兜底：该调休日所在周的对应星期（10/11 周六 + 周三 → 10/8）
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return monday.plusDays((weekday.value - 1).toLong())
    }

    /** 假期名是否匹配（同名或互相包含：「国庆节」与「国庆节后补班」）。 */
    private fun nameMatches(blockName: String, makeupName: String): Boolean {
        if (blockName.isEmpty() || makeupName.isEmpty()) return false
        return blockName == makeupName ||
            blockName.contains(makeupName) ||
            makeupName.contains(blockName)
    }

    private fun distanceToBlock(block: Block, dates: List<LocalDate>): Long =
        dates.minOf { d ->
            when {
                d.isBefore(block.first) -> daysBetween(d, block.first)
                d.isAfter(block.last) -> daysBetween(block.last, d)
                else -> 0L
            }
        }

    /** 把假期切成区间：同名假期合并，连续的日期算一块。 */
    private fun buildBlocks(holidays: List<Holiday>): List<Block> {
        val byName = LinkedHashMap<String, MutableList<Holiday>>()
        for (h in holidays) {
            if (h.type == HolidayType.MAKEUP) continue
            byName.getOrPut(h.name) { ArrayList() }.add(h)
        }
        val out = ArrayList<Block>()
        for ((name, list) in byName) {
            val days = sortedSetOf<LocalDate>()
            for (h in list) days.addAll(expand(h))
            var current = ArrayList<LocalDate>()
            for (d in days) {
                if (current.isEmpty() || daysBetween(current.last(), d) == 1L) {
                    current.add(d)
                } else {
                    out.add(Block(name, current))
                    current = ArrayList()
                    current.add(d)
                }
            }
            if (current.isNotEmpty()) out.add(Block(name, current))
        }
        return out
    }

    /** 展开成一个假期占用的所有日期（闭区间）。 */
    private fun expand(h: Holiday): List<LocalDate> {
        val out = ArrayList<LocalDate>(8)
        var d = h.start
        var guard = 0
        while (!d.isAfter(h.end) && guard < 400) {
            out.add(d)
            d = d.plusDays(1)
            guard++
        }
        return out
    }

    private fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
}
