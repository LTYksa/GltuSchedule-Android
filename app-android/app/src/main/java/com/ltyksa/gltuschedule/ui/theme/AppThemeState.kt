// GLTU 课表 App —— 全局主题模式状态（供"个性换肤"实时切换）
package com.ltyksa.gltuschedule.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.ltyksa.gltuschedule.data.SchedulePreferences

/**
 * 全局主题模式。
 * 侧边栏"个性换肤"修改后会更新此状态，进而触发根主题重组，实现即时换肤。
 */
object AppThemeState {
    var mode by mutableIntStateOf(SchedulePreferences.THEME_SYSTEM)

    /** 是否使用深色（结合系统设置）。 */
    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        SchedulePreferences.THEME_LIGHT -> false
        SchedulePreferences.THEME_DARK -> true
        else -> systemDark
    }
}
