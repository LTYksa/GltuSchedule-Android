// GLTU 课表 App —— 根构建配置
// 统一在此声明插件仓库与依赖仓库，子模块不再单独声明仓库。

pluginManagement {
    repositories {
        // 国内镜像优先，加速依赖下载；官方源作为兜底
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 禁止子模块私自声明仓库，避免仓库来源不一致
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
    }
}

rootProject.name = "GltuSchedule"
include(":app")
