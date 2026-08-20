package com.nova.assistant.intent

/**
 * The extensible internal command set. New commands = new enum entry + one branch in
 * IntentExecutor + (optionally) a pattern in OfflineCommandParser. Nothing else changes.
 */
enum class CommandIntent {
    ACTION_TORCH_ON, ACTION_TORCH_OFF,
    ACTION_OPEN_CAMERA, ACTION_TAKE_PHOTO,
    ACTION_OPEN_APP, ACTION_OPEN_PLAY_STORE,
    ACTION_CALL_CONTACT, ACTION_DIAL_NUMBER,
    ACTION_SEND_MESSAGE,
    ACTION_SET_ALARM, ACTION_SET_TIMER, ACTION_CANCEL_ALARM,
    ACTION_PLAY_MEDIA, ACTION_PAUSE_MEDIA, ACTION_NEXT_TRACK, ACTION_PREV_TRACK,
    ACTION_VOLUME_UP, ACTION_VOLUME_DOWN, ACTION_SET_VOLUME, ACTION_SILENT_MODE, ACTION_VIBRATE_MODE,
    ACTION_OPEN_SETTINGS_SCREEN,
    ACTION_BLUETOOTH_ON, ACTION_BLUETOOTH_OFF,
    ACTION_WIFI_ON, ACTION_WIFI_OFF,
    ACTION_BATTERY_STATUS, ACTION_BATTERY_SAVER_ON,
    ACTION_TIME, ACTION_DATE,
    ACTION_SCREENSHOT, ACTION_RECENT_APPS, ACTION_GO_HOME, ACTION_GO_BACK, ACTION_LOCK_SCREEN,
    ACTION_LOCATION_STATUS, ACTION_OPEN_MAPS, ACTION_NAVIGATE_TO,
    ACTION_READ_NOTIFICATIONS,
    ACTION_GENERAL_AI,
    ACTION_UNKNOWN
}

data class ParsedCommand(
    val intent: CommandIntent,
    val params: Map<String, String> = emptyMap(),
    val requiresConfirmation: Boolean = false,
    val rawText: String = ""
)

/** Actions considered sensitive enough to require an explicit yes/no from the user. */
val SENSITIVE_INTENTS = setOf(
    CommandIntent.ACTION_CALL_CONTACT,
    CommandIntent.ACTION_DIAL_NUMBER,
    CommandIntent.ACTION_SEND_MESSAGE,
    CommandIntent.ACTION_CANCEL_ALARM
)
