package com.example.wohenhao

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.wohenhao.data.db.AppDatabase
import com.example.wohenhao.data.db.MessageRecord
import com.example.wohenhao.databinding.ActivityHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 历史记录页面
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    companion object {
        private const val TAG = "HistoryActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "HistoryActivity onCreate")

        try {
            binding = ActivityHistoryBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setupUI()
            loadHistory()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "页面加载异常", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getInstance(applicationContext).messageRecordDao()
                val records = withContext(Dispatchers.IO) {
                    dao.getRecentRecords(50)
                }

                // 观察 LiveData
                dao.getRecentRecords(50).observe(this@HistoryActivity) { records ->
                    updateUI(records)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading history", e)
            }
        }
    }

    private fun updateUI(records: List<MessageRecord>) {
        try {
            if (records.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvHistoryList.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.tvHistoryList.visibility = View.VISIBLE

                val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

                val text = records.take(20).joinToString("\n\n") { record ->
                    val time = dateFormat.format(Date(record.timestamp))
                    val type = when (record.type) {
                        "sos" -> "🆘 SOS求助"
                        "auto_help" -> "⚠️ 自动求助"
                        "report" -> "📍 位置汇报"
                        else -> "未知"
                    }
                    val status = if (record.status == "success") "✅" else "⚠️"
                    val location = if (record.latitude != 0.0 || record.longitude != 0.0) {
                        "位置: ${String.format("%.4f", record.latitude)}, ${String.format("%.4f", record.longitude)}"
                    } else {
                        "位置: 未知"
                    }

                    "$time  $type $status\n$location\n发送给: ${record.recipients}"
                }

                binding.tvHistoryList.text = text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI", e)
        }
    }
}