package com.nova.assistant.intent

import android.content.Context
import android.media.AudioManager
import android.text.format.DateFormat
import com.nova.assistant.device.AlarmController
import com.nova.assistant.device.AppLauncher
import com.nova.assistant.device.CallController
import com.nova.assistant.device.ControlResult
import com.nova.assistant.device.DeviceController
import com.nova.assistant.device.MediaController
import com.nova.assistant.device.MessageController
import java.util.Calendar
import java.util.Locale

/** Result of executing a command — what to say, and whether it still needs user confirmation. */
data class ExecutionResult(
    val spokenReply: String,
    val needsConfirmation: Boolean = false,
    val confirmationPrompt: String? = null,
    val statusLabel: String? = null // e.g. "✓ Flashlight ON" shown in the conversation UI
)

class IntentExecutor(private val context: Context) {

    private val device = DeviceController(context)
    private val appLauncher = AppLauncher(context)
    private val callController = CallController(context)
    private val messageController = MessageController(context)
    private val alarmController = AlarmController(context)
    private val mediaController = MediaController(context)

    /**
     * Executes non-sensitive commands immediately. For SENSITIVE_INTENTS, callers must first
     * present confirmationPrompt to the user and only call executeConfirmed() on a "yes".
     */
    fun execute(command: ParsedCommand): ExecutionResult {
        if (command.intent in SENSITIVE_INTENTS) {
            return buildConfirmationRequest(command)
        }
        return dispatch(command)
    }

    fun executeConfirmed(command: ParsedCommand): ExecutionResult = dispatch(command)

    private fun buildConfirmationRequest(command: ParsedCommand): ExecutionResult {
        val prompt = when (command.intent) {
            CommandIntent.ACTION_CALL_CONTACT -> {
                val name = command.params["contact"].orEmpty()
                val match = callController.findContact(name)
                if (match != null) "I found ${match.name}'s number. Should I call?"
                else "I couldn't find a contact named $name. Want me to open the dialer instead?"
            }
            CommandIntent.ACTION_DIAL_NUMBER -> "Should I call ${command.params["number"]}?"
            CommandIntent.ACTION_SEND_MESSAGE -> "Ready to send that message to ${command.params["contact"]}. Send it?"
            CommandIntent.ACTION_CANCEL_ALARM -> "Cancel your alarm? This will open the clock app."
            else -> "Are you sure?"
        }
        return ExecutionResult(spokenReply = prompt, needsConfirmation = true, confirmationPrompt = prompt)
    }

