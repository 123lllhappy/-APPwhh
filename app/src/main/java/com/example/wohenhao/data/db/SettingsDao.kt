package com.example.wohenhao.data.db

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 设置 DAO
 */
@Dao
interface SettingsDao {

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): AppSettings?

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    fun getSettingLive(key: String): LiveData<AppSettings?>

    @Query("SELECT * FROM app_settings")
    suspend fun getAllSettings(): List<AppSettings>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: AppSettings)

    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun delete(key: String)

    /**
     * 获取布尔值设置
     */
    suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val setting = getSetting(key)
        return setting?.value?.toBooleanStrictOrNull() ?: defaultValue
    }

    /**
     * 获取整数值设置
     */
    suspend fun getInt(key: String, defaultValue: Int): Int {
        val setting = getSetting(key)
        return setting?.value?.toIntOrNull() ?: defaultValue
    }

    /**
     * 获取长整型设置
     */
    suspend fun getLong(key: String, defaultValue: Long): Long {
        val setting = getSetting(key)
        return setting?.value?.toLongOrNull() ?: defaultValue
    }

    /**
     * 获取双精度浮点数设置
     */
    suspend fun getDouble(key: String, defaultValue: Double): Double {
        val setting = getSetting(key)
        return setting?.value?.toDoubleOrNull() ?: defaultValue
    }

    /**
     * 获取字符串设置
     */
    suspend fun getString(key: String, defaultValue: String): String {
        val setting = getSetting(key)
        return setting?.value ?: defaultValue
    }

    /**
     * 保存布尔值设置
     */
    suspend fun putBoolean(key: String, value: Boolean) {
        insert(AppSettings(key, value.toString()))
    }

    /**
     * 保存整数值设置
     */
    suspend fun putInt(key: String, value: Int) {
        insert(AppSettings(key, value.toString()))
    }

    /**
     * 保存长整型设置
     */
    suspend fun putLong(key: String, value: Long) {
        insert(AppSettings(key, value.toString()))
    }

    /**
     * 保存双精度浮点数设置
     */
    suspend fun putDouble(key: String, value: Double) {
        insert(AppSettings(key, value.toString()))
    }

    /**
     * 保存字符串设置
     */
    suspend fun putString(key: String, value: String) {
        insert(AppSettings(key, value))
    }
}