package com.example.wohenhao

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.example.wohenhao.data.db.AppDatabase
import com.example.wohenhao.data.db.SmsTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 类 - 应用入口
 * 负责全局异常捕获和基础初始化
 */
class App : Application() {

    companion object {
        const val TAG = "WoHenHao"
        const val NOTIFICATION_CHANNEL_ID = "guardian_service"

        lateinit var instance: Application
            private set
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 设置全局异常捕获
        setupGlobalExceptionHandler()

        // 创建通知通道
        createNotificationChannel()

        // 初始化默认短信模板
        initDefaultTemplates()

        Log.d(TAG, "App initialized")
    }

    /**
     * 全局异常捕获 - 防止App崩溃
     */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception: ${throwable.message}", throwable)

            try {
                // 尝试显示错误提示
                Toast.makeText(
                    this,
                    "应用遇到问题，正在重启...",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show toast", e)
            }

            // 调用系统默认处理器
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 创建通知通道 - Android 8.0+ 需要
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * 初始化默认短信模板 - 确保首次启动就有可用模板
     */
    private fun initDefaultTemplates() {
        appScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val count = db.smsTemplateDao().getTemplateCount()
                if (count == 0) {
                    db.smsTemplateDao().insertTemplate(SmsTemplate.getDefaultSOSTemplate())
                    db.smsTemplateDao().insertTemplate(SmsTemplate.getDefaultAutoHelpTemplate())
                    Log.d(TAG, "Default SMS templates initialized")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init default templates", e)
            }
        }
    }
}