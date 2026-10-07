# 应用混淆规则。
# 骨架阶段 release 未开启 minify（isMinifyEnabled=false）。
# 后续开启 R8/ProGuard 时按需在此补充 keep 规则。
#
# Room / Compose / OkHttp / Jsoup 的官方 keep 规则已由各自 consumer rules 自动带上，
# 一般无需手动添加。若开启混淆后遇到问题，常见补充（按需取消注释）：
#
# -keep class com.gltu.schedule.database.entity.** { *; }
# -keep class com.gltu.schedule.model.** { *; }
# -dontwarn org.jsoup.**
