package dev.citali.taskpilot.agent

import android.content.Context
import dev.citali.taskpilot.accessibility.TaskPilotAccessibilityService
import dev.citali.taskpilot.data.HistoryStore
import dev.citali.taskpilot.data.SecureStore
import dev.citali.taskpilot.data.SettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * The one-action control loop: observe the UI, think of one action, validate it
 * against the safety policy, execute it, then observe again.
 *
 * State is exposed through [state] so the Compose UI and the floating overlay
 * can render the same truth.
 */
object AgentEngine {

    enum class Phase { IDLE, RUNNING, WAITING_FOR_USER, PAUSED, BLOCKED, COMPLETED, FAILED, STOPPED }

    enum class LogLevel { INFO, ACTION, MODEL, WARN, ERROR }

    data class LogEntry(val timeMillis: Long, val level: LogLevel, val message: String)

    data class Question(val text: String, val highRisk: Boolean)

    data class EngineState(
        val phase: Phase = Phase.IDLE,
        val command: String = "",
        val statusText: String = "Ready",
        val log: List<LogEntry> = emptyList(),
        val question: Question? = null,
        val serviceConnected: Boolean = TaskPilotAccessibilityService.isConnected(),
    )

    private data class QuestionAnswer(val approved: Boolean, val text: String?)

