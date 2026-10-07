// GLTU 课表 App —— 课表仓库（MVVM 的 Repository 层）
package com.ltyksa.gltuschedule.repository

import com.ltyksa.gltuschedule.data.GltuTimeTable
import com.ltyksa.gltuschedule.data.Holiday
import com.ltyksa.gltuschedule.data.TimeSlot
import com.ltyksa.gltuschedule.database.dao.CourseDao
import com.ltyksa.gltuschedule.database.dao.HolidayDao
import com.ltyksa.gltuschedule.database.dao.TimeSlotDao
import com.ltyksa.gltuschedule.database.mapper.toDomain
import com.ltyksa.gltuschedule.database.mapper.toEntity
import com.ltyksa.gltuschedule.model.Course
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 课表仓库：ViewModel 与数据层之间的唯一入口。
 * 对外暴露领域模型（model.Course / data.TimeSlot / data.Holiday），
 * 内部负责 entity <-> domain 映射。
 */
class ScheduleRepository(
    private val courseDao: CourseDao,
    private val timeSlotDao: TimeSlotDao,
    private val holidayDao: HolidayDao,
) {

    // ---------- 响应式查询（供 ViewModel 以 Flow 订阅） ----------

    fun observeWeekCourses(): Flow<List<Course>> =
        courseDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeTimeSlots(): Flow<List<TimeSlot>> =
        timeSlotDao.observeAll().map { list -> list.map { it.toDomain() } }

    // ---------- 同步读取（供小部件等后台线程场景） ----------

    fun getWeekCoursesSync(): List<Course> = courseDao.getAllSync().map { it.toDomain() }

    fun getDayCoursesSync(day: DayOfWeek): List<Course> = courseDao.getByDaySync(day).map { it.toDomain() }

    /** 按 id 读取单门课程（供编辑页回填）。 */
    suspend fun getCourseById(id: Long): Course? = courseDao.getById(id)?.toDomain()

    // ---------- 写入（供导入模块 / 设置模块调用） ----------

    suspend fun upsertCourse(course: Course): Long = courseDao.upsert(course.toEntity())

    suspend fun upsertCourses(courses: List<Course>) = courseDao.upsertAll(courses.map { it.toEntity() })

    suspend fun deleteCourse(id: Long) = courseDao.deleteById(id)

    suspend fun clearCourses() = courseDao.clearAll()

    /**
     * 用内置作息表（data.GltuTimeTable）刷新 Room 里的节次表。
     * 作息表属于"随版本更新的静态配置"，所以每次启动都整体覆盖，避免旧版本残留。
     */
    suspend fun refreshTimeSlots() {
        timeSlotDao.clearAll()
        timeSlotDao.upsertAll(GltuTimeTable.slots.map { it.toEntity() })
    }
}
