package com.example.ai

enum class SonaModel(
    val id: String, // Provider-neutral identifier (tier)
    val displayName: String,
    val description: String,
    val badge: String,
    val supportsThinking: Boolean = false,
    val isLiveAudio: Boolean = false
) {
    BALANCED(
        id = "model_balanced",
        displayName = "Sona Balanced",
        description = "Balanced intelligence, fast responses, and excellent coding & everyday reasoning.",
        badge = "Balanced",
        supportsThinking = false
    ),
    STRONG(
        id = "model_strong",
        displayName = "Sona Strong",
        description = "Deep reasoning & high thinking mode for complex math, science, and coding.",
        badge = "High Reasoning",
        supportsThinking = true
    ),
    FAST(
        id = "model_fast",
        displayName = "Sona Fast",
        description = "Ultra-low latency for instant answers and quick summaries.",
        badge = "Fast",
        supportsThinking = false
    ),
    LIVE(
        id = "model_live",
        displayName = "Sona Live",
        description = "Real-time bidirectional multimodal voice conversations (Live API preview).",
        badge = "Live Audio",
        supportsThinking = false,
        isLiveAudio = true
    );

    companion object {
        fun fromId(id: String): SonaModel {
            // 1. Check for generic provider-neutral IDs
            entries.find { it.id == id }?.let { return it }

            // 2. Compatibility mapping for legacy Gemini-specific IDs
            return when (id) {
                "gemini-3.5-flash" -> BALANCED
                "gemini-3.1-pro-preview" -> STRONG
                "gemini-3.1-flash-lite-preview" -> FAST
                "gemini-3.8-live" -> LIVE
                else -> BALANCED // Default fallback
            }
        }
    }
}
