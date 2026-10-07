# GltuSchedule · Android 工程

**为桂林旅游学院（GLTU）同学做的**课程表 Android 客户端。全程本地运行，无自建后端。

> ⚠️ **本工程是 Android 版，仅供 Android 用户使用**（Android 8.0 / API 26 及以上）。
> iOS、HarmonyOS 等其他系统**后续会适配**，目前仓库里只有这一份 Android 实现。

👉 **项目介绍、功能说明、构建步骤请见 [仓库根目录的 README](../README.md)。**

## 快速开始

```bash
# 用 Android Studio 打开「本目录」（不是仓库根目录），或命令行：
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
./gradlew testDebugUnitTest    # 62 个单元测试
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 环境要求

| 组件 | 版本 |
|---|---|
| JDK | **17** |
| Android SDK | **API 35**（build-tools 35.0.0） |
| Gradle | 8.7（用仓库自带的 wrapper 即可，无需另装） |
| AGP / Kotlin / KSP | 8.6.1 / 2.0.21 / 2.0.21-1.0.27 |

> SDK 路径由 `local.properties` 中的 `sdk.dir` 指定（该文件不入库，Android Studio 会自动生成）。

## 目录速览

```
app/src/main/java/com/gltu/schedule/
├── calendar/       同步到系统日历
├── data/           作息节次、节假日、教室楼号、配色、偏好存储、壁纸
├── database/       Room（实体 / DAO / 迁移 / 映射），schemas/ 已导出
├── import/         教务系统课表解析器（多策略）
├── notification/   上课提醒（AlarmManager）+ 每日后台维护
├── share/          课表分享码
├── ui/             Compose 界面
└── widget/         桌面小部件（三种规格）
```

其余文档见 [`docs/`](../docs/)：

- [功能与实现](../docs/功能与实现.md)
- [构建与打包](../docs/构建与打包.md)（含 Android Studio 汉化）
- [教务系统调研](../docs/教务系统调研.md)

## 开源协议

[MIT](../LICENSE)
