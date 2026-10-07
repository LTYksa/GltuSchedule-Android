// GLTU 课表 App —— 设置页（获取课表 + 节假日屏蔽与同步 + 上课提醒时间 + 同步日历）
package com.ltyksa.gltuschedule.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ltyksa.gltuschedule.AppInfo
import com.ltyksa.gltuschedule.GltuScheduleApp
import com.ltyksa.gltuschedule.data.HolidayManager
import com.ltyksa.gltuschedule.data.HolidayType
import com.ltyksa.gltuschedule.data.SemesterStore
import com.ltyksa.gltuschedule.notification.ReminderPreference
import com.ltyksa.gltuschedule.ui.common.NumberWheel
import com.ltyksa.gltuschedule.ui.common.SettingSwitchRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onNavigateToWebImport: () -> Unit = {},
    onNavigateToShare: () -> Unit = {},
    onAddCourse: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as GltuScheduleApp
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(app) }

    val blockSettings by viewModel.blockSettings.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val lastSyncAt by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    val holidayCount by viewModel.holidayCount.collectAsStateWithLifecycle()
    val reminderEnabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val reminderMinutes by viewModel.reminderMinutes.collectAsStateWithLifecycle()
    val syncingCalendar by viewModel.syncingCalendar.collectAsStateWithLifecycle()
    val calendarMessage by viewModel.calendarMessage.collectAsStateWithLifecycle()
    val holidayList by viewModel.holidayList.collectAsStateWithLifecycle()
    val syncSource by viewModel.syncSource.collectAsStateWithLifecycle()
    val exactAlarmAllowed by viewModel.exactAlarmAllowed.collectAsStateWithLifecycle()

    var showMinutePicker by remember { mutableStateOf(false) }
    var showHolidayList by remember { mutableStateOf(false) }

    // 从系统设置页返回时重新检查精确闹钟权限
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshExactAlarmState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 日历权限：授予后自动继续同步
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            viewModel.syncToCalendar()
        } else {
            viewModel.setCalendarMessage("未授予日历权限，无法同步")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // ---------- 获取课表 ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("获取课表", style = MaterialTheme.typography.titleMedium)
                Text(
                    "三种方式任选，也可以混用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("① 手动添加课程", style = MaterialTheme.typography.titleMedium)
                Text(
                    "自己录入课程名/地点/时间，最稳妥，不依赖学校系统",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(onClick = onAddCourse) { Text("去添加") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("② 课表分享码", style = MaterialTheme.typography.titleMedium)
                Text(
                    "导入同学分享的课表，或把自己的课表导出给对方",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(onClick = onNavigateToShare) { Text("导出 / 导入分享码") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("③ 教务系统导入", style = MaterialTheme.typography.titleMedium)
                Text(
                    "App 里打开教务系统网页，你自己登录并进入课表页，再点「确定导入」自动解析",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(onClick = onNavigateToWebImport) { Text("进入教务系统导入") }
            }
        }

        // ---------- 当前学期 ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("当前学期", style = MaterialTheme.typography.titleMedium)
                val semester = SemesterStore.semesterName(context).ifBlank { "未设置" }
                val week = SemesterStore.currentWeek(context)
                Text(
                    "$semester · 第 $week 周",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // ---------- 节假日屏蔽（实装 + 同步） ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("节假日屏蔽课程", style = MaterialTheme.typography.titleMedium)
                Text(
                    "只识别【中国大陆】法定节假日（元旦 / 春节 / 清明节 / 劳动节 / 端午节 / 中秋节 / 国庆节）" +
                        "以及国务院办公厅公布的调休补班日，不含任何其他国家或地区的节日。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "已收录 $holidayCount 条记录" +
                        if (syncSource.isNotBlank()) "，来源：$syncSource" else "，来源：内置数据（未同步）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "自动同步：打开 App 时检查一次，另外每天凌晨 3:30 后台自动更新一次；" +
                        "国务院办公厅通常在前一年 11 月发布次年放假安排，那段时间若还没拿到次年数据会每 3 天重试一次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 三个互相独立的屏蔽开关
                Column {
                    SettingSwitchRow(
                        title = "屏蔽法定节假日",
                        subtitle = "元旦 / 春节 / 清明节 / 劳动节 / 端午节 / 中秋节 / 国庆节",
                        checked = blockSettings.statutory,
                        onCheckedChange = {
                            viewModel.setBlockSettings(blockSettings.copy(statutory = it))
                        },
                    )
                    SettingSwitchRow(
                        title = "屏蔽学校假期",
                        subtitle = "校运会 / 寒假 / 暑假（来自 GLTU 校历）",
                        checked = blockSettings.school,
                        onCheckedChange = {
                            viewModel.setBlockSettings(blockSettings.copy(school = it))
                        },
                    )
                    SettingSwitchRow(
                        title = "屏蔽日常周末",
                        subtitle = "周六、周日",
                        checked = blockSettings.weekend,
                        onCheckedChange = {
                            viewModel.setBlockSettings(blockSettings.copy(weekend = it))
                        },
                    )
                    Text(
                        "补班日（调休上班的周末）不受以上开关影响，一律照常上课；" +
                            "三个开关互相独立，可任意组合。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.syncHolidays() }, enabled = !syncing) {
                        if (syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("同步中…")
                        } else {
                            Text("同步节假日")
                        }
                    }
                    Text(
                        if (lastSyncAt > 0) {
                            "上次同步：" + SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
                                .format(Date(lastSyncAt))
                        } else {
                            "尚未同步（使用内置数据）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                syncMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // 已收录假期清单（用户可自行核对是不是中国大陆的节日）
                TextButton(onClick = { showHolidayList = !showHolidayList }) {
                    Text(if (showHolidayList) "收起假期清单" else "查看已收录的假期清单（${holidayList.size} 条）")
                }
                if (showHolidayList) {
                    val groups = listOf(
                        HolidayType.STATUTORY to "法定节假日（中国大陆）",
                        HolidayType.SCHOOL to "学校假期（GLTU 校历）",
                        HolidayType.MAKEUP to "调休补班日",
                        HolidayType.USER_DEFINED to "自定义假期",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        groups.forEach { (type, groupLabel) ->
                            val items = holidayList.filter { it.type == type }
                            if (items.isEmpty()) return@forEach

                            Text(
                                "$groupLabel · ${items.size} 条",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                            )
                            items.forEach { h ->
                                // 当前开关下这天会不会被屏蔽
                                val willBlock = when (type) {
                                    HolidayType.STATUTORY, HolidayType.USER_DEFINED -> blockSettings.statutory
                                    HolidayType.SCHOOL -> blockSettings.school
                                    HolidayType.MAKEUP -> false
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        h.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(96.dp),
                                    )
                                    Text(
                                        text = if (h.start == h.end) {
                                            "${h.start.monthValue}月${h.start.dayOfMonth}日"
                                        } else {
                                            "${h.start.monthValue}/${h.start.dayOfMonth} – " +
                                                "${h.end.monthValue}/${h.end.dayOfMonth}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = if (type == HolidayType.MAKEUP) "照常上课"
                                        else if (willBlock) "已屏蔽" else "未屏蔽",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            type == HolidayType.MAKEUP ->
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            willBlock -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.outline
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- 上课提醒 ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("上课提醒", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "课前 ${reminderMinutes.joinToString("、")} 分钟各提醒一次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { viewModel.setReminderEnabled(it) },
                    )
                }
                TextButton(
                    onClick = { showMinutePicker = true },
                    enabled = reminderEnabled,
                ) { Text("选择提醒时间（1–60 分钟）") }

                // Android 12+ 的精确闹钟权限若被拒，提醒会退化成不精确闹钟（可能延迟很久）
                if (reminderEnabled && !exactAlarmAllowed) {
                    Text(
                        "⚠️ 系统未授予「闹钟和提醒」权限，提醒可能延迟到上课后才到。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { openExactAlarmSettings(context) }) {
                        Text("去授权精确闹钟")
                    }
                }
            }
        }

        // ---------- 一键同步到日历 ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("同步到手机日历", style = MaterialTheme.typography.titleMedium)
                Text(
                    "把课表一次性写进系统日历，可在日历 App 里查看、也能随账号同步到其他设备。" +
                        "周次连续的课会写成「每周重复」，单双周等不规则周次会逐次写入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        if (viewModel.hasCalendarPermission()) {
                            viewModel.syncToCalendar()
                        } else {
                            calendarPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR,
                                ),
                            )
                        }
                    }, enabled = !syncingCalendar) {
                        if (syncingCalendar) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("同步中…")
                        } else {
                            Text("一键同步课表到日历")
                        }
                    }
                    Text(
                        "重复点击会先清理上次同步的条目，不会重复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                calendarMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ---------- 关于（原底部「关于」标签已并入这里） ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        AppInfo.NAME,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            "v${AppInfo.VERSION}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutRow("作者", AppInfo.AUTHOR)
                AboutRow("开源", "${AppInfo.OPEN_SOURCE}，欢迎 Star / Issue / PR")
                if (AppInfo.GITHUB_URL.isNotBlank()) {
                    TextButton(onClick = { openUrl(context, AppInfo.GITHUB_URL) }) {
                        Text("查看 GitHub 仓库")
                    }
                }
                Text(
                    AppInfo.TECH,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showMinutePicker) {
        ReminderMinutesDialog(
            selected = reminderMinutes,
            onConfirm = {
                viewModel.setReminderMinutes(it)
                showMinutePicker = false
            },
            onDismiss = { showMinutePicker = false },
        )
    }
}

/** 提醒时间选择：滚轮选分钟数（1–60），可添加多个。 */@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderMinutesDialog(
    selected: List<Int>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(selected.sortedDescending()) }
    var wheelValue by remember { mutableIntStateOf(picked.firstOrNull() ?: 10) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒时间") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "已选提醒（点一下可删除）",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (picked.isEmpty()) {
                    Text(
                        "还没有选任何时间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        picked.forEach { m ->
                            FilterChip(
                                selected = true,
                                onClick = { picked = picked - m },
                                label = { Text("$m 分钟 ✕") },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "滚动选择要添加的分钟数",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberWheel(
                    range = ReminderPreference.MIN_MINUTES..ReminderPreference.MAX_MINUTES,
                    selected = wheelValue,
                    onSelectedChange = { wheelValue = it },
                    suffix = " 分钟",
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (wheelValue !in picked) {
                            picked = (picked + wheelValue).sortedDescending()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("添加：课前 $wheelValue 分钟") }

                if (picked.size > 4) {
                    Text(
                        "提示：选太多会收到很多通知，建议不超过 3 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        picked.ifEmpty { ReminderPreference.DEFAULT_MINUTES }.toList(),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 「关于」里的一行信息。 */
@Composable
private fun AboutRow(label: String, value: String) {
    Row {
        Text(
            "$label：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** 打开系统的"闹钟和提醒"授权页（Android 12+）。 */
private fun openExactAlarmSettings(context: android.content.Context) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                android.net.Uri.parse("package:${context.packageName}"),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 打开外部链接（仓库地址等）。 */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
