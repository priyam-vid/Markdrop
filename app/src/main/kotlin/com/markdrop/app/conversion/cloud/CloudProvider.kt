package com.markdrop.app.conversion.cloud

enum class CloudProvider(
    val displayName: String,
    val keyPageUrl: String,
    val defaultModel: String,
    val availableModels: List<String>
) {
    GEMINI(
        displayName = "Google Gemini",
        keyPageUrl = "https://aistudio.google.com/apikey",
        defaultModel = "gemini-2.5-flash",
        availableModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash"
        )
    ),
    OPENAI(
        displayName = "OpenAI",
        keyPageUrl = "https://platform.openai.com/api-keys",
        defaultModel = "gpt-4o-mini",
        availableModels = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "chatgpt-4o-latest"
        )
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        keyPageUrl = "https://console.anthropic.com/settings/keys",
        defaultModel = "claude-sonnet-5-5",
        availableModels = listOf(
            "claude-sonnet-5-5",
            "claude-haiku-5-5",
            "claude-opus-5-5"
        )
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        keyPageUrl = "https://openrouter.ai/keys",
        defaultModel = "google/gemini-2.5-flash",
        availableModels = listOf(
            "google/gemini-2.5-flash",
            "google/gemini-2.5-pro",
            "anthropic/claude-sonnet-5.5",
            "anthropic/claude-haiku-5.5",
            "openai/gpt-4o-mini",
            "openai/gpt-4o"
        )
    )
}
