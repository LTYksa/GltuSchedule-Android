// GLTU 课表 App —— 小部件的「显示哪一天 / 显示什么状态」规则
//
// 抽成纯函数是为了能单测：时间边界（23:30、最后一节课下课）是最容易写错的地方。
package com.ltyksa.gltuschedule.widget

import com.ltyksa.gltuschedule.data.GltuTimeTable
import com.ltyksa.gltuschedule.model.Course
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object WidgetTiming {

    /** 过了这个点，小部件就显示「明天」的课。 */
    const val NEXT_DAY_HOUR = 23
    const val NEXT_DAY_MINUTE = 30

    /**
     * 小部件当前该显示哪一天。
     *
     * **23:30 之后算第二天** —— 这个点用户基本已经睡了，
     * 再显示今天的课没意义；提前切成明天的课，第二天早上解锁就能直接看到。
     */
    fun displayDate(now: LocalDateTime): LocalDate {
        val d = now.toLocalDate()
        val lateNight = now.hour > NEXT_DAY_HOUR ||
            (now.hour == NEXT_DAY_HOUR && now.minute >= NEXT_DAY_MINUTE)
        return if (lateNight) d.plusDays(1) else d
    }

    /**
     * 「显示日」的课是否**全部上完**了。
     *
     * 判定用最后一节课的**下课时间**；没有课或查不到节次时返回 false
     * （那种情况由「今天没有课」分支去处理，不该在这里冒充「上完了」）。
     */
    fun allDone(now: LocalDateTime, courses: List<Course>): Boolean {
        val end = lastEndTime(courses) ?: return false
        return !now.toLocalTime().isBefore(end)
    }

    /** 一天里最后一节课的下课时刻；没课/查不到返回 null */
    fun lastEndTime(courses: List<Course>): LocalTime? {
        if (courses.isEmpty()) {
            return null
        }
        val lastEndIndex = courses.maxOfOrNull { it.endIndex } ?: return null
        val slot = GltuTimeTable.byIndex(lastEndIndex) ?: return null
        return parseHm(slot.endTime)
    }

    /**
     * 「今天最后一节课下课」对应的毫秒时间戳 —— 用来排准点刷新。
     * 已经过了、或今天没课，返回 null。
     */
    fun lastClassEndMillis(now: LocalDateTime, courses: List<Course>): Long? {
        val end = lastEndTime(courses) ?: return null
        val at = now.toLocalDate().atTime(end)
        if (!at.isAfter(now)) {
            return null
        }
        return at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** "21:30" → LocalTime；解析不了返回 null */
    private fun parseHm(hm: String): LocalTime? {
        val parts = hm.split(":")
        if (parts.size != 2) {
            return null
        }
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            return null
        }
        return LocalTime.of(h, m)
    }

    /** 下一天的 23:30（本地时间）对应的毫秒时间戳 —— 用来排「切到明天」的精确刷新 */
    fun nextFlipMillis(now: LocalDateTime): Long {
        var t = now.withHour(NEXT_DAY_HOUR).withMinute(NEXT_DAY_MINUTE)
            .withSecond(0).withNano(0)
        if (!t.isAfter(now)) {
            t = t.plusDays(1)
        }
        return t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
