// GLTU 课表 App —— 根界面导航
// 一级页面（课表/设置）显示底部导航；二级页面（导入/分享/编辑）统一显示
// 置顶返回栏（TopAppBar + 返回箭头），并隐藏底部导航，避免层级混淆。
// 「关于」已并入设置页，不再单独占底部标签。
package com.gltu.schedule.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gltu.schedule.ui.edit.EditCourseScreen
import com.gltu.schedule.ui.import.WebImportScreen
import com.gltu.schedule.ui.schedule.ScheduleScreen
import com.gltu.schedule.ui.settings.SettingsScreen
import com.gltu.schedule.ui.share.CourseShareScreen

/** 一级导航目的地（带底部导航）。 */
enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    SCHEDULE("schedule", "课表", Icons.Filled.DateRange),
    SETTINGS("settings", "设置", Icons.Filled.Settings),
}

/** 二级路由（带置顶返回栏）。 */
object Routes {
    const val WEB_IMPORT = "web_import"
    const val SHARE = "share"
    const val EDIT_COURSE = "edit_course"
    const val EDIT_COURSE_ARG = "edit_course/{courseId}"

    fun editCourse(id: Long) = "edit_course/$id"

    /** 二级页面标题。 */
    fun titleOf(route: String?): String = when {
        route == null -> ""
        route.startsWith(EDIT_COURSE) -> if (route == EDIT_COURSE) "添加课程" else "编辑课程"
        route == WEB_IMPORT -> "教务系统导入"
        route == SHARE -> "课表分享"
        else -> ""
    }

    /** 是否是二级页面（需要返回栏）。 */
    fun isSecondary(route: String?): Boolean =
        route != null && AppDestination.entries.none { it.route == route }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val secondary = Routes.isSecondary(currentRoute)

    Scaffold(
        topBar = {
            if (secondary) {
                TopAppBar(
                    title = {
                        Text(
                            Routes.titleOf(currentRoute),
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        bottomBar = {
            if (!secondary) {
                NavigationBar {
                    val currentDestination = backStackEntry?.destination
                    AppDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy
                                ?.any { it.route == destination.route } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(destination.icon, contentDescription = destination.label)
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.SCHEDULE.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            // ---------- 一级页面 ----------
            composable(AppDestination.SCHEDULE.route) {
                ScheduleScreen(
                    onAddCourse = { navController.navigate(Routes.EDIT_COURSE) },
                    onEditCourse = { id -> navController.navigate(Routes.editCourse(id)) },
                )
            }
            composable(AppDestination.SETTINGS.route) {
                SettingsScreen(
                    onNavigateToWebImport = { navController.navigate(Routes.WEB_IMPORT) },
                    onNavigateToShare = { navController.navigate(Routes.SHARE) },
                    onAddCourse = { navController.navigate(Routes.EDIT_COURSE) },
                )
            }

            // ---------- 二级页面（置顶返回栏） ----------
            composable(Routes.WEB_IMPORT) {
                WebImportScreen(onDone = { navController.navigateUp() })
            }
            composable(Routes.SHARE) {
                CourseShareScreen(onDone = { navController.navigateUp() })
            }
            composable(Routes.EDIT_COURSE) {
                EditCourseScreen(courseId = null, onDone = { navController.navigateUp() })
            }
            composable(
                route = Routes.EDIT_COURSE_ARG,
                arguments = listOf(navArgument("courseId") { type = NavType.LongType }),
            ) { entry ->
                EditCourseScreen(
                    courseId = entry.arguments?.getLong("courseId"),
                    onDone = { navController.navigateUp() },
                )
            }
        }
    }
}
