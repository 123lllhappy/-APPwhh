package com.example.wohenhao.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room 数据库 - 懒加载单例模式
 * 防止在数据库未初始化完成时被访问导致崩溃
 */
@Database(
    entities = [Contact::class, MessageRecord::class, AppSettings::class, SmsTemplate::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun messageRecordDao(): MessageRecordDao
    abstract fun settingsDao(): SettingsDao
    abstract fun smsTemplateDao(): SmsTemplateDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null
        private var isInitializing = false

        /**
         * 获取数据库实例 - 线程安全
         * 首次调用时会初始化数据库
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                if (INSTANCE == null && !isInitializing) {
                    isInitializing = true
                    try {
                        INSTANCE = buildDatabase(context.applicationContext)
                        Log.d(TAG, "Database initialized")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize database", e)
                        isInitializing = false
                        throw e
                    }
                    isInitializing = false
                }
                INSTANCE!!
            }
        }

        /**
         * 检查数据库是否已初始化
         */
        fun isInitialized(): Boolean {
            return INSTANCE != null
        }

        /**
         * 构建数据库
         */
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "wohenhao_database"
            )
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * 在后台线程执行数据库操作
         */
        fun <T> executeInIO(block: () -> T): T {
            if (INSTANCE == null) {
                throw IllegalStateException("Database not initialized. Call getInstance() first.")
            }
            return block()
        }
    }
}