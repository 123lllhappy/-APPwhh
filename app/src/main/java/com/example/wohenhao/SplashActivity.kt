package com.example.wohenhao

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.wohenhao.data.db.AppDatabase
import com.example.wohenhao.service.GuardianService

/**
 * 启动页 - 负责初始化和权限检查
 * 设计原则：绝不在这里做可能崩溃的操作
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_DELAY_MS = 1000L // 1秒启动页
    }

    // 需要申请的权限
    private val requiredPermissions = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        Log.d(TAG, "Permission result: allGranted=$allGranted")

        if (allGranted) {
            // 权限授予，进入主页面
            goToMain()
        } else {
            // 部分权限被拒绝，仍然可以进入，但部分功能受限
            Toast.makeText(
                this,
                getString(R.string.permission_denied),
                Toast.LENGTH_LONG
            ).show()
            goToMain()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Log.d(TAG, "SplashActivity onCreate")

        // 在后台线程初始化数据库
        Thread {
            try {
                Log.d(TAG, "Initializing database...")
                AppDatabase.getInstance(applicationContext)
                Log.d(TAG, "Database initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize database", e)
            }
        }.start()

        // 检查权限
        checkPermissions()
    }

    /**
     * 检查并申请权限
     */
    private fun checkPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            Log.d(TAG, "All permissions already granted")
            // 权限已有，延迟进入主页面
            Handler(Looper.getMainLooper()).postDelayed({
                goToMain()
            }, SPLASH_DELAY_MS)
        } else {
            Log.d(TAG, "Requesting ${permissionsToRequest.size} permissions")
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    /**
     * 进入主页面
     */
    private fun goToMain() {
        try {
            // 启动守护服务
            GuardianService.start(this)

            // 跳转到主页面
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to go to main", e)
            // 尝试重新初始化
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "SplashActivity onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "SplashActivity onPause")
    }
}