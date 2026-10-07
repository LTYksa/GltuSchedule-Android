// GLTU 课表 App —— 手动添加/编辑课程界面（不依赖教务系统，超级课程表式录入）
package com.ltyksa.gltuschedule.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ltyksa.gltuschedule.GltuScheduleApp
import com.ltyksa.gltuschedule.data.CourseColor
import com.ltyksa.gltuschedule.ui.schedule.ScheduleViewModel

@Composable
fun EditCourseScreen(
    courseId: Long?,
    onDone: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as GltuScheduleApp
    val viewModel: EditCourseViewModel = viewModel { EditCourseViewModel(app) }
    val form by viewModel.form.collectAsStateWithLifecycle()

    LaunchedEffect(courseId) { viewModel.load(courseId) }
    LaunchedEffect(form.saved) { if (form.saved) onDone() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (form.id > 0) "编辑课程" else "添加课程",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // 颜色预览
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CourseColor.background(form.name.ifBlank { "课程" })),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = form.name.ifBlank { "课程预览（颜色随课程名自动分配）" },
                color = CourseColor.of(form.name.ifBlank { "课程" }),
                fontWeight = FontWeight.Bold,
            )
        }

        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> viewModel.update { it.copy(name = v, error = null) } },
            label = { Text("课程名称 *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.teacher,
            onValueChange = { v -> viewModel.update { it.copy(teacher = v) } },
            label = { Text("授课教师") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.location,
            onValueChange = { v -> viewModel.update { it.copy(location = v) } },
            label = { Text("上课地点") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // 星期
        SectionLabel("星期")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ScheduleViewModel.WEEK_DAYS.forEach { (label, day) ->
                FilterChip(
                    selected = form.dayOfWeek == day,
                    onClick = { viewModel.update { it.copy(dayOfWeek = day) } },
                    label = { Text(label) },
                )
            }
        }

        // 开始节次
        SectionLabel("开始节次")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            viewModel.timeSlots.forEach { slot ->
                FilterChip(
                    selected = form.startIndex == slot.index,
                    onClick = {
                        viewModel.update {
                            it.copy(
                                startIndex = slot.index,
                                endIndex = if (it.endIndex < slot.index) slot.index else it.endIndex,
                            )
                        }
                    },
                    label = { Text(slot.name) },
                )
            }
        }

        // 结束节次
        SectionLabel("结束节次")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            viewModel.timeSlots.forEach { slot ->
                FilterChip(
                    selected = form.endIndex == slot.index,
                    onClick = {
                        viewModel.update {
                            it.copy(
                                endIndex = slot.index,
                                startIndex = if (it.startIndex > slot.index) slot.index else it.startIndex,
                            )
                        }
                    },
                    label = { Text(slot.name) },
                )
            }
        }

        // 周次范围
        SectionLabel("周次范围")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.startWeek.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { n -> viewModel.update { it.copy(startWeek = n) } }
                },
                label = { Text("起始周") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = form.endWeek.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { n -> viewModel.update { it.copy(endWeek = n) } }
                },
                label = { Text("结束周") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        // 单双周
        SectionLabel("单双周")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "每周", 1 to "单周", 2 to "双周").forEach { (value, label) ->
                FilterChip(
                    selected = form.oddEven == value,
                    onClick = { viewModel.update { it.copy(oddEven = value) } },
                    label = { Text(label) },
                )
            }
        }

        form.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = { viewModel.save() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (form.id > 0) "保存修改" else "添加课程")
        }

        if (form.id > 0) {
            OutlinedButton(
                onClick = { viewModel.delete() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("删除这门课", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
