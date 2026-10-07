// GLTU 课表 App —— 小部件数据源（Room 实现）
package com.gltu.schedule.database

import com.gltu.schedule.repository.ScheduleRepository
import com.gltu.schedule.widget.WidgetDataSource
import com.gltu.schedule.widget.WidgetSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 实现 widget.WidgetDataSource 接口，从 Room 读取课程供小部件展示。
 * 由 GltuScheduleApp 初始化时注入到 widget.WidgetDataHolder.source。
 *
 * 注意：load() 内部使用同步 Room 查询，请在后台线程调用
 * （RemoteViewsFactory.onDataSetChanged 运行在 Binder 线程池，天然满足）。
 */
class RoomWidgetDataSource(
    private val repository: ScheduleRepository,
) : WidgetDataSource {

    override fun load(day: DayOfWeek, firstWeekMonday: LocalDate): WidgetSchedule {
        val today = LocalDate.now()
        val week = computeCurrentWeek(firstWeekMonday, today)
        val todayCourses = repository.getDayCoursesSync(day).filter { it.occursOnWeek(week) }
        val weekCourses = repository.getWeekCoursesSync()
        return WidgetSchedule(
            currentWeek = week,
            todayCourses = todayCourses,
            weekCourses = weekCourses,
        )
    }

    /** 由"第一周周一"与今天推算出当前周次（1 起）。 */
    private fun computeCurrentWeek(firstWeekMonday: LocalDate, today: LocalDate): Int {
        val mondayOfThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = ChronoUnit.WEEKS.between(firstWeekMonday, mondayOfThisWeek)
        return (weeks + 1).toInt().coerceAtLeast(1)
    }
}
