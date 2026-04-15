package com.example.wohenhao.data.db

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 联系人 DAO
 */
@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY createdAt DESC")
    fun getAllContacts(): LiveData<List<Contact>>

    @Query("SELECT * FROM contacts ORDER BY createdAt DESC")
    suspend fun getAllContactsSync(): List<Contact>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: Long): Contact?

    @Query("SELECT COUNT(*) FROM contacts")
    fun getContactCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getContactCountSync(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact): Long

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Long)
}