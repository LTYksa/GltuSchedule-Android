// GLTU 课表 App —— 小部件列表服务（内容与课表页保持一致）
package com.ltyksa.gltuschedule.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import com.ltyksa.gltuschedule.R
import com.ltyksa.gltuschedule.data.Classroom
import com.ltyksa.gltuschedule.data.CourseColor
import com.ltyksa.gltuschedule.data.GltuTimeTable
import com.ltyksa.gltuschedule.data.HolidayManager
import com.ltyksa.gltuschedule.data.SemesterStore
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ScheduleWidgetFactory(applicationContext)
}

class ScheduleWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<WidgetItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items = loadTodayCourses()
    }

    override fun onDestroy() {}

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_course_item)
        views.setTextViewText(R.id.widget_item_name, item.name)
        views.setTextColor(R.id.widget_item_name, item.color)
        views.setTextViewText(R.id.widget_item_meta, item.meta)
        views.setTextViewText(R.id.widget_item_period, item.period)
        views.setInt(R.id.widget_item_bar, "setBackgroundColor", item.color)
        if (item.teacher.isBlank()) {
            views.setViewVisibility(R.id.widget_item_teacher, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_item_teacher, View.VISIBLE)
            views.setTextViewText(R.id.widget_item_teacher, item.teacher)
        }
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true

    private fun loadTodayCourses(): List<WidgetItem> {
        val now = java.time.LocalDateTime.now()
        // 23:30 之后显示「明天」的课；其余时间显示今天
        val date = WidgetTiming.displayDate(now)
        val isTomorrow = date != now.toLocalDate()

        // 节假日屏蔽（与课表页同一套判定）
        if (HolidayManager.isBlocked(context, date)) {
            val name = HolidayManager.holidayNameOf(context, date)
                ?: if (HolidayManager.isWeekend(date)) "周末" else "假期"
            return listOf(
                WidgetItem(
                    name = if (name == "周末") "今天是周末" else "假期：$name",
                    meta = "课程已屏蔽，好好休息 🎉",
                    teacher = "",
                    period = "",
                    color = GRAY,
                ),
            )
        }

        val data = WidgetDataHolder.source?.load(
            DayOfWeek.from(date),
            SemesterStore.firstWeekMonday(context),
        ) ?: return listOf(
            WidgetItem("点击进入 App 并导入课表", "支持手动添加 / 分享码 / 教务系统导入", "", "", GRAY),
        )

        if (data.todayCourses.isEmpty()) {
            return listOf(
                WidgetItem(
                    if (isTomorrow) "明天没有课" else "今天没有课",
                    "享受你的休息日",
                    "", "", GRAY,
                ),
            )
        }

        // 今天的课全上完了 → 不再罗列已结束的课，直接提示休息
        // （显示「明天」时不适用：那时候的课还没开始呢）
        if (!isTomorrow && WidgetTiming.allDone(now, data.todayCourses)) {
            return listOf(
                WidgetItem("今天的课都上完啦", "好好休息 🎉", "", "", GRAY),
            )
        }

        return data.todayCourses
            .sortedBy { it.startIndex }
            .map { c ->
                val start = GltuTimeTable.byIndex(c.startIndex)
                val end = GltuTimeTable.byIndex(c.endIndex)
                val time = if (start != null && end != null) {
                    "${start.startTime}–${end.endTime}"
                } else {
                    ""
                }
                val location = Classroom.shortLabel(c.location).ifBlank { "地点待定" }
                WidgetItem(
                    name = c.name,
                    meta = listOf(time, location).filter { it.isNotBlank() }.joinToString(" · "),
                    teacher = c.teacher,
                    period = GltuTimeTable.rangeLabel(c.startIndex, c.endIndex),
                    color = CourseColor.of(c.name).toArgb(),
                )
            }
    }

    private data class WidgetItem(
        val name: String,
        val meta: String,
        val teacher: String,
        val period: String,
        val color: Int,
    )

    companion object {
        /** 灰色（提示类条目的配色）。 */
        private const val GRAY = 0xFF9E9E9E.toInt()
    }
}

/** 小部件数据源持有者（由 App 初始化时注入 Room 实现）。 */
object WidgetDataHolder {
    @Volatile var source: WidgetDataSource? = null
}
