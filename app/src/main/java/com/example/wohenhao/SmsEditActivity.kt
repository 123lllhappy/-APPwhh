package com.example.wohenhao

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wohenhao.data.db.AppDatabase
import com.example.wohenhao.data.db.SmsTemplate
import kotlinx.coroutines.launch
import java.util.*

class SmsEditActivity : AppCompatActivity() {
    
    private lateinit var database: AppDatabase
    private lateinit var templatesRecyclerView: RecyclerView
    private lateinit var templateAdapter: SmsTemplateAdapter
    private lateinit var addTemplateButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_edit)
        
        database = AppDatabase.getInstance(this)
        
        initViews()
        setupRecyclerView()
        loadTemplates()
        initDefaultTemplates()
    }
    
    private fun initViews() {
        templatesRecyclerView = findViewById(R.id.recyclerViewTemplates)
        addTemplateButton = findViewById(R.id.btnAddTemplate)
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        addTemplateButton.setOnClickListener {
            showEditTemplateDialog(null)
        }
    }
    
    private fun setupRecyclerView() {
        templateAdapter = SmsTemplateAdapter(
            onEditClick = { template -> showEditTemplateDialog(template) },
            onDeleteClick = { template -> deleteTemplate(template) }
        )
        
        templatesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SmsEditActivity)
            adapter = templateAdapter
        }
    }
    
    private fun loadTemplates() {
        lifecycleScope.launch {
            database.smsTemplateDao().getAllTemplates().collect { templates ->
                templateAdapter.submitList(templates)
            }
        }
    }
    
    private fun initDefaultTemplates() {
        lifecycleScope.launch {
            // 检查并插入SOS模板
            val sosTemplate = database.smsTemplateDao().getTemplateById(SmsTemplate.DEFAULT_SOS_ID)
            if (sosTemplate == null) {
                database.smsTemplateDao().insertTemplate(SmsTemplate.getDefaultSOSTemplate())
            }
            
            // 检查并插入自动求助模板
            val autoHelpTemplate = database.smsTemplateDao().getTemplateById(SmsTemplate.DEFAULT_AUTO_HELP_ID)
            if (autoHelpTemplate == null) {
                database.smsTemplateDao().insertTemplate(SmsTemplate.getDefaultAutoHelpTemplate())
            }
            
            // 检查并插入骑行模板
            val cyclingTemplate = database.smsTemplateDao().getTemplateById(SmsTemplate.DEFAULT_CYCLING_ID)
            if (cyclingTemplate == null) {
                database.smsTemplateDao().insertTemplate(SmsTemplate.getDefaultCyclingTemplate())
            }
        }
    }
    
    private fun showEditTemplateDialog(template: SmsTemplate?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_sms_template, null)
        val editName = dialogView.findViewById<EditText>(R.id.editTemplateName)
        val editContent = dialogView.findViewById<EditText>(R.id.editTemplateContent)
        val textHint = dialogView.findViewById<TextView>(R.id.textVariableHint)
        
        template?.let {
            editName.setText(it.name)
            editContent.setText(it.content)
        }
        
        textHint.text = "可用变量：{latitude} {longitude} {map_link}"
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (template == null) "添加短信模板" else "编辑短信模板")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = editName.text.toString().trim()
                val content = editContent.text.toString().trim()
                
                if (name.isNotEmpty() && content.isNotEmpty()) {
                    saveTemplate(template, name, content)
                } else {
                    Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun saveTemplate(existingTemplate: SmsTemplate?, name: String, content: String) {
        lifecycleScope.launch {
            try {
                if (existingTemplate != null) {
                    // 更新现有模板
                    val updatedTemplate = existingTemplate.copy(
                        name = name,
                        content = content,
                        updatedAt = System.currentTimeMillis()
                    )
                    database.smsTemplateDao().updateTemplate(updatedTemplate)
                } else {
                    // 创建新模板
                    val newTemplate = SmsTemplate(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        content = content,
                        isDefault = false
                    )
                    database.smsTemplateDao().insertTemplate(newTemplate)
                }
                Toast.makeText(this@SmsEditActivity, "保存成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SmsEditActivity, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun deleteTemplate(template: SmsTemplate) {
        if (template.isDefault) {
            Toast.makeText(this, "默认模板不能删除", Toast.LENGTH_SHORT).show()
            return
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("删除模板")
            .setMessage("确定要删除模板「${template.name}」吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    try {
                        database.smsTemplateDao().deleteTemplate(template)
                        Toast.makeText(this@SmsEditActivity, "删除成功", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@SmsEditActivity, "删除失败：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}