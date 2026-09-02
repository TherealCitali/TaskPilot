package dev.citali.taskpilot.agent

/**
 * The plan shown to the user before any task runs, plus a parsed [TaskIntent]
 * that the built-in deterministic executor can follow even without an AI key.
 */
data class PlanStep(
    val title: String,
    val detail: String,
    val highRisk: Boolean = false,
)

data class Plan(
    val command: String,
    val steps: List<PlanStep>,
    val intent: TaskIntent,
)

sealed class TaskIntent {
    data class OpenApp(val packageName: String?, val label: String) : TaskIntent()
    data class OpenAppAndSearch(val packageName: String?, val label: String, val query: String) : TaskIntent()
    data class MessageDraft(
        val packageName: String,
        val label: String,
        val contact: String?,
        val message: String,
        val send: Boolean,
    ) : TaskIntent()
    data class Generic(val description: String) : TaskIntent()
}

data class AppSpec(val packageName: String?, val label: String)

/** Turns a natural-language command into a plan + structured intent. */
object CommandPlanner {

    private val appKeywords = linkedMapOf(
        "whatsapp" to AppSpec("com.whatsapp", "WhatsApp"),
        "youtube" to AppSpec("com.google.android.youtube", "YouTube"),
        "chrome" to AppSpec("com.android.chrome", "Chrome"),
        "play store" to AppSpec("com.android.vending", "Play Store"),
        "calculator" to AppSpec("com.google.android.calculator", "Calculator"),
        "photos" to AppSpec("com.google.android.apps.photos", "Google Photos"),
        "gallery" to AppSpec(null, "Gallery"),
        "maps" to AppSpec("com.google.android.apps.maps", "Maps"),
        "gmail" to AppSpec("com.google.android.gm", "Gmail"),
        "instagram" to AppSpec("com.instagram.android", "Instagram"),
        "clock" to AppSpec("com.google.android.deskclock", "Clock"),
        "calendar" to AppSpec("com.google.android.calendar", "Calendar"),
        "settings" to AppSpec("com.android.settings", "Settings"),
    )

    fun parse(command: String): Plan {
        val trimmed = command.trim()
        val normalized = trimmed.lowercase()
        val match = appKeywords.entries.firstOrNull { normalized.contains(it.key) }

        return when {
            match != null && match.key == "whatsapp" -> messagePlan(trimmed, match.value)
            match != null && extractQuery(trimmed) != null -> searchPlan(trimmed, match.value)
            match != null -> openPlan(trimmed, match.value)
            "delete" in normalized && ("screenshot" in normalized || "photo" in normalized || "image" in normalized || "gallery" in normalized) ->
                deletePlan(trimmed)
            "battery saver" in normalized -> batteryPlan(trimmed)
            else -> genericPlan(trimmed)
        }
    }

    private fun openPlan(command: String, spec: AppSpec): Plan {
        val steps = listOf(
            PlanStep("Open ${spec.label}", "Launch ${spec.label}."),
            PlanStep("Verify", "Confirm the app is in the foreground."),
        )
        return Plan(command, steps, TaskIntent.OpenApp(spec.packageName, spec.label))
    }

    private fun searchPlan(command: String, spec: AppSpec): Plan {
        val query = extractQuery(command).orEmpty()
        val steps = listOf(
            PlanStep("Open ${spec.label}", "Launch ${spec.label}."),
            PlanStep("Find search", "Locate the accessible search field."),
            PlanStep("Enter query", "Type \"$query\" into the search field."),
            PlanStep("Submit", "Submit the search and verify results."),
        )
        return Plan(command, steps, TaskIntent.OpenAppAndSearch(spec.packageName, spec.label, query))
    }

