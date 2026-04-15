package com.example.wohenhao.service

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.wohenhao.App
import com.example.wohenhao.R
import com.example.wohenhao.data.db.AppDatabase
import com.example.wohenhao.data.db.AppSettings
import com.example.wohenhao.data.db.MessageRecord
import com.example.wohenhao.util.Constants
import com.example.wohenhao.util.LocationHelper
import com.example.wohenhao.util.SmsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 守护 Worker - 后台超时检测
 * 每 15 分钟执行一次，检查是否需要触发自动求助
 */
class GuardianWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "GuardianWorker"
        private const val CHANNEL_ID_AUTO_HELP = "auto_help_channel"
        private const val CHANNEL_ID_FAILURE = "sms_failure_channel"
        private const val NOTIFICATION_ID_AUTO_HELP = 2001
        private const val NOTIFICATION_ID_FAILURE = 2002
        private const val USAGE_STATS_LOOKBACK_HOURS = 4 // 检测最近4小时内是否有App使用
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "GuardianWorker started")

        return try {
            // 确保数据库已初始化
            if (!AppDatabase.isInitialized()) {
                Log.w(TAG, "Database not initialized, initializing now...")
                AppDatabase.getInstance(applicationContext)
            }

            // 确保通知渠道已创建
            createNotificationChannels()

            withContext(Dispatchers.IO) {
                checkAndTriggerAutoHelp()
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "GuardianWorker failed", e)
            Result.failure()
        }
    }

    /**
     * 创建通知渠道（Android 8.0+ 必须）
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

            // 自动求助成功渠道
            val autoHelpChannel = NotificationChannel(
                CHANNEL_ID_AUTO_HELP,
                "求助通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "自动求助短信发送结果通知"
                enableVibration(true)
            }

            // 失败提醒渠道
            val failureChannel = NotificationChannel(
                CHANNEL_ID_FAILURE,
                "求助失败提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "当求助短信发送失败时提醒您"
                enableVibration(true)
            }

            notificationManager?.createNotificationChannels(listOf(autoHelpChannel, failureChannel))
        }
    }

    /**
     * 检查并触发自动求助
     */
    private suspend fun checkAndTriggerAutoHelp() {
        try {
            val database = AppDatabase.getInstance(applicationContext)
            val settingsDao = database.settingsDao()
            val contactDao = database.contactDao()

            // 获取设置
            val needCheckIn = settingsDao.getBoolean(AppSettings.KEY_NEED_CHECK_IN, false)
            val lastCheckInTime = settingsDao.getLong(AppSettings.KEY_LAST_CHECK_IN_TIME, 0L)
            val lastOpenTime = settingsDao.getLong(AppSettings.KEY_LAST_OPEN_TIME, 0L)
            val timeoutHours = settingsDao.getInt(AppSettings.KEY_TIMEOUT_HOURS, 12)

            // 判断今天是否已打卡
            val now = System.currentTimeMillis()
            val todayStart = java.util.Calendar.getInstance().apply {
                timeInMillis = now
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val checkedInToday = lastCheckInTime >= todayStart

            // 需要打卡 且 今天已打卡 → 跳过，不触发求助
            if (needCheckIn && checkedInToday) {
                Log.d(TAG, "Checked in today, skipping auto help")
                return
            }

            Log.d(TAG, "needCheckIn=$needCheckIn, checkedInToday=$checkedInToday, timeoutHours=$timeoutHours, lastOpenTime=$lastOpenTime, lastCheckInTime=$lastCheckInTime")

            // 计算超时时间（毫秒）
            val timeoutMillis = when (timeoutHours) {
                0 -> 15_000L // 0 = 15秒（测试模式）
                1 -> Constants.MILLIS_PER_HOUR // 1小时
                else -> timeoutHours.toLong() * Constants.MILLIS_PER_HOUR // 其他小时数
            }

            // ========== UsageStatsManager 检测：最近4小时内有任何App使用 = 活着 ==========
            // 15秒测试模式时跳过此检测，直接触发求助
            if (timeoutHours != 0) {
                val recentAppUsage = hasRecentAppUsage()
                Log.d(TAG, "Phone usage check: recentAppUsage=$recentAppUsage (last ${USAGE_STATS_LOOKBACK_HOURS}h)")

                // 如果最近4小时有App使用，说明手机在用 → 视为"活着"，跳过求助
                if (recentAppUsage) {
                    Log.d(TAG, "Phone was used recently, skipping auto help")
                    return
                }
            } else {
                Log.d(TAG, "15s test mode: skipping UsageStatsManager check")
            }

            // 检查是否超时
            // 15秒测试模式：使用 lastCheckInTime 作为基准（如果已打卡），否则使用 lastOpenTime
            // 正常模式（1小时/12小时等）：如果今天已打卡，不触发求助；否则使用 lastOpenTime
            val baseTime = if (timeoutHours == 0 && lastCheckInTime > 0) {
                lastCheckInTime  // 测试模式：从打卡时间开始计时
            } else if (checkedInToday) {
                // 正常模式但今天已打卡：不触发求助
                Log.d(TAG, "Checked in today in normal mode, skipping auto help")
                return
            } else {
                lastOpenTime     // 正常模式且未打卡：从最后打开时间开始计时
            }
            
            if (now - baseTime >= timeoutMillis) {
                Log.d(TAG, "Timeout reached, checking contacts...")

                val contacts = contactDao.getAllContactsSync()

                if (contacts.isEmpty()) {
                    Log.d(TAG, "No contacts, skipping auto help")
                    return
                }

                // 获取位置
                val locationHelper = LocationHelper(applicationContext)
                val locationResult = locationHelper.getCurrentLocation()

                val latitude: Double
                val longitude: Double

                if (locationResult.isSuccess) {
                    val location = locationResult.getOrNull()
                    if (location != null) {
                        latitude = location.latitude
                        longitude = location.longitude
                    } else {
                        latitude = 0.0
                        longitude = 0.0
                    }
                } else {
                    Log.w(TAG, "Failed to get location: ${locationResult.exceptionOrNull()?.message}")
                    latitude = 0.0
                    longitude = 0.0
                }

                // 发送求助短信
                val results = SmsHelper.sendEmergencyMessages(
                    applicationContext,
                    contacts,
                    latitude,
                    longitude,
                    isSOS = false
                )

                val successCount = results.values.count { it }
                val failCount = contacts.size - successCount

                // 记录到数据库
                val record = MessageRecord(
                    type = if (successCount == contacts.size) Constants.MSG_TYPE_AUTO_HELP else Constants.MSG_TYPE_AUTO_HELP_FAILED,
                    latitude = latitude,
                    longitude = longitude,
                    recipients = contacts.joinToString(",") { it.phone },
                    status = when {
                        successCount == contacts.size -> "success"
                        successCount > 0 -> "partial"
                        else -> "failed"
                    }
                )
                database.messageRecordDao().insert(record)

                // ========== 失败通知（核心新增）==========
                if (failCount > 0) {
                    showFailureNotification(successCount, contacts.size, failCount)
                } else {
                    showSuccessNotification(successCount, contacts.size)
                }

                Log.d(TAG, "Auto help sent: $successCount/${contacts.size}")
            } else {
                Log.d(TAG, "Not timeout yet, skipping auto help")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkAndTriggerAutoHelp", e)
        }
    }

    /**
     * 显示求助成功通知
     */
    private fun showSuccessNotification(successCount: Int, totalCount: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "No POST_NOTIFICATIONS permission, skip notification")
                    return
                }
            }

            val intent = android.content.Intent(applicationContext, com.example.wohenhao.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                applicationContext, 0, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_AUTO_HELP)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("求助已发送")
                .setContentText("已向 $successCount/$totalCount 位联系人发送求助短信")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID_AUTO_HELP, notification)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show success notification", e)
        }
    }

    /**
     * 显示求助失败通知（核心新增）
     * 告知用户部分/全部联系人发送失败，并给出解决方案
     */
    private fun showFailureNotification(successCount: Int, totalCount: Int, failCount: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "No POST_NOTIFICATIONS permission, skip failure notification")
                    return
                }
            }

            val intent = android.content.Intent(applicationContext, com.example.wohenhao.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                applicationContext, 0, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            val contentText = when {
                successCount == 0 -> "⚠️ 求助短信发送全部失败！请检查短信权限或联系人在世"
                else -> "⚠️ $failCount/$totalCount 位联系人发送失败，请检查"
            }

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_FAILURE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("求助短信发送异常")
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("""您超过了设定的守护时间，已尝试向 $totalCount 位紧急联系人发送求助短信。
                        |
                        |发送结果：$successCount 成功，$failCount 失败
                        |
                        |常见原因：
                        |• 短信余额不足
                        |• 短信功能被运营商限制
                        |• 联系人是本机号码
                        |
                        |请打开 App 检查，或手动重试""".trimMargin()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID_FAILURE, notification)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show failure notification", e)
        }
    }

    /**
     * 使用 UsageStatsManager 检测手机最近 N 小时是否有任何 App 被使用
     * @return true = 有App使用记录（视为"活着"），false = 没有（手机可能是闲置/关机状态）
     */
    private fun hasRecentAppUsage(): Boolean {
        try {
            // 检查是否有 PACKAGE_USAGE_STATS 权限（特殊权限，需跳转设置页授权）
            val appOps = applicationContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    applicationContext.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    applicationContext.packageName
                )
            }

            if (mode != AppOpsManager.MODE_ALLOWED) {
                Log.w(TAG, "UsageStats permission not granted, skipping phone usage check")
                return false
            }

            val usageStatsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return false

            val now = System.currentTimeMillis()
            val lookbackTime = now - (USAGE_STATS_LOOKBACK_HOURS * 60 * 60 * 1000L)

            val usageEvents = usageStatsManager.queryEvents(lookbackTime, now)
            val event = UsageEvents.Event()

            // 只要有任何App切换到前台的事件，就说明手机在用 → 视为"活着"
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    Log.d(TAG, "Recent app usage detected: ${event.packageName}")
                    return true
                }
            }

            Log.d(TAG, "No recent app usage in last ${USAGE_STATS_LOOKBACK_HOURS}h")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "Error checking phone usage", e)
            return false
        }
    }
}
