package com.example.wohenhao

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wohenhao.data.db.SmsTemplate

class SmsTemplateAdapter(
    private val onEditClick: (SmsTemplate) -> Unit,
    private val onDeleteClick: (SmsTemplate) -> Unit
) : ListAdapter<SmsTemplate, SmsTemplateAdapter.TemplateViewHolder>(TemplateDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms_template, parent, false)
        return TemplateViewHolder(view)
    }

    override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TemplateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.textTemplateName)
        private val textContent: TextView = itemView.findViewById(R.id.textTemplateContent)
        private val textDefault: TextView = itemView.findViewById(R.id.textDefaultLabel)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditTemplate)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteTemplate)

        fun bind(template: SmsTemplate) {
            textName.text = template.name
            textContent.text = template.content
            
            // 显示默认标签
            textDefault.visibility = if (template.isDefault) View.VISIBLE else View.GONE
            
            // 默认模板不能删除
            btnDelete.visibility = if (template.isDefault) View.GONE else View.VISIBLE
            
            btnEdit.setOnClickListener { onEditClick(template) }
            btnDelete.setOnClickListener { onDeleteClick(template) }
            
            itemView.setOnClickListener { onEditClick(template) }
        }
    }

    private class TemplateDiffCallback : DiffUtil.ItemCallback<SmsTemplate>() {
        override fun areItemsTheSame(oldItem: SmsTemplate, newItem: SmsTemplate): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SmsTemplate, newItem: SmsTemplate): Boolean {
            return oldItem == newItem
        }
    }
}