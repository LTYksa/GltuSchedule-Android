// GLTU 课表 App —— 课表设置侧边栏
package com.ltyksa.gltuschedule.ui.schedule

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ltyksa.gltuschedule.data.GltuTimeTable
import com.ltyksa.gltuschedule.data.MakeupRule
import com.ltyksa.gltuschedule.data.SchedulePreferences
import com.ltyksa.gltuschedule.data.weekdayLabel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsDrawer(
    semesterLabel: String,
    maxPeriods: Int,
    weekStart: Int,
    showAllWeeks: Boolean,
    themeMode: Int,
    startDate: LocalDate,
    /** 调休规则（按日期升序）。 */
    makeupRules: List<MakeupRule>,
    /** 当前是第几周。 */
    currentWeek: Int,
    hasBackground: Boolean,
    onClose: () -> Unit,
    onMaxPeriodsChange: (Int) -> Unit,
    onWeekStartChange: (Int) -> Unit,
    onShowAllWeeksChange: (Boolean) -> Unit,
    onThemeModeChange: (Int) -> Unit,
    onSetStartDate: (LocalDate) -> Unit,
    onSetSemesterName: (String) -> Unit,
    onSetCurrentWeek: (Int) -> Unit,
    /** 手动指定某个调休日补周几的课。 */
    onSetMakeupOverride: (LocalDate, DayOfWeek) -> Unit,
    /** 清除手动指定，回到自动推导。 */
    onClearMakeupOverride: (LocalDate) -> Unit,
    onSaveBackgroundBitmap: (android.graphics.Bitmap) -> Boolean,
    onClearBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<String?>(null) }

    ModalDrawerSheet(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // 标题区
            Column(modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "关闭侧边栏",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.size(6.dp))
                    Text("课表设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = semesterLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp, top = 2.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 显示的是用户自己选的开学日期；第一周周一周后台推导
            DrawerItem(
                "设置开学日期",
                "${startDate.year}/${startDate.monthValue}/${startDate.dayOfMonth}",
            ) { dialog = "term_start" }
            DrawerItem("设置学期", semesterLabel) { dialog = "semester" }
            // 用户往往不知道"开学日期"，但一定知道"这周是第几周"
            DrawerItem("设置当前周次", "第 $currentWeek 周") { dialog = "current_week" }

            SectionHeader("通用设置")

            DrawerItem("课表显示节数", "前 $maxPeriods 节") { dialog = "periods" }
            DrawerItem("每周起始日", if (weekStart == 7) "周日" else "周一") { dialog = "week_start" }
            SwitchItem(
                title = "显示非本周课程",
                checked = showAllWeeks,
                onCheckedChange = onShowAllWeeksChange,
            )
            DrawerItem("调休补课",
                if (makeupRules.isEmpty()) "未识别到调休日" else "${makeupRules.size} 天",
            ) { dialog = "makeup" }
            DrawerItem("个性换肤", themeLabel(themeMode)) { dialog = "theme" }
            DrawerItem("添加桌面小部件", null) { dialog = "widget" }
        }
    }

    // ---------------- 弹窗 ----------------

    when (dialog) {
        "semester" -> {
            var input by remember { mutableStateOf(semesterLabel) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("设置学期") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "导入课表时会自动识别学期；识别不到就在这里手动填。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            label = { Text("如：2026-2027 第1学期") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        onSetSemesterName(input)
                        dialog = null
                    }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } },
            )
        }

        "term_start" -> {
            // 显示用户选的原始日期；后台会自动推算出该日期所在周的周一
            val initial = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val state = rememberDatePickerState(initialSelectedDateMillis = initial)
            DatePickerDialog(
                onDismissRequest = { dialog = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            onSetStartDate(picked)
                        }
                        dialog = null
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } },
            ) {
                DatePicker(state = state)
            }
        }

        "periods" -> ChoiceDialog(
            title = "课表显示节数",
            options = listOf(6, 8, 10, 12, GltuTimeTable.slots.size).distinct().map { it to "前 $it 节" },
            selected = maxPeriods,
            onPick = { onMaxPeriodsChange(it); dialog = null },
            onDismiss = { dialog = null },
        )

        "week_start" -> ChoiceDialog(
            title = "每周起始日",
            options = listOf(1 to "周一", 7 to "周日"),
            selected = weekStart,
            onPick = { onWeekStartChange(it); dialog = null },
            onDismiss = { dialog = null },
        )

        // 节假日屏蔽已移到「设置」标签页（设置 → 节假日屏蔽课程），侧边栏不再重复

        "current_week" -> CurrentWeekDialog(
            current = currentWeek,
            onPick = { onSetCurrentWeek(it); dialog = null },
            onDismiss = { dialog = null },
        )

        "makeup" -> MakeupDialog(
            rules = makeupRules,
            onPick = { date, wd -> onSetMakeupOverride(date, wd) },
            onReset = { date -> onClearMakeupOverride(date) },
            onDismiss = { dialog = null },
        )

        "theme" -> ThemeDialog(
            themeMode = themeMode,
            hasBackground = hasBackground,
            onThemeModeChange = onThemeModeChange,
            onSaveBackgroundBitmap = onSaveBackgroundBitmap,
            onClearBackground = onClearBackground,
            onDismiss = { dialog = null },
        )
        "widget" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("添加桌面小部件") },
            text = {
                Text(
                    "1. 长按桌面空白处\n" +
                        "2. 选择「小部件 / 添加工具」\n" +
                        "3. 搜索「GLTU 课表」\n" +
                        "4. 选规格（2×2 / 4×2 / 4×4）拖到桌面\n\n" +
                        "小米手机：若小部件不刷新，请到\n设置 → 应用管理 → GLTU课表 → 省电策略 → 无限制",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("知道了") } },
        )
    }
}

