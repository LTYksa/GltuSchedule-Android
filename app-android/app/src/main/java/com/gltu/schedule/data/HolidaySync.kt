// GLTU 课表 App —— 中国大陆法定节假日同步
//
// 数据源（按优先级）：
//   1. holiday-cn 数据集 —— **中国大陆法定节假日**，直接由「国务院办公厅关于节假日安排的通知」生成，
//      每条数据都带政府文件链接（papers 字段）。放假 + 调休补班日齐全。
//   2. timor.tech 节假日接口 —— 备用源，同样是中国大陆法定节假日。
//   3. 内置数据 —— 两个源都拿不到时兜底（离线可用）。
//
// ⚠️ 本功能只处理**中国大陆**的法定节假日（元旦/春节/清明节/劳动节/端午节/中秋节/国庆节）
//    与国务院办公厅公布的调休补班日，不包含任何其他国家或地区的节日。
package com.gltu.schedule.data

import android.content.Context
import android.os.SystemClock
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object HolidaySync {

    /** 主源：中国大陆法定节假日（源自国务院办公厅通知）。 */
    const val SOURCE_CN = "中国大陆法定节假日（国务院办公厅通知）"
    /** 备用源。 */
    const val SOURCE_BACKUP = "timor.tech 中国大陆节假日接口"

    private const val URL_HOLIDAY_CN = "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/%d.json"
    private const val URL_TIMOR = "https://timor.tech/api/holiday/year/%d"

    data class Result(
        val success: Boolean,
        val message: String,
        val holidayCount: Int = 0,
        val makeupCount: Int = 0,
        val source: String = "",
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /** 有没有可用网络（避免无网时白等一串超时）。判断不了就返回 true，照常尝试。 */
    private fun hasNetwork(context: Context): Boolean = try {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        true
    }

    /** 同步当年与下一年的中国大陆法定节假日。 */
    suspend fun sync(context: Context): Result = withContext(Dispatchers.IO) {
        syncBlocking(context)
    }

    /**
     * 是否该联网同步（纯函数，便于单元测试）。
     *
     * 规则：
     *  1. 从没同步过 → 同步
     *  2. 距上次成功同步 ≥ 7 天 → 同步
     *  3. 已同步但**还没有下一年的数据**，且当前是 11/12 月 → 每 3 天重试一次
     *     （国务院办公厅通常在前一年 11 月发布次年放假安排，这段时间要盯紧）
     */
    internal fun shouldSyncPure(
        lastSyncAt: Long,
        nowMillis: Long,
        now: LocalDate,
        coversNextYear: Boolean,
    ): Boolean {
        if (lastSyncAt <= 0L) return true
        val days = TimeUnit.MILLISECONDS.toDays(nowMillis - lastSyncAt)
        if (days >= 7) return true
        if (!coversNextYear && now.monthValue >= 11) return days >= 3
        return false
    }

    /** 结合本地状态判断是否需要自动同步。 */
    fun shouldSync(context: Context, now: LocalDate = LocalDate.now()): Boolean {
        val last = HolidayManager.lastSyncAt(context)
        // 从未同步过时，也允许在没有网络的情况下反复尝试
        val coversNext = last > 0L && HolidayManager.syncedCoversYear(context, now.year + 1)
        return shouldSyncPure(last, System.currentTimeMillis(), now, coversNext)
    }

    /**
     * 同步实现（阻塞版）。
     * 后台广播接收器（每日维护）直接在自己的线程里调用它，不需要协程环境。
     *
     * @param budgetMs 整体耗时上限。**在 BroadcastReceiver 里必须传一个小值**
     *                 （广播接收器约 10 秒就会被系统掐断），避免超时被杀。
     */
    fun syncBlocking(context: Context, budgetMs: Long = 60_000): Result {
        val deadline = SystemClock.elapsedRealtime() + budgetMs
        val year = LocalDate.now().year
        val years = listOf(year, year + 1)

        // 没网就别白等一串超时了，直接走内置数据
        if (!hasNetwork(context)) {
            return Result(
                success = false,
                message = "当前无网络，稍后会自动重试；现在使用内置的中国大陆节假日数据。",
            )
        }

        // ---------- 1) 主源：holiday-cn ----------
        val fromCn = ArrayList<Holiday>()
        for (y in years) {
            if (SystemClock.elapsedRealtime() >= deadline) break
            val body = runCatching { fetch(String.format(URL_HOLIDAY_CN, y)) }.getOrNull() ?: continue
            fromCn.addAll(parseHolidayCn(body))
        }
        if (fromCn.isNotEmpty()) {
            HolidayManager.saveSynced(context, fromCn, SOURCE_CN)
            return buildResult(SOURCE_CN, fromCn, "（国务院办公厅通知）")
        }

        // ---------- 2) 备用源：timor.tech ----------
        val fromBackup = ArrayList<Holiday>()
        for (y in years) {
            if (SystemClock.elapsedRealtime() >= deadline) break
            val body = runCatching { fetch(String.format(URL_TIMOR, y)) }.getOrNull() ?: continue
            fromBackup.addAll(parseTimor(y, body))
        }
        if (fromBackup.isNotEmpty()) {
            HolidayManager.saveSynced(context, fromBackup, SOURCE_BACKUP)
            return buildResult(SOURCE_BACKUP, fromBackup, "")
        }

        // ---------- 3) 都失败：用内置数据 ----------
        return Result(
            success = false,
            message = "同步失败：两个数据源都没取到（可能是网络问题或超时）。" +
                "当前使用内置的中国大陆节假日数据，不影响屏蔽功能。",
        )
    }

    private fun buildResult(source: String, list: List<Holiday>, tail: String): Result {
        val holidays = list.count { it.type == HolidayType.STATUTORY }
        val makeups = list.count { it.type == HolidayType.MAKEUP }
        return Result(
            success = true,
            message = "同步成功：$holidays 个中国大陆法定假期、$makeups 个调休补班日$tail",
            holidayCount = holidays,
            makeupCount = makeups,
            source = source,
        )
    }

    private fun fetch(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) GLTU-Schedule")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    // ---------------- 解析：holiday-cn ----------------

    /**
     * holiday-cn 格式：
     * ```json
     * { "year": 2026,
     *   "papers": ["https://www.gov.cn/zhengce/...htm"],
     *   "days": [ {"name":"元旦","date":"2026-01-01","isOffDay":true},
     *             {"name":"春节","date":"2026-02-28","isOffDay":false} ] }
     * ```
     * isOffDay=true 为放假；false 为调休补班。同名的连续放假日合并成一个区间。
     */
    internal fun parseHolidayCn(body: String): List<Holiday> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val days = root.optJSONArray("days") ?: return emptyList()
        if (days.length() == 0) return emptyList()

        val offByName = LinkedHashMap<String, MutableList<LocalDate>>()
        val makeups = ArrayList<Holiday>()

        for (i in 0 until days.length()) {
            val o = days.optJSONObject(i) ?: continue
            val name = o.optString("name").ifBlank { "节假日" }
            val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: continue
            if (o.optBoolean("isOffDay", false)) {
                offByName.getOrPut(name) { ArrayList() }.add(date)
            } else {
                makeups.add(Holiday(name, date, date, HolidayType.MAKEUP))
            }
        }

        val merged = ArrayList<Holiday>()
        for ((name, dates) in offByName) {
            dates.sort()
            var start = dates.first()
            var end = dates.first()
            for (d in dates.drop(1)) {
                if (d == end.plusDays(1)) {
                    end = d
                } else {
                    merged.add(Holiday(name, start, end, HolidayType.STATUTORY))
                    start = d
                    end = d
                }
            }
            merged.add(Holiday(name, start, end, HolidayType.STATUTORY))
        }
        return merged.sortedBy { it.start } + makeups.sortedBy { it.start }
    }

    // ---------------- 解析：timor.tech（备用） ----------------

    /**
     * timor.tech 格式：
     * ```json
     * { "code":0, "holiday": { "01-01": {"holiday":true,"name":"元旦","date":"2026-01-01"}, ... } }
     * ```
     * holiday=false 且名字含"补班"的是调休上班日。连续放假日合并成一个区间。
     */
    internal fun parseTimor(year: Int, body: String): List<Holiday> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        if (root.optInt("code", -1) != 0) return emptyList()
        val map = root.optJSONObject("holiday") ?: return emptyList()

        val offDates = sortedMapOf<LocalDate, String>()
        val makeups = ArrayList<Holiday>()

        val keys = map.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val o = map.optJSONObject(key) ?: continue
            val dateStr = o.optString("date").ifBlank { "$year-$key" }
            val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
            val name = o.optString("name").ifBlank { "节假日" }
            if (o.optBoolean("holiday", false)) {
                offDates[date] = name
            } else if (name.contains("补班")) {
                makeups.add(Holiday(name, date, date, HolidayType.MAKEUP))
            }
        }

        val merged = ArrayList<Holiday>()
        var runStart: LocalDate? = null
        var runEnd: LocalDate? = null
        var runName = ""
        for ((date, name) in offDates) {
            if (runStart == null) {
                runStart = date; runEnd = date; runName = name
            } else if (date == runEnd!!.plusDays(1)) {
                runEnd = date
            } else {
                merged.add(Holiday(runName, runStart!!, runEnd!!, HolidayType.STATUTORY))
                runStart = date; runEnd = date; runName = name
            }
        }
        if (runStart != null && runEnd != null) {
            merged.add(Holiday(runName, runStart, runEnd, HolidayType.STATUTORY))
        }

        return merged + makeups
    }
}
