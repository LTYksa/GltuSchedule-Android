// GLTU 课表 App —— 上课提醒调度器
//
// 设计：只为「未来 30 天」排精确闹钟（滚动窗口），由每日刷新闹钟续排。
// 这样用户随时改提醒时间（1–60 分钟）都能干净重排、不残留旧闹钟，闹钟数量也可控。
package com.ltyksa.gltuschedule.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ltyksa.gltuschedule.data.GltuTimeTable
import com.ltyksa.gltuschedule.data.HolidayManager
import com.ltyksa.gltuschedule.data.HolidaySync
import com.ltyksa.gltuschedule.data.SemesterStore
import com.ltyksa.gltuschedule.database.AppDatabase
import com.ltyksa.gltuschedule.database.mapper.toDomain
import com.ltyksa.gltuschedule.model.Course
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object ClassReminderScheduler {

    const val CHANNEL_ID = "class_reminder"

    private const val PREFS = "gltu_reminder_schedule"

    /** 排期窗口（天）。 */
    const val HORIZON_DAYS = 30

    private const val RC_BASE = 0x1000_0000
    private const val DAILY_REFRESH_RC = 0x5C0_FF00
    const val ACTION_DAILY_MAINTENANCE = "com.ltyksa.gltuschedule.action.DAILY_MAINTENANCE"

    /** 上次排入的提醒请求码（用于下次精确清理，避免遍历整个码空间）。 */
    private const val KEY_SCHEDULED_CODES = "scheduled_reminder_codes"

    /** 每日后台维护时间（凌晨 3:30，此时一般插着电、连着 WiFi，且不打扰用户）。 */
    private const val DAILY_HOUR = 3
    private const val DAILY_MINUTE = 30

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "上课提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "在上课前提醒你"
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * 请求码：**必须包含"哪门课"**。
     *
     * 为什么：`PendingIntent` 的匹配用的是 `Intent.filterEquals`，它**不比较 extras**。
     * 如果请求码只有「日期 + 提前分钟数」，同一天同一档提醒的多门课就会共用同一个
     * PendingIntent，后一节课的 `setExactAndAllowWhileIdle` 会把前一节的闹钟**顶掉**
     * ——表现为"一天只有最后一节课会响"。
     *
     * 位分配（共 27 位，不溢出 Int）：
     *   日期 9 位（epochDay % 512，滚动窗口只有 30 天，足够唯一）
     *   课程 12 位（课程 id 取模，避免撞车）
     *   分钟 6 位（1–60）
     */
    internal fun requestCode(date: LocalDate, minutesBefore: Int, courseSlot: Int): Int {
        val day = ((date.toEpochDay() % 512L).toInt() + 512) % 512
        val slot = courseSlot and 0x0FFF
        val minute = (minutesBefore - 1).coerceIn(0, 63)
        return RC_BASE + (((day shl 12) or slot) shl 6) + minute
    }

    /** 课程 → 12 位槽位。已落库的课用自增 id；未落库的用内容哈希兜底。 */
    internal fun courseSlot(course: Course): Int =
        if (course.id > 0L) {
            (course.id % 4096L).toInt()
        } else {
            "${course.name}|${course.dayOfWeek.value}|${course.startIndex}|${course.location}"
                .hashCode() and 0x0FFF
        }

    /** 读出上次排入的请求码。 */
    private fun loadScheduledCodes(context: Context): List<Int> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SCHEDULED_CODES, null) ?: return emptyList()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }
    }

    private fun saveScheduledCodes(context: Context, codes: List<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SCHEDULED_CODES, codes.joinToString(","))
            .apply()
    }

    /** 精确取消上次排入的那批闹钟（比遍历整个码空间快几百倍）。 */
    private fun cancelScheduledCodes(context: Context) {
        val codes = loadScheduledCodes(context)
        if (codes.isEmpty()) return
        val am = context.getSystemService(AlarmManager::class.java)
        val baseIntent = Intent(context, ReminderReceiver::class.java)
        for (code in codes) {
            val pi = PendingIntent.getBroadcast(
                context, code, baseIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
        saveScheduledCodes(context, emptyList())
    }

    /**
     * 重排未来 [horizonDays] 天的提醒（先清上次排入的那批，再按当前设置重排）。
     * @return 实际排入的闹钟数
     */
    fun scheduleNext(
        context: Context,
        courses: List<Course>,
        firstWeekMonday: LocalDate,
        horizonDays: Int = HORIZON_DAYS,
    ): Int {
        // 精确清理上次排入的闹钟（换过"提前分钟数"也能清干净）
        cancelScheduledCodes(context)
        if (!ReminderPreference.isEnabled(context)) return 0

        val minutesList = ReminderPreference.advanceMinutes(context)
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val am = context.getSystemService(AlarmManager::class.java)
        val usedCodes = ArrayList<Int>()
        var count = 0

        for (offset in 0 until horizonDays) {
            val date = today.plusDays(offset.toLong())
            // 被屏蔽的日子（法定节假日 / 校历假期 / 周末开关命中）不排提醒。
            // 补班日（调休上班的周末）在 isBlocked 里一律放行，不会被这里挡掉。
            if (HolidayManager.isBlocked(context, date)) continue

            // 调休（补课）感知：调休上班日上的是「它代替的那一天」的课，
            // 所以用 eff 的星期匹配课程、用 eff 所在的周次判断周次
            // （例如 10/11 周六补 10/8 周三 → 上的必须是第 6 周周三的课，而不是第 7 周周三的）。
            // 闹钟的**触发日期仍是 date**（那天才上课），requestCode 也仍按 date 编码，保证旧闹钟能取消掉。
            val eff = HolidayManager.effectiveDate(context, date)
            val week = weekOf(firstWeekMonday, eff)
            if (week < 1) continue
            for (course in courses) {
                if (course.dayOfWeek != eff.dayOfWeek) continue
                if (!course.occursOnWeek(week)) continue
                val slot = GltuTimeTable.byIndex(course.startIndex) ?: continue
                val start = runCatching { LocalTime.parse(slot.startTime) }.getOrNull() ?: continue

                for (m in minutesList) {
                    val at = ZonedDateTime.of(date, start.minusMinutes(m.toLong()), zone)
                    if (at.toInstant().toEpochMilli() <= System.currentTimeMillis()) continue
                    val code = requestCode(date, m, courseSlot(course))
                    val pi = pendingIntent(context, code, course, m, slot.startTime)
                    try {
                        am.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pi,
                        )
                    } catch (_: SecurityException) {
                        am.set(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pi)
                    }
                    usedCodes.add(code)
                    count++
                }
            }
        }
        saveScheduledCodes(context, usedCodes)
        return count
    }

    /**
     * 立即取消所有已排的上课提醒（用户关闭提醒开关时调用）。
     */
    fun cancelAll(context: Context) {
        cancelScheduledCodes(context)
    }

    /** 精确闹钟权限是否可用（Android 12+ 可能被系统拒绝，导致提醒不准时）。 */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    private fun pendingIntent(
        context: Context,
        requestCode: Int,
        course: Course,
        minutesBefore: Int,
        startTime: String,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, course.name)
            putExtra(ReminderReceiver.EXTRA_LOCATION, course.location)
            putExtra(ReminderReceiver.EXTRA_ADVANCE, minutesBefore)
            putExtra(ReminderReceiver.EXTRA_START_TIME, startTime)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun weekOf(firstWeekMonday: LocalDate, date: LocalDate): Int {
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        val weeks = ChronoUnit.WEEKS.between(firstWeekMonday, monday)
        return (weeks + 1).toInt()
    }

    // ---------------- 每日后台维护 ----------------

    /** 注册每天凌晨的维护闹钟（幂等）。 */
    fun scheduleDailyMaintenanceAlarm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, DailyMaintenanceReceiver::class.java).apply {
            action = ACTION_DAILY_MAINTENANCE
        }
        val pi = PendingIntent.getBroadcast(
            context,
            DAILY_REFRESH_RC,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val next = ZonedDateTime.now()
            .plusDays(1)
            .withHour(DAILY_HOUR).withMinute(DAILY_MINUTE).withSecond(0).withNano(0)
        try {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                next.toInstant().toEpochMilli(),
                AlarmManager.INTERVAL_DAY,
                pi,
            )
        } catch (_: Exception) {
            // 忽略；下次打开 App 会重新排
        }
    }
}

