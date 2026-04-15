package com.example.wohenhao.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用设置实体
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val key: String,
    val value: String
) {
    companion object {
        // 守护模式
        const val KEY_BOOT_AUTO_START = "boot_auto_start"
        const val KEY_NEED_CHECK_IN = "need_check_in"
        const val KEY_TIMEOUT_HOURS = "timeout_hours"          // 触发超时（小时）：0=15秒测试, 1=1h, 12, 24, 48, 72
        const val KEY_CHECK_INTERVAL_SECONDS = "check_interval_seconds"  // 检测频率（秒）：15 / 3600 / 43200

        // 骑行守护
        const val KEY_CYCLING_MODE = "cycling_mode"              // 是否开启骑行守护
        const val KEY_CYCLING_START_TIME = "cycling_start_time"  // 骑行开始时间
        const val KEY_CYCLING_TIMEOUT_MINUTES = "cycling_timeout_minutes"  // 未移动超时（分钟）：默认3
        const val KEY_LAST_MOVING_TIME = "last_moving_time"      // 最后移动时间
        const val KEY_LAST_CYCLING_LOCATION_LAT = "cycling_lat"  // 骑行最后位置-纬度
        const val KEY_LAST_CYCLING_LOCATION_LNG = "cycling_lng"  // 骑行最后位置-经度

        // 定时汇报
        const val KEY_REPORT_ENABLED = "report_enabled"
        const val KEY_REPORT_INTERVAL_HOURS = "report_interval_hours"

        // 短信模板
        const val KEY_SOS_TEMPLATE_ID = "sos_template_id"
        const val KEY_AUTO_HELP_TEMPLATE_ID = "auto_help_template_id"
        const val KEY_CYCLING_TEMPLATE_ID = "cycling_template_id"

        // 其他
        const val KEY_ATTACH_MAP_LINK = "attach_map_link"
        const val KEY_LAST_OPEN_TIME = "last_open_time"
        const val KEY_LAST_CHECK_IN_TIME = "last_check_in_time" // 打卡时间（判断今天是否已打卡）

        // 默认值
        const val DEFAULT_TIMEOUT_HOURS = 12  // 默认12小时
        const val DEFAULT_CHECK_INTERVAL_SECONDS = 3600  // 默认1小时
        const val DEFAULT_REPORT_INTERVAL_HOURS = 24

        // 图片设置
        const val KEY_FRONT_IMAGE_MODE = "front_image_mode"          // 正面图片模式："random" 随机, "sequence" 顺序
        const val KEY_BACK_IMAGE_MODE = "back_image_mode"            // 背面图片模式："random" 随机, "sequence" 顺序
        const val KEY_FRONT_CUSTOM_IMAGES = "front_custom_images"    // 正面自定义图片路径列表（逗号分隔）
        const val KEY_BACK_CUSTOM_IMAGES = "back_custom_images"      // 背面自定义图片路径列表（逗号分隔）
        const val KEY_FRONT_USE_CUSTOM = "front_use_custom"          // 是否使用正面自定义图片
        const val KEY_BACK_USE_CUSTOM = "back_use_custom"            // 是否使用背面自定义图片
        
        const val KEY_CHECKIN_IMAGES_A = "checkin_images_a"          // 图片库A（逗号分隔）
        const val KEY_IMAGE_MODE_A = "image_mode_a"                  // 图片库A显示模式："random" 随机, "sequence" 顺序
        const val KEY_CHECKIN_IMAGES_B = "checkin_images_b"          // 图片库B（逗号分隔）
        const val KEY_IMAGE_MODE_B = "image_mode_b"                  // 图片库B显示模式："random" 随机, "sequence" 顺序
        const val KEY_ACTIVE_IMAGE_LIBRARY = "active_image_library"  // 当前使用的图库："A" 或 "B"

        // 默认值
        const val DEFAULT_IMAGE_MODE = "random"
    }
}