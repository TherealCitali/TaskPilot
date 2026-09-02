package dev.citali.taskpilot.agent

import dev.citali.taskpilot.accessibility.UiSnapshot

/**
 * Built-in executor that follows a structured [TaskIntent] without an AI key.
 *
 * It works one action at a time and prefers to pause with an [Action.AskUser]
 * rather than guess when the expected control cannot be confidently identified.
 */
class DeterministicExecutor(private val intent: TaskIntent) {

    private var openedOnce = false
    private var openMisses = 0
    private var typed = false
    private var submitted = false
    private var searchOpened = false
    private var contactTyped = false
    private var conversationOpened = false
    private var messageTyped = false
    private var sent = false
    private var askedOnce = false
    private var askedNullOpen = false
    private var userAnswer: String? = null

    fun onUserAnswer(text: String) {
        userAnswer = text
    }

    fun next(snapshot: UiSnapshot): Action = when (intent) {
        is TaskIntent.OpenApp -> openApp(snapshot, intent)
        is TaskIntent.OpenAppAndSearch -> search(snapshot, intent)
        is TaskIntent.MessageDraft -> message(snapshot, intent)
        is TaskIntent.Generic -> generic(snapshot, intent)
    }

    private fun ensureOpened(snapshot: UiSnapshot, packageName: String?, label: String): Action? {
        if (packageName == null) {
            // OEM-dependent app (e.g. Gallery). We cannot launch it deterministically,
            // so we ask once and then treat it as opened on the user's confirmation.
            if (!askedNullOpen) {
                askedNullOpen = true
                return Action.AskUser("Open $label yourself, then approve to continue.")
            }
            return null
        }
        if (snapshot.packageName == packageName) {
            openMisses = 0
            return null
        }
        if (!openedOnce) {
            openedOnce = true
            return Action.OpenApp(packageName, label)
        }
        openMisses++
        if (openMisses > 4) return Action.Complete("$label did not come to the foreground; stopping to avoid loops.")
        return Action.Wait(900)
    }

    private fun openApp(snapshot: UiSnapshot, intent: TaskIntent.OpenApp): Action {
        ensureOpened(snapshot, intent.packageName, intent.label)?.let { return it }
        return Action.Complete("${intent.label} is open.")
    }

    private fun search(snapshot: UiSnapshot, intent: TaskIntent.OpenAppAndSearch): Action {
        ensureOpened(snapshot, intent.packageName, intent.label)?.let { return it }

        val editable = snapshot.editableNodes().firstOrNull()
        if (editable == null) {
            val searchControl = snapshot.firstByTextOrDescContaining("search", clickableOnly = true)
            return searchControl?.let { Action.Tap("#${it.id}", "Open the search field") }
                ?: Action.AskUser("I can't find the search field in ${intent.label}. What should I do?")
        }
        if (!typed) {
            typed = true
            return Action.Type("#${editable.id}", intent.query)
        }
        if (!submitted) {
            submitted = true
            return Action.Key(Action.KeyAction.ENTER)
        }
        return Action.Complete("Searched ${intent.label} for \"${intent.query}\".")
    }

    private fun message(snapshot: UiSnapshot, intent: TaskIntent.MessageDraft): Action {
        ensureOpened(snapshot, intent.packageName, intent.label)?.let { return it }

        // 1. Open the in-app search.
        if (!searchOpened) {
            val searchControl = snapshot.firstByTextOrDescContaining("search", clickableOnly = true)
                ?: return Action.AskUser("I can't find the search button in ${intent.label}. What should I do?")
            searchOpened = true
            return Action.Tap("#${searchControl.id}", "Open contact search")
        }

        // 2. Type the contact name.
        if (!contactTyped) {
            val contact = intent.contact ?: return Action.AskUser("Which contact should I open?")
            val editable = snapshot.editableNodes().firstOrNull()
                ?: return Action.AskUser("I can't find the contact search field. What should I do?")
            contactTyped = true
            return Action.Type("#${editable.id}", contact)
        }

        // 3. Open the conversation.
        if (!conversationOpened) {
            val contact = intent.contact
            val row = snapshot.firstByTextOrDescContaining(contact ?: "", clickableOnly = true)
                ?: snapshot.clickableNodes().firstOrNull { it.id != snapshot.editableNodes().firstOrNull()?.id }
                ?: return Action.AskUser("I can't confidently identify the conversation. What should I do?")
            conversationOpened = true
            return Action.Tap("#${row.id}", "Open conversation")
        }

        // 4. Type the message.
        if (!messageTyped) {
            val message = intent.message.ifBlank { userAnswer ?: "" }
            if (message.isBlank()) return Action.AskUser("What message should I draft?")
            val field = snapshot.editableNodes().firstOrNull()
                ?: return Action.AskUser("I can't find the message field. What should I do?")
            messageTyped = true
            return Action.Type("#${field.id}", message)
        }

        // 5. Send (optional, high-risk) or finish as draft.
        if (intent.send && !sent) {
            val sendControl = snapshot.firstByTextOrDescContaining("send", clickableOnly = true)
                ?: return Action.AskUser("I can't find the send button. Send it manually?")
            sent = true
            return Action.Tap("#${sendControl.id}", "Send the message")
        }
        return Action.Complete(
            if (intent.send) "Message sent to ${intent.contact ?: "the contact"}."
            else "Draft composed for ${intent.contact ?: "the contact"} and left unsent."
        )
    }

    private fun generic(snapshot: UiSnapshot, intent: TaskIntent.Generic): Action {
        if (!askedOnce) {
            askedOnce = true
            return Action.AskUser(
                "I don't have a built-in routine for: \"${intent.description.take(80)}\". " +
                    "Add an AI provider in Settings to handle open-ended tasks, or give a more specific command."
            )
        }
        return Action.Fail("Open-ended task without a built-in routine or configured AI provider.")
    }
}
