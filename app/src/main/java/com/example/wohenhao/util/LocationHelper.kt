package com.example.wohenhao.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.wohenhao.R
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 定位工具类 - GMS 优先 + 自动备用方案
 *
 * 定位策略：
 * 1. 检测 GMS 是否可用（Google Play 服务）
 * 2. GMS 可用 → 使用 FusedLocationProviderClient（精度高，耗电低）
 * 3. GMS 不可用 → 自动切换到 Android 内置 LocationManager（所有设备都有）
 * 4. 首次检测到 GMS 不可用时，弹出一次提示（不再重复弹出）
 */
class LocationHelper(private val context: Context) {

    companion object {
        private const val TAG = "LocationHelper"
        private const val TIMEOUT_MS = 15000L
        private const val PREF_NAME = "location_pref"
        private const val KEY_GMS_NOTICE_SHOWN = "gms_notice_shown"
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationManager: LocationManager? = null
    private var prefs: SharedPreferences? = null

    // GMS 是否可用（由 init 初始化，后续只读）
    private var gmsAvailable: Boolean = true

    // 是否已在备用模式（GMS 不可用）
    private val isFallbackMode: Boolean
        get() = !gmsAvailable

    init {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // 检查 GMS 是否可用
        val gmsStatus = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)

        gmsAvailable = (gmsStatus == ConnectionResult.SUCCESS)

        Log.d(TAG, "GMS availability: $gmsAvailable (status=$gmsStatus)")

        if (gmsAvailable) {
            // GMS 可用 → 初始化 FusedLocationProviderClient
            try {
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                Log.d(TAG, "Using GMS FusedLocationProviderClient")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create FusedLocationProviderClient despite GMS available", e)
                gmsAvailable = false
            }
        }

        if (!gmsAvailable) {
            // GMS 不可用 → 初始化 Android 内置 LocationManager
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            Log.d(TAG, "Using fallback Android LocationManager")

            // 首次检测到 GMS 不可用 → 弹出一次提示
            postGmsFallbackNoticeIfNeeded()
        }
    }

