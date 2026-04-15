package com.example.wohenhao.util

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.SystemClock
import android.telephony.SmsManager
import android.util.Log
import com.example.wohenhao.data.db.AppDatabase
import com.example.wohenhao.data.db.AppSettings
import com.example.wohenhao.data.db.Contact
import com.example.wohenhao.data.db.SmsTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 短信发送工具类
 *
 * sendTextMessage / sendMultipartTextMessage 是异步 API，不抛异常。
 * 通过 PendingIntent + BroadcastReceiver + CountDownLatch 等待真实结果。
 */
object SmsHelper {

    private const val TAG = "SmsHelper"

    /**
     * 检查短信权限是否已授予
     */
    fun hasSmsPermission(context: Context): Boolean {
        return try {
            PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(android.Manifest.permission.SEND_SMS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check SMS permission", e)
            false
        }
    }

    /**
     * 发送紧急求助短信
     *
     * @return Map<手机号, 是否成功> — 基于 PendingIntent 回调判定
     */
    suspend fun sendEmergencyMessages(
        context: Context,
        contacts: List<Contact>,
        latitude: Double,
        longitude: Double,
        isSOS: Boolean = true,
        isCycling: Boolean = false
    ): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()

        if (!hasSmsPermission(context)) {
            Log.e(TAG, "No SMS permission, marking all as failed")
            contacts.forEach { results[it.phone] = false }
            return@withContext results
        }

        if (contacts.isEmpty()) {
            Log.w(TAG, "No contacts")
            return@withContext results
        }

        val message = getCustomMessage(context, latitude, longitude, isSOS, isCycling)
        val smsManager = getSmsManager(context)

        contacts.forEach { contact ->
            Log.d(TAG, "Sending SMS to: ${contact.phone}")
            val success = sendSmsWithConfirmation(context, smsManager, contact.phone, message)
            results[contact.phone] = success
            Log.d(TAG, "Result for ${contact.phone}: $success")
            SystemClock.sleep(300)
        }

        val successCount = results.values.count { it }
        Log.d(TAG, "sendEmergencyMessages done: $successCount/${contacts.size} succeeded")
        results
    }

    /**
     * 发送位置汇报短信
     */
    suspend fun sendLocationReport(
        context: Context,
        contacts: List<Contact>,
        latitude: Double,
        longitude: Double
    ): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()

        if (!hasSmsPermission(context)) {
            contacts.forEach { results[it.phone] = false }
            return@withContext results
        }

        if (contacts.isEmpty()) {
            return@withContext results
        }

        val message = buildString {
            append("📍 定时位置汇报\n\n")
            append("纬度：$latitude\n")
            append("经度：$longitude\n")
            append("地图：https://uri.amap.com/marker?position=$longitude,$latitude&coordinate=gaode\n\n")
            append("—— 我很好 App")
        }

        val smsManager = getSmsManager(context)

        contacts.forEach { contact ->
            val success = sendSmsWithConfirmation(context, smsManager, contact.phone, message)
            results[contact.phone] = success
            SystemClock.sleep(300)
        }

