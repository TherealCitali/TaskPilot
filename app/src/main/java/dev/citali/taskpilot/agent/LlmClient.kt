package dev.citali.taskpilot.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal OpenAI-compatible chat-completions client (no third-party SDK).
 * The endpoint, model, and key are supplied by the caller from SettingsStore /
 * SecureStore, so the key never lingers in this file.
 */
object LlmClient {

    data class ChatMessage(val role: String, val content: String)

    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 60_000

    suspend fun complete(
        endpoint: String,
        model: String,
        apiKey: String,
        messages: List<ChatMessage>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(endpoint.trimEnd('/') + "/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")

                val body = JSONObject()
                    .put("model", model)
                    .put("temperature", 0.0)
                    .put(
                        "messages",
                        JSONArray().apply {
                            messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
                        }
                    )

                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    throw IOException("HTTP $code: ${text.take(300)}")
                }
                JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } finally {
                connection.disconnect()
            }
        }
    }

    fun parseAction(raw: String): Action? {
        if (raw.isBlank()) return null
        val jsonText = extractJson(raw)
        val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
        val action = json.optString("action").trim().lowercase()
        return when {
            action == "tap" -> withTarget(json) { t -> Action.Tap(t, json.optString("description")) }
            action == "long_press" || action == "longpress" -> withTarget(json) { t -> Action.LongPress(t, json.optString("description")) }
            action == "swipe" -> parseDirection(json.optString("direction"))?.let { Action.Swipe(it) }
            action == "type" -> json.optString("target").takeIf { it.isNotBlank() }?.let { t ->
                Action.Type(t, json.optString("text")).takeIf { a -> a.text.isNotBlank() }
            }
            action == "key" -> parseKey(json.optString("code"))?.let { Action.Key(it) }
            action == "open_app" || action == "open" -> json.optString("package").takeIf { it.isNotBlank() }
                ?.let { Action.OpenApp(it, json.optString("label")) }
            action == "wait" -> Action.Wait((json.optDouble("millis", 1000.0)).toLong().coerceIn(200, 5000))
            action == "ask" -> Action.AskUser(json.optString("question").ifBlank { "I need clarification to continue." })
            action == "complete" -> Action.Complete(json.optString("summary").ifBlank { "Task completed." })
            action == "fail" -> Action.Fail(json.optString("reason").ifBlank { "The model could not continue." })
            else -> null
        }
    }

    private fun withTarget(json: JSONObject, block: (String) -> Action): Action? =
        json.optString("target").takeIf { it.isNotBlank() }?.let(block)

    private fun parseDirection(value: String): Action.SwipeDirection? = when (value.trim().lowercase()) {
        "up" -> Action.SwipeDirection.UP
        "down" -> Action.SwipeDirection.DOWN
        "left" -> Action.SwipeDirection.LEFT
        "right" -> Action.SwipeDirection.RIGHT
        else -> null
    }

    private fun parseKey(value: String): Action.KeyAction? = when (value.trim().lowercase()) {
        "back" -> Action.KeyAction.BACK
        "home" -> Action.KeyAction.HOME
        "enter" -> Action.KeyAction.ENTER
        "recents" -> Action.KeyAction.RECENTS
        else -> null
    }

    private fun extractJson(raw: String): String {
        val s = raw.trim()
        val fenced = Regex("""```(?:json)?\s*([\s\S]*?)```""").find(s)
        if (fenced != null) return fenced.groupValues[1].trim()
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start >= 0 && end > start) s.substring(start, end + 1) else s
    }

    // ---- Prompt builders --------------------------------------------------

    fun systemPrompt(): ChatMessage = ChatMessage(
        role = "system",
        content = """
            You are TaskPilot, a careful Android UI-automation agent. You are given a REDACTED
            view of the current accessibility tree and a user task with an already-approved plan.
            Choose exactly ONE next action and reply with a single JSON object only — no prose,
            no markdown fences.

            Allowed actions and required fields:
            {"action":"tap","target":"<#id or text or resource-id>","description":"<short reason>"}
            {"action":"long_press","target":"...","description":"..."}
            {"action":"swipe","direction":"up|down|left|right"}
            {"action":"type","target":"<#id>","text":"<exact text to enter>"}
            {"action":"key","code":"back|home|enter|recents"}
            {"action":"open_app","package":"<package name>"}
            {"action":"wait","millis":1200}
            {"action":"ask","question":"<question for the user>"}
            {"action":"complete","summary":"<what was accomplished>"}
            {"action":"fail","reason":"<why you cannot continue>"}

            Rules:
            - Prefer targets that are #ids from the CURRENT tree. Use text or resource-id
              substrings only when no #id fits.
            - Never type passwords, OTPs, PINs, card numbers, or credentials. If such input
              is needed, use "ask" instead.
            - For destructive or externally-visible controls (send, delete, buy, pay, uninstall,
              grant), only proceed if the user's plan explicitly requires it; otherwise ask.
            - If the screen does not match expectations, ask instead of guessing.
            - If the task appears complete, respond with "complete".
            - Never exceed one action per reply.
        """.trimIndent()
    )

    fun userPrompt(task: String, planSteps: List<PlanStep>, tree: String, recent: List<String>): ChatMessage {
        val steps = planSteps.joinToString("\n") { "- ${it.title}: ${it.detail}" }
        val recentLines = if (recent.isEmpty()) "(none yet)" else recent.takeLast(12).joinToString("\n")
        return ChatMessage(
            role = "user",
            content = """
                TASK: $task

                APPROVED PLAN:
                $steps

                RECENT ACTIONS:
                $recentLines

                CURRENT UI TREE (values are redacted):
                $tree

                Reply with exactly one JSON action.
            """.trimIndent()
        )
    }
}
