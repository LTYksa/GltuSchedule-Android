// GLTU 课表 App —— Room 类型转换器
package com.ltyksa.gltuschedule.database

import androidx.room.TypeConverter
import com.ltyksa.gltuschedule.data.CourseCategory
import com.ltyksa.gltuschedule.data.DaySection
import com.ltyksa.gltuschedule.data.HolidayType
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 把 java.time / 自定义枚举转成 Room 能直接存储的基础类型。
 * 统一在 [AppDatabase] 上通过 @TypeConverters 注册，对所有实体/DAO 生效。
 */
class Converters {

    // DayOfWeek <-> Int（1=周一 … 7=周日，与 java.time.DayOfWeek 一致）
    @TypeConverter
    fun dayOfWeekToInt(value: DayOfWeek): Int = value.value

    @TypeConverter
    fun intToDayOfWeek(value: Int): DayOfWeek = DayOfWeek.of(value)

    // CourseCategory <-> String（按枚举名，稳健可读，避免 ordinal 易位问题）
    @TypeConverter
    fun categoryToString(value: CourseCategory): String = value.name

    @TypeConverter
    fun stringToCategory(value: String): CourseCategory = CourseCategory.valueOf(value)

    // DaySection <-> String
    @TypeConverter
    fun daySectionToString(value: DaySection): String = value.name

    @TypeConverter
    fun stringToDaySection(value: String): DaySection = DaySection.valueOf(value)

    // HolidayType <-> String
    @TypeConverter
    fun holidayTypeToString(value: HolidayType): String = value.name

    @TypeConverter
    fun stringToHolidayType(value: String): HolidayType = HolidayType.valueOf(value)

    // LocalDate <-> String（ISO-8601，字典序即时间序）
    @TypeConverter
    fun localDateToString(value: LocalDate): String = value.toString()

    @TypeConverter
    fun stringToLocalDate(value: String): LocalDate = LocalDate.parse(value)
}
