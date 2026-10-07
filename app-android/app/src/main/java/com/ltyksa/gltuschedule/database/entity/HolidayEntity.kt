// GLTU 课表 App —— 节假日持久化实体（Room）
package com.ltyksa.gltuschedule.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ltyksa.gltuschedule.data.HolidayType
import java.time.LocalDate

/**
 * 节假日记录（闭区间）。
 * 说明：当前"节假日屏蔽"由 data.HolidayManager（SharedPreferences）承担；
 * 本表作为统一持久化的扩展点预留，后续可把 HolidayManager 迁移到 Room。
 */
@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val type: HolidayType,
)
