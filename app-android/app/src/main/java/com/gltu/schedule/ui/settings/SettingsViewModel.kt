// GLTU 课表 App —— 设置页 ViewModel（节假日同步 / 屏蔽模式 / 上课提醒时间）
package com.gltu.schedule.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gltu.schedule.GltuScheduleApp
import com.gltu.schedule.calendar.CalendarSync
import com.gltu.schedule.data.Holiday
import com.gltu.schedule.data.HolidayManager
import com.gltu.schedule.data.HolidaySync
import com.gltu.schedule.data.SemesterStore
import com.gltu.schedule.notification.ClassReminderScheduler
import com.gltu.schedule.notification.ReminderPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as GltuScheduleApp).container.scheduleRepository

    // ---------------- 节假日 ----------------

    private val _blockSettings = MutableStateFlow(HolidayManager.blockSettings(app))
    val blockSettings: StateFlow<HolidayManager.BlockSettings> = _blockSettings.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(HolidayManager.lastSyncAt(app))
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    private val _holidayCount = MutableStateFlow(HolidayManager.allHolidays(app).size)
    val holidayCount: StateFlow<Int> = _holidayCount.asStateFlow()

    /** 已收录的假期清单（供用户核对是不是中国大陆的节日）。 */
    private val _holidayList = MutableStateFlow(
        HolidayManager.allHolidays(app).sortedBy { it.start },
    )
    val holidayList: StateFlow<List<Holiday>> = _holidayList.asStateFlow()

    /** 当前数据来源。 */
    private val _syncSource = MutableStateFlow(HolidayManager.syncSource(app))
    val syncSource: StateFlow<String> = _syncSource.asStateFlow()

    private fun refreshHolidayState() {
        val ctx = getApplication<Application>()
        _holidayList.value = HolidayManager.allHolidays(ctx).sortedBy { it.start }
        _holidayCount.value = _holidayList.value.size
        _syncSource.value = HolidayManager.syncSource(ctx)
        _lastSyncAt.value = HolidayManager.lastSyncAt(ctx)
    }

    fun setBlockSettings(settings: HolidayManager.BlockSettings) {
        HolidayManager.setBlockSettings(getApplication(), settings)
        _blockSettings.value = settings
    }

    fun syncHolidays() {
        viewModelScope.launch {
            _syncing.value = true
            _syncMessage.value = null
            val result = HolidaySync.sync(getApplication())
            _syncing.value = false
            _syncMessage.value = result.message
            refreshHolidayState()
        }
    }

    // ---------------- 上课提醒 ----------------

    private val _reminderEnabled = MutableStateFlow(ReminderPreference.isEnabled(app))
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    private val _reminderMinutes = MutableStateFlow(ReminderPreference.advanceMinutes(app))
    val reminderMinutes: StateFlow<List<Int>> = _reminderMinutes.asStateFlow()

    /** 系统是否允许排"精确闹钟"（Android 12+ 默认可能被拒，会导致提醒不准时）。 */
    private val _exactAlarmAllowed = MutableStateFlow(ClassReminderScheduler.canScheduleExactAlarms(app))
    val exactAlarmAllowed: StateFlow<Boolean> = _exactAlarmAllowed.asStateFlow()

    fun refreshExactAlarmState() {
        _exactAlarmAllowed.value = ClassReminderScheduler.canScheduleExactAlarms(getApplication())
    }

    fun setReminderEnabled(enabled: Boolean) {
        ReminderPreference.setEnabled(getApplication(), enabled)
        _reminderEnabled.value = enabled
        if (enabled) {
            rescheduleReminders()
        } else {
            // 关掉提醒时要把已排的闹钟清掉，否则它们还会继续响
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { ClassReminderScheduler.cancelAll(getApplication()) }
                }
            }
        }
    }

    /** 设置提前提醒的分钟数（1–60），并立即重排闹钟。 */
    fun setReminderMinutes(minutes: List<Int>) {
        ReminderPreference.setAdvanceMinutes(getApplication(), minutes)
        _reminderMinutes.value = ReminderPreference.advanceMinutes(getApplication())
        rescheduleReminders()
    }

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

    // ---------------- 同步到系统日历 ----------------

    private val _syncingCalendar = MutableStateFlow(false)
    val syncingCalendar: StateFlow<Boolean> = _syncingCalendar.asStateFlow()

    private val _calendarMessage = MutableStateFlow<String?>(null)
    val calendarMessage: StateFlow<String?> = _calendarMessage.asStateFlow()

    fun hasCalendarPermission(): Boolean = CalendarSync.hasPermission(getApplication())

    fun setCalendarMessage(msg: String?) {
        _calendarMessage.value = msg
    }

    /** 一键把课表写进系统日历。 */
    fun syncToCalendar() {
        viewModelScope.launch {
            _syncingCalendar.value = true
            val result = withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                CalendarSync.sync(
                    ctx,
                    repository.getWeekCoursesSync(),
                    SemesterStore.firstWeekMonday(ctx),
                )
            }
            _syncingCalendar.value = false
            _calendarMessage.value = result.message
        }
    }
}
