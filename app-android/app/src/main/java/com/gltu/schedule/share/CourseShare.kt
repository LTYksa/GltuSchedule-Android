// GLTU 课表 App —— 课表分享码（超级课程表式"课程格子"互传）
// 全程本地：导出为一段 Base64 分享码，同学之间可复制粘贴导入；不经过任何服务器。
package com.gltu.schedule.share

import com.gltu.schedule.data.CoursePalette
import com.gltu.schedule.model.Course
import java.time.DayOfWeek
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

object CourseShare {

    private const val MAGIC = "GLTU-SCHEDULE"

    /** 分享码格式版本。v1 = 只有起止周；v2 = 增加显式周次集合 / 原始周次文本 / 学分。 */
    private const val VERSION = 2

    /**
     * 导出分享码：JSON → Base64（无换行），可直接微信/QQ 发给同学。
     * JSON 字段做了短名压缩，避免分享码过长。
     *
     * 注意：**必须带上 `w`（显式周次集合）**。
     * 只带 startWeek/endWeek 的话，像 `4-5周,8周,11-13周(单)` 这种不规则周次
     * 到了对方那边会变成连续的 4-16 周，凭空多出好几周的课。
     */
    fun export(courses: List<Course>): String {
        val arr = JSONArray()
        for (c in courses) {
            val o = JSONObject()
            o.put("n", c.name)
            o.put("t", c.teacher)
            o.put("l", c.location)
            o.put("d", c.dayOfWeek.value)
            o.put("s", c.startIndex)
            o.put("e", c.endIndex)
            o.put("sw", c.startWeek)
            o.put("ew", c.endWeek)
            o.put("o", c.oddEven)
            if (c.weeks.isNotEmpty()) {
                o.put("w", c.weeks.distinct().sorted().joinToString(","))
            }
            if (c.weeksText.isNotBlank()) o.put("wt", c.weeksText)
            c.credit?.let { o.put("cr", it) }
            arr.put(o)
        }
        val root = JSONObject()
        root.put("app", MAGIC)
        root.put("v", VERSION)
        root.put("c", arr)
        val json = root.toString()
        return Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
    }

    /**
     * 导入分享码。失败返回 null，并给出 [lastError] 便于提示用户。
     * 支持容错：用户粘贴时可能带空格/换行；也兼容 v1 的老分享码。
     */
    var lastError: String? = null
        private set

    fun import(code: String): List<Course>? {
        lastError = null
        val cleaned = code.trim().replace(Regex("\\s"), "")
        if (cleaned.isEmpty()) {
            lastError = "分享码为空"
            return null
        }
        val jsonText = try {
            String(Base64.getDecoder().decode(cleaned), Charsets.UTF_8)
        } catch (e: Exception) {
            lastError = "分享码格式不正确（不是有效的分享码）"
            return null
        }
        return try {
            val root = JSONObject(jsonText)
            if (root.optString("app") != MAGIC) {
                lastError = "这不是 GLTU 课表的分享码"
                return null
            }
            val arr = root.optJSONArray("c") ?: JSONArray()
            val result = ArrayList<Course>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("n").trim()
                if (name.isEmpty()) continue
                val day = o.optInt("d", 1).coerceIn(1, 7)
                val start = o.optInt("s", 1)
                val end = o.optInt("e", start)

                // 显式周次集合（v2 才有；老分享码没有这个字段 → 空集合，退化成起止周）
                val weeks = o.optString("w")
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..30 }
                    .distinct()
                    .sorted()

                result.add(
                    Course(
                        id = 0,
                        name = name,
                        teacher = o.optString("t"),
                        location = o.optString("l"),
                        dayOfWeek = DayOfWeek.of(day),
                        startIndex = minOf(start, end),
                        endIndex = maxOf(start, end),
                        startWeek = o.optInt("sw", 1).coerceAtLeast(1),
                        endWeek = o.optInt("ew", 16).coerceAtLeast(1),
                        oddEven = o.optInt("o", 0),
                        category = CoursePalette.inferCategory(name, o.optString("l")),
                        credit = if (o.has("cr")) o.optDouble("cr").takeIf { !it.isNaN() } else null,
                        weeksText = o.optString("wt"),
                        weeks = weeks,
                    )
                )
            }
            if (result.isEmpty()) {
                lastError = "分享码里没有课程"
                return null
            }
            result
        } catch (e: Exception) {
            lastError = "分享码解析失败：${e.message ?: e.javaClass.simpleName}"
            null
        }
    }
}
