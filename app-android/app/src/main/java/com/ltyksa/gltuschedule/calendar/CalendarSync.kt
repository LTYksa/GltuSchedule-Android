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
import com.ltyksa.gltuschedule.data.HolidayManager
import com.ltyksa.gltuschedule.model.Course
import java.time.DayOfWeek
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
     *  - **调休（补课）感知**：某周的名义日期若是调休规则要「补」的那一天（如 10/8 周三），
     *    这节课实际在调休日（如 10/11 周六）上，事件就写到调休日那天，并在描述里标注「调休补课」；
     *    受影响的那门课**不用 RRULE**，改成逐次写入（调休打断了每周等间隔的规律）
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

            // 调休与屏蔽信息**一次性取好**（避免在循环里反复读 prefs）
            val rules = runCatching { HolidayManager.makeupRules(context).values }
                .getOrDefault(emptyList())
            // 调休反查表：源日期 → 调休日（如 10/8 → 10/11）
            val makeupOfSource = LinkedHashMap<LocalDate, LocalDate>()
            for (rule in rules.sortedBy { it.date }) {
                makeupOfSource.putIfAbsent(rule.sourceDate, rule.date)
            }
            val makeupDays = rules.map { it.date }.toSet()
            val blockSettings = HolidayManager.blockSettings(context)
            val holidays = HolidayManager.allHolidays(context)
            val isBlocked: (LocalDate) -> Boolean = { d ->
                HolidayManager.isBlockedPure(d, blockSettings, holidays)
            }

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

                // 这门课每一周实际写进日历的日期（调休搬运 / 调休停课 / 放假停课都已处理）
                val occurrences = classDatesOf(
                    firstWeekMonday, weeks, course.dayOfWeek,
                    makeupOfSource, makeupDays, isBlocked,
                )
                if (occurrences.isEmpty()) continue
                val shifted = occurrences.any { it.second }

                val contiguous = course.oddEven == 0 &&
                    weeks.size == (weeks.last() - weeks.first() + 1) &&
                    // 有停课的周次被剔除了 → 不能再按「每周重复」写，否则会凭空多出几节
                    occurrences.size == weeks.size

                if (contiguous && !shifted) {
                    // 未受调休影响，沿用原来的「每周重复」写法
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
                    // 单双周 / 不规则周次 / **含调休周** → 逐次写入。
                    // 调休把某一次课搬到了别的日子，RRULE 的「每周等间隔」假设不再成立，
                    // 宁可多写几条，也不要在错误日期上凭空生成一节课。
                    for ((date, isMakeup) in occurrences) {
                        insertEvent(
                            context, calendarId,
                            title, location,
                            if (isMakeup) "$desc · 调休补课" else desc,
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

    private fun dateOfWeek(firstWeekMonday: LocalDate, week: Int, dayOfWeek: DayOfWeek): LocalDate =
        firstWeekMonday.plusWeeks((week - 1).toLong()).plusDays((dayOfWeek.value - 1).toLong())

    /**
     * 一门课每一周**实际写进日历的上课日**（纯函数，便于单元测试）。
     *
     * 名义日期 = 第一周周一 + (W-1)*7 + (星期-1)，然后按三种情况修正：
     *
     *  1. **命中调休反查表**（那天放假、课被搬到调休日，如 10/8 周三 → 10/11 周六）
     *     → 事件写到调休日，标记 `true`；
     *  2. **名义日期本身是调休上班日**（如本来就排在周六的课，撞上 10/11 补周三的课）
     *     → 那天全天按周三的课表走，**这节课不上** → 跳过。
     *     不跳过的话，日历里会多出一节根本不存在的课，而且提醒侧（按调休后的星期匹配）
     *     不会给它排提醒，两边会对不上；
     *  3. **名义日期被假期屏蔽**且没有被补课安排（如 10/1 周四）→ 这节课不上 → 跳过。
     *
     * 因此返回的列表**可能比 [weeks] 短**（有停课时），调用方据此决定能否用 RRULE。
     *
     * @param makeupOfSource 源日期 → 调休日
     * @param makeupDays 所有调休上班日
     * @param isBlocked 该日期是否被假期屏蔽（传 [HolidayManager.isBlockedPure] 的结果）
     * @return (实际日期, 是否调休补课)，按周次升序，已剔除停课的周
     */
    internal fun classDatesOf(
        firstWeekMonday: LocalDate,
        weeks: List<Int>,
        dayOfWeek: DayOfWeek,
        makeupOfSource: Map<LocalDate, LocalDate>,
        makeupDays: Set<LocalDate> = emptySet(),
        isBlocked: (LocalDate) -> Boolean = { false },
    ): List<Pair<LocalDate, Boolean>> = weeks.mapNotNull { w ->
        val nominal = dateOfWeek(firstWeekMonday, w, dayOfWeek)
        val makeupDate = makeupOfSource[nominal]
        when {
            // ① 这天放假、课被搬到调休日
            makeupDate != null -> makeupDate to true
            // ② 这天是调休上班日 → 按别的星期的课表走，本课不上
            nominal in makeupDays -> null
            // ③ 这天放假且没有补课安排 → 本课不上
            isBlocked(nominal) -> null
            else -> nominal to false
        }
    }

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
