// GLTU 课表 App —— 节假日 DAO
package com.gltu.schedule.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gltu.schedule.database.entity.HolidayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {

    @Query("SELECT * FROM holidays ORDER BY startDate ASC")
    fun observeAll(): Flow<List<HolidayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(holidays: List<HolidayEntity>)

    @Query("DELETE FROM holidays")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM holidays")
    suspend fun count(): Int
}
