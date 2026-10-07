// GLTU 课表 App —— 小部件数据源抽象
// 由数据层（Room）实现；小部件只依赖本接口，便于适配多种规格。
package com.gltu.schedule.widget

import com.gltu.schedule.model.Course
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 小部件需要展示的课程数据。
 * @param currentWeek 当前是第几周（1 起）。
 * @param todayCourses 今天（按小部件时区）的课程，按节次排序。
 * @param weekCourses 本周全部课程，用于大尺寸小部件展示周课表。
 */
data class WidgetSchedule(
    val currentWeek: Int,
    val todayCourses: List<Course>,
    val weekCourses: List<Course>,
)

/** 抽象数据源：小部件与存储解耦。 */
interface WidgetDataSource {
    /** 读取某一天对应的周次与课程。 */
    fun load(day: DayOfWeek, firstWeekMonday: LocalDate): WidgetSchedule
}
