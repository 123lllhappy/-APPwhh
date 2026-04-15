package com.example.wohenhao.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Dao
interface SmsTemplateDao {
    
    @Query("SELECT * FROM sms_templates ORDER BY isDefault DESC, updatedAt DESC")
    fun getAllTemplates(): Flow<List<SmsTemplate>>
    
    @Query("SELECT * FROM sms_templates ORDER BY isDefault DESC, updatedAt DESC")
    suspend fun getAllTemplatesSync(): List<SmsTemplate> = getAllTemplates().first()
    
    @Query("SELECT * FROM sms_templates WHERE id = :id")
    suspend fun getTemplateById(id: String): SmsTemplate?
    
    @Query("SELECT * FROM sms_templates WHERE isDefault = 1 AND id = :id")
    suspend fun getDefaultTemplate(id: String): SmsTemplate?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: SmsTemplate)
    
    @Update
    suspend fun updateTemplate(template: SmsTemplate)
    
    @Delete
    suspend fun deleteTemplate(template: SmsTemplate)
    
    @Query("DELETE FROM sms_templates WHERE id = :id AND isDefault = 0")
    suspend fun deleteCustomTemplate(id: String)
    
    @Query("SELECT COUNT(*) FROM sms_templates")
    suspend fun getTemplateCount(): Int
}