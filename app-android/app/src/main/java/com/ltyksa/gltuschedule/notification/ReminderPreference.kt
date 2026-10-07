// GLTU 课表 App —— 上课提醒的用户设置
package com.ltyksa.gltuschedule.notification

import android.content.Context
import android.content.SharedPreferences

/** 上课提醒设置（开关、提前分钟数 1–60）。 */
object ReminderPreference {

    private const val PREFS = "gltu_reminder_prefs"
    private const val KEY_ENABLED = "reminder_enabled"
    private const val KEY_ADVANCE = "reminder_advance_minutes"

    /** 允许的提前分钟范围。 */
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 60

    /** 默认：课前 30 分钟与 10 分钟各提醒一次。 */
    val DEFAULT_MINUTES = listOf(30, 10)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** 提前提醒分钟数（升序、去重、限制在 1–60）。 */
    fun advanceMinutes(context: Context): List<Int> {
        val raw = prefs(context).getString(KEY_ADVANCE, null) ?: return DEFAULT_MINUTES
        val list = raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in MIN_MINUTES..MAX_MINUTES }
            .distinct()
            .sortedDescending()
        return if (list.isEmpty()) DEFAULT_MINUTES else list
    }

    fun setAdvanceMinutes(context: Context, minutes: List<Int>) {
        val cleaned = minutes
            .filter { it in MIN_MINUTES..MAX_MINUTES }
            .distinct()
            .sortedDescending()
            .ifEmpty { DEFAULT_MINUTES }
        prefs(context).edit().putString(KEY_ADVANCE, cleaned.joinToString(",")).apply()
    }

    /** 人类可读描述，如 "课前 30、10 分钟"。 */
    fun describe(context: Context): String =
        advanceMinutes(context).joinToString("、") { it.toString() } + " 分钟"
}
