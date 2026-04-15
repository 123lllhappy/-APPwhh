package com.example.wohenhao.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.wohenhao.App
import com.example.wohenhao.MainActivity
import com.example.wohenhao.R
import com.example.wohenhao.data.db.*
import com.example.wohenhao.util.*
import kotlinx.coroutines.*
import kotlin.math.sqrt

/**
 * 骑行守护服务
 * 每分钟检测位置，3分钟未移动自动触发求助
 */
class CyclingService : Service() {
    
    companion object {
        private const val TAG = "CyclingService"
        private const val NOTIFICATION_ID = 1002
        
        const val MOVING_THRESHOLD_METERS = 50.0  // 移动阈值：50米
        const val CHECK_INTERVAL_MS = 60_000L      // 检测间隔：1分钟
        const val TIMEOUT_MS = 3 * 60_000L         // 超时时间：3分钟
        
        fun start(context: Context) {
            val intent = Intent(context, CyclingService::class.java)
            context.startForegroundService(intent)
            Log.d(TAG, "CyclingService start requested")
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, CyclingService::class.java)
            context.stopService(intent)
            Log.d(TAG, "CyclingService stop requested")
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var checkJob: Job? = null
    private var lastLocation: android.location.Location? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CyclingService created")
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "CyclingService started")
        
        // 记录骑行开始时间
        serviceScope.launch {
            val dao = AppDatabase.getInstance(this@CyclingService).settingsDao()
            dao.putLong(AppSettings.KEY_CYCLING_START_TIME, System.currentTimeMillis())
            dao.putBoolean(AppSettings.KEY_CYCLING_MODE, true)
            dao.putLong(AppSettings.KEY_LAST_MOVING_TIME, System.currentTimeMillis())
        }
        
        // 启动位置检测定时器
        startLocationCheck()
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CyclingService destroyed")
        checkJob?.cancel()
        
        // 清除骑行状态
        serviceScope.launch {
            val dao = AppDatabase.getInstance(this@CyclingService).settingsDao()
            dao.putBoolean(AppSettings.KEY_CYCLING_MODE, false)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🚴 骑行守护中")
            .setContentText("正在监测位置变化...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun startLocationCheck() {
        checkJob?.cancel()
        
        checkJob = serviceScope.launch {
            Log.d(TAG, "Location check timer started")
            
            while (isActive) {
                try {
                    delay(CHECK_INTERVAL_MS)
                    Log.d(TAG, "Checking location...")
                    
                    checkLocationAndTimeout()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in location check loop", e)
                }
            }
        }
    }
    
    private suspend fun checkLocationAndTimeout() {
        try {
            val context = this@CyclingService
            val dao = AppDatabase.getInstance(context).settingsDao()
            
            // 获取当前位置
            val locationHelper = LocationHelper(context)
            val locationResult = locationHelper.getCurrentLocation()
            
            if (locationResult.isFailure) {
                Log.w(TAG, "Failed to get location: ${locationResult.exceptionOrNull()?.message}")
                return
            }
            
            val currentLocation = locationResult.getOrNull() ?: return
            
            // 检查是否移动
            var hasMoved = false
            if (lastLocation != null) {
                val distance = calculateDistance(lastLocation!!, currentLocation)
                Log.d(TAG, "Distance from last location: ${distance}m")
                
                if (distance > MOVING_THRESHOLD_METERS) {
                    hasMoved = true
                    Log.d(TAG, "User is moving, updating last moving time")
                    
                    // 更新最后移动时间
                    dao.putLong(AppSettings.KEY_LAST_MOVING_TIME, System.currentTimeMillis())
                    dao.putDouble(AppSettings.KEY_LAST_CYCLING_LOCATION_LAT, currentLocation.latitude)
                    dao.putDouble(AppSettings.KEY_LAST_CYCLING_LOCATION_LNG, currentLocation.longitude)
                }
            }
            
            lastLocation = currentLocation
            
            // 检查是否超时
            if (!hasMoved) {
                val lastMovingTime = dao.getLong(AppSettings.KEY_LAST_MOVING_TIME, System.currentTimeMillis())
                val timeSinceLastMove = System.currentTimeMillis() - lastMovingTime
                
                Log.d(TAG, "Time since last move: ${timeSinceLastMove / 1000}s")
                
                if (timeSinceLastMove >= TIMEOUT_MS) {
                    Log.d(TAG, "Timeout reached! Triggering cycling help...")
                    triggerCyclingHelp(context, currentLocation)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkLocationAndTimeout", e)
        }
    }
    
    private fun calculateDistance(loc1: android.location.Location, loc2: android.location.Location): Double {
        // 使用 Haversine 公式计算距离
        val earthRadius = 6371000.0 // 地球半径（米）
        
        val dLat = Math.toRadians(loc2.latitude - loc1.latitude)
        val dLng = Math.toRadians(loc2.longitude - loc1.longitude)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(loc1.latitude)) * Math.cos(Math.toRadians(loc2.latitude)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    private suspend fun triggerCyclingHelp(context: Context, location: android.location.Location) {
        try {
            Log.d(TAG, "Triggering cycling auto help...")
            
            val database = AppDatabase.getInstance(context)
            val contactDao = database.contactDao()
            val contacts = contactDao.getAllContactsSync()
            
            if (contacts.isEmpty()) {
                Log.d(TAG, "No contacts, skipping cycling help")
                return
            }
            
            // 发送求助短信
            val results = SmsHelper.sendEmergencyMessages(
                context,
                contacts,
                location.latitude,
                location.longitude,
                isSOS = false,
                isCycling = true
            )
            
            val successCount = results.values.count { it }
            Log.d(TAG, "Cycling help SMS sent: success=$successCount/${contacts.size}")
            
            // 记录到数据库
            val record = MessageRecord(
                type = Constants.MSG_TYPE_AUTO_HELP,
                timestamp = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude,
                address = "",
                recipients = contacts.joinToString(",") { it.phone },
                status = if (successCount == contacts.size) "success" else "partial"
            )
            database.messageRecordDao().insert(record)
            
            // 停止骑行服务
            stop(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering cycling help", e)
        }
    }
}
