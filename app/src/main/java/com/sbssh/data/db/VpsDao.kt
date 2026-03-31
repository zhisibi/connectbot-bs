package com.sbssh.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VpsDao {
    @Query("SELECT * FROM vps ORDER BY updatedAt DESC")
    fun getAllVps(): Flow<List<VpsEntity>>

    @Query("SELECT * FROM vps ORDER BY updatedAt DESC")
    suspend fun getAllVpsAsList(): List<VpsEntity>

    @Query("SELECT * FROM vps WHERE id = :id")
    suspend fun getVpsById(id: Long): VpsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVps(vps: VpsEntity): Long

    @Update
    suspend fun updateVps(vps: VpsEntity)

    @Delete
    suspend fun deleteVps(vps: VpsEntity)

    @Query("DELETE FROM vps WHERE id = :id")
    suspend fun deleteVpsById(id: Long)

    @Query("UPDATE vps SET connectbotHostId = :hostId WHERE id = :id")
    suspend fun updateConnectbotHostId(id: Long, hostId: Long)
}
