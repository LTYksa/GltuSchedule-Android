// GLTU 课表 App —— 持久化实体 <-> 领域模型 映射
package com.gltu.schedule.database.mapper

import com.gltu.schedule.data.Holiday
import com.gltu.schedule.data.TimeSlot
import com.gltu.schedule.database.entity.CourseEntity
import com.gltu.schedule.database.entity.HolidayEntity
import com.gltu.schedule.database.entity.TimeSlotEntity
import com.gltu.schedule.model.Course

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