    /**
     * GMS 不可用时，弹出一次提示（只在首次检测到时弹出）
     */
    private fun postGmsFallbackNoticeIfNeeded() {
        val alreadyShown = prefs?.getBoolean(KEY_GMS_NOTICE_SHOWN, false) ?: false
        if (alreadyShown) {
            Log.d(TAG, "GMS fallback notice already shown, skip")
            return
        }

        // 标记为已显示
        prefs?.edit()?.putBoolean(KEY_GMS_NOTICE_SHOWN, true)?.apply()

        // 延迟显示，等 App 初始化完成
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                android.app.AlertDialog.Builder(context)
                    .setTitle(R.string.gms_fallback_title)
                    .setMessage(R.string.gms_fallback_message)
                    .setPositiveButton(R.string.gms_fallback_confirm, null)
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show GMS fallback dialog", e)
            }
        }, 500)
    }

    /**
     * 检查定位权限
     */
    fun hasLocationPermission(): Boolean {
        return try {
            val fine = PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            fine || coarse
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check location permission", e)
            false
        }
    }

    /**
     * 获取当前位置
     * 自动选择 GMS 或备用定位方式
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Result<Location> {
        if (!hasLocationPermission()) {
            return Result.failure(Exception("没有定位权限"))
        }

        return if (gmsAvailable && fusedLocationClient != null) {
            // GMS 优先
            getGmsLocation()
        } else {
            // 备用定位（所有 Android 设备都有）
            getFallbackLocation()
        }
    }

    /**
     * GMS 定位（高精度）
     */
    @SuppressLint("MissingPermission")
    private suspend fun getGmsLocation(): Result<Location> {
        val client = fusedLocationClient ?: return getFallbackLocation()

        return try {
            // 先尝试最后已知位置（快）
            val lastLocation = suspendCancellableCoroutine<Location?> { continuation ->
                client.lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(location)
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
                continuation.invokeOnCancellation { }
            }

            if (lastLocation != null) {
                Log.d(TAG, "GMS last location: ${lastLocation.latitude}, ${lastLocation.longitude}")
                Result.success(lastLocation)
            } else {
                // 没有缓存，实时获取
                getFreshGmsLocation(client)
            }
        } catch (e: Exception) {
            Log.e(TAG, "GMS location failed, fallback to native: ${e.message}")
            // GMS 调用失败，自动切换备用方案
            getFallbackLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshGmsLocation(
        client: FusedLocationProviderClient
    ): Result<Location> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val locationRequest = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    1000L
                ).apply {
                    setWaitForAccurateLocation(true)
                    setMinUpdateIntervalMillis(500L)
                    setMaxUpdates(1)
                }.build()

                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        try {
                            client.removeLocationUpdates(this)
                        } catch (e: Exception) { /* ignore */ }

                        val loc = result.lastLocation
                        if (loc != null) {
                            Log.d(TAG, "GMS fresh location: ${loc.latitude}, ${loc.longitude}")
                            continuation.resume(Result.success(loc))
                        } else {
                            continuation.resume(Result.failure(Exception("无法获取位置")))
                        }
                    }
                }

                client.requestLocationUpdates(
                    locationRequest,
                    callback,
                    Looper.getMainLooper()
                )

                // 超时
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        client.removeLocationUpdates(callback)
                    } catch (e: Exception) { /* ignore */ }
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("定位超时")))
                    }
                }, TIMEOUT_MS)

                continuation.invokeOnCancellation {
                    try {
                        client.removeLocationUpdates(callback)
                    } catch (e: Exception) { /* ignore */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "GMS fresh location request failed", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * 备用定位（Android 内置 LocationManager）
     * 所有 Android 设备都有，不依赖 GMS
     */
    @SuppressLint("MissingPermission")
    private suspend fun getFallbackLocation(): Result<Location> {
        val lm = locationManager ?: return Result.failure(Exception("定位服务不可用"))

        // 获取可用的定位方式（GPS 优先）
        val providers = try {
            lm.getProviders(true) // true = 只获取启用的 providers
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get providers", e)
            return Result.failure(Exception("无法获取定位服务"))
        }

        Log.d(TAG, "Available providers: $providers")

        // 尝试每个 provider
        for (providerName in providers) {
            val result = getLocationFromProvider(lm, providerName)
            if (result.isSuccess) {
                Log.d(TAG, "Fallback location from $providerName: ${result.getOrNull()?.latitude}")
                return result
            }
        }

        return Result.failure(Exception("所有定位方式均不可用"))
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocationFromProvider(
        lm: LocationManager,
        provider: String
    ): Result<Location> {
        return suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    try {
                        lm.removeUpdates(this)
                    } catch (e: Exception) { /* ignore */ }
                    if (continuation.isActive) {
                        Log.d(TAG, "Fallback got location from $provider: ${location.latitude}")
                        continuation.resume(Result.success(location))
                    }
                }

                @Deprecated("Deprecated in API level 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                    // Android 11+ 不再调用此方法
                }

                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            try {
                // 先尝试获取最后已知位置
                val lastKnown = lm.getLastKnownLocation(provider)
                if (lastKnown != null) {
                    Log.d(TAG, "Fallback last known from $provider: ${lastKnown.latitude}")
                    continuation.resume(Result.success(lastKnown))
                    return@suspendCancellableCoroutine
                }

                // 请求实时更新
                lm.requestLocationUpdates(
                    provider,
                    0L,      // 立即获取
                    0f,      // 立即获取
                    listener,
                    Looper.getMainLooper()
                )

                // 超时处理
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        lm.removeUpdates(listener)
                    } catch (e: Exception) { /* ignore */ }
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("备用定位超时（provider=$provider）")))
                    }
                }, TIMEOUT_MS)

                continuation.invokeOnCancellation {
                    try {
                        lm.removeUpdates(listener)
                    } catch (e: Exception) { /* ignore */ }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to request location from $provider", e)
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }

    /**
     * 生成高德地图链接
     */
    fun getAmapLocationUrl(latitude: Double, longitude: Double): String {
        return "https://uri.amap.com/marker?position=$longitude,$latitude&coordinate=gaode"
    }
}
