package com.nova.assistant.engine

import android.content.Context
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

sealed class SpeechEvent {
    object ReadyForSpeech : SpeechEvent()
    object BeginningOfSpeech : SpeechEvent()
    data class PartialResult(val text: String) : SpeechEvent()
    data class FinalResult(val text: String) : SpeechEvent()
    data class Error(val message: String) : SpeechEvent()
    object EndOfSpeech : SpeechEvent()
}

/**
 * Wraps Android's SpeechRecognizer. Uses the on-device recognizer when available for
 * offline / low-battery listening, otherwise falls back to Google's cloud recognizer
 * (still on-device audio capture — no raw audio is sent anywhere by this app itself).
 */
class VoiceEngine(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun listen(preferOffline: Boolean, languageTag: String = "en-US"): Flow<SpeechEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechEvent.Error("Speech recognition isn't available on this device."))
            close()
            return@callbackFlow
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { trySend(SpeechEvent.ReadyForSpeech) }
                override fun onBeginningOfSpeech() { trySend(SpeechEvent.BeginningOfSpeech) }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { trySend(SpeechEvent.EndOfSpeech) }
                override fun onError(error: Int) {
                    trySend(SpeechEvent.Error(speechErrorToString(error)))
                    close()
                }
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull().orEmpty()
                    trySend(SpeechEvent.FinalResult(best))
                    close()
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { trySend(SpeechEvent.PartialResult(it)) }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }

        val intent = Intent_forRecognition(preferOffline, languageTag)
        recognizer?.startListening(intent)

        awaitClose {
            recognizer?.stopListening()
            recognizer?.destroy()
            recognizer = null
        }
    }

    fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun Intent_forRecognition(preferOffline: Boolean, languageTag: String) =
        android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

    private fun speechErrorToString(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
        else -> "Speech recognition error ($error)."
    }
}

/** Wraps Android TextToSpeech with a simple suspend-friendly speak() call. */
class VoiceOutput(context: Context, private val onReady: () -> Unit = {}) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) onReady()
        }
    }

    fun setLanguage(locale: Locale) {
        tts?.language = locale
    }

    fun setPitchAndRate(pitch: Float, rate: Float) {
        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)
    }

    fun speak(text: String, utteranceId: String = System.currentTimeMillis().toString()) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() { tts?.stop() }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
