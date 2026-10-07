// GLTU 课表 App —— 课表分享（导出/导入分享码）
package com.gltu.schedule.ui.share

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gltu.schedule.GltuScheduleApp
import com.gltu.schedule.data.SemesterStore
import com.gltu.schedule.model.Course
import com.gltu.schedule.notification.ClassReminderScheduler
import com.gltu.schedule.repository.ScheduleRepository
import com.gltu.schedule.share.CourseShare
import com.gltu.schedule.widget.ScheduleWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ShareUiState(
    val shareCode: String = "",
    val courseCount: Int = 0,
    val message: String? = null,
    val success: Boolean = false,
)

class ShareViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: ScheduleRepository =
        (app as GltuScheduleApp).container.scheduleRepository

    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    fun refreshExport() {
        viewModelScope.launch {
            val courses = withContext(Dispatchers.IO) { repository.getWeekCoursesSync() }
            _state.value = if (courses.isEmpty()) {
                _state.value.copy(shareCode = "", courseCount = 0, message = "当前没有课程可导出")
            } else {
                _state.value.copy(
                    shareCode = CourseShare.export(courses),
                    courseCount = courses.size,
                    message = null,
                )
            }
        }
    }

    fun importCode(code: String, replace: Boolean) {
        viewModelScope.launch {
            val courses: List<Course>? = withContext(Dispatchers.IO) { CourseShare.import(code) }
            if (courses == null) {
                _state.value = _state.value.copy(
                    message = CourseShare.lastError ?: "导入失败", success = false,
                )
                return@launch
            }
            withContext(Dispatchers.IO) {
                if (replace) repository.clearCourses()
                repository.upsertCourses(courses)
                // 课表已变：必须重排提醒 + 刷新小部件。
                // 否则旧课（已删除）的闹钟还在会继续响，新课则完全没有提醒。
                runCatching {
                    val ctx = getApplication<Application>()
                    val all = repository.getWeekCoursesSync()
                    ClassReminderScheduler.scheduleNext(
                        ctx, all, SemesterStore.firstWeekMonday(ctx),
                    )
                    ScheduleWidgetProvider.refreshAll(ctx)
                }
            }
            _state.value = _state.value.copy(
                message = "导入成功：${courses.size} 门课" + if (replace) "（已替换原课表）" else "（已追加）",
                success = true,
            )
            refreshExport()
        }
    }
}

@Composable
fun CourseShareScreen(onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as GltuScheduleApp
    val viewModel: ShareViewModel = viewModel { ShareViewModel(app) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var importText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refreshExport() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("课表分享", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "把课表导出成一段分享码发给同学，对方粘贴即可导入。全程本地，不经过任何服务器。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 导出
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("导出我的课表（${state.courseCount} 门课）", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.shareCode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("分享码") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            copyToClipboard(context, state.shareCode)
                        },
                        enabled = state.shareCode.isNotEmpty(),
                    ) { Text("复制分享码") }
                    OutlinedButton(onClick = { viewModel.refreshExport() }) { Text("刷新") }
                }
            }
        }

        // 导入
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("导入同学的课表", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("粘贴分享码") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "提示：导入会替换当前课表；如需保留请先导出备份",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.importCode(importText, replace = true) }) {
                        Text("导入并替换")
                    }
                    OutlinedButton(onClick = { viewModel.importCode(importText, replace = false) }) {
                        Text("追加导入")
                    }
                    OutlinedButton(onClick = {
                        importText = getFromClipboard(context) ?: ""
                    }) { Text("从剪贴板粘贴") }
                }
            }
        }

        state.message?.let {
            Text(
                text = it,
                color = if (state.success) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("GLTU课表分享码", text))
}

private fun getFromClipboard(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.getItemAt(0)?.text?.toString()
}
