// GLTU 课表 App —— 课表页 ViewModel（周/日双视图 + 周次切换 + 课表设置）
package com.ltyksa.gltuschedule.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ltyksa.gltuschedule.GltuScheduleApp
import com.ltyksa.gltuschedule.data.BackgroundImageStore
import com.ltyksa.gltuschedule.data.GltuTimeTable
import com.ltyksa.gltuschedule.data.HolidayManager
import com.ltyksa.gltuschedule.data.SchedulePreferences
import com.ltyksa.gltuschedule.data.SemesterStore
import com.ltyksa.gltuschedule.data.TimeSlot
import com.ltyksa.gltuschedule.model.Course
import com.ltyksa.gltuschedule.notification.ClassReminderScheduler
import com.ltyksa.gltuschedule.repository.ScheduleRepository
import com.ltyksa.gltuschedule.widget.ScheduleWidgetProvider
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 课表显示模式：周视图（网格）/ 日视图（列表）。 */
enum class ScheduleViewMode { WEEK, DAY }

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: ScheduleRepository =
        (app as GltuScheduleApp).container.scheduleRepository

    // ---------------- 周次 / 学期 ----------------

    private val _selectedWeek = MutableStateFlow(SemesterStore.currentWeek(app))
    val selectedWeek: StateFlow<Int> = _selectedWeek.asStateFlow()

    private val _semesterName = MutableStateFlow(SemesterStore.semesterName(app))
    val semesterName: StateFlow<String> = _semesterName.asStateFlow()

    private val _firstMonday = MutableStateFlow(SemesterStore.firstWeekMonday(app))

    /** 学期第一周周一（周次换算基准）。 */
    val firstMonday: StateFlow<LocalDate> = _firstMonday.asStateFlow()

    /** 用户选择的开学日期（侧边栏显示的就是这个原样日期）。 */
    private val _startDateRaw = MutableStateFlow(SemesterStore.startDateRaw(app))
    val startDateRaw: StateFlow<LocalDate> = _startDateRaw.asStateFlow()

    /** 节假日屏蔽的三个独立开关（法定节假日 / 学校假期 / 周末）。 */
    private val _blockSettings = MutableStateFlow(HolidayManager.blockSettings(app))
    val blockSettings: StateFlow<HolidayManager.BlockSettings> = _blockSettings.asStateFlow()

    /**
     * 调休规则：调休上班日 → 规则（补哪一天的课）。
     * 推导算法见 [MakeupResolver]；用户可在课表设置里手动覆盖。
     */
    private val _makeupRules = MutableStateFlow(HolidayManager.makeupRules(app))
    val makeupRules: StateFlow<Map<java.time.LocalDate, com.ltyksa.gltuschedule.data.MakeupRule>> =
        _makeupRules.asStateFlow()

    /** 调休规则列表（按日期升序，供侧边栏展示）。 */
    val makeupRuleList: StateFlow<List<com.ltyksa.gltuschedule.data.MakeupRule>> =
        _makeupRules.map { it.values.sortedBy { r -> r.date } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 每天都实际要上「哪一天」的课：调休日 → 源日期；普通日子 → 它自己。
     * 课表网格按它取星期与周次 —— 调休日要连**周次**一起搬。
     */
    val effectiveDates: StateFlow<Map<java.time.LocalDate, java.time.LocalDate>> =
        _makeupRules.map { rules -> rules.mapValues { it.value.sourceDate } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 手动指定某天补周几的课。 */
    fun setMakeupOverride(date: java.time.LocalDate, weekday: java.time.DayOfWeek) {
        val ctx = getApplication<Application>()
        HolidayManager.setMakeupOverride(ctx, date, weekday)
        _makeupRules.value = HolidayManager.makeupRules(ctx)
    }

    /** 清除某天的手动覆盖，回到自动推导。 */
    fun clearMakeupOverride(date: java.time.LocalDate) {
        val ctx = getApplication<Application>()
        HolidayManager.clearMakeupOverride(ctx, date)
        _makeupRules.value = HolidayManager.makeupRules(ctx)
    }

    fun setBlockSettings(settings: HolidayManager.BlockSettings) {
        HolidayManager.setBlockSettings(getApplication(), settings)
        _blockSettings.value = settings
    }

    init {
        _semesterName.value = SemesterStore.semesterName(app)
        _firstMonday.value = SemesterStore.firstWeekMonday(app)
        _startDateRaw.value = SemesterStore.startDateRaw(app)
    }

    /** 重新读取学期/周次与偏好（导入完成、从设置页返回、从后台回到前台时调用）。 */
    fun refreshSemesterInfo() {
        val ctx = getApplication<Application>()
        // 跨零点后"今天"要跟着变
        _today.value = LocalDate.now()
        _firstMonday.value = SemesterStore.firstWeekMonday(ctx)
        _startDateRaw.value = SemesterStore.startDateRaw(ctx)
        _semesterName.value = SemesterStore.semesterName(ctx)
        _maxPeriods.value = SchedulePreferences.maxPeriods(ctx)
        _weekStart.value = SchedulePreferences.weekStart(ctx)
        _showAllWeeks.value = SchedulePreferences.showAllWeeks(ctx)
        _themeMode.value = SchedulePreferences.themeMode(ctx)
        _blockSettings.value = HolidayManager.blockSettings(ctx)
        _makeupRules.value = HolidayManager.makeupRules(ctx)
    }

    /**
     * 设置开学日期。
     * [picked] 是用户选的原始日期（原样保存并显示）；第一周周一由后台推导为该日期所在周的周一。
     */
    fun setStartDate(picked: LocalDate) {
        val ctx = getApplication<Application>()
        SemesterStore.save(ctx, picked, _semesterName.value.ifBlank { "教务系统导入" })
        _startDateRaw.value = picked
        _firstMonday.value = SemesterStore.firstWeekMonday(ctx)
        rescheduleReminders()
    }

    /** 重排上课提醒（改了提醒时间/开学日期后调用）。 */
    fun rescheduleReminders() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                val courses = repository.getWeekCoursesSync()
                if (courses.isEmpty()) return@withContext
                runCatching {
                    ClassReminderScheduler.scheduleNext(
                        ctx, courses, SemesterStore.firstWeekMonday(ctx),
                    )
                }
            }
        }
    }

    /** 手动设置学期名（自动识别失败时用户自己填）。 */
    fun setSemesterName(name: String) {
        SemesterStore.setSemesterName(getApplication(), name)
        _semesterName.value = name.trim()
    }

    /**
     * **设置当前是第几周**。
     *
     * 用户往往不知道"开学日期"，但一定知道"这周是第几周"。
     * 这里用「本周周一 − (week−1) 周」反推出开学日期，再交给 [SemesterStore] 保存，
     * 顺带把课表跳到该周。
     */
    fun setCurrentWeek(week: Int) {
        val ctx = getApplication<Application>()
        val w = week.coerceIn(1, 30)
        val thisMonday = LocalDate.now()
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val startDate = thisMonday.minusWeeks((w - 1).toLong())
        // 保留原有学期名（save 会用传入值覆盖）——空着就写"手动设置"
        SemesterStore.save(ctx, startDate, _semesterName.value.ifBlank { "手动设置" })
        _semesterName.value = SemesterStore.semesterName(ctx)
        _firstMonday.value = SemesterStore.firstWeekMonday(ctx)
        _startDateRaw.value = SemesterStore.startDateRaw(ctx)
        _selectedWeek.value = w
        rescheduleReminders()
    }

    // ---------------- 课表背景图 ----------------

    private val _backgroundVersion = MutableStateFlow(0)
    val backgroundVersion: StateFlow<Int> = _backgroundVersion.asStateFlow()

    private val _hasBackground = MutableStateFlow(BackgroundImageStore.exists(app))
    val hasBackground: StateFlow<Boolean> = _hasBackground.asStateFlow()

    /** 保存用户选择的背景图，返回是否成功。 */
    fun setBackground(uri: android.net.Uri): Boolean {
        val ctx = getApplication<Application>()
        val ok = BackgroundImageStore.save(ctx, uri)
        if (ok) {
            _hasBackground.value = true
            _backgroundVersion.value += 1
        }
        return ok
    }

    /** 保存用户裁切后的背景图。 */
    fun setBackgroundBitmap(bitmap: android.graphics.Bitmap): Boolean {
        val ctx = getApplication<Application>()
        val ok = BackgroundImageStore.saveBitmap(ctx, bitmap)
        if (ok) {
            _hasBackground.value = true
            _backgroundVersion.value += 1
        }
        return ok
    }

    fun clearBackground() {
        BackgroundImageStore.clear(getApplication())
        _hasBackground.value = false
        _backgroundVersion.value += 1
    }

    // ---------------- 课表设置（侧边栏） ----------------

    private val _maxPeriods = MutableStateFlow(SchedulePreferences.maxPeriods(app))
    val maxPeriods: StateFlow<Int> = _maxPeriods.asStateFlow()

    fun setMaxPeriods(n: Int) {
        SchedulePreferences.setMaxPeriods(getApplication(), n)
        _maxPeriods.value = n.coerceIn(4, GltuTimeTable.slots.size)
    }

    /** 每周起始日：1=周一，7=周日。 */
    private val _weekStart = MutableStateFlow(SchedulePreferences.weekStart(app))
    val weekStart: StateFlow<Int> = _weekStart.asStateFlow()

    fun setWeekStart(dayValue: Int) {
        SchedulePreferences.setWeekStart(getApplication(), dayValue)
        _weekStart.value = if (dayValue == 7) 7 else 1
    }

    /** 星期列顺序（受"每周起始日"影响）。 */
    val dayOrder: StateFlow<List<Pair<String, DayOfWeek>>> =
        _weekStart.map { start ->
            if (start == 7) listOf(WEEK_DAYS.last()) + WEEK_DAYS.dropLast(1) else WEEK_DAYS
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WEEK_DAYS)

    private val _showAllWeeks = MutableStateFlow(SchedulePreferences.showAllWeeks(app))
    val showAllWeeks: StateFlow<Boolean> = _showAllWeeks.asStateFlow()

    fun setShowAllWeeks(enabled: Boolean) {
        SchedulePreferences.setShowAllWeeks(getApplication(), enabled)
        _showAllWeeks.value = enabled
    }

    private val _themeMode = MutableStateFlow(SchedulePreferences.themeMode(app))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    fun setThemeMode(mode: Int) {
        SchedulePreferences.setThemeMode(getApplication(), mode)
        _themeMode.value = mode
        // 同步全局主题状态，实现即时换肤
        com.ltyksa.gltuschedule.ui.theme.AppThemeState.mode = mode
    }

    // ---------------- 视图模式 / 选中日 ----------------

    private val _viewMode = MutableStateFlow(ScheduleViewMode.WEEK)
    val viewMode: StateFlow<ScheduleViewMode> = _viewMode.asStateFlow()

    private val _selectedDay = MutableStateFlow(DayOfWeek.from(LocalDate.now()))
    val selectedDay: StateFlow<DayOfWeek> = _selectedDay.asStateFlow()

    fun setViewMode(mode: ScheduleViewMode) { _viewMode.value = mode }

    fun toggleViewMode() {
        _viewMode.value =
            if (_viewMode.value == ScheduleViewMode.WEEK) ScheduleViewMode.DAY else ScheduleViewMode.WEEK
    }

    fun selectDay(day: DayOfWeek) { _selectedDay.value = day }

    /** 本周 7 天的日期（受"每周起始日"影响）。 */
    val weekDates: StateFlow<List<LocalDate>> =
        combine(_selectedWeek, _firstMonday, _weekStart) { week, monday, start ->
            val weekMonday = monday.plusWeeks((week - 1).toLong())
            val startDate = if (start == 7) weekMonday.minusDays(1) else weekMonday
            (0..6).map { startDate.plusDays(it.toLong()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 今天（跨零点 / 从后台回到前台时刷新）。 */
    private val _today = MutableStateFlow(LocalDate.now())

    /** 今天在本周日期行里的下标（-1 = 不在本周）。 */
    val todayIndex: StateFlow<Int> =
        weekDates.combine(_today) { dates, today ->
            dates.indexOfFirst { it == today }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    /** 日视图标题：如 "10月6日 周二"。 */
    val selectedDayLabel: StateFlow<String> =
        combine(weekDates, _selectedDay) { dates, day ->
            val date = dates.firstOrNull { DayOfWeek.from(it) == day }
            if (date == null) weekdayLabel(day)
            else "${date.monthValue}月${date.dayOfMonth}日 ${weekdayLabel(day)}"
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    // ---------------- 课程数据 ----------------

    /** 本课程周次的课程；开启"显示非本周课程"后不再过滤。 */
    val coursesOfWeek: StateFlow<List<Course>> =
        combine(
            repository.observeWeekCourses(),
            _selectedWeek,
            _showAllWeeks,
        ) { list, week, showAll ->
            if (showAll) list else list.filter { it.occursOnWeek(week) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 选中那天的课程（日视图用）。**调休感知**：
     * 调休日显示的是「源日期」那天的课，周次也按源日期算。
     */
    val coursesOfDay: StateFlow<List<Course>> =
        combine(
            repository.observeWeekCourses(),
            _selectedWeek,
            _selectedDay,
            weekDates,
            combine(effectiveDates, _showAllWeeks) { e, s -> e to s },
        ) { all, week, day, dates, effShow ->
            val eff = effShow.first
            val showAll = effShow.second
            val idx = dates.indexOfFirst { java.time.DayOfWeek.from(it) == day }
            val date = dates.getOrNull(idx)
            val source = date?.let { eff[it] } ?: date
            val weekday = source?.dayOfWeek ?: day
            // 源日期可能落在别的周（如 9/28 补 10/7 的课）
            val sourceWeek = if (source == null) {
                week
            } else {
                val baseMonday = dates.firstOrNull()
                    ?.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                val srcMonday = source
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                if (baseMonday == null) week
                else week + java.time.temporal.ChronoUnit.WEEKS.between(baseMonday, srcMonday).toInt()
            }
            val raw = all.filter { it.dayOfWeek == weekday }
            (if (showAll) raw else raw.filter { it.occursOnWeek(sourceWeek) })
                .sortedBy { it.startIndex }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 日视图选中日的**有效周次**：调休日取源日期所在的周。
     * 用于判断课程"是不是本周的"（灰显）。
     */
    val selectedSourceWeek: StateFlow<Int> =
        combine(_selectedWeek, _selectedDay, weekDates, effectiveDates) { week, day, dates, eff ->
            val idx = dates.indexOfFirst { java.time.DayOfWeek.from(it) == day }
            val date = dates.getOrNull(idx)
            val source = date?.let { eff[it] } ?: date
            if (source == null) {
                week
            } else {
                val baseMonday = dates.firstOrNull()
                    ?.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                val srcMonday = source
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                if (baseMonday == null) week
                else week + java.time.temporal.ChronoUnit.WEEKS.between(baseMonday, srcMonday).toInt()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    val allCourses: StateFlow<List<Course>> =
        repository.observeWeekCourses()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 全部作息节次（设置页/侧边栏展示用）。 */
    val timeSlots: StateFlow<List<TimeSlot>> = repository.observeTimeSlots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 实际显示的节次（受"课表显示节数"限制）。 */
    val visibleTimeSlots: StateFlow<List<TimeSlot>> =
        combine(timeSlots, _maxPeriods) { slots, max ->
            slots.take(max)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------- 操作 ----------------

    fun selectWeek(week: Int) { _selectedWeek.value = week.coerceIn(1, 30) }

    fun previousWeek() = selectWeek(_selectedWeek.value - 1)

    fun nextWeek() = selectWeek(_selectedWeek.value + 1)

    fun goToCurrentWeek() {
        selectWeek(SemesterStore.currentWeek(getApplication()))
        _selectedDay.value = DayOfWeek.from(LocalDate.now())
    }

    fun deleteCourse(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteCourse(id)
                // 删课后必须重排提醒 + 刷新小部件，
                // 否则已删课程的闹钟还在（课程名/地点已固化在 PendingIntent 里）会继续响。
                syncDownstream()
            }
        }
    }

    /** 课程数据变化后：重排上课提醒 + 刷新桌面小部件。 */
    private suspend fun syncDownstream() {
        val ctx = getApplication<Application>()
        runCatching {
            val courses = repository.getWeekCoursesSync()
            ClassReminderScheduler.scheduleNext(ctx, courses, SemesterStore.firstWeekMonday(ctx))
            ScheduleWidgetProvider.refreshAll(ctx)
        }
    }

    companion object {
        val WEEK_DAYS: List<Pair<String, DayOfWeek>> = listOf(
            "一" to DayOfWeek.MONDAY,
            "二" to DayOfWeek.TUESDAY,
            "三" to DayOfWeek.WEDNESDAY,
            "四" to DayOfWeek.THURSDAY,
            "五" to DayOfWeek.FRIDAY,
            "六" to DayOfWeek.SATURDAY,
            "日" to DayOfWeek.SUNDAY,
        )

        fun weekdayLabel(day: DayOfWeek): String = "周" + when (day) {
            DayOfWeek.MONDAY -> "一"
            DayOfWeek.TUESDAY -> "二"
            DayOfWeek.WEDNESDAY -> "三"
            DayOfWeek.THURSDAY -> "四"
            DayOfWeek.FRIDAY -> "五"
            DayOfWeek.SATURDAY -> "六"
            DayOfWeek.SUNDAY -> "日"
        }
    }
}