    private fun dispatch(command: ParsedCommand): ExecutionResult {
        return when (command.intent) {
            CommandIntent.ACTION_TORCH_ON -> fromControlResult(device.setTorch(true), "Flashlight ON")
            CommandIntent.ACTION_TORCH_OFF -> fromControlResult(device.setTorch(false), "Flashlight OFF")

            CommandIntent.ACTION_OPEN_CAMERA, CommandIntent.ACTION_TAKE_PHOTO -> {
                val intent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ExecutionResult("Opening the camera.", statusLabel = "✓ Camera opened")
            }

            CommandIntent.ACTION_OPEN_APP -> {
                val appName = command.params["app"].orEmpty()
                val app = appLauncher.findApp(appName)
                if (app != null && appLauncher.launch(app)) {
                    ExecutionResult("Opening ${app.label}.", statusLabel = "✓ ${app.label} opened")
                } else {
                    ExecutionResult("I couldn't find an app called $appName on this phone.")
                }
            }

            CommandIntent.ACTION_OPEN_PLAY_STORE -> {
                appLauncher.openPlayStore()
                ExecutionResult("Opening the Play Store.")
            }

            CommandIntent.ACTION_CALL_CONTACT -> {
                val match = callController.findContact(command.params["contact"].orEmpty())
                if (match != null && callController.callNumber(match.number)) {
                    ExecutionResult("Calling ${match.name}.", statusLabel = "✓ Calling ${match.name}")
                } else {
                    ExecutionResult("I couldn't place that call — check contacts/call permission.")
                }
            }
            CommandIntent.ACTION_DIAL_NUMBER -> {
                val number = command.params["number"].orEmpty()
                if (callController.callNumber(number)) {
                    ExecutionResult("Calling $number.", statusLabel = "✓ Calling $number")
                } else {
                    callController.dialNumber(number)
                    ExecutionResult("Opened the dialer with $number ready to call.")
                }
            }

            CommandIntent.ACTION_SEND_MESSAGE -> {
                val contact = command.params["contact"].orEmpty()
                val body = command.params["body"].orEmpty()
                val match = callController.findContact(contact)
                val number = match?.number ?: contact
                messageController.composeMessage(number, body)
                ExecutionResult("Message ready to send to $contact.", statusLabel = "✓ Message drafted")
            }

            CommandIntent.ACTION_SET_ALARM -> {
                val (h, m) = parseTimeToHourMinute(command.params["time"].orEmpty())
                alarmController.setAlarm(h, m)
                ExecutionResult("Setting an alarm for ${"%02d:%02d".format(h, m)}.", statusLabel = "✓ Alarm set")
            }
            CommandIntent.ACTION_SET_TIMER -> {
                val amount = command.params["amount"]?.toIntOrNull() ?: 5
                val unit = command.params["unit"].orEmpty()
                val seconds = if (unit.startsWith("sec")) amount else amount * 60
                alarmController.setTimer(seconds)
                ExecutionResult("Timer set for $amount ${if (unit.startsWith("sec")) "seconds" else "minutes"}.", statusLabel = "✓ Timer started")
            }
            CommandIntent.ACTION_CANCEL_ALARM -> {
                alarmController.openAlarmsList()
                ExecutionResult("Opened your alarms so you can cancel the one you want.")
            }

            CommandIntent.ACTION_PLAY_MEDIA -> { mediaController.play(); ExecutionResult("Playing music.", statusLabel = "✓ Media playing") }
            CommandIntent.ACTION_PAUSE_MEDIA -> { mediaController.pause(); ExecutionResult("Paused.", statusLabel = "✓ Media paused") }
            CommandIntent.ACTION_NEXT_TRACK -> { mediaController.next(); ExecutionResult("Skipping to the next track.") }
            CommandIntent.ACTION_PREV_TRACK -> { mediaController.previous(); ExecutionResult("Going back a track.") }

            CommandIntent.ACTION_VOLUME_UP -> fromControlResult(device.adjustVolume(AudioManager.ADJUST_RAISE), null)
            CommandIntent.ACTION_VOLUME_DOWN -> fromControlResult(device.adjustVolume(AudioManager.ADJUST_LOWER), null)
            CommandIntent.ACTION_SET_VOLUME -> {
                val percent = command.params["percent"]?.toIntOrNull() ?: 50
                fromControlResult(device.setVolumePercent(percent), null)
            }
            CommandIntent.ACTION_SILENT_MODE -> fromControlResult(device.setRingerMode(AudioManager.RINGER_MODE_SILENT), "✓ Silent mode")
            CommandIntent.ACTION_VIBRATE_MODE -> fromControlResult(device.setRingerMode(AudioManager.RINGER_MODE_VIBRATE), "✓ Vibrate mode")

            CommandIntent.ACTION_BLUETOOTH_ON -> fromControlResult(device.setBluetooth(true), "Bluetooth")
            CommandIntent.ACTION_BLUETOOTH_OFF -> fromControlResult(device.setBluetooth(false), "Bluetooth")
            CommandIntent.ACTION_WIFI_ON -> fromControlResult(device.setWifi(true), "Wi-Fi")
            CommandIntent.ACTION_WIFI_OFF -> fromControlResult(device.setWifi(false), "Wi-Fi")

            CommandIntent.ACTION_OPEN_SETTINGS_SCREEN -> fromControlResult(
                device.openGenericSettings(command.params["screen"].orEmpty()), null
            )

            CommandIntent.ACTION_BATTERY_STATUS -> {
                val pct = device.getBatteryPercent()
                val charging = if (device.isCharging()) "and charging" else "and not charging"
                ExecutionResult("You're at $pct percent battery, $charging.")
            }
            CommandIntent.ACTION_BATTERY_SAVER_ON -> fromControlResult(device.openBatterySaverSettings(), null)

            CommandIntent.ACTION_TIME -> {
                val now = Calendar.getInstance()
                val fmt = DateFormat.format("h:mm a", now)
                ExecutionResult("It's $fmt right now.")
            }
            CommandIntent.ACTION_DATE -> {
                val now = Calendar.getInstance()
                val fmt = DateFormat.format("EEEE, MMMM d, yyyy", now)
                ExecutionResult("Today is $fmt.")
            }

            CommandIntent.ACTION_SCREENSHOT, CommandIntent.ACTION_RECENT_APPS,
            CommandIntent.ACTION_GO_HOME, CommandIntent.ACTION_GO_BACK, CommandIntent.ACTION_LOCK_SCREEN -> {
                ExecutionResult(
                    "That needs the Accessibility Service, which isn't enabled yet. " +
                        "You can turn it on in Settings > Accessibility to unlock screen actions like this."
                )
            }

            CommandIntent.ACTION_LOCATION_STATUS -> {
                ExecutionResult("I'd need location permission and a location provider to answer that precisely — want me to open Maps instead?")
            }
            CommandIntent.ACTION_OPEN_MAPS -> {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ExecutionResult("Opening Maps.")
            }
            CommandIntent.ACTION_NAVIGATE_TO -> {
                val dest = command.params["destination"].orEmpty()
                val uri = android.net.Uri.parse("google.navigation:q=${android.net.Uri.encode(dest)}")
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ExecutionResult("Navigating to $dest.")
            }

            CommandIntent.ACTION_READ_NOTIFICATIONS -> {
                ExecutionResult("I need Notification Access to read those — enable it in Settings > Privacy if you'd like this.")
            }

            CommandIntent.ACTION_GENERAL_AI, CommandIntent.ACTION_UNKNOWN -> {
                ExecutionResult("Let me think about that.") // caller routes ACTION_GENERAL_AI to the AI provider instead
            }
        }
    }

    private fun fromControlResult(result: ControlResult, label: String?): ExecutionResult = when (result) {
        is ControlResult.Performed -> ExecutionResult(result.message, statusLabel = label?.let { "✓ $it" })
        is ControlResult.OpenedSettings -> ExecutionResult(result.message, statusLabel = label?.let { "↗ $it settings opened" })
        is ControlResult.Failed -> ExecutionResult(result.message)
    }

    private fun parseTimeToHourMinute(text: String): Pair<Int, Int> {
        val cleaned = text.trim().lowercase()
        val ampmMatch = Regex("(\\d{1,2})(?::(\\d{2}))? ?(am|pm)?").find(cleaned)
        if (ampmMatch != null) {
            var hour = ampmMatch.groupValues[1].toIntOrNull() ?: 7
            val minute = ampmMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = ampmMatch.groupValues[3]
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return hour to minute
        }
        return 7 to 0
    }
}
