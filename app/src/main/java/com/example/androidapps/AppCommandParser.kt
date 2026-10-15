package com.example.androidapps

sealed interface CommandLanguage {
    object English : CommandLanguage
    object Hinglish : CommandLanguage
}

data class ParsedAppCommand(
    val targetApp: String,
    val language: CommandLanguage,
    val originalPrompt: String
)

object AppCommandParser {

    // English action prefixes
    private val englishPrefixes = listOf(
        "open the app",
        "open app",
        "open",
        "launch the app",
        "launch app",
        "launch",
        "start the app",
        "start app",
        "start",
        "run the app",
        "run app",
        "run"
    )

    // Conversational prefixes to strip from English commands
    private val englishConversationalPrefixes = listOf(
        "please",
        "can you please",
        "could you please",
        "can you",
        "could you",
        "would you",
        "sona please",
        "sona",
        "hey sona",
        "ok sona"
    )

    // Conversational prefixes to strip from Hinglish commands
    private val hinglishConversationalPrefixes = listOf(
        "zara",
        "ek baar",
        "sona",
        "bhai",
        "arre",
        "please",
        "kripya",
        "jaldi"
    )

    // Hinglish action suffixes (ordered longest to shortest)
    private val hinglishActionSuffixes = listOf(
        "open kar do please",
        "open kardo please",
        "open kar dena",
        "open kar do",
        "open kardo",
        "open karo please",
        "open karo na",
        "open karo",
        "open karna",
        "open kr do",
        "open krdo",
        "open kro",
        "khol do please",
        "khol dena",
        "khol do na",
        "khol do",
        "kholdo",
        "khol na",
        "kholna",
        "kholo na",
        "kholo please",
        "kholo",
        "chala do please",
        "chala dena",
        "chala do",
        "chalado",
        "chalao please",
        "chalao na",
        "chalao",
        "start kar do",
        "start kardo",
        "start karo",
        "start kr do",
        "start kro",
        "launch kar do",
        "launch kardo",
        "launch karo"
    )

    // Hinglish action prefixes
    private val hinglishActionPrefixes = listOf(
        "khol do please",
        "khol do",
        "kholdo",
        "kholo please",
        "kholo",
        "chala do please",
        "chala do",
        "chalado",
        "chalao please",
        "chalao",
        "start karo",
        "open karo",
        "open kardo",
        "open kar do"
    )

    // Conversational/informational questions that should NOT be treated as app commands
    private val informationalQuestionPatterns = listOf(
        "what is",
        "what are",
        "how to",
        "how do",
        "how can",
        "why is",
        "why do",
        "who is",
        "when was",
        "where is",
        "explain",
        "tell me about",
        "kya hai",
        "kya hota",
        "kaise kare",
        "kaise hota",
        "kaise kahu",
        "kyun",
        "kab",
        "kahan"
    )

    /**
     * Parses the given prompt into a [ParsedAppCommand] if it represents a command to launch an app.
     * Returns null if the prompt is not an app launch command.
     */
    fun parse(input: String): ParsedAppCommand? {
        val trimmed = input.trim().replace(Regex("[?!.]+$"), "").trim()
        if (trimmed.isBlank()) return null

        val lower = trimmed.lowercase()

        // Exclude conversational questions
        for (pattern in informationalQuestionPatterns) {
            if (lower.startsWith(pattern) || lower.contains(" $pattern ")) {
                return null
            }
        }

        // 1. Try Hinglish suffixes (e.g. "YouTube kholo", "Chrome open karo", "WhatsApp chalao")
        for (suffix in hinglishActionSuffixes) {
            if (lower.endsWith(" $suffix")) {
                var target = lower.removeSuffix(" $suffix").trim()
                // Strip optional "app" suffix from target (e.g. "YouTube app kholo")
                if (target.endsWith(" app")) {
                    target = target.removeSuffix(" app").trim()
                }
                // Strip conversational prefixes
                for (cp in hinglishConversationalPrefixes) {
                    if (target.startsWith("$cp ")) {
                        target = target.removePrefix("$cp ").trim()
                    }
                }
                if (target.isNotBlank()) {
                    return ParsedAppCommand(
                        targetApp = target,
                        language = CommandLanguage.Hinglish,
                        originalPrompt = input
                    )
                }
            }
        }

        // 2. Try Hinglish prefixes (e.g. "kholo YouTube", "chalao WhatsApp")
        var hinglishCandidate = lower
        for (cp in hinglishConversationalPrefixes) {
            if (hinglishCandidate.startsWith("$cp ")) {
                hinglishCandidate = hinglishCandidate.removePrefix("$cp ").trim()
            }
        }

        for (prefix in hinglishActionPrefixes) {
            if (hinglishCandidate.startsWith("$prefix ")) {
                var target = hinglishCandidate.removePrefix("$prefix ").trim()
                if (target.endsWith(" app")) {
                    target = target.removeSuffix(" app").trim()
                }
                if (target.isNotBlank()) {
                    return ParsedAppCommand(
                        targetApp = target,
                        language = CommandLanguage.Hinglish,
                        originalPrompt = input
                    )
                }
            }
        }

        // 3. Try English commands (e.g. "Open Settings", "Launch Chrome", "Please open YouTube")
        var engCandidate = lower
        for (cp in englishConversationalPrefixes) {
            if (engCandidate.startsWith("$cp ")) {
                engCandidate = engCandidate.removePrefix("$cp ").trim()
            }
        }

        for (prefix in englishPrefixes) {
            if (engCandidate.startsWith("$prefix ")) {
                var target = engCandidate.removePrefix("$prefix ").trim()
                if (target.endsWith(" app")) {
                    target = target.removeSuffix(" app").trim()
                }
                if (target.isNotBlank()) {
                    return ParsedAppCommand(
                        targetApp = target,
                        language = CommandLanguage.English,
                        originalPrompt = input
                    )
                }
            }
        }

        return null
    }
}
