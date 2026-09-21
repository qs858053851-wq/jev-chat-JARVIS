package com.jev.probe.core

import android.content.Context

/**
 * App-private config store. Holds the Jev key/endpoint, Chat key/endpoint,
 * model choices, the relationship description used in Jev's state, and whitelist.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("jev_assistant", Context.MODE_PRIVATE)

    // Legacy OpenRouter key (kept for backward compatibility)
    var openRouterKey: String
        get() = sp.getString(K_KEY, "") ?: ""
        set(v) = sp.edit().putString(K_KEY, v.trim()).apply()

    /** Jev decision API Key (TypeSafe or OpenRouter) */
    var jevKey: String
        get() = sp.getString(K_JEV_KEY, "") ?: ""
        set(v) = sp.edit().putString(K_JEV_KEY, v.trim()).apply()

    /** Jev decision endpoint URL (default TypeSafe SystemOne) */
    var jevUrl: String
        get() = sp.getString(K_JEV_URL, DEFAULT_JEV_URL) ?: DEFAULT_JEV_URL
        set(v) = sp.edit().putString(K_JEV_URL, v.trim()).apply()

    /** Chat reply generation API Key (Command Go, OpenAI, etc.) */
    var chatKey: String
        get() = sp.getString(K_CHAT_KEY, "") ?: ""
        set(v) = sp.edit().putString(K_CHAT_KEY, v.trim()).apply()

    /** Chat reply generation endpoint URL (default Command Go) */
    var chatUrl: String
        get() = sp.getString(K_CHAT_URL, DEFAULT_CHAT_URL) ?: DEFAULT_CHAT_URL
        set(v) = sp.edit().putString(K_CHAT_URL, v.trim()).apply()

    /** Generative model for drafting the 3 candidate replies */
    var replyModel: String
        get() = sp.getString(K_REPLY_MODEL, DEFAULT_REPLY_MODEL) ?: DEFAULT_REPLY_MODEL
        set(v) = sp.edit().putString(K_REPLY_MODEL, v.trim()).apply()

    /** Free-text describing who the other person is; goes into Jev's state. */
    var relationship: String
        get() = sp.getString(K_REL, DEFAULT_REL) ?: DEFAULT_REL
        set(v) = sp.edit().putString(K_REL, v).apply()

    /** Master on/off for showing the overlay + running analysis. */
    var enabled: Boolean
        get() = sp.getBoolean(K_ENABLED, true)
        set(v) = sp.edit().putBoolean(K_ENABLED, v).apply()

    /** Conversation whitelist: titles the assistant is allowed to act on. */
    var whitelist: Set<String>
        get() = sp.getStringSet(K_WHITELIST, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(K_WHITELIST, v).apply()

    /** Overlay panel opacity, 60..100 (%). */
    var overlayOpacity: Int
        get() = sp.getInt(K_OPACITY, 92).coerceIn(60, 100)
        set(v) = sp.edit().putInt(K_OPACITY, v.coerceIn(60, 100)).apply()

    /** Remembered vertical position of the bubble (px); -1 = default. */
    var bubbleY: Int
        get() = sp.getInt(K_BUBBLE_Y, -1)
        set(v) = sp.edit().putInt(K_BUBBLE_Y, v).apply()

    /** Remembered horizontal position of the bubble (px); -1 = default. */
    var bubbleX: Int
        get() = sp.getInt(K_BUBBLE_X, -1)
        set(v) = sp.edit().putInt(K_BUBBLE_X, v).apply()

    /** Auto-analyze on every incoming message. */
    var autoAnalyze: Boolean
        get() = sp.getBoolean(K_AUTO, true)
        set(v) = sp.edit().putBoolean(K_AUTO, v).apply()

    fun isAllowed(title: String?): Boolean {
        val wl = whitelist
        if (wl.isEmpty()) return true
        if (title == null) return false
        return wl.any { title.contains(it) }
    }

    val effectiveJevKey: String
        get() = jevKey.ifBlank { openRouterKey }

    val effectiveJevUrl: String
        get() = jevUrl.ifBlank {
            if (openRouterKey.isNotBlank() && jevKey.isBlank()) "https://openrouter.ai/api/alpha/decisions"
            else DEFAULT_JEV_URL
        }

    val effectiveChatKey: String
        get() = chatKey.ifBlank { openRouterKey }

    val effectiveChatUrl: String
        get() = chatUrl.ifBlank {
            if (openRouterKey.isNotBlank() && chatKey.isBlank()) "https://openrouter.ai/api/v1/chat/completions"
            else DEFAULT_CHAT_URL
        }

    fun hasKey(): Boolean = effectiveJevKey.isNotBlank() && effectiveChatKey.isNotBlank()

    companion object {
        private const val K_KEY = "openrouter_key"
        private const val K_JEV_KEY = "jev_key"
        private const val K_JEV_URL = "jev_url"
        private const val K_CHAT_KEY = "chat_key"
        private const val K_CHAT_URL = "chat_url"
        private const val K_REPLY_MODEL = "reply_model"
        private const val K_REL = "relationship"
        private const val K_ENABLED = "enabled"
        private const val K_WHITELIST = "whitelist"
        private const val K_OPACITY = "overlay_opacity"
        private const val K_BUBBLE_Y = "bubble_y"
        private const val K_BUBBLE_X = "bubble_x"
        private const val K_AUTO = "auto_analyze"

        const val DEFAULT_JEV_URL = "https://api.typesafe.ai/v1/systemone"
        const val DEFAULT_CHAT_URL = "https://api.commandcode.ai/provider/v1/chat/completions"

        const val MODEL_DEEPSEEK_4_1_FLASH = "deepseek/deepseek-v4.1-flash"
        const val MODEL_GOOGLE_3_8_FLASH = "google/gemini-3.8-flash"

        const val DEFAULT_REPLY_MODEL = MODEL_DEEPSEEK_4_1_FLASH
        const val DEFAULT_REL = "对方是我的伴侣；from=me 的是我发的，from=other 的是对方发的"
    }
}
