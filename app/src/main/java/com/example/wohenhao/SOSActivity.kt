package com.example.wohenhao

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.wohenhao.data.db.AppDatabase
import com.example.wohenhao.data.db.Contact
import com.example.wohenhao.data.db.MessageRecord
import com.example.wohenhao.databinding.ActivitySosBinding
import com.example.wohenhao.util.LocationHelper
import com.example.wohenhao.util.PermissionHelper
import com.example.wohenhao.util.SmsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SOS 紧急求助页面
 * 独立Activity，防止误触
 */
class SOSActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySosBinding

    companion object {
        private const val TAG = "SOSActivity"
    }

    private var contacts: List<Contact> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "SOSActivity onCreate")

        try {
            binding = ActivitySosBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setupUI()
            loadContacts()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "页面加载异常", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次进入 SOS 页面都检查权限（持续引导）
        PermissionHelper.checkAndRequestPermissions(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionHelper.handlePermissionResult(this, requestCode, permissions, grantResults)
    }

    private fun setupUI() {
        // 返回按钮
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // SOS 按钮
        binding.btnSos.setOnClickListener {
            showConfirmDialog()
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getInstance(applicationContext).contactDao()
                contacts = withContext(Dispatchers.IO) {
                    dao.getAllContactsSync()
                }

                if (contacts.isEmpty()) {
                    binding.tvContactCount.text = "请先添加紧急联系人"
                    binding.btnSos.isEnabled = false
                } else {
                    binding.tvContactCount.text = "将发送给 ${contacts.size} 位联系人"
                    binding.btnSos.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading contacts", e)
                binding.tvContactCount.text = "加载联系人失败"
                binding.btnSos.isEnabled = false
            }
        }
    }

    private fun showConfirmDialog() {
        if (contacts.isEmpty()) {
            Toast.makeText(this, getString(R.string.sos_no_contacts), Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.sos_confirm_title)
            .setMessage(getString(R.string.sos_confirm_message, contacts.size))
            .setPositiveButton(R.string.sos_confirm) { _, _ ->
                triggerSOS()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun triggerSOS() {
        if (contacts.isEmpty()) {
            Toast.makeText(this, R.string.sos_no_contacts, Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSos.isEnabled = false
        binding.tvStatus.text = getString(R.string.sos_sending)
        binding.tvStatus.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val locationHelper = LocationHelper(applicationContext)
                val locationResult = locationHelper.getCurrentLocation()

                val latitude = locationResult.getOrNull()?.latitude ?: 0.0
                val longitude = locationResult.getOrNull()?.longitude ?: 0.0

                val results = SmsHelper.sendEmergencyMessages(
                    applicationContext,
                    contacts,
                    latitude,
                    longitude,
                    isSOS = true
                )

                val successCount = results.values.count { it }

                // 记录
                withContext(Dispatchers.IO) {
                    val record = MessageRecord(
                        type = "sos",
                        latitude = latitude,
                        longitude = longitude,
                        recipients = contacts.joinToString(",") { it.phone },
                        status = if (successCount == contacts.size) "success" else "partial"
                    )
                    AppDatabase.getInstance(applicationContext).messageRecordDao().insert(record)
                }

                // 更新 UI
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = getString(R.string.sos_success, successCount)

                // 3秒后返回
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 3000)

            } catch (e: Exception) {
                Log.e(TAG, "Error triggering SOS", e)
                binding.progressBar.visibility = View.GONE
                binding.btnSos.isEnabled = true
                binding.tvStatus.text = getString(R.string.sos_failed)
            }
        }
    }
}