package com.nova.assistant.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nova.assistant.MainActivity
import com.nova.assistant.R
import com.nova.assistant.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps a short-cycle "listen for wake phrase" loop alive within
 * Android's background execution limits. Runs a lightweight on-device SpeechRecognizer pass,
 * checks for the configured wake phrase locally (no audio leaves the device for this step),
 * and — only once the wake phrase is heard — starts a full command-listening session.
 *
 * NOTE: continuous SpeechRecognizer cycling has real battery/OS constraints; the
 * "sensitivity" and "battery-saving mode" settings throttle how aggressively this loop runs.
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var listenJob: Job? = null
    private lateinit var voiceEngine: VoiceEngine
    private lateinit var settingsRepo: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        voiceEngine = VoiceEngine(applicationContext)
        settingsRepo = SettingsRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Listening for wake word…"))
        startWakeLoop()
        return START_STICKY
    }

    private fun startWakeLoop() {
        listenJob?.cancel()
        listenJob = scope.launch {
            settingsRepo.settingsFlow.collectLatest { settings ->
                if (!settings.wakeWordEnabled) return@collectLatest
                loopListenForWakePhrase(settings.assistantName, settings.batterySavingMode)
            }
        }
    }

    private suspend fun loopListenForWakePhrase(assistantName: String, batterySaving: Boolean) {
        while (true) {
            voiceEngine.listen(preferOffline = true).collectLatest { event ->
                if (event is SpeechEvent.FinalResult) {
                    if (event.text.contains(assistantName, ignoreCase = true)) {
                        onWakePhraseDetected()
                    }
                }
            }
            // Battery-saving mode inserts a short pause between listen cycles instead of
            // continuous back-to-back recognition passes.
            if (batterySaving) kotlinx.coroutines.delay(1500)
        }
    }

    private fun onWakePhraseDetected() {
        updateNotification("Yes, I'm listening…")
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra(EXTRA_WAKE_TRIGGERED, true)
        }
        startActivity(launchIntent)
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nova Assistant")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_nova_orb)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Wake Word Listening", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows when Nova is listening in the background" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        voiceEngine.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "nova_wake_word"
        const val NOTIFICATION_ID = 42
        const val EXTRA_WAKE_TRIGGERED = "wake_triggered"
    }
}
