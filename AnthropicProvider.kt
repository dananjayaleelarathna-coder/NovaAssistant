package com.nova.assistant.engine.providers

import com.nova.assistant.engine.AIProvider
import com.nova.assistant.engine.AIResponse
import com.nova.assistant.engine.ConversationTurn
import com.nova.assistant.engine.RequestCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Example concrete AIProvider. The API key is supplied by the user in Settings and stored
 * in EncryptedSharedPreferences — never hard-coded, never logged.
 */
class AnthropicProvider(private val apiKeyProvider: () -> String?) : AIProvider {

    override val id = "anthropic"
    override val displayName = "Anthropic Claude"
    override val requiresApiKey = true

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun classifyAndRespond(
        userText: String,
        history: List<ConversationTurn>,
        assistantName: String,
        personality: String,
        preferredLanguage: String
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider() ?: return@withContext Result.failure(
            IllegalStateException("No API key configured for Anthropic provider")
        )
        try {
            val systemPrompt = buildSystemPrompt(assistantName, personality, preferredLanguage)
            val messagesJson = JSONArray()
            history.takeLast(10).forEach {
                messagesJson.put(JSONObject().put("role", it.role).put("content", it.text))
            }
            messagesJson.put(JSONObject().put("role", "user").put("content", userText))

            val body = JSONObject()
                .put("model", "claude-sonnet-4-6")
                .put("max_tokens", 500)
                .put("system", systemPrompt)
                .put("messages", messagesJson)
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("AI request failed: ${resp.code}"))
                }
                val raw = resp.body?.string().orEmpty()
                val json = JSONObject(raw)
                val text = json.getJSONArray("content").getJSONObject(0).getString("text")
                Result.success(parseModelJson(text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildSystemPrompt(name: String, personality: String, language: String): String = """
        You are $name, an on-device Android voice assistant with a $personality personality.
        The user may speak English, Sinhala, or mixed Sinhala-English. Preferred reply language: $language.
        Classify every message into exactly one category: GENERAL_CONVERSATION, PHONE_CONTROL,
        APP_CONTROL, INFORMATION_REQUEST, or AUTOMATION_REQUEST.
        If it is a control/automation request, also propose an internal intent id such as
        ACTION_TORCH_ON, ACTION_OPEN_APP, ACTION_CALL_CONTACT, ACTION_SET_ALARM, ACTION_BLUETOOTH,
        ACTION_WIFI, ACTION_VOLUME, ACTION_SCREENSHOT, ACTION_NAVIGATION, etc, with any parameters
        (e.g. app name, contact name, time). Respond ONLY with strict JSON, no prose, no markdown:
        {"category":"...","reply":"...","intent":"...or null","params":{"key":"value"}}
    """.trimIndent()

    private fun parseModelJson(text: String): AIResponse {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(cleaned)
        val category = try {
            RequestCategory.valueOf(json.getString("category"))
        } catch (e: Exception) {
            RequestCategory.UNKNOWN
        }
        val params = mutableMapOf<String, String>()
        json.optJSONObject("params")?.let { p ->
            p.keys().forEach { k -> params[k] = p.getString(k) }
        }
        return AIResponse(
            category = category,
            spokenReply = json.optString("reply", ""),
            suggestedIntent = json.optString("intent", null).takeIf { it != "null" && !it.isNullOrBlank() },
            suggestedParams = params
        )
    }
}
