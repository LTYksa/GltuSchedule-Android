// GLTU 课表 App —— Room 数据库
package com.ltyksa.gltuschedule.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ltyksa.gltuschedule.database.dao.CourseDao
import com.ltyksa.gltuschedule.database.dao.HolidayDao
import com.ltyksa.gltuschedule.database.dao.TimeSlotDao
import com.ltyksa.gltuschedule.database.entity.CourseEntity
import com.ltyksa.gltuschedule.database.entity.HolidayEntity
import com.ltyksa.gltuschedule.database.entity.TimeSlotEntity

/**
 * 应用本地数据库（Room）。全程本地运行，无网络后端依赖。
 *
 * version 每次结构变更时 +1，并在 [Migrations] 中登记对应迁移；
 * exportSchema = true 会导出 schema 到 app/schemas（见 build.gradle.kts 的 ksp 配置）。
 */
@Database(
    entities = [CourseEntity::class, TimeSlotEntity::class, HolidayEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun timeSlotDao(): TimeSlotDao
    abstract fun holidayDao(): HolidayDao

    companion object {
        private const val DATABASE_NAME = "gltu_schedule.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(*Migrations.ALL)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
