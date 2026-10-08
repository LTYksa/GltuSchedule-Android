// GLTU 课表 App —— 应用信息（版本 / 作者 / 开源说明）
// 改作者、仓库地址只需要动这里。
package com.ltyksa.gltuschedule

import android.content.Context

object AppInfo {

    const val NAME = "GLTU 课表"

    /**
     * 兜底版本号 —— 只有读包信息失败时才会用到。
     *
     * ⚠️ 不要把这个当准。原来设置页直接显示它，结果
     * `app/build.gradle.kts` 的 versionName 升到 0.14.0 了，
     * 这里还写着 0.11beta，界面上显示的就是旧版本号。
     * 现在统一走 [version]。
     */
    private const val FALLBACK_VERSION = "0.14.0"

    /**
     * 版本号 —— 运行时从 PackageManager 读，和
     * `app/build.gradle.kts` 的 `versionName` 永远一致。
     */
    fun version(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { FALLBACK_VERSION }

    const val AUTHOR = "木下葵"

    const val OPEN_SOURCE = "本软件已在 GitHub 开源"

    /**
     * 开源仓库地址。
     * 填上后「设置 → 关于」会自动出现可点击的仓库入口；留空则只显示开源说明文字。
     */
    const val GITHUB_URL = ""

    const val TECH = "全程本地运行 · 无自建服务器\nKotlin + Jetpack Compose + Room + MVVM"
}
