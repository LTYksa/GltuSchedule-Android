// GLTU 课表 App —— 学期信息存储
// 保存：用户选择的开学日期（原样保留用于显示）+ 推导出的"第一周周一"（用于周次换算）+ 学期名。
package com.gltu.schedule.data

import android.content.Context
import android.content.SharedPreferences
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object SemesterStore {

    private const val PREFS = "gltu_semester"
    private const val KEY_FIRST_MONDAY = "first_week_monday"
    private const val KEY_START_RAW = "start_date_raw"
    private const val KEY_SEMESTER = "semester_name"

    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 保存开学日期。
     * [pickedDate] 是用户自己选的日期（原样保存用于显示）；
     * "第一周周一"由后台推导为所选日期所在周的周一，用于周次换算。
     */
    fun save(context: Context, pickedDate: LocalDate, semester: String) {
        val monday = pickedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        prefs(context).edit()
            .putString(KEY_START_RAW, fmt.format(pickedDate))
            .putString(KEY_FIRST_MONDAY, fmt.format(monday))
            .putString(KEY_SEMESTER, semester)
            .apply()
    }

    /** 用户选择的开学日期（侧边栏显示用）；未设置时回退到第一周周一。 */
    fun startDateRaw(context: Context): LocalDate {
        val raw = prefs(context).getString(KEY_START_RAW, null)
        if (raw != null) {
            runCatching { LocalDate.parse(raw, fmt) }.getOrNull()?.let { return it }
        }
        return firstWeekMonday(context)
    }

    /** 第一周周一（周次换算基准）。 */
    fun firstWeekMonday(context: Context): LocalDate {
        val raw = prefs(context).getString(KEY_FIRST_MONDAY, null)
        if (raw != null) {
            runCatching { LocalDate.parse(raw, fmt) }.getOrNull()?.let { return it }
        }
        return fallbackMonday()
    }

    fun semesterName(context: Context): String =
        prefs(context).getString(KEY_SEMESTER, "") ?: ""

    /** 只更新学期名。 */
    fun setSemesterName(context: Context, semester: String) {
        prefs(context).edit().putString(KEY_SEMESTER, semester.trim()).apply()
    }

    /** 当前是第几周（1 起）。 */
    fun currentWeek(context: Context): Int {
        val first = firstWeekMonday(context)
        val thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = ChronoUnit.WEEKS.between(first, thisMonday)
        return (weeks + 1).toInt().coerceAtLeast(1)
    }

    /** 指定日期属于第几周（早于开学返回 0）。 */
    fun weekOf(context: Context, date: LocalDate): Int {
        val first = firstWeekMonday(context)
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = ChronoUnit.WEEKS.between(first, monday)
        return (weeks + 1).toInt()
    }

    private fun fallbackMonday(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