    private sealed interface ExecResult {
        object Ok : ExecResult
        data class Complete(val summary: String) : ExecResult
        data class Fail(val reason: String) : ExecResult
    }

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    /**
     * Last-resort net. The loop already handles its own failures, but anything
     * that escapes a coroutine on this scope would otherwise reach the default
     * uncaught handler and kill the process mid-task. Surface it as a failed
     * run instead.
     */
    private val crashGuard = CoroutineExceptionHandler { _, error ->
        if (error is CancellationException) return@CoroutineExceptionHandler
        runCatching {
            log(LogLevel.ERROR, "Unexpected error: ${error.message ?: error::class.java.simpleName}")
            finish(Phase.FAILED, "TaskPilot stopped after an unexpected error.")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
    private var job: Job? = null

    @Volatile
    private var paused = false

    private var pendingQuestion: CompletableDeferred<QuestionAnswer>? = null

    fun isActive(): Boolean = job?.isActive == true

    fun start(context: Context, plan: Plan) {
        if (isActive()) cancelJob()
        paused = false
        pendingQuestion = null
        _state.value = EngineState(
            phase = Phase.RUNNING,
            command = plan.command,
            statusText = "Starting…",
            serviceConnected = TaskPilotAccessibilityService.isConnected(),
        )
        log(LogLevel.INFO, "Task approved: ${plan.command}")
        plan.steps.forEach { step ->
            log(LogLevel.INFO, "• ${step.title}" + if (step.highRisk) "  [high-risk]" else "")
        }
        job = scope.launch { runLoop(context.applicationContext, plan) }
    }

    fun stop() {
        if (!isActive()) return
        log(LogLevel.WARN, "Stop requested.")
        finish(Phase.STOPPED, "Stopped by user.")
        cancelJob()
    }

    fun pause() {
        if (!isActive()) return
        paused = true
        _state.value = _state.value.copy(phase = Phase.PAUSED, statusText = "Paused")
    }

    fun resume() {
        paused = false
        if (job?.isActive == true && _state.value.phase == Phase.PAUSED) {
            _state.value = _state.value.copy(phase = Phase.RUNNING, statusText = "Resuming…")
        }
    }

    fun answer(approved: Boolean, text: String? = null) {
        val deferred = pendingQuestion ?: return
        if (deferred.isCompleted) return
        pendingQuestion = null
        _state.value = _state.value.copy(question = null, statusText = "Continuing…")
        deferred.complete(QuestionAnswer(approved, text))
    }

    fun onServiceConnected(connected: Boolean) {
        _state.value = _state.value.copy(serviceConnected = connected)
        if (!connected && isActive()) {
            log(LogLevel.ERROR, "Accessibility service disconnected.")
            finish(Phase.BLOCKED, "Re-enable TaskPilot in Accessibility settings.")
            cancelJob()
        }
    }

    fun onInterrupted() {
        if (isActive()) {
            log(LogLevel.WARN, "System interrupted the accessibility service.")
            pause()
        }
    }

    // ---- Loop -------------------------------------------------------------

    private suspend fun runLoop(context: Context, plan: Plan) {
        val settings = SettingsStore.snapshot(context)
        val deterministic = DeterministicExecutor(plan.intent)
        val recent = ArrayDeque<String>()
        var consecutiveFailures = 0
        var lastSignature: String? = null
        var stuckCount = 0
        var highRiskApprovedFor: String? = null

        if (settings.showOverlay) {
            TaskPilotAccessibilityService.instance?.showOverlay("Running")
        }

        try {
            while (coroutineContext.isActive) {
                while (paused && coroutineContext.isActive) {
                    delay(250)
                }

                val service = TaskPilotAccessibilityService.instance
                if (service == null) {
                    finish(Phase.BLOCKED, "Enable TaskPilot in Accessibility settings, then run the task again.")
                    record(context, plan.command, "blocked")
                    return
                }

                val snapshot = runCatching { service.captureSnapshot(settings.redactSensitiveValues) }
                    .onFailure { log(LogLevel.WARN, "Could not read the screen; retrying.") }
                    .getOrNull()
                if (snapshot == null) {
                    delay(600)
                    continue
                }
                if (settings.showTreeSummary) {
                    log(
                        LogLevel.INFO,
                        "Tree: ${snapshot.nodeCount} nodes in ${snapshot.packageName ?: "unknown"} (redacted)."
                    )
                }

                val action = nextAction(context, settings, plan, snapshot, recent, deterministic)

                when (action) {
                    is Action.Complete -> {
                        log(LogLevel.INFO, "Done: ${action.summary}")
                        finish(Phase.COMPLETED, action.summary)
                        record(context, plan.command, "completed")
                        return
                    }
                    is Action.Fail -> {
                        log(LogLevel.ERROR, action.reason)
                        finish(Phase.FAILED, action.reason)
                        record(context, plan.command, "failed")
                        return
                    }
                    else -> Unit
                }

                // Validate against the safety policy before executing.
                val targetDescription = action.nodeTarget?.let { service.describeNode(it) }
                val decision = SafetyPolicy.assess(action, targetDescription)
                if (settings.showValidation) {
                    log(LogLevel.INFO, "Validation [${decision.level}]: ${decision.reason}")
                }
                when (decision.level) {
                    RiskLevel.BLOCKED -> {
                        log(LogLevel.ERROR, decision.reason)
                        finish(Phase.BLOCKED, decision.reason)
                        record(context, plan.command, "blocked")
                        return
                    }
                    RiskLevel.HIGH_RISK -> {
                        val key = actionKey(action)
                        if (settings.highRiskConfirmations && highRiskApprovedFor != key) {
                            val answer = awaitAnswer(
                                Question(
                                    "${decision.reason}\n\n${describeAction(action)}",
                                    highRisk = true,
                                )
                            )
                            if (!answer.approved) {
                                log(LogLevel.WARN, "High-risk action declined; stopping.")
                                finish(Phase.STOPPED, "High-risk action declined.")
                                record(context, plan.command, "stopped")
                                return
                            }
                            highRiskApprovedFor = key
                            log(LogLevel.INFO, "High-risk action approved for this step.")
                        }
                    }
                    else -> Unit
                }

                if (action is Action.AskUser) {
                    val answer = awaitAnswer(Question(action.question, highRisk = false))
                    if (!answer.approved) {
                        log(LogLevel.WARN, "Question declined; stopping.")
                        finish(Phase.STOPPED, "Task cancelled.")
                        record(context, plan.command, "stopped")
                        return
                    }
                    answer.text?.takeIf { it.isNotBlank() }?.let {
                        deterministic.onUserAnswer(it)
                        recent.add("user answered: ${it.take(80)}")
                    }
                    continue
                }

                if (action is Action.Wait) {
                    delay(action.millis.coerceIn(100L, 5000L))
                    continue
                }

                // Execute exactly one action.
                val description = describeAction(action)
                log(LogLevel.ACTION, description)
                recent.add(description)
                val ok = runCatching { service.execute(action) }
                    .onFailure { log(LogLevel.WARN, "Action threw: ${it.message}") }
                    .getOrDefault(false)
                if (!ok) {
                    consecutiveFailures++
                    log(LogLevel.WARN, "Action did not succeed ($consecutiveFailures/5).")
                } else {
                    consecutiveFailures = 0
                }
                if (consecutiveFailures >= 5) {
                    log(LogLevel.ERROR, "Five consecutive failures; stopping to avoid a loop.")
                    finish(Phase.FAILED, "Five consecutive failed actions.")
                    record(context, plan.command, "failed")
                    return
                }

                // Stuck-screen detection.
                val signature = snapshot.signature
                if (signature == lastSignature) {
                    stuckCount++
                } else {
                    stuckCount = 0
                    lastSignature = signature
                }
                if (stuckCount >= 3) {
                    val answer = awaitAnswer(
                        Question(
                            "The screen does not seem to change after repeated actions. Continue anyway?",
                            highRisk = false,
                        )
                    )
                    if (!answer.approved) {
                        finish(Phase.STOPPED, "Stopped because the screen appeared stuck.")
                        record(context, plan.command, "stopped")
                        return
                    }
                    stuckCount = 0
                }

                if (settings.showOverlay) {
                    service.updateOverlay(description.take(40))
                }
                delay(850)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            log(LogLevel.ERROR, "Unexpected error: ${t.message}")
            finish(Phase.FAILED, "Unexpected error: ${t.message}")
            record(context, plan.command, "failed")
        } finally {
            TaskPilotAccessibilityService.instance?.hideOverlay()
        }
    }

    private suspend fun nextAction(
        context: Context,
        settings: dev.citali.taskpilot.data.TaskPilotSettings,
        plan: Plan,
        snapshot: dev.citali.taskpilot.accessibility.UiSnapshot,
        recent: List<String>,
        deterministic: DeterministicExecutor,
    ): Action {
        val apiKey = SecureStore.getApiKey(context)
        if (!apiKey.isNullOrBlank() && settings.endpointUrl.isNotBlank() && settings.model.isNotBlank()) {
            val result = LlmClient.complete(
                settings.endpointUrl,
                settings.model,
                apiKey,
                listOf(
                    LlmClient.systemPrompt(),
                    LlmClient.userPrompt(plan.command, plan.steps, snapshot.toPromptString(), recent),
                ),
            )
            result.onSuccess { raw ->
                val snippet = raw.trim().replace('\n', ' ').take(160)
                log(LogLevel.MODEL, "Model: $snippet")
                val action = LlmClient.parseAction(raw)
                if (action != null) return action
                log(LogLevel.WARN, "Model reply could not be parsed; using built-in executor.")
            }.onFailure { error ->
                log(LogLevel.WARN, "AI provider error: ${error.message}")
            }
        }
        return deterministic.next(snapshot)
    }

    // ---- Helpers ----------------------------------------------------------

    private suspend fun awaitAnswer(question: Question): QuestionAnswer {
        val deferred = CompletableDeferred<QuestionAnswer>()
        pendingQuestion = deferred
        _state.value = _state.value.copy(
            phase = Phase.WAITING_FOR_USER,
            question = question,
            statusText = "Waiting for your decision",
        )
        return deferred.await()
    }

    private fun describeAction(action: Action): String = when (action) {
        is Action.Tap -> "Tap ${action.target}" + action.description.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        is Action.LongPress -> "Long-press ${action.target}"
        is Action.Swipe -> "Swipe ${action.direction.name.lowercase()}"
        is Action.Type -> "Type ${action.text.length} character(s) into ${action.target}"
        is Action.Key -> "Press ${action.key.name.lowercase()}"
        is Action.OpenApp -> "Open ${action.label.ifBlank { action.packageName }}"
        is Action.Wait -> "Wait ${action.millis} ms"
        is Action.AskUser -> "Ask: ${action.question.take(60)}"
        is Action.Complete -> "Complete: ${action.summary.take(60)}"
        is Action.Fail -> "Fail: ${action.reason.take(60)}"
    }

    private fun actionKey(action: Action): String = when (action) {
        is Action.Tap -> "tap:${action.target}"
        is Action.LongPress -> "longpress:${action.target}"
        is Action.Type -> "type:${action.target}"
        is Action.Swipe -> "swipe:${action.direction.name}"
        is Action.Key -> "key:${action.key.name}"
        is Action.OpenApp -> "open:${action.packageName}"
        else -> action.javaClass.simpleName
    }

    private fun log(level: LogLevel, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, message)
        _state.value = _state.value.copy(
            log = (_state.value.log + entry).takeLast(200),
            statusText = message.take(120),
        )
    }

    private fun finish(phase: Phase, statusText: String) {
        _state.value = _state.value.copy(phase = phase, statusText = statusText, question = null)
        TaskPilotAccessibilityService.instance?.hideOverlay()
    }

    private fun record(context: Context, command: String, status: String) {
        scope.launch { runCatching { HistoryStore.record(context, command, status) } }
    }

    private fun cancelJob() {
        job?.cancel()
        job = null
    }
}
