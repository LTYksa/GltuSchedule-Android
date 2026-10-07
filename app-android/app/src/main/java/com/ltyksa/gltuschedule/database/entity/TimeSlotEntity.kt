// GLTU 课表 App —— 作息节次持久化实体（Room）
package com.ltyksa.gltuschedule.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ltyksa.gltuschedule.data.DaySection

/** 单个节次（上课时间段）。[index] 与 data.TimeSlot / data.GltuTimeTable 的 index 对齐。
 *  列名用 slot_index，避免 "index" 作为 SQL 保留字引发 Room 解析错误。 */
@Entity(tableName = "time_slots")
data class TimeSlotEntity(
    @PrimaryKey
    @ColumnInfo(name = "slot_index")
    val index: Int,
    val name: String,
    val startTime: String,   // "HH:mm"
    val endTime: String,     // "HH:mm"
    val section: DaySection, // 上午/下午/晚上
)
