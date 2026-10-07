// GLTU 课表 App —— 课表页 ViewModel（周/日双视图 + 周次切换 + 课表设置）
package com.gltu.schedule.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gltu.schedule.GltuScheduleApp
import com.gltu.schedule.data.BackgroundImageStore
import com.gltu.schedule.data.GltuTimeTable
import com.gltu.schedule.data.HolidayManager
import com.gltu.schedule.data.SchedulePreferences
import com.gltu.schedule.data.SemesterStore
import com.gltu.schedule.data.TimeSlot
import com.gltu.schedule.model.Course
import com.gltu.schedule.notification.ClassReminderScheduler
import com.gltu.schedule.repository.ScheduleRepository
import com.gltu.schedule.widget.ScheduleWidgetProvider
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
        com.gltu.schedule.ui.theme.AppThemeState.mode = mode
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

    /** 选中那天的课程（日视图用）。 */
    val coursesOfDay: StateFlow<List<Course>> =
        combine(coursesOfWeek, _selectedDay) { list, day ->
            list.filter { it.dayOfWeek == day }.sortedBy { it.startIndex }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
