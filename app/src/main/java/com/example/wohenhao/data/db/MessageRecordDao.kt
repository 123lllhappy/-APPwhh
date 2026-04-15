package com.example.wohenhao.data.db

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 消息记录 DAO
 */
@Dao
interface MessageRecordDao {

    @Query("SELECT * FROM message_records ORDER BY timestamp DESC")
    fun getAllRecords(): LiveData<List<MessageRecord>>

    @Query("SELECT * FROM message_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentRecords(limit: Int): LiveData<List<MessageRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MessageRecord): Long

    @Query("DELETE FROM message_records")
    suspend fun deleteAll()
}