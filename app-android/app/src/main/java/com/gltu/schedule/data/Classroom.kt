// GLTU 课表 App —— 教室信息解析（按学校房号规则推导教学楼 / 楼层）
//
// 桂林旅游学院房号规则（4 位数 ABCD）：
//   首位 A = 教学楼：1=旅勤楼 2=旅思楼 3=旅博楼 4=旅齐楼
//   次位 B = 楼层：2=二楼（数字几代表几楼）
//   后两位 CD = 教室号
// 例：3217 → 旅博楼 三楼 17 号教室
package com.gltu.schedule.data

object Classroom {

    private val BUILDINGS = mapOf(
        '1' to "旅勤楼",
        '2' to "旅思楼",
        '3' to "旅博楼",
        '4' to "旅齐楼",
    )

    data class RoomInfo(
        val room: String,      // 原始房号，如 "3217"
        val building: String,  // 旅博楼
        val floor: Int,        // 3
        val roomNo: Int,       // 17
    )

    private val ROOM_PATTERN = Regex("""(?<!\d)([1-4])(\d)(\d{2})(?!\d)""")

    /** 从地点文本里找出房号并推导楼栋信息；找不到返回 null。 */
    fun find(location: String): RoomInfo? {
        if (location.isBlank()) return null
        val m = ROOM_PATTERN.find(location) ?: return null
        val room = m.value
        val building = BUILDINGS[room[0]] ?: return null
        val floor = room[1].digitToInt()
        val roomNo = room.substring(2).toIntOrNull() ?: return null
        return RoomInfo(room, building, floor, roomNo)
    }

    /**
     * 短标签（课表格子 / 日视图用）：
     * "雁山校区 3220（阶7教室）" → "旅博楼 3220（阶7教室）"
     * 识别不到楼栋时原样返回。
     */
    fun shortLabel(location: String): String {
        if (location.isBlank()) return location
        val info = find(location) ?: return location
        val withoutCampus = location.replace(Regex("""^[\u4e00-\u9fa5]{2,4}校区\s*"""), "")
        return withoutCampus.replaceFirst(info.room, "${info.building} ${info.room}")
    }

    /**
     * 详细标签（课程详情弹窗用）：
     * "雁山校区 3220（阶7教室）" → "雁山校区 · 旅博楼 2 楼 · 3220（阶7教室）"
     */
    fun detailLabel(location: String): String {
        if (location.isBlank()) return location
        val info = find(location) ?: return location
        val campus = Regex("""^([\u4e00-\u9fa5]{2,4}校区)\s*""").find(location)?.groupValues?.get(1)
        val rest = location.replace(Regex("""^[\u4e00-\u9fa5]{2,4}校区\s*"""), "")
        return listOfNotNull(campus, "${info.building} ${info.floor} 楼", rest)
            .joinToString(" · ")
    }
}