        results
    }

    @Suppress("DEPRECATION")
    private fun getSmsManager(context: Context): SmsManager {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            SmsManager.getDefault()
        }
    }

    /**
     * 发送短信并等待回调确认
     *
     * 使用 CountDownLatch + BroadcastReceiver + PendingIntent 机制：
     * 1. 注册 BroadcastReceiver 监听 "SMS_SENT_ACTION"
     * 2. sendTextMessage 时传入 PendingIntent
     * 3. CountDownLatch.await() 阻塞等待
     * 4. 回调收到结果后 latch.countDown()，函数返回
     *
     * @param timeoutSeconds 等待超时秒数
     */
    private fun sendSmsWithConfirmation(
        context: Context,
        smsManager: SmsManager,
        phoneNumber: String,
        message: String,
        timeoutSeconds: Long = 30
    ): Boolean {
        val parts = smsManager.divideMessage(message)
        val partsCount = parts.size

        // 预检查
        if (phoneNumber.length < 7) {
            Log.e(TAG, "Invalid phone number: $phoneNumber")
            return false
        }
        if (message.isBlank()) {
            Log.e(TAG, "Empty message")
            return false
        }

        // 用于通知结果的 CountDownLatch
        val latch = CountDownLatch(1)
        val resultCode = intArrayOf(Activity.RESULT_OK) // 用数组以便在内部类中修改

        // BroadcastReceiver 接收 PendingIntent 回调
        val sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                resultCode[0] = intent?.getIntExtra("errorCode", Activity.RESULT_OK)
                    ?: SmsManager.RESULT_ERROR_GENERIC_FAILURE
                Log.d(TAG, "SMS sent callback: phone=$phoneNumber, errorCode=${resultCode[0]}")
                latch.countDown()
            }
        }

        val filter = IntentFilter("com.example.wohenhao.SMS_SENT")
        val receiverFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.content.Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        context.registerReceiver(sentReceiver, filter, receiverFlags)

        try {
            // 构造 PendingIntent
            val sentIntent = PendingIntent.getBroadcast(
                context,
                phoneNumber.hashCode(), // 每个号码用不同 requestCode
                Intent("com.example.wohenhao.SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )?.let {
                // 如果已存在，克隆它
                PendingIntent.getBroadcast(
                    context,
                    phoneNumber.hashCode(),
                    Intent("com.example.wohenhao.SMS_SENT"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } ?: PendingIntent.getBroadcast(
                context,
                phoneNumber.hashCode(),
                Intent("com.example.wohenhao.SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )

            // 发送短信
            if (partsCount == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, arrayListOf(sentIntent), null)
            }

            Log.d(TAG, "SMS queued for $phoneNumber (waiting for callback...)")

            // 等待回调，最多等 timeoutSeconds
            val completed = latch.await(timeoutSeconds, TimeUnit.SECONDS)

            if (!completed) {
                Log.w(TAG, "SMS callback timeout for $phoneNumber (waited ${timeoutSeconds}s)")
            }

            val success = resultCode[0] == Activity.RESULT_OK
            if (!success) {
                Log.e(TAG, "SMS failed for $phoneNumber: ${getErrorDescription(resultCode[0])}")
            }
            return success

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException (likely no SMS permission): ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception sending SMS: ${e.message}")
            return false
        } finally {
            try {
                context.unregisterReceiver(sentReceiver)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * 获取自定义短信内容
     */
    private suspend fun getCustomMessage(
        context: Context,
        latitude: Double,
        longitude: Double,
        isSOS: Boolean,
        isCycling: Boolean = false
    ): String {
        return try {
            val database = AppDatabase.getInstance(context)
            val settingsDao = database.settingsDao()

            val defaultId = when {
                isCycling -> SmsTemplate.DEFAULT_CYCLING_ID
                isSOS -> SmsTemplate.DEFAULT_SOS_ID
                else -> SmsTemplate.DEFAULT_AUTO_HELP_ID
            }
            
            val settingsKey = when {
                isCycling -> AppSettings.KEY_CYCLING_TEMPLATE_ID
                isSOS -> AppSettings.KEY_SOS_TEMPLATE_ID
                else -> AppSettings.KEY_AUTO_HELP_TEMPLATE_ID
            }
            
            val selectedId = settingsDao.getString(settingsKey, defaultId)

            val template = database.smsTemplateDao().getTemplateById(selectedId)
                ?: database.smsTemplateDao().getTemplateById(defaultId)
                ?: (if (isCycling) SmsTemplate.getDefaultCyclingTemplate()
                    else if (isSOS) SmsTemplate.getDefaultSOSTemplate()
                    else SmsTemplate.getDefaultAutoHelpTemplate())

            val mapLink = "https://uri.amap.com/marker?position=$longitude,$latitude&coordinate=gaode"

            template.content
                .replace("{latitude}", latitude.toString())
                .replace("{longitude}", longitude.toString())
                .replace("{map_link}", mapLink)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get custom message: ${e.message}")
            getDefaultMessage(latitude, longitude, isSOS, isCycling)
        }
    }

    /**
     * 获取默认短信内容
     */
    private fun getDefaultMessage(
        latitude: Double,
        longitude: Double,
        isSOS: Boolean,
        isCycling: Boolean = false
    ): String {
        return when {
            isCycling -> {
                // 骑行求助
                buildString {
                    append("🚴 骑行求助！\n")
                    append("检测到3分钟未移动，可能存在异常\n")
                    append("纬度：$latitude\n")
                    append("经度：$longitude\n")
                    append("地图：https://uri.amap.com/marker?position=$longitude,$latitude&coordinate=gaode\n")
                    append("—— 我很好 App")
                }
            }
            isSOS -> {
                // SOS求助
                buildString {
                    append("🆘 紧急求助！\n")
                    append("纬度：$latitude\n")
                    append("经度：$longitude\n")
                    append("地图：https://uri.amap.com/marker?position=$longitude,$latitude&coordinate=gaode")
                }
            }
            else -> {
                // 自动求助
                buildString {
                    append("⚠️ 自动求助！\n")
                    append("我很好App检测到您超过设定时间未回应\n")
                    append("纬度：$latitude\n")
                    append("经度：$longitude\n")
                    append("地图：https://uri.amap.com/marker?position=$longitude,$latitude&coordinate=gaode")
                }
            }
        }
    }

    /**
     * 获取错误码的人类可读描述
     */
    fun getErrorDescription(errorCode: Int): String {
        return when (errorCode) {
            Activity.RESULT_OK -> "发送成功"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "发送失败：通用错误（可能是余额不足、号码格式错误或运营商限制）"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "发送失败：飞行模式或无线已关闭"
            SmsManager.RESULT_ERROR_NULL_PDU -> "发送失败：网络协议错误"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "发送失败：无信号或无服务"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "发送失败：发送条数超限（运营商通常限制24小时内不超过XX条）"
            else -> "发送失败（错误码: $errorCode）"
        }
    }
}
