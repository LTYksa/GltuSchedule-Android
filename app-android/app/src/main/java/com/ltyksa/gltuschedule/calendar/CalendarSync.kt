// GLTU 课表 App —— 一键同步课程表到系统日历
// 写入系统日历（CalendarContract），可通过"日历"App 查看，也可被其他设备同步。
package com.ltyksa.gltuschedule.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.ltyksa.gltuschedule.data.Classroom
import com.ltyksa.gltuschedule.data.GltuTimeTable
import com.ltyksa.gltuschedule.model.Course
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

object CalendarSync {

    /** 同步进日历的事件都带这个前缀，便于重复同步时先清理旧的。 */
    private const val MARK = "GLTU课表"

    data class Result(
        val success: Boolean,
        val message: String,
        val inserted: Int = 0,
    )

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 把课表写入系统日历。
     * 规则：
     *  - 周次连续的课 → 一个「每周重复」事件（干净、条目少）
     *  - 单双周 / 不规则周次（如 4-5周,8周,11-13周(单)）→ 逐次写入，保证准确
     *  - 每次同步前先删除上次写入的 GLTU课表 事件，重复点击不会产生重复条目
     */
    fun sync(context: Context, courses: List<Course>, firstWeekMonday: LocalDate): Result {
        if (!hasPermission(context)) {
            return Result(false, "缺少日历权限，请先授予「读取/写入日历」权限")
        }
        if (courses.isEmpty()) {
            return Result(false, "还没有课程，先导入或添加课程吧")
        }

        return try {
            val calendarId = ensureCalendar(context)
                ?: return Result(false, "没有找到可写入的日历账户")

            val removed = deletePrevious(context, calendarId)
            var count = 0
            val zone = ZoneId.systemDefault()

            for (course in courses) {
                val slotStart = GltuTimeTable.byIndex(course.startIndex)?.startTime ?: continue
                val slotEnd = GltuTimeTable.byIndex(course.endIndex)?.endTime ?: continue
                val start = runCatching { LocalTime.parse(slotStart) }.getOrNull() ?: continue
                val end = runCatching { LocalTime.parse(slotEnd) }.getOrNull() ?: continue

                val title = course.name
                val location = Classroom.shortLabel(course.location)
                val desc = buildString {
                    append(MARK)
                    if (course.teacher.isNotBlank()) append(" · ").append(course.teacher)
                    append(" · ").append(course.displayWeeks)
                }

                val weeks = courseWeeks(course, firstWeekMonday)
                if (weeks.isEmpty()) continue

                val contiguous = course.oddEven == 0 &&
                    weeks.size == (weeks.last() - weeks.first() + 1)

                if (contiguous) {
                    val firstDate = dateOfWeek(firstWeekMonday, weeks.first(), course.dayOfWeek)
                    insertEvent(
                        context, calendarId,
                        title, location, desc,
                        beginMillis(firstDate, start, zone),
                        endMillis(firstDate, end, zone),
                        rrule = "FREQ=WEEKLY;COUNT=${weeks.size}",
                    )
                    count++
                } else {
                    for (w in weeks) {
                        val date = dateOfWeek(firstWeekMonday, w, course.dayOfWeek)
                        insertEvent(
                            context, calendarId,
                            title, location, desc,
                            beginMillis(date, start, zone),
                            endMillis(date, end, zone),
                            rrule = null,
                        )
                        count++
                    }
                }
            }

            val tail = if (removed > 0) "（已清理上次同步的 $removed 条）" else ""
            Result(true, "已同步 $count 条课程到系统日历$tail", count)
        } catch (e: SecurityException) {
            Result(false, "日历权限被拒绝，无法写入")
        } catch (e: Exception) {
            Result(false, "同步失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ---------------- 内部实现 ----------------

    private fun courseWeeks(course: Course, firstWeekMonday: LocalDate): List<Int> {
        val maxWeek = 30
        val from = course.startWeek.coerceIn(1, maxWeek)
        val to = course.endWeek.coerceIn(from, maxWeek)
        if (course.weeks.isNotEmpty()) {
            return course.weeks.filter { it in from..to }.sorted()
        }
        if (firstWeekMonday.isAfter(LocalDate.now().plusWeeks(maxWeek.toLong()))) return emptyList()
        return (from..to).filter { course.occursOnWeek(it) }
    }

    private fun dateOfWeek(firstWeekMonday: LocalDate, week: Int, dayOfWeek: java.time.DayOfWeek): LocalDate =
        firstWeekMonday.plusWeeks((week - 1).toLong()).plusDays((dayOfWeek.value - 1).toLong())

    private fun beginMillis(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private fun endMillis(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private fun ensureCalendar(context: Context): Long? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null,
        )?.use { c ->
            // 优先找可写的非只读日历
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (id > 0) return id
            }
        }

        // 没有可写日历时，创建一个本地日历
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "GLTU课表")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "GLTU课表")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "GLTU课表")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "GLTU 课表")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4E6E9E.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "GLTU课表")
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }
        return resolver.insert(uri, values)?.let { ContentUris.parseId(it) }
    }

    private fun deletePrevious(context: Context, calendarId: Long): Int {
        val resolver = context.contentResolver
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
            "${CalendarContract.Events.DESCRIPTION} LIKE ?"
        return resolver.delete(
            CalendarContract.Events.CONTENT_URI,
            selection,
            arrayOf(calendarId.toString(), "$MARK%"),
        )
    }

    private fun insertEvent(
        context: Context,
        calendarId: Long,
        title: String,
        location: String,
        description: String,
        begin: Long,
        end: Long,
        rrule: String?,
    ) {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, begin)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            if (rrule != null) put(CalendarContract.Events.RRULE, rrule)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return
        val eventId = ContentUris.parseId(uri)
        // 默认提前 10 分钟提醒
        val reminder = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 10)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        runCatching { context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder) }
    }
}
