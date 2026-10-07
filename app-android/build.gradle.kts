// 根工程构建脚本：只声明插件版本（实际版本号集中在 gradle/libs.versions.toml），
// 不在此 apply（apply false 表示仅声明供子模块引用）。

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