    private fun messagePlan(command: String, spec: AppSpec): Plan {
        val contact = extractContact(command)
        val message = extractMessage(command)
        val send = !(
            command.contains("do not send", ignoreCase = true) ||
                command.contains("don't send", ignoreCase = true) ||
                command.contains("dont send", ignoreCase = true) ||
                command.contains("without sending", ignoreCase = true) ||
                command.contains("as a draft", ignoreCase = true)
            )
        val steps = mutableListOf<PlanStep>()
        steps += PlanStep("Open ${spec.label}", "Launch ${spec.label}.")
        steps += PlanStep("Find ${contact ?: "the contact"}", "Locate the conversation with the named person.")
        steps += PlanStep("Draft the message", "Type the requested text into the message field.")
        steps += if (send) {
            PlanStep("Send the message", "Send after an additional confirmation.", highRisk = true)
        } else {
            PlanStep("Do not send", "Leave the message as a draft; no send action will be attempted.")
        }
        return Plan(
            command,
            steps,
            TaskIntent.MessageDraft(spec.packageName ?: "com.whatsapp", spec.label, contact, message, send)
        )
    }

    private fun deletePlan(command: String): Plan {
        val steps = listOf(
            PlanStep("Open Gallery", "Launch the available Gallery application."),
            PlanStep("Find matches", "Identify items matching the requested scope."),
            PlanStep("Review matches", "Show the matched items and verify the deletion scope."),
            PlanStep("Delete matched items", "Delete only after an additional confirmation.", highRisk = true),
        )
        return Plan(command, steps, TaskIntent.Generic("gallery-delete"))
    }

    private fun batteryPlan(command: String): Plan {
        val steps = listOf(
            PlanStep("Open Settings", "Launch Android Settings."),
            PlanStep("Find Battery Saver", "Locate the Battery Saver setting."),
            PlanStep("Enable Battery Saver", "Change the setting after the approved plan."),
        )
        return Plan(command, steps, TaskIntent.Generic("battery-saver"))
    }

    private fun genericPlan(command: String): Plan {
        val steps = listOf(
            PlanStep("Identify the destination", "Determine which app or Android surface matches the request."),
            PlanStep("Observe the current UI", "Build a redacted accessibility-tree snapshot."),
            PlanStep("Complete the requested task", "Continue one validated action at a time."),
            PlanStep("Verify completion", "Confirm the outcome or ask a follow-up question."),
        )
        return Plan(command, steps, TaskIntent.Generic(command))
    }

    // ---- Extraction helpers ----------------------------------------------

    private fun extractQuery(command: String): String? {
        Regex("""["“]([^"”]{1,120})["”]""").find(command)
            ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val patterns = listOf(
            Regex("""search(?: the (?:web|net))? for (.{1,120}?)(?:[.,!]| and | on \w+)?$""", RegexOption.IGNORE_CASE),
            Regex("""search (.{1,120}?)(?:[.,!]| and | on \w+)?$""", RegexOption.IGNORE_CASE),
            Regex("""(?:play|watch|look up|look for|find) (.{1,120}?)(?:[.,!]| and | on \w+)?$""", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val match = pattern.find(command.trim())
            val value = match?.groupValues?.get(1)?.trim()
            if (!value.isNullOrBlank() && value.length >= 2) return value
        }
        return null
    }

    private fun extractContact(command: String): String? {
        val patterns = listOf(
            Regex("""(?:message|text|msg|whatsapp)\s+(?:to\s+)?([A-Z][a-z]+(?:\s+[A-Z][a-z]+)?)"""),
            Regex("""\bto\s+([A-Z][a-z]+(?:\s+[A-Z][a-z]+)?)"""),
        )
        val stopWords = setOf("saying", "that", "who", "with", "without", "and", "the", "but", "do", "dont", "don't")
        for (pattern in patterns) {
            val value = pattern.find(command)?.groupValues?.get(1)?.trim()
            if (!value.isNullOrBlank() && value.lowercase() !in stopWords) return value
        }
        return null
    }

    private fun extractMessage(command: String): String {
        Regex("""["“]([^"”]{1,400})["”]""").find(command)
            ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val marker = Regex("""\b(saying|that says|reading|with the message|with text|:\s+)""", RegexOption.IGNORE_CASE)
            .find(command)?.range?.last
        if (marker != null) {
            val rest = command.substring(marker + 1).trim().removePrefix(":").trim()
            val but = Regex("""\s+but\s+""", RegexOption.IGNORE_CASE).find(rest)?.range?.first
            val message = if (but != null) rest.substring(0, but) else rest
            if (message.isNotBlank()) return message.trim()
        }
        return ""
    }
}
