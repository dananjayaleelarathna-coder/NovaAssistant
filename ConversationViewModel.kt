package com.nova.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nova.assistant.data.MemoryManager
import com.nova.assistant.data.SecureKeyStore
import com.nova.assistant.data.SettingsRepository
import com.nova.assistant.engine.AIProviderRegistry
import com.nova.assistant.engine.ConversationTurn
import com.nova.assistant.engine.RequestCategory
import com.nova.assistant.engine.SpeechEvent
import com.nova.assistant.engine.VoiceEngine
import com.nova.assistant.engine.VoiceOutput
import com.nova.assistant.engine.providers.AnthropicProvider
import com.nova.assistant.intent.CommandIntent
import com.nova.assistant.intent.IntentExecutor
import com.nova.assistant.intent.OfflineCommandParser
import com.nova.assistant.intent.ParsedCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AssistantStatus { IDLE, LISTENING, THINKING, SPEAKING }

data class ChatMessage(val fromUser: Boolean, val text: String, val statusLabel: String? = null)

data class ConversationUiState(
    val status: AssistantStatus = AssistantStatus.IDLE,
    val messages: List<ChatMessage> = emptyList(),
    val partialTranscript: String = "",
    val offlineMode: Boolean = false,
    val pendingConfirmation: ParsedCommand? = null,
    val assistantName: String = "Nova"
)

class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val settingsRepo = SettingsRepository(context)
    private val secureKeyStore = SecureKeyStore(context)
    private val memoryManager = MemoryManager(context)
    private val voiceEngine = VoiceEngine(context)
    private val voiceOutput = VoiceOutput(context)
    private val executor = IntentExecutor(context)

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var currentSettings = com.nova.assistant.data.NovaSettings()

    init {
        AIProviderRegistry.register(AnthropicProvider { secureKeyStore.getApiKey("anthropic") })

        viewModelScope.launch {
            settingsRepo.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                _uiState.value = _uiState.value.copy(
                    assistantName = settings.assistantName,
                    offlineMode = settings.offlineModeOnly || !settings.cloudAiEnabled
                )
            }
        }
    }

    fun startListening() {
        _uiState.value = _uiState.value.copy(status = AssistantStatus.LISTENING, partialTranscript = "")
        viewModelScope.launch {
            val langTag = when (currentSettings.language) {
                "si-LK" -> "si-LK"
                else -> "en-US" // "mixed" still uses en-US recognizer; Sinhala-English mixed
                                 // phrases are handled at the text-parsing layer, not ASR layer.
            }
            voiceEngine.listen(preferOffline = currentSettings.offlineModeOnly, languageTag = langTag)
                .collectLatest { event ->
                    when (event) {
                        is SpeechEvent.PartialResult -> _uiState.value = _uiState.value.copy(partialTranscript = event.text)
                        is SpeechEvent.FinalResult -> {
                            _uiState.value = _uiState.value.copy(partialTranscript = "")
                            onUserUtterance(event.text)
                        }
                        is SpeechEvent.Error -> {
                            _uiState.value = _uiState.value.copy(status = AssistantStatus.IDLE)
                        }
                        else -> Unit
                    }
                }
        }
    }

    fun stopListening() {
        voiceEngine.cancel()
        _uiState.value = _uiState.value.copy(status = AssistantStatus.IDLE)
    }

    /** Also used for typed text input, per requirement #27. */
    fun submitTypedText(text: String) {
        if (text.isBlank()) return
        onUserUtterance(text)
    }

    fun confirmPendingAction(confirmed: Boolean) {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
        if (!confirmed) {
            respond("Okay, cancelled.")
            return
        }
        val result = executor.executeConfirmed(pending)
        respond(result.spokenReply, result.statusLabel)
    }

    private fun onUserUtterance(text: String) {
        addMessage(ChatMessage(fromUser = true, text = text))
        viewModelScope.launch { memoryManager.logTurn("user", text) }
        _uiState.value = _uiState.value.copy(status = AssistantStatus.THINKING)

        val offlineCommand = OfflineCommandParser.parse(text)

        // Anything the rule-based parser confidently recognized as a device/app action
        // executes locally — no network round-trip needed.
        if (offlineCommand.intent != CommandIntent.ACTION_GENERAL_AI) {
            if (offlineCommand.requiresConfirmation) {
                promptConfirmation(offlineCommand)
            } else {
                val result = executor.execute(offlineCommand)
                if (result.needsConfirmation) {
                    promptConfirmation(offlineCommand)
                } else {
                    respond(result.spokenReply, result.statusLabel)
                }
            }
            return
        }

        // Otherwise, general conversation / information request -> cloud AI (if allowed),
        // else an honest offline fallback.
        if (currentSettings.offlineModeOnly || !currentSettings.cloudAiEnabled) {
            respond("I'm in offline mode, so I can't answer open-ended questions right now — but I can still control your phone.")
            return
        }

        viewModelScope.launch {
            val provider = AIProviderRegistry.get(currentSettings.aiProviderId)
            if (provider == null) {
                respond("No AI provider is configured. Add one in Settings.")
                return@launch
            }
            val history = _uiState.value.messages.takeLast(10).map {
                ConversationTurn(if (it.fromUser) "user" else "assistant", it.text)
            }
            val result = provider.classifyAndRespond(
                userText = text,
                history = history,
                assistantName = currentSettings.assistantName,
                personality = currentSettings.personality,
                preferredLanguage = currentSettings.language
            )
            result.onSuccess { ai ->
                if (ai.category == RequestCategory.GENERAL_CONVERSATION || ai.category == RequestCategory.INFORMATION_REQUEST || ai.suggestedIntent == null) {
                    respond(ai.spokenReply)
                } else {
                    // AI identified a device/app action outside what the offline parser caught.
                    val intent = runCatching { CommandIntent.valueOf(ai.suggestedIntent) }.getOrNull()
                    if (intent != null) {
                        val command = ParsedCommand(intent, ai.suggestedParams, rawText = text)
                        val execResult = executor.execute(command)
                        if (execResult.needsConfirmation) promptConfirmation(command)
                        else respond(execResult.spokenReply, execResult.statusLabel)
                    } else {
                        respond(ai.spokenReply)
                    }
                }
            }.onFailure {
                respond("I couldn't reach the AI service, so here's what I can do offline: control your phone, apps, alarms, and settings.")
            }
        }
    }

    private fun promptConfirmation(command: ParsedCommand) {
        val result = executor.execute(command) // returns confirmation prompt, doesn't act yet
        _uiState.value = _uiState.value.copy(pendingConfirmation = command)
        addMessage(ChatMessage(fromUser = false, text = result.spokenReply))
        speak(result.spokenReply)
        _uiState.value = _uiState.value.copy(status = AssistantStatus.IDLE)
    }

    private fun respond(text: String, statusLabel: String? = null) {
        addMessage(ChatMessage(fromUser = false, text = text, statusLabel = statusLabel))
        viewModelScope.launch { memoryManager.logTurn("assistant", text) }
        speak(text)
        _uiState.value = _uiState.value.copy(status = AssistantStatus.IDLE)
    }

    private fun speak(text: String) {
        _uiState.value = _uiState.value.copy(status = AssistantStatus.SPEAKING)
        voiceOutput.speak(text)
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + message)
    }

    override fun onCleared() {
        voiceEngine.cancel()
        voiceOutput.shutdown()
        super.onCleared()
    }
}
