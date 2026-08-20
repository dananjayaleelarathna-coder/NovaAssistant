package com.nova.assistant.device

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Wraps official Android APIs for device control. Every method reports back whether it
 * performed the action directly or had to fall back to opening a Settings screen —
 * Android (8+) intentionally forbids apps from silently toggling Wi-Fi/Bluetooth/etc,
 * so honesty about that limitation is baked into the return type rather than hidden.
 */
sealed class ControlResult {
    data class Performed(val message: String) : ControlResult()
    data class OpenedSettings(val message: String) : ControlResult()
    data class Failed(val message: String) : ControlResult()
}

class DeviceController(private val context: Context) {

    // ---------- Flashlight (works directly via CameraManager on all supported versions) ----------
    fun setTorch(on: Boolean): ControlResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ControlResult.Failed("This device has no flash unit.")
            cameraManager.setTorchMode(cameraId, on)
            ControlResult.Performed(if (on) "Flashlight turned on." else "Flashlight turned off.")
        } catch (e: Exception) {
            ControlResult.Failed("Couldn't control the flashlight: ${e.message}")
        }
    }

    // ---------- Bluetooth: Android 13+ forbids silent enable/disable; open settings instead ----------
    fun setBluetooth(on: Boolean): ControlResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            ControlResult.OpenedSettings("On this Android version I can't toggle Bluetooth directly, so I opened Bluetooth settings for you.")
        } else {
            try {
                @Suppress("DEPRECATION")
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    ControlResult.Failed("This device has no Bluetooth adapter.")
                } else {
                    @Suppress("DEPRECATION")
                    if (on) adapter.enable() else adapter.disable()
                    ControlResult.Performed(if (on) "Bluetooth turned on." else "Bluetooth turned off.")
                }
            } catch (e: Exception) {
                openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                ControlResult.OpenedSettings("Couldn't toggle Bluetooth directly, so I opened Bluetooth settings.")
            }
        }
    }

    // ---------- Wi-Fi: Android 10+ blocks WifiManager.setWifiEnabled for apps ----------
    fun setWifi(on: Boolean): ControlResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openSettings(Settings.Panel.ACTION_WIFI)
            ControlResult.OpenedSettings("Android no longer allows apps to toggle Wi-Fi directly, so I opened the Wi-Fi panel.")
        } else {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = on
                ControlResult.Performed(if (on) "Wi-Fi turned on." else "Wi-Fi turned off.")
            } catch (e: Exception) {
                openSettings(Settings.ACTION_WIFI_SETTINGS)
                ControlResult.OpenedSettings("Couldn't toggle Wi-Fi directly, so I opened Wi-Fi settings.")
            }
        }
    }

    fun setMobileData() = openSettingsResult(Settings.ACTION_DATA_ROAMING_SETTINGS, "Opened mobile data settings — Android does not allow apps to toggle mobile data directly.")
    fun setAirplaneMode() = openSettingsResult(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Opened Airplane mode settings.")
    fun setHotspot() = openSettingsResult(Settings.ACTION_WIRELESS_SETTINGS, "Opened hotspot & tethering settings.")
    fun setNfc() = openSettingsResult(Settings.ACTION_NFC_SETTINGS, "Opened NFC settings.")
    fun setVpn() = openSettingsResult(Settings.ACTION_VPN_SETTINGS, "Opened VPN settings.")
    fun setLocation() = openSettingsResult(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Opened location settings.")

    fun openGenericSettings(screenKey: String): ControlResult {
        val action = when (screenKey) {
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            "data_usage" -> Settings.ACTION_DATA_ROAMING_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "hotspot" -> Settings.ACTION_WIRELESS_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "vpn" -> Settings.ACTION_VPN_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return openSettingsResult(action, "Opened ${screenKey.replace('_', ' ')} settings.")
    }

    private fun openSettingsResult(action: String, message: String): ControlResult {
        openSettings(action)
        return ControlResult.OpenedSettings(message)
    }

    private fun openSettings(action: String) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ---------- Volume (AudioManager — fully allowed) ----------
    fun adjustVolume(direction: Int, stream: Int = AudioManager.STREAM_MUSIC): ControlResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
        return ControlResult.Performed("Volume adjusted.")
    }

    fun setVolumePercent(percent: Int, stream: Int = AudioManager.STREAM_MUSIC): ControlResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(stream)
        val target = (max * (percent.coerceIn(0, 100) / 100f)).toInt()
        am.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
        return ControlResult.Performed("Volume set to $percent%.")
    }

    fun setRingerMode(mode: Int): ControlResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (mode != AudioManager.RINGER_MODE_NORMAL &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !notificationManager.isNotificationPolicyAccessGranted
        ) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ControlResult.OpenedSettings("I need Do Not Disturb access to change ringer mode — opened that settings screen.")
        } else {
            am.ringerMode = mode
            ControlResult.Performed("Ringer mode updated.")
        }
    }

    // ---------- Battery (BatteryManager — fully allowed) ----------
    fun getBatteryPercent(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun isCharging(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }

    fun openBatterySaverSettings() = openSettingsResult(Settings.ACTION_BATTERY_SAVER_SETTINGS, "Opened battery saver settings.")
}
