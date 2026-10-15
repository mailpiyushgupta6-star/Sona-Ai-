package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class GeminiProvider : AiProvider {

    override val providerName: String = "Google Gemini"

    override val isConfigured: Boolean
        get() {
            val key = getApiKey()
            return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
        }

    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun streamGenerate(
        messages: List<AiMessage>,
        model: SonaModel,
        thinkingEnabled: Boolean,
        systemInstruction: String?
    ): Flow<GenerationResult> = flow {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            emit(
                GenerationResult.Error(
                    message = "Gemini API key is not configured. Please set GEMINI_API_KEY in the Secrets panel in Google AI Studio.",
                    isApiKeyMissing = true
                )
            )
            return@flow
        }

        val requestPayload = buildRequestJson(
            messages = messages,
            model = model,
            thinkingEnabled = thinkingEnabled,
            systemInstruction = systemInstruction
        )

        val genericModelId = model.id
        val targetModelId = if (model.isLiveAudio) {
            "gemini-3.5-flash" // Current workaround behavior preserved
        } else {
            mapGenericToGeminiId(genericModelId)
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModelId:streamGenerateContent?alt=sse&key=$apiKey"

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestPayload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        val fullTextBuilder = StringBuilder()
        var hasEmittedAnyChunk = false

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                val parsedError = try {
                    val errorJson = JSONObject(errorBody)
                    errorJson.optJSONObject("error")?.optString("message") ?: errorBody
                } catch (_: Exception) {
                    errorBody
                }
                emit(GenerationResult.Error("API Error (${response.code}): $parsedError"))
                return@flow
            }

            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                emit(GenerationResult.Error("Empty response body from Gemini API"))
                return@flow
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String? = null

            while (currentCoroutineContext().isActive && reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim().orEmpty()
                if (currentLine.startsWith("data:")) {
                    val jsonString = currentLine.removePrefix("data:").trim()
                    if (jsonString.isEmpty() || jsonString == "[DONE]") continue

                    try {
                        val rootObj = JSONObject(jsonString)
                        val candidates = rootObj.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val content = candidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null) {
                                for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)
                                    val text = part.optString("text", "")
                                    if (text.isNotEmpty()) {
                                        fullTextBuilder.append(text)
                                        hasEmittedAnyChunk = true
                                        emit(GenerationResult.Chunk(text))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore malformed chunk
                    }
                }
            }

            reader.close()
            inputStream.close()

            if (currentCoroutineContext().isActive) {
                if (hasEmittedAnyChunk) {
                    emit(GenerationResult.Complete(fullTextBuilder.toString()))
                } else {
                    emit(GenerationResult.Error("No response candidates returned by model."))
                }
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                emit(GenerationResult.Error("Connection failed: ${e.localizedMessage ?: "Unknown error"}"))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestJson(
        messages: List<AiMessage>,
        model: SonaModel,
        thinkingEnabled: Boolean,
        systemInstruction: String?
    ): JSONObject {
        val root = JSONObject()

        // 1. System instruction
        if (!systemInstruction.isNullOrBlank()) {
            val sysObj = JSONObject()
            val sysParts = JSONArray()
            val sysPart = JSONObject().apply { put("text", systemInstruction) }
            sysParts.put(sysPart)
            sysObj.put("parts", sysParts)
            root.put("systemInstruction", sysObj)
        }

        // 2. Contents
        val contentsArray = JSONArray()
        for (msg in messages) {
            val contentObj = JSONObject()
            // Map "assistant" to "model" for Gemini API
            val role = if (msg.role == "assistant") "model" else "user"
            contentObj.put("role", role)

            val partsArray = JSONArray()
            if (msg.content.isNotBlank()) {
                val textPart = JSONObject().apply { put("text", msg.content) }
                partsArray.put(textPart)
            }

            if (!msg.imageBase64.isNullOrBlank()) {
                val imagePart = JSONObject().apply {
                    val inlineData = JSONObject().apply {
                        put("mimeType", msg.imageMimeType ?: "image/jpeg")
                        put("data", msg.imageBase64)
                    }
                    put("inlineData", inlineData)
                }
                partsArray.put(imagePart)
            }

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
        }
        root.put("contents", contentsArray)

        // 3. Generation Config
        val genConfig = JSONObject()
        if (thinkingEnabled && model.supportsThinking) {
            val thinkingConfig = JSONObject()
            thinkingConfig.put("thinkingLevel", "HIGH")
            genConfig.put("thinkingConfig", thinkingConfig)
        }
        if (genConfig.length() > 0) {
            root.put("generationConfig", genConfig)
        }

        return root
    }

    internal fun mapGenericToGeminiId(genericId: String): String {
        return when (genericId) {
            "model_balanced" -> "gemini-3.5-flash"
            "model_strong" -> "gemini-3.1-pro-preview"
            "model_fast" -> "gemini-3.1-flash-lite-preview"
            "model_live" -> "gemini-3.8-live"
            else -> "gemini-3.5-flash" // Default fallback
        }
    }
}
