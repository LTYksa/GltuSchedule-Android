// GLTU 课表 App —— 手动添加/编辑课程的 ViewModel
package com.ltyksa.gltuschedule.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ltyksa.gltuschedule.GltuScheduleApp
import com.ltyksa.gltuschedule.data.CoursePalette
import com.ltyksa.gltuschedule.data.GltuTimeTable
import com.ltyksa.gltuschedule.data.SemesterStore
import com.ltyksa.gltuschedule.data.TimeSlot
import com.ltyksa.gltuschedule.model.Course
import com.ltyksa.gltuschedule.notification.ClassReminderScheduler
import com.ltyksa.gltuschedule.repository.ScheduleRepository
import com.ltyksa.gltuschedule.widget.ScheduleWidgetProvider
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 编辑表单状态。 */
data class CourseFormState(
    val id: Long = 0,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val startIndex: Int = 1,
    val endIndex: Int = 1,
    val startWeek: Int = 1,
    val endWeek: Int = 16,
    val oddEven: Int = 0,
    val loading: Boolean = true,
    val saved: Boolean = false,
    val error: String? = null,
)

class EditCourseViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: ScheduleRepository =
        (app as GltuScheduleApp).container.scheduleRepository

    private val _form = MutableStateFlow(CourseFormState())
    val form: StateFlow<CourseFormState> = _form.asStateFlow()

    /**
     * 加载进来的原课程（新增时为 null）。
     * 表单里没有这些字段（显式周次集合、学分、原始周次文本、配色分类），
     * 保存时必须沿用原值，否则"只是改个教室"也会把它们清空。
     */
    private var original: Course? = null

    val timeSlots: List<TimeSlot> = GltuTimeTable.slots

    /** 加载要编辑的课程；[courseId] 为 null 表示新增。 */
    fun load(courseId: Long?) {
        if (courseId == null || courseId <= 0L) {
            original = null
            _form.value = CourseFormState(loading = false)
            return
        }
        viewModelScope.launch {
            val c = withContext(Dispatchers.IO) { repository.getCourseById(courseId) }
            original = c
            _form.value = if (c == null) {
                CourseFormState(loading = false, error = "课程不存在")
            } else {
                CourseFormState(
                    id = c.id,
                    name = c.name,
                    teacher = c.teacher,
                    location = c.location,
                    dayOfWeek = c.dayOfWeek,
                    startIndex = c.startIndex,
                    endIndex = c.endIndex,
                    startWeek = c.startWeek,
                    endWeek = c.endWeek,
                    oddEven = c.oddEven,
                    loading = false,
                )
            }
        }
    }

    fun update(transform: (CourseFormState) -> CourseFormState) {
        _form.value = transform(_form.value)
    }

    /** 保存（新增或更新）。 */
    fun save() {
        val f = _form.value
        if (f.name.isBlank()) {
            _form.value = f.copy(error = "请填写课程名称")
            return
        }
        val orig = original
        val course = mergeCourseForm(f, orig)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.upsertCourse(course)
                syncDownstream()
            }
            _form.value = _form.value.copy(saved = true, error = null)
        }
    }

    fun delete() {
        val id = _form.value.id
        if (id <= 0L) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteCourse(id)
                syncDownstream()
            }
            _form.value = _form.value.copy(saved = true)
        }
    }

    /** 课程变更后：重排上课提醒 + 刷新桌面小部件，保证与小部件/提醒同步。 */
    private suspend fun syncDownstream() {
        val ctx = getApplication<Application>()
        runCatching {
            val courses = repository.getWeekCoursesSync()
            ClassReminderScheduler.scheduleNext(ctx, courses, SemesterStore.firstWeekMonday(ctx))
            ScheduleWidgetProvider.refreshAll(ctx)
        }
    }
}

/**
 * 把编辑表单合并成最终课程对象（纯函数，便于单元测试）。
 *
 * 关键点：表单里**没有**这些字段 —— 显式周次集合 `weeks`、原始周次文本 `weeksText`、
 * 学分 `credit`、原始记录 `raw`、配色分类 `category`。
 * 编辑时必须沿用 [original]，否则"只改个教室"也会把它们清空：
 *   - `weeks` 丢了 → 不规则周次（如 `4-5周,8周,11-13周(单)`）会退化成连续的 4-16 周，
 *     凭空多出好几周的课，还会连带影响上课提醒和日历同步。
 *   - `credit` 丢了 → 详情弹窗里的学分消失。
 *
 * 但用户若在表单里改过「周次范围 / 单双周」，就以表单为准，丢弃原来的显式周次。
 */
internal fun mergeCourseForm(form: CourseFormState, original: Course?): Course {
    val start = minOf(form.startIndex, form.endIndex).coerceAtLeast(1)
    val end = maxOf(form.startIndex, form.endIndex)
    // 周次两端都要夹在 1..30，且保证 start <= end。
    // 只夹 end 不夹 start 的话，输入"起始周 40 / 结束周 35"会得到 35..30，
    // 使 occursOnWeek 恒为 false —— 课程在课表里彻底消失（提醒和日历同步也会跳过）。
    val weekLo = minOf(form.startWeek, form.endWeek)
    val weekHi = maxOf(form.startWeek, form.endWeek)
    val startWeek = weekLo.coerceIn(1, 30)
    val endWeek = weekHi.coerceIn(startWeek, 30)

    val weeksTouched = original == null ||
        original.startWeek != startWeek ||
        original.endWeek != endWeek ||
        original.oddEven != form.oddEven

    return Course(
        id = form.id,
        name = form.name.trim(),
        teacher = form.teacher.trim(),
        location = form.location.trim(),
        dayOfWeek = form.dayOfWeek,
        startIndex = start,
        endIndex = end,
        startWeek = startWeek,
        endWeek = endWeek,
        oddEven = form.oddEven,
        category = original?.category ?: CoursePalette.inferCategory(form.name, form.location),
        credit = original?.credit,
        weeksText = if (weeksTouched) "" else original?.weeksText.orEmpty(),
        weeks = if (weeksTouched) emptyList() else original?.weeks.orEmpty(),
        raw = original?.raw.orEmpty(),
    )
}
