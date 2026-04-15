package com.example.wohenhao.util

/**
 * 常量定义
 */
object Constants {

    // Intent Keys
    const val EXTRA_CONTACT_ID = "contact_id"
    const val EXTRA_CONTACT_NAME = "contact_name"
    const val EXTRA_CONTACT_PHONE = "contact_phone"
    const val EXTRA_CONTACT_RELATION = "contact_relation"
    const val EXTRA_CONTACT_NOTE = "contact_note"

    // Request Codes
    const val REQUEST_PERMISSIONS = 1001
    const val REQUEST_BACKGROUND_LOCATION = 1002

    // Worker Tags
    const val WORKER_GUARDIAN = "guardian_worker"
    const val WORKER_REPORT = "report_worker"

    // Time Constants
    const val MILLIS_PER_HOUR = 60 * 60 * 1000L
    const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR

    // Notification IDs
    const val NOTIFICATION_ID_SERVICE = 1001
    const val NOTIFICATION_ID_ALERT = 1002

    // SharedPreferences
    const val PREFS_NAME = "wohenhao_prefs"
    const val PREF_FIRST_LAUNCH = "first_launch"
    const val PREF_PERMISSION_ASKED = "permission_asked"

    // Timeout Options (hours)
    val TIMEOUT_OPTIONS = listOf(12, 24, 48, 72)

    // Message Types
    const val MSG_TYPE_SOS = "sos"
    const val MSG_TYPE_AUTO_HELP = "auto_help"
    const val MSG_TYPE_AUTO_HELP_FAILED = "auto_help_failed"
    const val MSG_TYPE_REPORT = "report"
}