/** 闹钟触发：弹上课提醒。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "课程"
        val location = intent.getStringExtra(EXTRA_LOCATION)
        val advance = intent.getIntExtra(EXTRA_ADVANCE, 0)
        val startTime = intent.getStringExtra(EXTRA_START_TIME) ?: ""

        if (!ReminderPreference.isEnabled(context)) return
        // 节假日屏蔽：当天被屏蔽就不弹
        if (com.ltyksa.gltuschedule.data.HolidayManager.isBlocked(context, LocalDate.now())) return

        val nm = context.getSystemService(NotificationManager::class.java)
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, ClassReminderScheduler.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("$advance 分钟后上课：$title")
                .setContentText(location?.let { "$startTime 开始 · $it" } ?: "$startTime 开始")
                .setAutoCancel(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("$advance 分钟后上课：$title")
                .setContentText(location?.let { "$startTime 开始 · $it" } ?: "$startTime 开始")
                .setAutoCancel(true)
                .build()
        }
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_ADVANCE = "advance"
        const val EXTRA_START_TIME = "start_time"
    }
}

/**
 * 每日后台维护（凌晨 3:30 触发，**App 不打开也会跑**）：
 *  1. **自动同步节假日** —— 国务院办公厅通常在前一年 11 月发布次年安排，
 *     11/12 月若还没拿到次年数据就每 3 天重试一次；平时 7 天检查一次。
 *  2. 把未来 30 天的上课提醒补齐。
 *  3. 重新注册下一天的维护闹钟，形成自续（不依赖 App 启动）。
 */
class DailyMaintenanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                // 1) 本地且要紧的：上课提醒续排（快，且不依赖网络）
                if (ReminderPreference.isEnabled(context)) {
                    val dao = AppDatabase.getInstance(context).courseDao()
                    val courses = dao.getAllSync().map { it.toDomain() }
                    if (courses.isNotEmpty()) {
                        ClassReminderScheduler.scheduleNext(
                            context,
                            courses,
                            SemesterStore.firstWeekMonday(context),
                        )
                    }
                }

                // 2) 先保证自续：排下一天的维护闹钟（放在联网步骤之前，避免被网络拖没）
                ClassReminderScheduler.scheduleDailyMaintenanceAlarm(context)

                // 3) 最后才联网同步节假日。
                //    广播接收器约 10 秒就会被系统掐断，所以开一个独立线程并**硬等 6 秒**：
                //    join 超时后照常 finish()，联网线程即使还在跑也不会拖垮接收器。
                //    （syncBlocking 内部的 budgetMs 只在两次请求之间检查，单次 OkHttp 调用
                //     最坏可阻塞 connect+read ≈ 11 秒，所以外面必须再加这道硬闸。）
                runCatching {
                    if (HolidaySync.shouldSync(context)) {
                        val worker = Thread {
                            runCatching { HolidaySync.syncBlocking(context, budgetMs = 5_000) }
                        }
                        worker.isDaemon = true
                        worker.start()
                        worker.join(6_000)
                    }
                }
            } catch (_: Exception) {
                // 忽略，下一次触发再续
            } finally {
                pending.finish()
            }
        }.start()
    }
}
