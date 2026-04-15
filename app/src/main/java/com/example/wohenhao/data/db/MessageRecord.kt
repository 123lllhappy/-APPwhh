package com.example.wohenhao.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 消息记录实体
 */
@Entity(tableName = "message_records")
data class MessageRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,           // "sos" | "auto_help" | "report"
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val recipients: String,     // 收件人列表，逗号分隔
    val status: String          // "success" | "failed"
)