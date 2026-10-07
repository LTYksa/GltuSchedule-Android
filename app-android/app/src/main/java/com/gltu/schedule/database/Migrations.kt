// GLTU 课表 App —— Room 迁移策略
package com.gltu.schedule.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 迁移原则（务必遵守，避免破坏性迁移清空用户数据）：
 * 1. 每提升一次 [AppDatabase] 的 version，就在此新增一个 Migration(start, end) 并追加到 [ALL]；
 * 2. 绝不使用 fallbackToDestructiveMigration()；
 * 3. SQLite 的 ALTER TABLE 有限制：默认只能 ADD COLUMN（带默认值或可空）、RENAME TABLE/COLUMN；
 *    如需"改列类型/删列/加非空约束"等破坏性操作，须走"建新表 → 拷数据 → 删旧表 → 换名"四步法
 *    （见下方 [MIGRATION_1_2] 注释里的示例）。
 */
object Migrations {

    /**
     * 1 -> 2：给 courses 表新增 weeksCsv 列（TEXT，可空）。
     * 用于存放"显式周次集合"（如 GLTU 的 `4-5周,8周,11-13周(单),14-16周` → "4,5,8,11,13,14,15,16"），
     * 因为教务系统的上课周次经常是不规则集合，startWeek/endWeek/oddEven 表达不了。
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE courses ADD COLUMN weeksCsv TEXT")
        }
    }

    /** 所有已登记的迁移，按顺序加入。 */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)

    /**
     * 【示例】破坏性变更的标准四步法（改列类型 / 删列 / 加非空约束等场景）。
     * 以"给 courses 的 name 列加 NOT NULL 且保留旧数据"为例（伪代码，未启用）：
     *
     * ```
     * db.execSQL("CREATE TABLE courses_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, ...)")
     * db.execSQL("INSERT INTO courses_new (id, name, ...) SELECT id, name, ... FROM courses")
     * db.execSQL("DROP TABLE courses")
     * db.execSQL("ALTER TABLE courses_new RENAME TO courses")
     * ```
     *
     * 注意：Room 会校验迁移后的 schema 与实体定义是否一致，务必逐列对齐。
     */
}
