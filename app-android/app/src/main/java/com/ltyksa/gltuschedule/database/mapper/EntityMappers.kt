// GLTU 课表 App —— 持久化实体 <-> 领域模型 映射
package com.ltyksa.gltuschedule.database.mapper

import com.ltyksa.gltuschedule.data.Holiday
import com.ltyksa.gltuschedule.data.TimeSlot
import com.ltyksa.gltuschedule.database.entity.CourseEntity
import com.ltyksa.gltuschedule.database.entity.HolidayEntity
import com.ltyksa.gltuschedule.database.entity.TimeSlotEntity
import com.ltyksa.gltuschedule.model.Course

// ---------- Course ----------

fun CourseEntity.toDomain(): Course = Course(
    id = id,
    name = name,
    teacher = teacher,
    location = location,
    dayOfWeek = dayOfWeek,
    startIndex = startIndex,
    endIndex = endIndex,
    startWeek = startWeek,
    endWeek = endWeek,
    oddEven = oddEven,
    category = category,
    credit = credit,
    weeksText = weeksText,
    weeks = weeksCsv
        ?.split(',')
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.distinct()
        ?.sorted()
        ?: emptyList(),
    raw = raw,
)

fun Course.toEntity(): CourseEntity = CourseEntity(
    id = id,
    name = name,
    teacher = teacher,
    location = location,
    dayOfWeek = dayOfWeek,
    startIndex = startIndex,
    endIndex = endIndex,
    startWeek = startWeek,
    endWeek = endWeek,
    oddEven = oddEven,
    category = category,
    credit = credit,
    weeksText = weeksText,
    weeksCsv = if (weeks.isEmpty()) null else weeks.distinct().sorted().joinToString(","),
    raw = raw,
)

// ---------- TimeSlot ----------

fun TimeSlotEntity.toDomain(): TimeSlot = TimeSlot(
    index = index,
    name = name,
    startTime = startTime,
    endTime = endTime,
    section = section,
)

fun TimeSlot.toEntity(): TimeSlotEntity = TimeSlotEntity(
    index = index,
    name = name,
    startTime = startTime,
    endTime = endTime,
    section = section,
)

// ---------- Holiday ----------

fun HolidayEntity.toDomain(): Holiday = Holiday(
    name = name,
    start = startDate,
    end = endDate,
    type = type,
)

fun Holiday.toEntity(): HolidayEntity = HolidayEntity(
    name = name,
    startDate = start,
    endDate = end,
    type = type,
)
