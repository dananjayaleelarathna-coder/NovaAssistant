package com.nova.assistant.intent

/**
 * Rule-based fallback that works with zero network access. Keeps the app usable
 * (torch, apps, alarms, volume, time, settings shortcuts) even in Offline Mode or
 * when the cloud AI call fails. Understands common Sinhala-English mixed phrasing
 * such as "torch eka on karanna" alongside plain English/Sinhala.
 */
object OfflineCommandParser {

    private val APP_KEYWORDS = mapOf(
        "youtube" to "YouTube", "whatsapp" to "WhatsApp", "chrome" to "Chrome",
        "gallery" to "Gallery", "camera" to "Camera", "messages" to "Messages",
        "phone" to "Phone", "settings" to "Settings", "instagram" to "Instagram",
        "play store" to "Play Store", "spotify" to "Spotify", "maps" to "Maps"
    )

    fun parse(rawInput: String): ParsedCommand {
        val text = rawInput.lowercase().trim()

        fun has(vararg words: String) = words.any { text.contains(it) }

        // Flashlight / torch — English + "eka on/off karanna" mixed style
        if (has("flashlight", "torch")) {
            return if (has(" off", "off karanna", "නිවන්න"))
                ParsedCommand(CommandIntent.ACTION_TORCH_OFF, rawText = rawInput)
            else
                ParsedCommand(CommandIntent.ACTION_TORCH_ON, rawText = rawInput)
        }

        // Camera
        if (has("take a photo", "take photo", "photo ekak")) {
            return ParsedCommand(CommandIntent.ACTION_TAKE_PHOTO, rawText = rawInput)
        }
        if (has("camera") && has("open", "on karanna")) {
            return ParsedCommand(CommandIntent.ACTION_OPEN_CAMERA, rawText = rawInput)
        }

        // Bluetooth / Wi-Fi
        if (has("bluetooth")) {
            return if (has("off", "nivanna"))
                ParsedCommand(CommandIntent.ACTION_BLUETOOTH_OFF, rawText = rawInput)
            else
                ParsedCommand(CommandIntent.ACTION_BLUETOOTH_ON, rawText = rawInput)
        }
        if (has("wifi", "wi-fi", "wi fi")) {
            return if (has("off"))
                ParsedCommand(CommandIntent.ACTION_WIFI_OFF, rawText = rawInput)
            else
                ParsedCommand(CommandIntent.ACTION_WIFI_ON, rawText = rawInput)
        }

        // Calls
        Regex("call (.+?)(\\.|$)").find(text)?.let { m ->
            val target = m.groupValues[1].trim()
            return if (target.matches(Regex("[0-9+ ]{5,}")))
                ParsedCommand(CommandIntent.ACTION_DIAL_NUMBER, mapOf("number" to target), true, rawInput)
            else
                ParsedCommand(CommandIntent.ACTION_CALL_CONTACT, mapOf("contact" to target), true, rawInput)
        }
        if (has("ta call ekak denna", "amma ta call")) {
            val contact = text.substringBefore(" ta call").trim()
            return ParsedCommand(CommandIntent.ACTION_CALL_CONTACT, mapOf("contact" to contact), true, rawInput)
        }

        // Alarms / Timers
        Regex("(?:set (?:an|a)? ?)?alarm (?:for|eka) ([0-9:apm ]+)").find(text)?.let { m ->
            return ParsedCommand(CommandIntent.ACTION_SET_ALARM, mapOf("time" to m.groupValues[1].trim()), rawText = rawInput)
        }
        if (has("cancel") && has("alarm")) {
            return ParsedCommand(CommandIntent.ACTION_CANCEL_ALARM, requiresConfirmation = true, rawText = rawInput)
        }
        Regex("timer (?:for )?([0-9]+) ?(minute|min|second|sec)").find(text)?.let { m ->
            return ParsedCommand(
                CommandIntent.ACTION_SET_TIMER,
                mapOf("amount" to m.groupValues[1], "unit" to m.groupValues[2]),
                rawText = rawInput
            )
        }

        // Media
        if (has("play music", "play song", "music play karanna")) return ParsedCommand(CommandIntent.ACTION_PLAY_MEDIA, rawText = rawInput)
        if (has("pause music", "pause song")) return ParsedCommand(CommandIntent.ACTION_PAUSE_MEDIA, rawText = rawInput)
        if (has("next song", "next track")) return ParsedCommand(CommandIntent.ACTION_NEXT_TRACK, rawText = rawInput)
        if (has("previous song", "previous track")) return ParsedCommand(CommandIntent.ACTION_PREV_TRACK, rawText = rawInput)

        // Volume
        if (has("volume up", "increase volume")) return ParsedCommand(CommandIntent.ACTION_VOLUME_UP, rawText = rawInput)
        if (has("volume down", "decrease volume")) return ParsedCommand(CommandIntent.ACTION_VOLUME_DOWN, rawText = rawInput)
        if (has("silent mode")) return ParsedCommand(CommandIntent.ACTION_SILENT_MODE, rawText = rawInput)
        if (has("vibrate mode")) return ParsedCommand(CommandIntent.ACTION_VIBRATE_MODE, rawText = rawInput)
        Regex("volume to ([0-9]{1,3})").find(text)?.let { m ->
            return ParsedCommand(CommandIntent.ACTION_SET_VOLUME, mapOf("percent" to m.groupValues[1]), rawText = rawInput)
        }

        // Battery / time / date
        if (has("battery")) return ParsedCommand(CommandIntent.ACTION_BATTERY_STATUS, rawText = rawInput)
        if (has("what time", "time eka kiyanna")) return ParsedCommand(CommandIntent.ACTION_TIME, rawText = rawInput)
        if (has("what date", "today's date", "what day")) return ParsedCommand(CommandIntent.ACTION_DATE, rawText = rawInput)

        // Screen actions
        if (has("screenshot")) return ParsedCommand(CommandIntent.ACTION_SCREENSHOT, rawText = rawInput)
        if (has("recent apps")) return ParsedCommand(CommandIntent.ACTION_RECENT_APPS, rawText = rawInput)
        if (has("go home") || text == "home") return ParsedCommand(CommandIntent.ACTION_GO_HOME, rawText = rawInput)
        if (has("go back")) return ParsedCommand(CommandIntent.ACTION_GO_BACK, rawText = rawInput)
        if (has("lock the screen", "lock screen")) return ParsedCommand(CommandIntent.ACTION_LOCK_SCREEN, rawText = rawInput)

        // Location / navigation
        if (has("navigate to")) {
            val dest = text.substringAfter("navigate to").trim()
            return ParsedCommand(CommandIntent.ACTION_NAVIGATE_TO, mapOf("destination" to dest), rawText = rawInput)
        }
        if (has("where am i")) return ParsedCommand(CommandIntent.ACTION_LOCATION_STATUS, rawText = rawInput)
        if (has("open google maps", "open maps")) return ParsedCommand(CommandIntent.ACTION_OPEN_MAPS, rawText = rawInput)

        // Settings shortcuts
        SETTINGS_KEYWORDS.forEach { (key, screen) ->
            if (has(key)) return ParsedCommand(CommandIntent.ACTION_OPEN_SETTINGS_SCREEN, mapOf("screen" to screen), rawText = rawInput)
        }

        // App open/launch — generic
        if (has("open", "launch", "eka open karanna")) {
            APP_KEYWORDS.forEach { (key, appName) ->
                if (has(key)) return ParsedCommand(CommandIntent.ACTION_OPEN_APP, mapOf("app" to appName), rawText = rawInput)
            }
            // Generic "open X" fallback — pass whatever text followed "open"
            val after = text.substringAfter("open", "").trim()
            if (after.isNotBlank()) {
                return ParsedCommand(CommandIntent.ACTION_OPEN_APP, mapOf("app" to after), rawText = rawInput)
            }
        }

        if (has("play store")) return ParsedCommand(CommandIntent.ACTION_OPEN_PLAY_STORE, rawText = rawInput)

        // Notifications
        if (has("notification", "any messages")) return ParsedCommand(CommandIntent.ACTION_READ_NOTIFICATIONS, rawText = rawInput)

        // Fallback: send to AI as general conversation/info request
        return ParsedCommand(CommandIntent.ACTION_GENERAL_AI, rawText = rawInput)
    }

    private val SETTINGS_KEYWORDS = mapOf(
        "display settings" to "display", "sound settings" to "sound",
        "battery settings" to "battery", "storage" to "storage",
        "app settings" to "apps", "location settings" to "location",
        "accessibility settings" to "accessibility", "security settings" to "security",
        "developer options" to "developer", "mobile data settings" to "data_usage",
        "airplane mode" to "airplane", "hotspot" to "hotspot", "nfc" to "nfc",
        "vpn settings" to "vpn"
    )
}
