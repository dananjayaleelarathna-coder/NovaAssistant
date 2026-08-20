package com.nova.assistant.engine

/**
 * Abstraction over any cloud AI backend (Anthropic, OpenAI, local model, etc).
 * The rest of the app never talks to a specific vendor directly — only through this
 * interface — so the backend can be swapped from Settings without touching app logic.
 */
interface AIProvider {
    val id: String
    val displayName: String
    val requiresApiKey: Boolean

    /**
     * Sends the user's utterance (plus recent conversation context) and asks the model to
     * classify it and, if it's general conversation / an information request, produce a
     * natural-language answer. Never throws for network errors — returns a Result so the
     * caller can fall back to the offline rule-based classifier.
     */
    suspend fun classifyAndRespond(
        userText: String,
        history: List<ConversationTurn>,
        assistantName: String,
        personality: String,
        preferredLanguage: String
    ): Result<AIResponse>
}

data class ConversationTurn(val role: String, val text: String)

data class AIResponse(
    val category: RequestCategory,
    val spokenReply: String,
    /** Only populated when category is a device/app/automation command. */
    val suggestedIntent: String? = null,
    val suggestedParams: Map<String, String> = emptyMap()
)

enum class RequestCategory {
    GENERAL_CONVERSATION,
    PHONE_CONTROL,
    APP_CONTROL,
    INFORMATION_REQUEST,
    AUTOMATION_REQUEST,
    UNKNOWN
}

/** Registry so new providers can be added without touching call sites. */
object AIProviderRegistry {
    private val providers = mutableMapOf<String, AIProvider>()

    fun register(provider: AIProvider) {
        providers[provider.id] = provider
    }

    fun get(id: String): AIProvider? = providers[id]

    fun all(): List<AIProvider> = providers.values.toList()
}
