// GLTU 课表 App —— 作息时间节次数据（Kotlin）
// 依据：桂林旅游学院 2026-2027 学年作息时间表（官方图片 OCR）
//      + 教务系统实际课表页/超级课程表显示的小节时间逐条核对
package com.ltyksa.gltuschedule.data

/**
 * 单个节次（上课时间段）。课表课程会引用 [index] 节次。
 * startTime/endTime 为 24 小时制 "HH:mm"。
 */
data class TimeSlot(
    val index: Int,          // 节次序号，从 1 开始
    val name: String,        // 显示名，如 "第1节"
    val startTime: String,   // 上课开始时间
    val endTime: String,     // 上课结束时间
    val section: DaySection, // 上午/下午/晚上
)

enum class DaySection { MORNING, AFTERNOON, EVENING }

/**
 * 桂林旅游学院 2026-2027 学年作息时间表（拆到小节，共 13 节）。
 *
 * 与教务系统节次编号严格对齐：教务系统 `kcsj` 的"第1~2节"即 index 1 与 2。
 * 例：第1-2节 = 08:20–09:40；第6-7节 = 14:00–15:25；第10-11节 = 18:30–19:55。
 */
object GltuTimeTable {
    val slots: List<TimeSlot> = listOf(
        TimeSlot(1,  "第1节",  "08:20", "08:50", DaySection.MORNING),
        TimeSlot(2,  "第2节",  "09:00", "09:40", DaySection.MORNING),
        TimeSlot(3,  "第3节",  "09:50", "10:30", DaySection.MORNING),
        TimeSlot(4,  "第4节",  "10:40", "11:20", DaySection.MORNING),
        TimeSlot(5,  "第5节",  "11:25", "12:05", DaySection.MORNING),
        TimeSlot(6,  "第6节",  "14:00", "14:40", DaySection.AFTERNOON),
        TimeSlot(7,  "第7节",  "14:45", "15:25", DaySection.AFTERNOON),
        TimeSlot(8,  "第8节",  "15:35", "16:15", DaySection.AFTERNOON),
        TimeSlot(9,  "第9节",  "16:20", "17:00", DaySection.AFTERNOON),
        TimeSlot(10, "第10节", "18:30", "19:10", DaySection.EVENING),
        TimeSlot(11, "第11节", "19:15", "19:55", DaySection.EVENING),
        TimeSlot(12, "第12节", "20:05", "20:45", DaySection.EVENING),
        TimeSlot(13, "第13节", "20:50", "21:30", DaySection.EVENING),
    )

    fun byIndex(i: Int): TimeSlot? = slots.firstOrNull { it.index == i }

    /** 节次区间显示名："第1-2节" / "第3节"。 */
    fun rangeLabel(start: Int, end: Int): String {
        val a = minOf(start, end)
        val b = maxOf(start, end)
        return if (a == b) "第$a" + "节" else "第$a-$b" + "节"
    }

    /** 合并时间区间："08:20–09:40"。 */
    fun rangeTime(start: Int, end: Int): String {
        val s = byIndex(minOf(start, end))
        val e = byIndex(maxOf(start, end))
        return if (s == null || e == null) "" else "${s.startTime}–${e.endTime}"
    }

    /**
     * 解析节次范围字符串为节次列表。
     * 例如 "1-2" -> [slot1, slot2]，"3" -> [slot3]。
     */
    fun parseSlots(range: String): List<TimeSlot> {
        val parts = range.split("-").mapNotNull { it.trim().toIntOrNull() }
        if (parts.isEmpty()) return emptyList()
        val start = parts.first()
        val end = parts.getOrElse(1) { start }
        return (start..end).mapNotNull { byIndex(it) }
    }
}
