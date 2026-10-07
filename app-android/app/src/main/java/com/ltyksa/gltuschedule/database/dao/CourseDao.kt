// GLTU 课表 App —— 课程 DAO
package com.ltyksa.gltuschedule.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ltyksa.gltuschedule.database.entity.CourseEntity
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    /** 观察全部课程（按周几、起始节次排序），供周课表整表渲染。 */
    @Query("SELECT * FROM courses ORDER BY dayOfWeek ASC, startIndex ASC, endIndex ASC")
    fun observeAll(): Flow<List<CourseEntity>>

    /** 观察某一天课程。 */
    @Query("SELECT * FROM courses WHERE dayOfWeek = :day ORDER BY startIndex ASC, endIndex ASC")
    fun observeByDay(day: DayOfWeek): Flow<List<CourseEntity>>

    /** 同步查询全部（供小部件等后台线程场景；切勿在主线程调用）。 */
    @Query("SELECT * FROM courses ORDER BY dayOfWeek ASC, startIndex ASC")
    fun getAllSync(): List<CourseEntity>

    /** 同步查询某一天（供小部件等后台线程场景）。 */
    @Query("SELECT * FROM courses WHERE dayOfWeek = :day ORDER BY startIndex ASC")
    fun getByDaySync(day: DayOfWeek): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CourseEntity?

    /** 插入或替换（以主键 id 为准）。sourceId 去重逻辑交给导入模块在写入前处理。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(courses: List<CourseEntity>): List<Long>

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM courses")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun count(): Int
}
