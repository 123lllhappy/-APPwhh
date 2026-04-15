package com.example.wohenhao.service

import android.content.Context
import android.util.Log
import com.example.wohenhao.data.db.AppDatabase
import com.example.wohenhao.data.db.AppSettings
import com.example.wohenhao.data.db.MessageRecord
import com.example.wohenhao.util.Constants
import com.example.wohenhao.util.LocationHelper
import com.example.wohenhao.util.SmsHelper

/**
 * 守护检查逻辑助手类
 * 从 GuardianWorker 提取，供 Service 和 Worker 共同使用
 */
object GuardianCheckHelper {
    private const val TAG = "GuardianCheckHelper"

    /**
     * 执行检查和自动求助
     */
    suspend fun executeCheck(context: Context) {
        try {
            Log.d(TAG, "Starting guardian check...")
            
            val database = AppDatabase.getInstance(context)
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
            
            // 检查是否超时
            if (now - baseTime >= timeoutMillis) {
                Log.d(TAG, "Timeout reached! baseTime=$baseTime, now=$now, diff=${now - baseTime}ms, timeout=${timeoutMillis}ms")
                Log.d(TAG, "Checking contacts...")

                val contacts = contactDao.getAllContactsSync()

                if (contacts.isEmpty()) {
                    Log.d(TAG, "No contacts, skipping auto help")
                    return
                }

                Log.d(TAG, "Found ${contacts.size} contacts, getting location...")

                // 获取位置
                val locationHelper = LocationHelper(context)
                val locationResult = locationHelper.getCurrentLocation()

                val latitude: Double
                val longitude: Double

                if (locationResult.isSuccess) {
                    val location = locationResult.getOrNull()
                    if (location != null) {
                        latitude = location.latitude
                        longitude = location.longitude
                        Log.d(TAG, "Location obtained: $latitude, $longitude")
                    } else {
                        latitude = 0.0
                        longitude = 0.0
                        Log.w(TAG, "Location is null")
                    }
                } else {
                    Log.w(TAG, "Failed to get location: ${locationResult.exceptionOrNull()?.message}")
                    latitude = 0.0
                    longitude = 0.0
                }

                // 发送求助短信
                Log.d(TAG, "Sending emergency messages to ${contacts.size} contacts...")
                val results = SmsHelper.sendEmergencyMessages(
                    context,
                    contacts,
                    latitude,
                    longitude,
                    isSOS = false
                )

                val successCount = results.values.count { it }
                val failCount = contacts.size - successCount

                Log.d(TAG, "SMS sent: success=$successCount, failed=$failCount")

                // 记录到数据库
                val record = MessageRecord(
                    type = if (successCount == contacts.size) Constants.MSG_TYPE_AUTO_HELP else Constants.MSG_TYPE_AUTO_HELP_FAILED,
                    timestamp = System.currentTimeMillis(),
                    latitude = latitude,
                    longitude = longitude,
                    address = "",
                    recipients = contacts.joinToString(",") { it.phone },
                    status = if (successCount == contacts.size) "success" else "partial_failure"
                )
                database.messageRecordDao().insert(record)

                Log.d(TAG, "Auto help process completed")
            } else {
                Log.d(TAG, "Not timeout yet. elapsed=${now - baseTime}ms, need=${timeoutMillis}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in executeCheck", e)
        }
    }
}
