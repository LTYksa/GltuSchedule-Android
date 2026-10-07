// GLTU 课表 App —— 作息节次 DAO
package com.gltu.schedule.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gltu.schedule.database.entity.TimeSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSlotDao {

    @Query("SELECT * FROM time_slots ORDER BY slot_index ASC")
    fun observeAll(): Flow<List<TimeSlotEntity>>

    @Query("SELECT * FROM time_slots ORDER BY slot_index ASC")
    fun getAllSync(): List<TimeSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(slots: List<TimeSlotEntity>)

    @Query("SELECT COUNT(*) FROM time_slots")
    suspend fun count(): Int

    @Query("DELETE FROM time_slots")
    suspend fun clearAll()
}
