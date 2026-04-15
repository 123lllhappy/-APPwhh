package com.example.wohenhao.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 紧急联系人实体
 */
@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String,
    val relation: String = "其他",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)