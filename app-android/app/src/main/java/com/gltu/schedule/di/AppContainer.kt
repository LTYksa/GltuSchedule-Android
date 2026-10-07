// GLTU 课表 App —— 手动依赖注入容器（ServiceLocator）
package com.gltu.schedule.di

import android.content.Context
import com.gltu.schedule.database.AppDatabase
import com.gltu.schedule.database.RoomWidgetDataSource
import com.gltu.schedule.repository.ScheduleRepository
import com.gltu.schedule.widget.WidgetDataSource

/**
 * 骨架阶段不引入 Hilt，用单例容器集中管理依赖，便于后续无缝替换为 Hilt。
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = AppDatabase.getInstance(context)

    /** 课表仓库：核心数据入口。 */
    val scheduleRepository: ScheduleRepository by lazy {
        ScheduleRepository(
            courseDao = database.courseDao(),
            timeSlotDao = database.timeSlotDao(),
            holidayDao = database.holidayDao(),
        )
    }

    /** 小部件数据源（Room 实现），由 App 启动时注入 widget.WidgetDataHolder。 */
    val widgetDataSource: WidgetDataSource by lazy { RoomWidgetDataSource(scheduleRepository) }

    // ---------- 预留扩展点 ----------
    // 后续如需拆分（例如把导入/提醒各自抽成独立依赖），在此追加即可。
}
