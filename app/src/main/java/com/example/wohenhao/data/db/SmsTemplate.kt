package com.example.wohenhao.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_templates")
data class SmsTemplate(
    @PrimaryKey val id: String,
    val name: String,
    val content: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_SOS_ID = "default_sos"
        const val DEFAULT_AUTO_HELP_ID = "default_auto_help"
        const val DEFAULT_CYCLING_ID = "default_cycling"
        
        fun getDefaultSOSTemplate(): SmsTemplate {
            return SmsTemplate(
                id = DEFAULT_SOS_ID,
                name = "SOS紧急求助",
                content = "🆘 紧急求助！\n我的当前位置：\n纬度：{latitude}\n经度：{longitude}\n地图链接：{map_link}",
                isDefault = true
            )
        }
        
        fun getDefaultAutoHelpTemplate(): SmsTemplate {
            return SmsTemplate(
                id = DEFAULT_AUTO_HELP_ID,
                name = "超时自动求助",
                content = "⚠️ 自动求助！\n我很好APP检测到用户超过设定时间未回应，发送用户当前位置：\n纬度：{latitude}\n经度：{longitude}\n地图链接：{map_link}",
                isDefault = true
            )
        }
        
        fun getDefaultCyclingTemplate(): SmsTemplate {
            return SmsTemplate(
                id = DEFAULT_CYCLING_ID,
                name = "骑行守护求助",
                content = "🚴 骑行求助！\n检测到3分钟未移动，可能存在异常\n最后位置：\n纬度：{latitude}\n经度：{longitude}\n地图链接：{map_link}\n—— 我很好 App",
                isDefault = true
            )
        }
    }
}