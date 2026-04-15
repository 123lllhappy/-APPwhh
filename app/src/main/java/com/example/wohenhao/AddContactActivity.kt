package com.example.wohenhao

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.wohenhao.data.db.AppDatabase
import com.example.wohenhao.data.db.Contact
import com.example.wohenhao.databinding.ActivityAddContactBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 添加/编辑联系人页面
 */
class AddContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddContactBinding

    companion object {
        private const val TAG = "AddContactActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "AddContactActivity onCreate")

        try {
            binding = ActivityAddContactBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setupUI()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "页面加载异常", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupUI() {
        // 返回按钮
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // 保存按钮
        binding.btnSave.setOnClickListener {
            saveContact()
        }

        // 取消按钮
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun saveContact() {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val relation = binding.etRelation.text.toString().trim().ifEmpty { "其他" }
        val note = binding.etNote.text.toString().trim()

        // 验证
        if (name.isEmpty()) {
            binding.tilName.error = "请输入姓名"
            return
        }

        if (phone.isEmpty()) {
            binding.tilPhone.error = "请输入手机号"
            return
        }

        if (!isValidPhone(phone)) {
            binding.tilPhone.error = "手机号格式不正确"
            return
        }

        try {
            val contact = Contact(
                name = name,
                phone = phone,
                relation = relation,
                note = note
            )

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(applicationContext).contactDao().insert(contact)
                    }
                    Toast.makeText(this@AddContactActivity, "添加成功", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving contact", e)
                    Toast.makeText(this@AddContactActivity, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveContact", e)
            Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isValidPhone(phone: String): Boolean {
        // 简单验证：11位数字或带+86前缀
        val regex = Regex("^(\\+86)?1[3-9]\\d{9}$")
        return regex.matches(phone) || phone.matches(Regex("^1[3-9]\\d{9}$"))
    }
}