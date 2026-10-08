// GLTU 课表 App —— 小部件的「准点刷新」调度
//
// 为什么不能只靠 updatePeriodMillis：
//   系统周期刷新最短 30 分钟，而且会被省电策略推迟，做不到准点。
//   偏偏有两个时刻必须尽量准时：
//     · 23:30 —— 内容要从「今天」翻转成「明天」
//     · 当天最后一节课下课 —— 要从课程列表变成「今天的课都上完啦」
//   所以在这两个点各补一次精确闹钟，直接给小部件发 ACTION_REFRESH。
package com.ltyksa.gltuschedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ltyksa.gltuschedule.data.SemesterStore
import java.time.DayOfWeek
import java.time.LocalDateTime

object WidgetRefreshScheduler {

    /** 23:30 翻转点 */
    private const val RC_FLIP = 0x7E57_1001

    /** 当天最后一节课下课 */
    private const val RC_LAST = 0x7E57_1002

    /** 排下一次准点刷新（幂等，重复调用会覆盖同一批闹钟）。 */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()

        // ① 下一个 23:30
        setSafe(am, WidgetTiming.nextFlipMillis(now), pi(context, RC_FLIP))

        // ② 今天最后一节课的下课时刻
        val today = now.toLocalDate()
        val data = WidgetDataHolder.source?.load(
            DayOfWeek.from(today),
            SemesterStore.firstWeekMonday(context),
        )
        val endAt = WidgetTiming.lastClassEndMillis(now, data?.todayCourses ?: emptyList())
        if (endAt != null) {
            setSafe(am, endAt, pi(context, RC_LAST))
        }
    }

    /** 取消（关掉小部件时调用，省电）。 */
    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pi(context, RC_FLIP))
        am.cancel(pi(context, RC_LAST))
    }

    private fun pi(context: Context, rc: Int): PendingIntent {
        val intent = Intent(context, ScheduleWidgetProvider::class.java).apply {
            action = ScheduleWidgetProvider.ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context, rc, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setSafe(am: AlarmManager, at: Long, pi: PendingIntent) {
        try {
            // 需要「闹钟和提醒」权限；App 已声明 SCHEDULE_EXACT_ALARM
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            // 用户没授权精确闹钟 → 退回非精确，最多晚几分钟，不会崩
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
