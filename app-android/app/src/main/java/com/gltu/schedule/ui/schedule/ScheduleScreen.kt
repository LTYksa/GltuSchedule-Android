// GLTU 课表 App —— 课表主界面（周视图网格 + 日视图列表）
// 自适应手机宽度；支持自定义壁纸；非本周课程灰显；节假日屏蔽。
package com.gltu.schedule.ui.schedule

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gltu.schedule.GltuScheduleApp
import com.gltu.schedule.data.BackgroundImageStore
import com.gltu.schedule.data.Classroom
import com.gltu.schedule.data.CourseColor
import com.gltu.schedule.data.GltuTimeTable
import com.gltu.schedule.data.HolidayManager
import com.gltu.schedule.data.TimeSlot
import com.gltu.schedule.model.Course
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlinx.coroutines.launch

private val TIME_COL_W = 40.dp
private val ROW_H = 54.dp

/** 日期行高度：留足空间让节假日标记「假」完整显示。 */
private val DATE_ROW_H = 70.dp
private val DATE_BOX = 26.dp

/** 非本周课程统一用灰色。 */
private val DIM_GRAY = Color(0xFF9E9E9E)

@Composable
fun ScheduleScreen(
    onAddCourse: () -> Unit = {},
    onEditCourse: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as GltuScheduleApp
    val viewModel: ScheduleViewModel = viewModel { ScheduleViewModel(app) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val week by viewModel.selectedWeek.collectAsStateWithLifecycle()
    val semester by viewModel.semesterName.collectAsStateWithLifecycle()
    val mode by viewModel.viewMode.collectAsStateWithLifecycle()
    val courses by viewModel.coursesOfWeek.collectAsStateWithLifecycle()
    val dayCourses by viewModel.coursesOfDay.collectAsStateWithLifecycle()
    val timeSlots by viewModel.visibleTimeSlots.collectAsStateWithLifecycle()
    val weekDates by viewModel.weekDates.collectAsStateWithLifecycle()
    val todayIndex by viewModel.todayIndex.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val dayLabel by viewModel.selectedDayLabel.collectAsStateWithLifecycle()
    val dayOrder by viewModel.dayOrder.collectAsStateWithLifecycle()
    val maxPeriods by viewModel.maxPeriods.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
    val showAllWeeks by viewModel.showAllWeeks.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val firstMonday by viewModel.firstMonday.collectAsStateWithLifecycle()
    val startDate by viewModel.startDateRaw.collectAsStateWithLifecycle()
    val blockSettings by viewModel.blockSettings.collectAsStateWithLifecycle()
    val hasBackground by viewModel.hasBackground.collectAsStateWithLifecycle()
    val bgVersion by viewModel.backgroundVersion.collectAsStateWithLifecycle()

    var detailCourse by remember { mutableStateOf<Course?>(null) }
    var showWeekPicker by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val drawerWidth: Dp = (configuration.screenWidthDp * 0.62f).dp

    val background = remember(bgVersion) {
        BackgroundImageStore.loadScaled(context)?.asImageBitmap()
    }

    // 假期数据版本号：后台同步完成 / 手动同步 / 改开关后会 +1，用来触发下面的重算
    val holidayVersion by HolidayManager.version.collectAsStateWithLifecycle()

    // ① 假期名 —— 纯粹的日历事实，**不随屏蔽开关变化**（关掉屏蔽后日期上依然显示"校运会"）
    val holidayLabels = remember(weekDates, holidayVersion) {
        weekDates.map { d -> HolidayManager.holidayNameOf(context, d) }
    }
    // ② 是否屏蔽 —— 受三个开关控制，只决定"课程显示/隐藏"，不影响上面的假期名
    val blockedDays = remember(weekDates, blockSettings, holidayVersion) {
        weekDates.map { d -> HolidayManager.isBlocked(context, d) }
    }
    // 日期下标：优先显示假期名；纯周末（没有具名假期）且被屏蔽时显示"周末"
    val dateLabels = remember(holidayLabels, blockedDays, weekDates) {
        weekDates.indices.map { i ->
            holidayLabels[i]
                ?: if (blockedDays[i] && HolidayManager.isWeekend(weekDates[i])) "周末" else null
        }
    }

    val selectedIndex = remember(selectedDay, weekDates) {
        weekDates.indexOfFirst { DayOfWeek.from(it) == selectedDay }
    }
    val selectedDayLabel = if (selectedIndex >= 0) dateLabels.getOrNull(selectedIndex) else null
    val selectedDayBlocked = selectedIndex >= 0 && blockedDays.getOrNull(selectedIndex) == true

    LaunchedEffect(Unit) { viewModel.refreshSemesterInfo() }

    // 从后台回到前台时重读设置与"今天"（跨零点后日期高亮才会跟着变）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSemesterInfo()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ScheduleSettingsDrawer(
                modifier = Modifier.requiredWidth(drawerWidth),
                semesterLabel = semester.ifBlank { "未设置学期" },
                maxPeriods = maxPeriods,
                weekStart = weekStart,
                showAllWeeks = showAllWeeks,
                themeMode = themeMode,
                startDate = startDate,
                holidaySettings = blockSettings,
                hasBackground = hasBackground,
                onClose = { scope.launch { drawerState.close() } },
                onMaxPeriodsChange = viewModel::setMaxPeriods,
                onWeekStartChange = viewModel::setWeekStart,
                onShowAllWeeksChange = viewModel::setShowAllWeeks,
                onThemeModeChange = viewModel::setThemeMode,
                onSetStartDate = viewModel::setStartDate,
                onSetSemesterName = viewModel::setSemesterName,
                onHolidaySettingsChange = viewModel::setBlockSettings,
                onSaveBackgroundBitmap = { bmp -> viewModel.setBackgroundBitmap(bmp) },
                onClearBackground = viewModel::clearBackground,
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (background != null) {
                // 壁纸铺满整屏：Crop 保证不留边；裁切输出已是屏幕比例，不会被二次裁掉内容
                Image(
                    bitmap = background,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // 渐变蒙层：顶部（顶栏/日期行）稍重，保证花哨壁纸上文字依然清晰
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.00f to MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
                                0.28f to MaterialTheme.colorScheme.surface.copy(alpha = 0.40f),
                                1.00f to MaterialTheme.colorScheme.surface.copy(alpha = 0.32f),
                            ),
                        ),
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                ScheduleTopBar(
                    week = week,
                    semester = semester,
                    mode = mode,
                    onOpenSettings = { scope.launch { drawerState.open() } },
                    onPrevWeek = viewModel::previousWeek,
                    onNextWeek = viewModel::nextWeek,
                    onPickWeek = { showWeekPicker = true },
                    onModeChange = viewModel::setViewMode,
                    onAdd = onAddCourse,
                )

                if (mode == ScheduleViewMode.WEEK) {
                    WeekDateRow(
                        dates = weekDates,
                        dayOrder = dayOrder,
                        todayIndex = todayIndex,
                        selectedDay = selectedDay,
                        labels = dateLabels,
                        blocked = blockedDays,
                        // 只切换选中日期，不跳日视图（只有点「日」按钮才切换）
                        onPickDay = { day -> viewModel.selectDay(day) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    WeekGridView(
                        courses = courses,
                        timeSlots = timeSlots,
                        dayOrder = dayOrder,
                        currentWeek = week,
                        blockedDaysByIndex = blockedDays,
                        onWallpaper = background != null,
                        onCourseClick = { detailCourse = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DayListView(
                        dayLabel = dayLabel,
                        courses = dayCourses,
                        currentWeek = week,
                        holidayName = selectedDayLabel,
                        blocked = selectedDayBlocked,
                        onWallpaper = background != null,
                        onCourseClick = { detailCourse = it },
                        onAdd = onAddCourse,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }

    if (showWeekPicker) {
        WeekPickerDialog(
            current = week,
            firstMonday = firstMonday,
            onPick = { viewModel.selectWeek(it); showWeekPicker = false },
            onDismiss = { showWeekPicker = false },
        )
    }

    detailCourse?.let { c ->
        CourseDetailDialog(
            course = c,
            onDismiss = { detailCourse = null },
            onEdit = { detailCourse = null; onEditCourse(c.id) },
            onDelete = { detailCourse = null; viewModel.deleteCourse(c.id) },
        )
    }
}

// ===================== 顶部栏 =====================

@Composable
private fun ScheduleTopBar(
    week: Int,
    semester: String,
    mode: ScheduleViewMode,
    onOpenSettings: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onPickWeek: () -> Unit,
    onModeChange: (ScheduleViewMode) -> Unit,
    onAdd: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 左上角常驻按钮：课表设置侧边栏
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "课表设置",
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onPrevWeek, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上一周", modifier = Modifier.size(20.dp))
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPickWeek)
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("第 $week 周", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择周次", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onNextWeek, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下一周", modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.weight(1f))

            ViewModeToggle(mode = mode, onChange = onModeChange)
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onAdd, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "添加课程")
            }
        }
        Text(
            text = semester.ifBlank { "未设置学期（课表设置里可手动填写）" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 58.dp),
        )
    }
}

/** 紧凑的「周 / 日」切换（比两个 FilterChip 省一半宽度）。 */
@Composable
private fun ViewModeToggle(
    mode: ScheduleViewMode,
    onChange: (ScheduleViewMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            listOf(
                ScheduleViewMode.WEEK to "周",
                ScheduleViewMode.DAY to "日",
            ).forEach { (m, label) ->
                val selected = mode == m
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .clickable { onChange(m) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ===================== 日期行 =====================

@Composable
private fun WeekDateRow(
    dates: List<LocalDate>,
    dayOrder: List<Pair<String, DayOfWeek>>,
    todayIndex: Int,
    selectedDay: DayOfWeek,
    /** 假期名（日历事实，不随屏蔽开关变化）。 */
    labels: List<String?>,
    /** 是否屏蔽课程（受开关控制，只影响底色与文字颜色）。 */
    blocked: List<Boolean>,
    onPickDay: (DayOfWeek) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(TIME_COL_W)
                .height(DATE_ROW_H),
            contentAlignment = Alignment.Center,
        ) {
            val month = dates.firstOrNull()?.monthValue
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    month?.toString() ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("月", style = MaterialTheme.typography.labelSmall)
            }
        }
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val colW = maxWidth / 7
            Row {
                dayOrder.forEachIndexed { idx, (label, day) ->
                    val date = dates.getOrNull(idx)
                    val isToday = idx == todayIndex
                    val isSelected = day == selectedDay
                    val holidayLabel = labels.getOrNull(idx)
                    val isBlocked = blocked.getOrNull(idx) == true
                    Column(
                        modifier = Modifier
                            .width(colW)
                            .height(DATE_ROW_H)
                            .clickable { onPickDay(day) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .size(DATE_BOX)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    when {
                                        isBlocked -> MaterialTheme.colorScheme.errorContainer
                                        isToday -> MaterialTheme.colorScheme.primary
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        else -> Color.Transparent
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = date?.dayOfMonth?.toString() ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isBlocked -> MaterialTheme.colorScheme.onErrorContainer
                                    isToday -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        // 假期名：始终显示（不管有没有开屏蔽）；被屏蔽时标红，未屏蔽时灰色
                        if (holidayLabel != null) {
                            Text(
                                text = holidayLabel,
                                fontSize = 9.sp,
                                lineHeight = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isBlocked) FontWeight.Medium else FontWeight.Normal,
                                color = if (isBlocked) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 1.dp),
                            )
                        } else {
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

// ===================== 周视图网格 =====================

@Composable
private fun WeekGridView(
    courses: List<Course>,
    timeSlots: List<TimeSlot>,
    dayOrder: List<Pair<String, DayOfWeek>>,
    currentWeek: Int,
    blockedDaysByIndex: List<Boolean>,
    onWallpaper: Boolean,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (timeSlots.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("作息节次未初始化", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val vScroll = rememberScrollState()

    BoxWithConstraints(modifier = modifier) {
        val dayColW = (maxWidth - TIME_COL_W) / 7
        // 找不到节次时返回 -1（而不是 0）。返回 0 会把"超出显示节数"的课
        // 画到第一行（08:20），视觉上完全错位。
        val rowIndexOf: (Int) -> Int = { idx -> timeSlots.indexOfFirst { it.index == idx } }
        val dayIndexOf: (DayOfWeek) -> Int = { d ->
            dayOrder.indexOfFirst { it.second == d }.let { if (it < 0) 0 else it }
        }
        // 被屏蔽的星期（受三个开关控制）
        val blockedWeekdays = dayOrder.mapIndexedNotNull { idx, (_, day) ->
            if (blockedDaysByIndex.getOrNull(idx) == true) day else null
        }.toSet()

        Column(modifier = Modifier.verticalScroll(vScroll)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_H * timeSlots.size),
            ) {
                timeSlots.forEachIndexed { row, _ ->
                    Box(
                        modifier = Modifier
                            .offset(x = TIME_COL_W, y = ROW_H * row)
                            .width(dayColW * 7)
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                Box(
                    modifier = Modifier
                        .offset(x = TIME_COL_W, y = 0.dp)
                        .width(0.5.dp)
                        .height(ROW_H * timeSlots.size)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )

                // 左侧时间列：节次号 + 「上课 / 下课」两行紧贴
                timeSlots.forEachIndexed { row, slot ->
                    Box(
                        modifier = Modifier
                            .offset(x = 0.dp, y = ROW_H * row)
                            .size(TIME_COL_W, ROW_H),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                slot.index.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            // 上下课时间合并成一个两行文本，行高收紧，两行贴在一起
                            Text(
                                text = "${slot.startTime}\n${slot.endTime}",
                                fontSize = 8.sp,
                                lineHeight = 9.5.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 课程格子
                val grouped = courses
                    .filter { it.dayOfWeek !in blockedWeekdays }   // 节假日屏蔽
                    // 起始节次不在"显示节数"范围内（例如只显示前 6 节，而课在第 10 节）的课直接跳过
                    .filter { rowIndexOf(it.startIndex) >= 0 }
                    .groupBy { it.dayOfWeek to rowIndexOf(it.startIndex) }

                grouped.forEach { (key, list) ->
                    val (day, rowStart) = key
                    if (rowStart < 0 || rowStart > timeSlots.lastIndex) return@forEach
                    val current = list.filter { it.occursOnWeek(currentWeek) }
                    val currentNames = current.map { it.name }.toSet()
                    // 非本周课程：与本周课程同名的直接不显示（避免重复占位）
                    val others = list.filter {
                        !it.occursOnWeek(currentWeek) && it.name !in currentNames
                    }
                    val ordered = current + others
                    if (ordered.isEmpty()) return@forEach

                    val laneW = dayColW / ordered.size
                    ordered.forEachIndexed { lane, course ->
                        // 结束节次若超出显示范围，就画到最后一行，而不是跳回第 0 行
                        val endRow = rowIndexOf(course.endIndex)
                            .let { if (it < 0) timeSlots.lastIndex else it }
                            .coerceAtLeast(rowStart)
                        CourseGridCell(
                            course = course,
                            x = TIME_COL_W + dayColW * dayIndexOf(day) + laneW * lane,
                            y = ROW_H * rowStart,
                            width = laneW,
                            height = ROW_H * (endRow - rowStart + 1),
                            dimmed = lane >= current.size,
                            onWallpaper = onWallpaper,
                            onClick = { onCourseClick(course) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 课程格子：优先保证文字"看得清"。
 * 字号按列宽取 8.5–10sp（不再缩到 6sp 的瞎眼尺寸）；
 * 空间不足时按优先级取舍内容：课程名 > 地点 > 教师。
 */
@Composable
private fun CourseGridCell(
    course: Course,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    dimmed: Boolean,
    onWallpaper: Boolean,
    onClick: () -> Unit,
) {
    val base = if (dimmed) DIM_GRAY else CourseColor.of(course.name)
    // 文字用加深色，浅底上对比更清晰
    val textColor = darken(base, if (dimmed) 0.75f else 0.62f)
    // 有壁纸时格子底色更实，避免被照片干扰
    val fillAlpha = when {
        dimmed -> 0.10f
        onWallpaper -> 0.34f
        else -> 0.13f
    }
    val barAlpha = if (dimmed) 0.55f else 0.9f

    val innerW = (width.value - 9f).coerceAtLeast(10f)
    val innerH = (height.value - 5f).coerceAtLeast(8f)
    // sp → dp 的换算系数：系统字体放大时，同样的 sp 会占更多 dp，
    // 不换算会把行数/字数估多，导致文字被裁或过早出现省略号。
    val fontScale = LocalDensity.current.fontScale

    // 按列宽定字号（保证可读）
    val fontSize = when {
        width.value < 30f -> 8.5f
        width.value < 42f -> 9f
        width.value < 60f -> 9.5f
        else -> 10f
    }
    val lineH = fontSize * 1.24f
    val lineHDp = lineH * fontScale
    val maxLines = (innerH / lineHDp).toInt().coerceAtLeast(1)
    val charWidthDp = fontSize * fontScale
    val charsPerLine = (innerW / charWidthDp).coerceAtLeast(1f)
    fun linesOf(s: String): Int = ceil(s.length / charsPerLine).toInt().coerceAtLeast(1)

    // 优先级：课程名 > 地点 > 教师；放不下就从最后往前丢
    val visible = ArrayList<String>().apply {
        add(course.name)
        Classroom.shortLabel(course.location).takeIf { it.isNotBlank() }?.let { add(it) }
        course.teacher.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    while (visible.size > 1 && visible.sumOf { linesOf(it) } > maxLines) {
        visible.removeAt(visible.lastIndex)
    }

    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(width, height)
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(base.copy(alpha = fillAlpha))
            .border(0.8.dp, base.copy(alpha = if (dimmed) 0.28f else 0.42f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(base.copy(alpha = barAlpha)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 5.dp, top = 2.dp, end = 2.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            visible.forEachIndexed { i, text ->
                Text(
                    text = text,
                    color = if (i == 0) textColor else textColor.copy(alpha = 0.9f),
                    fontSize = (if (i == 0) fontSize else fontSize - 0.5f).sp,
                    lineHeight = lineH.sp,
                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = linesOf(text).coerceAtMost(maxLines),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 把颜色按比例加深（用于文字，提升浅底上的可读性）。 */
private fun darken(color: Color, factor: Float): Color = Color(
    red = (color.red * factor).coerceIn(0f, 1f),
    green = (color.green * factor).coerceIn(0f, 1f),
    blue = (color.blue * factor).coerceIn(0f, 1f),
    alpha = 1f,
)

// ===================== 日视图 =====================

@Composable
private fun DayListView(
    dayLabel: String,
    courses: List<Course>,
    currentWeek: Int,
    holidayName: String?,
    blocked: Boolean,
    onWallpaper: Boolean,
    onCourseClick: (Course) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(dayLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            // 假期名始终显示；后面跟的状态取决于是否被屏蔽
            if (holidayName != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (blocked) "$holidayName · 不排课" else holidayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (blocked) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (blocked) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "周末 · 不排课",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (blocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "今天是${holidayName ?: "周末"}，课程已屏蔽 🎉",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "可在课表设置里调整节假日屏蔽方式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }
        if (courses.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("这一天没有课", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onAdd) { Text("添加课程") }
            }
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 8.dp, end = 12.dp, bottom = 24.dp,
            ),
        ) {
            items(courses, key = { it.id * 1000 + it.startIndex }) { c ->
                DayCourseRow(
                    course = c,
                    dimmed = !c.occursOnWeek(currentWeek),
                    onWallpaper = onWallpaper,
                    onClick = { onCourseClick(c) },
                )
            }
        }
    }
}

@Composable
private fun DayCourseRow(course: Course, dimmed: Boolean, onWallpaper: Boolean, onClick: () -> Unit) {
    val accent = if (dimmed) DIM_GRAY else CourseColor.of(course.name)
    val rowFill = when {
        dimmed -> 0.10f
        onWallpaper -> 0.34f
        else -> 0.13f
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(54.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                GltuTimeTable.byIndex(course.startIndex)?.startTime ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(
                        if (dimmed) DIM_GRAY.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
            )
            Text(
                GltuTimeTable.byIndex(course.endIndex)?.endTime ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            color = accent.copy(alpha = rowFill),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
        ) {
            Row {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(78.dp)
                        .background(accent.copy(alpha = if (dimmed) 0.5f else 0.9f)),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        course.name,
                        color = accent,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        Classroom.shortLabel(course.location).ifBlank { "地点待定" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (course.teacher.isNotBlank()) {
                        Text(
                            course.teacher,
                            style = MaterialTheme.typography.bodySmall,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(
                    modifier = Modifier.padding(end = 10.dp, top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        GltuTimeTable.rangeLabel(course.startIndex, course.endIndex),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                    if (dimmed) {
                        Spacer(Modifier.height(4.dp))
                        Text("非本周", style = MaterialTheme.typography.labelSmall, color = DIM_GRAY)
                    }
                }
            }
        }
    }
}

// ===================== 周次选择 / 课程详情 =====================

@Composable
private fun WeekPickerDialog(
    current: Int,
    firstMonday: LocalDate?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择周次") },
        text = {
            LazyColumn(modifier = Modifier.height(320.dp)) {
                items((1..24).toList()) { w ->
                    val range = firstMonday?.let { m ->
                        val s = m.plusWeeks((w - 1).toLong())
                        val e = s.plusDays(6)
                        "${s.monthValue}/${s.dayOfMonth} – ${e.monthValue}/${e.dayOfMonth}"
                    } ?: ""
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(w) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "第 $w 周",
                            fontWeight = if (w == current) FontWeight.Bold else FontWeight.Normal,
                            color = if (w == current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(80.dp),
                        )
                        Text(
                            range,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun CourseDetailDialog(
    course: Course,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除课程") },
            text = { Text("确定删除「${course.name}」吗？") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(course.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow("教师", course.teacher.ifBlank { "—" })
                InfoRow("地点", Classroom.detailLabel(course.location).ifBlank { "—" })
                InfoRow(
                    "时间",
                    "${ScheduleViewModel.weekdayLabel(course.dayOfWeek)} " +
                        "${GltuTimeTable.rangeLabel(course.startIndex, course.endIndex)} " +
                        GltuTimeTable.rangeTime(course.startIndex, course.endIndex),
                )
                InfoRow("周次", course.displayWeeks)
                course.credit?.let { InfoRow("学分", it.toString()) }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("编辑") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("删除") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            "$label：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