/**
 * 「设置当前周次」。
 *
 * 用户通常不知道"开学日期"，但一定知道"这周是第几周"。
 * 交互与「教务系统导入」成功后的那次输入保持一致：**直接填周数**，不弹列表让用户翻。
 * 选好之后由 ViewModel 用「本周周一 − (N−1) 周」反推开学日期。
 */
@Composable
private fun CurrentWeekDialog(
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置当前周次") },
        text = {
            Column {
                Text(
                    "用来校准「这周是第几周」。填完之后，课表周次和开学日期都会跟着调整。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { v ->
                        // 只留数字，最多两位（与导入页的输入规则一致）
                        val digits = v.filter { it.isDigit() }.take(2)
                        input = digits
                    },
                    label = { Text("当前第几周") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(input.toIntOrNull()?.coerceIn(1, 30) ?: current)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 「调休补课」：列出放假期间要补课的调休上班日，让用户自己指定补周几的课。
 *
 * 为什么必须能手动改：放假通知只说「X月X日（星期六）上班」，
 * 不会说补周几 —— 那是学校另行通知的，App 只能按惯例猜。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MakeupDialog(
    rules: List<MakeupRule>,
    onPick: (LocalDate, DayOfWeek) -> Unit,
    onReset: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调休补课") },
        text = {
            Column(
                modifier = Modifier
                    .height(380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "放假的调休上班日要补某一天的课（如「10月11日（周六）补10月8日（周三）的课」）。" +
                        "通知里不写补周几，App 按惯例猜一个；猜得不对，点下面的星期自己选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (rules.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "当前没有识别到调休日。放假安排公布后会自动出现；" +
                            "也可以先在「节假日屏蔽」里同步一次数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }

                rules.forEach { rule ->
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${rule.date.monthValue}月${rule.date.dayOfMonth}日" +
                                "（${weekdayLabel(rule.date.dayOfWeek)}）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (rule.manual) "手动" else "自动",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (rule.manual) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "现在补 ${rule.sourceDate.monthValue}/${rule.sourceDate.dayOfMonth}" +
                            "（${weekdayLabel(rule.sourceWeekday)}）的课",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DayOfWeek.entries.forEach { wd ->
                            FilterChip(
                                selected = rule.sourceWeekday == wd,
                                onClick = { onPick(rule.date, wd) },
                                label = { Text(weekdayLabel(wd)) },
                            )
                        }
                        if (rule.manual) {
                            TextButton(onClick = { onReset(rule.date) }) { Text("恢复自动") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

/** 个性换肤：主题模式 + 课表背景图（选图后会进入裁切界面）。 */
@Composable
private fun ThemeDialog(
    themeMode: Int,
    hasBackground: Boolean,
    onThemeModeChange: (Int) -> Unit,
    onSaveBackgroundBitmap: (android.graphics.Bitmap) -> Boolean,
    onClearBackground: () -> Unit,
    onDismiss: () -> Unit,
) {
    var message by remember { mutableStateOf<String?>(null) }
    var pendingCrop by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) pendingCrop = uri else message = "未选择图片"
    }

    // 选完图先进裁切界面（整屏所见即所得）
    pendingCrop?.let { uri ->
        BackgroundCropScreen(
            uri = uri,
            onCancel = { pendingCrop = null },
            onConfirm = { bitmap ->
                val ok = onSaveBackgroundBitmap(bitmap)
                message = if (ok) "壁纸已设置，已铺满背景" else "壁纸保存失败，请换一张试试"
                pendingCrop = null
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("个性换肤") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("主题", style = MaterialTheme.typography.labelLarge)
                listOf(
                    SchedulePreferences.THEME_SYSTEM to "跟随系统",
                    SchedulePreferences.THEME_LIGHT to "浅色",
                    SchedulePreferences.THEME_DARK to "深色",
                ).forEach { (v, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeModeChange(v) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = themeMode == v, onClick = { onThemeModeChange(v) })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("课表壁纸", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (hasBackground) "当前：已设置自定义壁纸" else "当前：默认背景",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }) { Text(if (hasBackground) "更换壁纸" else "选择壁纸") }
                    if (hasBackground) {
                        TextButton(onClick = {
                            onClearBackground()
                            message = "已移除壁纸"
                        }) { Text("移除壁纸") }
                    }
                }
                Text(
                    "选好后进入裁切：双指缩放、拖动，自动铺满屏幕",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

private fun themeLabel(mode: Int): String = when (mode) {
    SchedulePreferences.THEME_LIGHT -> "浅色"
    SchedulePreferences.THEME_DARK -> "深色"
    else -> "跟随系统"
}

// ---------------- 列表项 ----------------

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun DrawerItem(title: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SwitchItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onPick(value) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
