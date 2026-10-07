// GLTU 课表 App —— 课表设置侧边栏
package com.ltyksa.gltuschedule.ui.schedule

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ltyksa.gltuschedule.data.GltuTimeTable
import com.ltyksa.gltuschedule.data.HolidayManager
import com.ltyksa.gltuschedule.data.SchedulePreferences
import com.ltyksa.gltuschedule.ui.common.SettingSwitchRow
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
    holidaySettings: HolidayManager.BlockSettings,
    hasBackground: Boolean,
    onClose: () -> Unit,
    onMaxPeriodsChange: (Int) -> Unit,
    onWeekStartChange: (Int) -> Unit,
    onShowAllWeeksChange: (Boolean) -> Unit,
    onThemeModeChange: (Int) -> Unit,
    onSetStartDate: (LocalDate) -> Unit,
    onSetSemesterName: (String) -> Unit,
    onHolidaySettingsChange: (HolidayManager.BlockSettings) -> Unit,
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

            SectionHeader("通用设置")

            DrawerItem("课表显示节数", "前 $maxPeriods 节") { dialog = "periods" }
            DrawerItem("每周起始日", if (weekStart == 7) "周日" else "周一") { dialog = "week_start" }
            SwitchItem(
                title = "显示非本周课程",
                checked = showAllWeeks,
                onCheckedChange = onShowAllWeeksChange,
            )
            DrawerItem("节假日屏蔽", holidaySettings.summary) { dialog = "holiday" }
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

        "holiday" -> {
            var local by remember { mutableStateOf(holidaySettings) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("节假日屏蔽") },
                text = {
                    Column {
                        SettingSwitchRow(
                            title = "屏蔽法定节假日",
                            subtitle = "元旦 / 春节 / 清明节 / 劳动节 / 端午节 / 中秋节 / 国庆节",
                            checked = local.statutory,
                            onCheckedChange = { local = local.copy(statutory = it) },
                        )
                        SettingSwitchRow(
                            title = "屏蔽学校假期",
                            subtitle = "校运会 / 寒假 / 暑假（来自 GLTU 校历）",
                            checked = local.school,
                            onCheckedChange = { local = local.copy(school = it) },
                        )
                        SettingSwitchRow(
                            title = "屏蔽日常周末",
                            subtitle = "周六、周日",
                            checked = local.weekend,
                            onCheckedChange = { local = local.copy(weekend = it) },
                        )
                        Text(
                            "说明：补班日（调休上班的周末）不受以上开关影响，一律照常上课；" +
                                "三个开关互相独立，可任意组合。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        onHolidaySettingsChange(local)
                        dialog = null
                    }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } },
            )
        }

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
