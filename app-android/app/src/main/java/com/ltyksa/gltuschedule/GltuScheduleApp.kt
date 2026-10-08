// GLTU 课表 App —— 应用入口
package com.ltyksa.gltuschedule

import android.app.Application
import com.ltyksa.gltuschedule.data.HolidaySync
import com.ltyksa.gltuschedule.data.SemesterStore
import com.ltyksa.gltuschedule.di.AppContainer
import com.ltyksa.gltuschedule.notification.ClassReminderScheduler
import com.ltyksa.gltuschedule.widget.WidgetDataHolder
import com.ltyksa.gltuschedule.widget.WidgetRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GltuScheduleApp : Application() {

    /** 全局依赖容器（ServiceLocator）。 */
    val container: AppContainer by lazy { AppContainer(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 注入小部件数据源（Room 实现）
        WidgetDataHolder.source = container.widgetDataSource

        // 排小部件的「准点刷新」（23:30 翻明天 / 最后一节课下课报休息）
        runCatching { WidgetRefreshScheduler.schedule(this) }

        // 上课提醒通知渠道
        ClassReminderScheduler.ensureChannel(this)

        appScope.launch {
            // 1) 刷新内置作息节次表
            container.scheduleRepository.refreshTimeSlots()

            // 2) 节假日自动同步（按 shouldSync 的规则判断，失败自动回退内置数据）
            if (HolidaySync.shouldSync(this@GltuScheduleApp)) {
                runCatching { HolidaySync.sync(this@GltuScheduleApp) }
            }

            // 3) 排未来 30 天上课提醒 + 注册每日后台维护（节假日同步也在里面）
            val courses = container.scheduleRepository.getWeekCoursesSync()
            if (courses.isNotEmpty()) {
                runCatching {
                    ClassReminderScheduler.scheduleNext(
                        this@GltuScheduleApp,
                        courses,
                        SemesterStore.firstWeekMonday(this@GltuScheduleApp),
                    )
                }
            }
            ClassReminderScheduler.scheduleDailyMaintenanceAlarm(this@GltuScheduleApp)
        }
    }
}
