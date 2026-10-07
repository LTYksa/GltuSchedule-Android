// GLTU 课表 App —— 主 Activity（类名约定为 com.gltu.schedule.MainActivity，小部件点击会打开它）
package com.gltu.schedule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import com.gltu.schedule.data.SchedulePreferences
import com.gltu.schedule.ui.navigation.ScheduleApp
import com.gltu.schedule.ui.theme.AppThemeState
import com.gltu.schedule.ui.theme.GltuScheduleTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果无需处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 载入"个性换肤"设置
        AppThemeState.mode = SchedulePreferences.themeMode(this)

        requestNotificationPermissionIfNeeded()

        setContent {
            val dark = AppThemeState.isDark(isSystemInDarkTheme())
            GltuScheduleTheme(darkTheme = dark) {
                ScheduleApp()
            }
        }
    }

    /** Android 13+ 需要运行时通知权限；首次启动申请一次。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
