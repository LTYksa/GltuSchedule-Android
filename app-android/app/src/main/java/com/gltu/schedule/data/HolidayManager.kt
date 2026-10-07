// GLTU 课表 App —— 节假日统一管理
// 支持：联网同步的法定节假日（含补班日）+ 内置校历假期 + 用户自定义；三种屏蔽模式。
package com.gltu.schedule.data

import android.content.Context
import android.content.SharedPreferences
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HolidayManager {

    private const val PREFS = "gltu_holiday_prefs"

    // 三个独立开关
    private const val KEY_BLOCK_STATUTORY = "block_statutory"   // 法定节假日
    private const val KEY_BLOCK_SCHOOL = "block_school"         // 学校假期（校运会/寒暑假）
    private const val KEY_BLOCK_WEEKEND = "block_weekend"       // 日常周末

    // 旧版单一模式（仅用于迁移）
    private const val KEY_MODE = "block_mode"
    private const val KEY_BLOCK_ENABLED = "block_enabled"

    private const val KEY_CUSTOM = "custom_holidays"
    private const val KEY_SYNCED = "synced_holidays"
    private const val KEY_SYNC_AT = "synced_at"
    private const val KEY_SYNC_SOURCE = "synced_source"

    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * 假期数据版本号：同步成功 / 增删自定义假期 / 改开关时 +1。
     * UI 用它作 remember 的 key，保证后台同步完成后界面能立刻刷新。
     */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun bump() {
        _version.value += 1
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------- 屏蔽开关（三个独立项） ----------------

    /**
     * 三类假期各自的屏蔽开关，互不影响。
     *
     * @param statutory 法定节假日（元旦/春节/清明节/劳动节/端午节/中秋节/国庆节）
     * @param school 学校假期（校运会、寒假、暑假，来自 GLTU 校历）
     * @param weekend 日常周末（周六、周日）
     */
    data class BlockSettings(
        val statutory: Boolean = true,
        val school: Boolean = true,
        val weekend: Boolean = false,
    ) {
        val anyEnabled: Boolean get() = statutory || school || weekend

        /** 给 UI 用的一句话摘要。 */
        val summary: String
            get() {
                if (!anyEnabled) return "不屏蔽"
                val parts = ArrayList<String>(3)
                if (statutory) parts.add("法定节假日")
                if (school) parts.add("学校假期")
                if (weekend) parts.add("周末")
                return parts.joinToString("+")
            }
    }

    fun blockSettings(context: Context): BlockSettings {
        val p = prefs(context)
        if (p.contains(KEY_BLOCK_STATUTORY)) {
            return BlockSettings(
                statutory = p.getBoolean(KEY_BLOCK_STATUTORY, true),
                school = p.getBoolean(KEY_BLOCK_SCHOOL, true),
                weekend = p.getBoolean(KEY_BLOCK_WEEKEND, false),
            )
        }
        // 从旧版的单一模式迁移
        return when {
            p.contains(KEY_MODE) -> when (p.getInt(KEY_MODE, 1)) {
                0 -> BlockSettings(false, false, false)
                2 -> BlockSettings(true, true, true)
                else -> BlockSettings(true, true, false)
            }
            p.getBoolean(KEY_BLOCK_ENABLED, true) -> BlockSettings(true, true, false)
            else -> BlockSettings(false, false, false)
        }
    }

    fun setBlockSettings(context: Context, settings: BlockSettings) {
        prefs(context).edit()
            .putBoolean(KEY_BLOCK_STATUTORY, settings.statutory)
            .putBoolean(KEY_BLOCK_SCHOOL, settings.school)
            .putBoolean(KEY_BLOCK_WEEKEND, settings.weekend)
            .apply()
        bump()
    }

    // ---------------- 联网同步的数据 ----------------

    fun syncedHolidays(context: Context): List<Holiday> =
        decode(prefs(context).getString(KEY_SYNCED, "") ?: "")

    fun saveSynced(context: Context, list: List<Holiday>, source: String) {
        prefs(context).edit()
            .putString(KEY_SYNCED, encode(list))
            .putLong(KEY_SYNC_AT, System.currentTimeMillis())
            .putString(KEY_SYNC_SOURCE, source)
            .apply()
        bump()
    }

    fun lastSyncAt(context: Context): Long = prefs(context).getLong(KEY_SYNC_AT, 0L)

    fun syncSource(context: Context): String = prefs(context).getString(KEY_SYNC_SOURCE, "") ?: ""

    /** 已同步的数据里是否包含指定年份（用于判断次年安排是否已发布）。 */
    fun syncedCoversYear(context: Context, year: Int): Boolean =
        syncedHolidays(context).any { it.start.year == year }

    // ---------------- 用户自定义 ----------------

    fun customHolidays(context: Context): List<Holiday> =
        decode(prefs(context).getString(KEY_CUSTOM, "") ?: "")

    fun addCustomHoliday(context: Context, name: String, start: LocalDate, end: LocalDate) {
        val list = customHolidays(context) + Holiday(name, start, end, HolidayType.USER_DEFINED)
        prefs(context).edit().putString(KEY_CUSTOM, encode(list)).apply()
    }

    fun removeCustomHoliday(context: Context, name: String, start: LocalDate, end: LocalDate) {
        val list = customHolidays(context).filterNot {
            it.name == name && it.start == start && it.end == end
        }
        prefs(context).edit().putString(KEY_CUSTOM, encode(list)).apply()
    }

    // ---------------- 汇总 ----------------

    /**
     * 全部假期：联网同步的法定节假日优先，没同步过就用内置的。
     * 内置校历假期（寒暑假等）与用户自定义假期始终保留。
     */
    fun allHolidays(context: Context): List<Holiday> {
        val synced = syncedHolidays(context)
        val hasSyncedStatutory = synced.any { it.type == HolidayType.STATUTORY }
        val statutory = if (hasSyncedStatutory) {
            synced.filter { it.type == HolidayType.STATUTORY }
        } else {
            GltuHolidays.statutoryHolidays
        }
        val makeup = synced.filter { it.type == HolidayType.MAKEUP }
        return GltuHolidays.schoolHolidays + statutory + makeup + customHolidays(context)
    }

    /** 当天是否是周末（周六/周日）。 */
    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    /**
     * 核心判定：某天是否屏蔽课程。
     * 三类开关各自独立，命中任意一个开启的类别即屏蔽。
     * **补班日（调休上班的周末）不受任何开关影响，一律照常上课。**
     */
    fun isBlocked(context: Context, date: LocalDate): Boolean =
        isBlockedPure(date, blockSettings(context), allHolidays(context))

    /** 纯函数版判定（不依赖 Context，便于单元测试）。 */
    internal fun isBlockedPure(
        date: LocalDate,
        settings: BlockSettings,
        holidays: List<Holiday>,
    ): Boolean {
        // 补班日优先：即使是周末也照常上课
        if (holidays.any { it.type == HolidayType.MAKEUP && it.contains(date) }) return false
        if (settings.weekend && isWeekend(date)) return true
        return holidays.any { h ->
            h.type != HolidayType.MAKEUP && h.contains(date) && when (h.type) {
                HolidayType.STATUTORY -> settings.statutory
                HolidayType.SCHOOL -> settings.school
                // 自定义假期没有单独开关，跟随"法定节假日"那一档
                HolidayType.USER_DEFINED -> settings.statutory
                HolidayType.MAKEUP -> false
            }
        }
    }

    /** 某天的假期名（不区分是否被屏蔽，用于展示）；非假期返回 null。 */
    fun holidayNameOf(context: Context, date: LocalDate): String? =
        allHolidays(context)
            .filter { it.type != HolidayType.MAKEUP && it.contains(date) }
            .firstOrNull()
            ?.name

    // ---------------- 编解码（name:start:end:type;…） ----------------

    private fun encode(list: List<Holiday>): String =
        list.joinToString(";") { "${it.name}:${fmt.format(it.start)}:${fmt.format(it.end)}:${it.type.name}" }

    private fun decode(raw: String): List<Holiday> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val p = part.split(":")
            if (p.size < 3) return@mapNotNull null
            try {
                val type = if (p.size >= 4) {
                    runCatching { HolidayType.valueOf(p[3]) }.getOrDefault(HolidayType.USER_DEFINED)
                } else {
                    HolidayType.USER_DEFINED
                }
                Holiday(p[0], LocalDate.parse(p[1], fmt), LocalDate.parse(p[2], fmt), type)
            } catch (_: Exception) {
                null
            }
        }
    }
}
