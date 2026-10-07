// GLTU 课表 App —— 课程持久化实体（Room）
// 与领域模型 model.Course 分离：存储层使用本实体，仓库层负责 entity <-> domain 映射。
package com.gltu.schedule.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gltu.schedule.data.CourseCategory
import java.time.DayOfWeek

/**
 * 课程表记录（一条 = 某门课在某周次区间、某一天、某几节连续节次的一次排课）。
 * [dayOfWeek] / [category] 为枚举，由 Converters 转为基础类型存储。
 */
@Entity(
    tableName = "courses",
    indices = [
        Index(value = ["dayOfWeek"]),
        Index(value = ["startWeek", "endWeek"]),
    ],
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: DayOfWeek,
    val startIndex: Int,
    val endIndex: Int,
    val startWeek: Int,
    val endWeek: Int,
    val oddEven: Int,          // 0=每周 1=单周 2=双周
    val category: CourseCategory,
    val credit: Double?,
    val weeksText: String,     // 原始周次文本
    val weeksCsv: String?,     // 显式周次集合，逗号分隔（如 "4,5,8,11,13,14,15,16"）
    val raw: String,           // 原始记录（调试用）
)
