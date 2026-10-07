// GLTU 课表 App —— 应用信息（版本 / 作者 / 开源说明）
// 改版本号、作者、仓库地址只需要动这里。
package com.gltu.schedule

object AppInfo {

    const val NAME = "GLTU 课表"

    /** 版本号（与 app/build.gradle.kts 的 versionName 保持一致）。 */
    const val VERSION = "0.10beta"

    const val AUTHOR = "木下葵"

    const val OPEN_SOURCE = "本软件已在 GitHub 开源"

    /**
     * 开源仓库地址。
     * 填上后「设置 → 关于」会自动出现可点击的仓库入口；留空则只显示开源说明文字。
     */
    const val GITHUB_URL = ""

    const val TECH = "全程本地运行 · 无自建服务器\nKotlin + Jetpack Compose + Room + MVVM"
}
