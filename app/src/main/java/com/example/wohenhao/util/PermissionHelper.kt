package com.example.wohenhao.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wohenhao.R

/**
 * 权限引导工具类
 *
 * 核心设计：短信和定位分开引导，互不阻塞
 * 短信：必须，用户体验降级但仍可用（引导到设置开启）
 * 定位：必须，守护的核心，优先引导「始终允许」
 */
object PermissionHelper {

    private const val TAG = "PermissionHelper"

    // 前台定位权限
    val FOREGROUND_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // 短信权限（单独申请）
    val SMS_PERMISSION = arrayOf(Manifest.permission.SEND_SMS)

    // 全部前台权限
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.SEND_SMS
    )

    // 请求码
    const val REQUEST_CODE_PERMISSIONS = 1001        // 定位 + 短信
    const val REQUEST_CODE_BACKGROUND_LOCATION = 1002 // 后台定位
    const val REQUEST_CODE_SMS_ONLY = 1003           // 仅短信（补申请）

    // ============================================================
    // 权限检查
    // ============================================================

    fun hasForegroundLocation(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun needsBackgroundLocationGuide(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return hasForegroundLocation(context) && !hasBackgroundLocation(context)
    }

    fun hasAllPermissions(context: Context): Boolean {
        return hasForegroundLocation(context)
            && hasSmsPermission(context)
            && hasBackgroundLocation(context)
    }

    /**
     * 核心入口：检查并引导权限
     * 短信和定位分开处理，一次只弹一个，避免用户困惑
     */
    fun checkAndRequestPermissions(activity: Activity, forceShow: Boolean = false): Boolean {
        // 优先处理短信权限（短信可补申请，不阻断流程）
        if (!hasSmsPermission(activity)) {
            guideSmsPermission(activity, forceShow)
            return false
        }

        // 处理前台定位
        if (!hasForegroundLocation(activity)) {
            guideForegroundLocation(activity, forceShow)
            return false
        }

        // 处理后台定位
        if (needsBackgroundLocationGuide(activity)) {
            guideBackgroundLocation(activity, forceShow)
            return false
        }

        return true
    }

    // ============================================================
    // 短信权限引导
    // ============================================================

    /**
     * 引导短信权限：分层处理
     */
    private fun guideSmsPermission(activity: Activity, forceShow: Boolean) {
        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.SEND_SMS
        )

        if (shouldShowRationale) {
            // 用户之前拒绝过 → 弹严重性说明框
            AlertDialog.Builder(activity)
                .setTitle("短信权限缺失")
                .setMessage("「我很好」需要短信权限才能发送求助信息给紧急联系人。\n\n请前往设置开启，否则求助短信无法发送。")
                .setPositiveButton("去设置") { _, _ ->
                    openAppSettings(activity)
                }
                .setNegativeButton("暂不开启", null)
                .setCancelable(true)
                .show()
        } else {
            // 首次请求
            ActivityCompat.requestPermissions(
                activity,
                SMS_PERMISSION,
                REQUEST_CODE_SMS_ONLY
            )
        }
    }

    // ============================================================
    // 前台定位权限引导
    // ============================================================

    private fun guideForegroundLocation(activity: Activity, forceShow: Boolean) {
        val hasDenied = FOREGROUND_PERMISSIONS.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

        if (hasDenied || forceShow) {
            AlertDialog.Builder(activity)
                .setTitle("需要定位权限")
                .setMessage("「我很好」需要定位权限来获取您的当前位置，在求助或自动守护时发送给紧急联系人。")
                .setPositiveButton("开启定位") { _, _ ->
                    ActivityCompat.requestPermissions(
                        activity,
                        FOREGROUND_PERMISSIONS,
                        REQUEST_CODE_PERMISSIONS
                    )
                }
                .setNegativeButton("稍后", null)
                .setCancelable(true)
                .show()
        } else {
            ActivityCompat.requestPermissions(
                activity,
                FOREGROUND_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    // ============================================================
    // 后台定位权限引导
    // ============================================================

    private fun guideBackgroundLocation(activity: Activity, forceShow: Boolean) {
        val hasDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )

        val (title, message, btnText) = if (hasDenied) {
            Triple(
                "需要「始终允许」定位",
                "「我很好」的守护功能需要在后台持续获取位置，这样即使 App 被关闭，也能发送准确的位置信息。\n\n请在设置中将定位权限改为「始终允许」。",
                "去设置"
            )
        } else {
            Triple(
                "开启后台定位",
                "为了在 App 关闭后仍能守护您，请允许「始终」使用位置信息。",
                "去设置"
            )
        }

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(btnText) { _, _ ->
                openLocationSettings(activity)
            }
            .setNegativeButton("暂不需要", null)
            .setCancelable(true)
            .show()
    }

    // ============================================================
    // 权限申请结果处理
    // ============================================================

    fun handlePermissionResult(
        activity: Activity,
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_PERMISSIONS -> {
                // 定位结果：申请前台定位成功后，检查是否需要引导后台定位
                val locationGranted = permissions.indices.any { i ->
                    permissions[i] in listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) && grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED
                }

                if (locationGranted) {
                    Log.d(TAG, "Foreground location granted")
                    // 延迟引导后台定位（等系统弹窗完全关闭）
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            // 短信如果还没通过，也一起检查
                            if (!hasSmsPermission(activity)) {
                                guideSmsPermission(activity, forceShow = false)
                            } else {
                                checkAndRequestPermissions(activity, forceShow = false)
                            }
                        }
                    }, 500)
                } else {
                    Log.d(TAG, "Foreground location denied")
                }
            }

            REQUEST_CODE_SMS_ONLY -> {
                // 短信补申请结果
                val smsGranted = grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "SMS permission result: $smsGranted")

                if (!smsGranted) {
                    // 短信被拒 → 弹引导去设置（不重复骚扰）
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            guideSmsPermission(activity, forceShow = true)
                        }
                    }, 300)
                }
            }

            REQUEST_CODE_BACKGROUND_LOCATION -> {
                Log.d(TAG, "Background location result handled")
            }
        }
    }

    // ============================================================
    // 跳转设置
    // ============================================================

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
        }
    }

    /**
     * 跳转到 App 的定位权限设置页面
     * Android 10+ 上，这里可以选择「始终允许」
     */
    fun openLocationSettings(context: Context) {
        try {
            // Android 10+ 可以直接跳到 App 的定位权限详情页
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened location settings page")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open location settings, fallback to app settings", e)
            openAppSettings(context)
        }
    }
}
