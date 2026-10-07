// GLTU 课表 App —— 桌面小部件（适配小米系统多种规格）
// 用传统 AppWidgetProvider + RemoteViews（RemoteViews 在 MIUI/HyperOS 上兼容性最好）。
// 基类 + 多个空子类分别对应不同尺寸规格，ListView 自动适配高度。
package com.ltyksa.gltuschedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ltyksa.gltuschedule.R
import com.ltyksa.gltuschedule.data.HolidayManager
import com.ltyksa.gltuschedule.data.SemesterStore
import java.time.LocalDate

open class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            // 动态取当前子类的 ComponentName，覆盖所有规格
            val ids = mgr.getAppWidgetIds(ComponentName(context, this::class.java))
            onUpdate(context, mgr, ids)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.ltyksa.gltuschedule.widget.REFRESH"

        fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule_list)
            val svcIntent = Intent(context, ScheduleWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            views.setRemoteAdapter(R.id.widget_list, svcIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // 标题与课表页同步：第N周 · M月D日 周X（假期则显示假期名）
            views.setTextViewText(R.id.widget_title, buildTitle(context))

            // 点击打开 App 主界面
            val openIntent = Intent().apply {
                setClassName(context, "com.ltyksa.gltuschedule.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val openPi = PendingIntent.getActivity(
                context, id,
                openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title_bar, openPi)

            // 点击空视图也打开 App
            views.setOnClickPendingIntent(R.id.widget_empty, openPi)

            mgr.updateAppWidget(id, views)
            // 关键：updateAppWidget 不会触发 RemoteViewsFactory.onDataSetChanged()，
            // 必须显式通知，否则 30 分钟的定时刷新只更新标题、课程列表还是旧的。
            mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
        }

        /** 标题：与课表页同一套周次/学期信息。 */
        private fun buildTitle(context: Context): String {
            val today = LocalDate.now()
            val dateText = "${today.monthValue}月${today.dayOfMonth}日 " + weekdayCn(today.dayOfWeek)
            val holiday = HolidayManager.holidayNameOf(context, today)
            return if (holiday != null && HolidayManager.isBlocked(context, today)) {
                "$dateText · $holiday"
            } else {
                "第 ${SemesterStore.currentWeek(context)} 周 · $dateText"
            }
        }

        private fun weekdayCn(day: java.time.DayOfWeek): String = when (day) {
            java.time.DayOfWeek.MONDAY -> "周一"
            java.time.DayOfWeek.TUESDAY -> "周二"
            java.time.DayOfWeek.WEDNESDAY -> "周三"
            java.time.DayOfWeek.THURSDAY -> "周四"
            java.time.DayOfWeek.FRIDAY -> "周五"
            java.time.DayOfWeek.SATURDAY -> "周六"
            java.time.DayOfWeek.SUNDAY -> "周日"
        }

        /** 课表数据变化后刷新所有规格的小部件。 */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val providers = listOf(
                ComponentName(context, ScheduleWidgetProvider::class.java),
                ComponentName(context, ScheduleWidgetProviderSmall::class.java),
                ComponentName(context, ScheduleWidgetProviderLarge::class.java),
            )
            for (provider in providers) {
                for (id in mgr.getAppWidgetIds(provider)) {
                    updateWidget(context, mgr, id)
                }
            }
        }
    }
}

/** 小规格（约 2x2）。 */
class ScheduleWidgetProviderSmall : ScheduleWidgetProvider()

/** 大规格（约 4x4）。 */
class ScheduleWidgetProviderLarge : ScheduleWidgetProvider()
