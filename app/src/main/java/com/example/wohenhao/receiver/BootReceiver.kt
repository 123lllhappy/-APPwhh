package com.example.wohenhao.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.example.wohenhao.data.db.AppDatabase
import com.example.wohenhao.data.db.AppSettings
import com.example.wohenhao.service.GuardianService

/**
 * 开机自启动接收器
 * 接收 BOOT_COMPLETED 广播，启动守护服务
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"

        // 使用 SharedPreferences 来存储开机自启动设置（避免在 BroadcastReceiver 中使用协程）
        private const val PREFS_NAME = "boot_settings"

        fun isBootAutoStartEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(AppSettings.KEY_BOOT_AUTO_START, true)
        }

        fun setBootAutoStart(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(AppSettings.KEY_BOOT_AUTO_START, enabled)
                .apply()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d(TAG, "Boot completed received")

        try {
            // 直接使用 SharedPreferences 检查设置（避免协程问题）
            val bootAutoStart = isBootAutoStartEnabled(context)

            Log.d(TAG, "bootAutoStart=$bootAutoStart")

            if (bootAutoStart) {
                // 启动守护服务
                GuardianService.start(context)
                Log.d(TAG, "GuardianService started from boot")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle boot", e)
        }
    }
}