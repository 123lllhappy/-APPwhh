package com.example.wohenhao.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.wohenhao.App
import com.example.wohenhao.MainActivity
import com.example.wohenhao.R
import com.example.wohenhao.util.Constants
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

/**
 * 前台守护服务
 * 负责：
 * 1. 保持 App 在后台运行
 * 2. 调度 GuardianWorker 定期检测超时
 * 3. 在通知栏显示守护状态
 */
class GuardianService : Service() {

    companion object {
        private const val TAG = "GuardianService"
        private const val EXTRA_CHECK_INTERVAL = "check_interval_seconds"

        /**
         * 启动守护服务（默认使用已保存的检测频率）
         */
        fun start(context: Context) {
            start(context, -1)
        }

        /**
         * 启动守护服务，并指定检测频率（秒）
         * @param intervalSeconds -1=从设置读取，15/3600/14400=具体秒数
         */
        fun start(context: Context, intervalSeconds: Int) {
            try {
                val intent = Intent(context, GuardianService::class.java).apply {
                    putExtra(EXTRA_CHECK_INTERVAL, intervalSeconds)
                }
                context.startForegroundService(intent)
                Log.d(TAG, "GuardianService started, interval=${intervalSeconds}s")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start GuardianService", e)
            }
        }

        /**
         * 停止守护服务
         */
        fun stop(context: Context) {
            try {
                val intent = Intent(context, GuardianService::class.java)
                context.stopService(intent)
                Log.d(TAG, "GuardianService stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop GuardianService", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var checkJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "GuardianService onCreate")

        try {
            // 启动前台通知
            startForeground(Constants.NOTIFICATION_ID_SERVICE, createNotification())

            // 从设置读取检测频率并调度 Worker
            val intervalSeconds = readCheckInterval()
            scheduleGuardianWorker(intervalSeconds)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize service", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "GuardianService onStartCommand")

        val intervalFromIntent = intent?.getIntExtra(EXTRA_CHECK_INTERVAL, -1) ?: -1
        if (intervalFromIntent > 0) {
            // 外部传入了具体间隔，重新调度
            scheduleGuardianWorker(intervalFromIntent)
        } else {
            // 无参数，检查设置是否有更新
            val intervalFromSettings = readCheckInterval()
            scheduleGuardianWorker(intervalFromSettings)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "GuardianService onDestroy")
    }

    /**
     * 创建前台通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * 从设置读取检测频率（秒）
     */
    private fun readCheckInterval(): Int {
        return try {
            // 使用 runBlocking 调用 suspend 函数
            kotlinx.coroutines.runBlocking {
                val db = com.example.wohenhao.data.db.AppDatabase.getInstance(this@GuardianService)
                val interval = db.settingsDao().getInt(
                    com.example.wohenhao.data.db.AppSettings.KEY_CHECK_INTERVAL_SECONDS, 
                    3600  // 默认 1小时
                )
                Log.d(TAG, "Read check interval from DB: ${interval}s")
                interval
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read check interval, using default 3600s", e)
            3600
        }
    }

    /**
     * 调度守护 Worker
     * @param intervalSeconds 检测间隔（秒）
     */
    private fun scheduleGuardianWorker(intervalSeconds: Int) {
        try {
            // 取消旧的定时任务
            checkJob?.cancel()
            
            Log.d(TAG, "Starting foreground timer with interval: ${intervalSeconds}s")
            
            // 启动前台服务定时器（不依赖 WorkManager，更可靠）
            checkJob = serviceScope.launch {
                while (isActive) {
                    Log.d(TAG, "Waiting ${intervalSeconds}s for next check...")
                    delay(intervalSeconds.toLong() * 1000)
                    Log.d(TAG, "Foreground timer triggered, executing check...")
                    
                    // 直接执行检查逻辑
                    GuardianCheckHelper.executeCheck(this@GuardianService)
                }
            }
            
            Log.d(TAG, "Foreground timer started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule GuardianWorker", e)
        }
    }
}