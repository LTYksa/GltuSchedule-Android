// GLTU 课表 App —— 课表页偏好设置
package com.gltu.schedule.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 课表页相关设置（侧边栏"课表设置"里可调）。
 */
object SchedulePreferences {

    private const val PREFS = "gltu_schedule_prefs"

    private const val KEY_MAX_PERIODS = "max_periods"
    private const val KEY_WEEK_START = "week_start"
    private const val KEY_SHOW_ALL_WEEKS = "show_all_weeks"
    private const val KEY_THEME_MODE = "theme_mode"

    /** 主题模式。 */
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 课表显示到第几节（默认 13 = 全部）。 */
    fun maxPeriods(context: Context): Int =
        prefs(context).getInt(KEY_MAX_PERIODS, GltuTimeTable.slots.size)

    fun setMaxPeriods(context: Context, n: Int) {
        prefs(context).edit().putInt(KEY_MAX_PERIODS, n.coerceIn(4, GltuTimeTable.slots.size)).apply()
    }

    /** 每周起始日：1=周一，7=周日（默认周一）。 */
    fun weekStart(context: Context): Int =
        prefs(context).getInt(KEY_WEEK_START, 1)

    fun setWeekStart(context: Context, dayValue: Int) {
        prefs(context).edit().putInt(KEY_WEEK_START, if (dayValue == 7) 7 else 1).apply()
    }

    /** 是否显示非本周课程（默认关）。开启后不再按周次过滤。 */
    fun showAllWeeks(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_ALL_WEEKS, false)

    fun setShowAllWeeks(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_ALL_WEEKS, enabled).apply()
    }

    /** 主题：0=跟随系统 1=浅色 2=深色。 */
    fun themeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, THEME_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }
}
