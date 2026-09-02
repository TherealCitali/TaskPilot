package dev.citali.taskpilot.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.citali.taskpilot.agent.Action
import dev.citali.taskpilot.agent.AgentEngine
import dev.citali.taskpilot.overlay.TaskPilotOverlay
import java.util.Locale

/**
 * Observes the accessible UI and executes exactly one validated action at a time.
 *
 * The service only exposes redacted snapshots via [SnapshotBuilder] and keeps a
 * single process-wide [instance] so the agent engine can drive it. It never
 * writes raw event text anywhere.
 */
class TaskPilotAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: TaskPilotAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlay: TaskPilotOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        instance = this
        AgentEngine.onServiceConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The agent loop re-captures the tree on its own cadence. Events are
        // intentionally not logged: raw event text may contain personal data.
    }

    override fun onInterrupt() {
        AgentEngine.onInterrupted()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hideOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideOverlay()
        if (instance === this) instance = null
        AgentEngine.onServiceConnected(false)
        super.onDestroy()
    }

    // ---- Snapshotting -----------------------------------------------------

    fun captureSnapshot(redact: Boolean = true): UiSnapshot =
        SnapshotBuilder.build(currentRoot(), redact)

    private fun currentRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { return it }
        return windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }?.root
    }

    // ---- Node resolution --------------------------------------------------

    /** Resolves an action target: "#12" means node id 12, otherwise a text / desc / resource-id match. */
    fun resolveNode(target: String): AccessibilityNodeInfo? {
        val root = currentRoot() ?: return null
        val walk = SnapshotBuilder.walkDfs(root)
        if (target.startsWith("#")) {
            val index = target.substring(1).toIntOrNull() ?: return null
            return walk.getOrNull(index)?.first
        }
        val q = target.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return null
        for ((info, _) in walk) {
            val resId = info.viewIdResourceName?.substringAfterLast('/')?.lowercase(Locale.ROOT)
            if (resId != null && (resId == q || resId.endsWith(q))) return info
        }
        for ((info, _) in walk) {
            if (info.text?.toString()?.contains(target, ignoreCase = true) == true) return info
            if (info.contentDescription?.toString()?.contains(target, ignoreCase = true) == true) return info
        }
        return null
    }

    /** Human-readable description of a target, used by the safety policy. */
    fun describeNode(target: String): String {
        val node = resolveNode(target) ?: return target
        val parts = mutableListOf<String>()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { parts.add("\"${it.take(40)}\"") }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { parts.add("desc=\"${it.take(40)}\"") }
        node.viewIdResourceName?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { parts.add("id=$it") }
        node.className?.toString()?.substringAfterLast('.')?.let { parts.add(it) }
        return parts.joinToString(" ")
    }

    // ---- Action execution -------------------------------------------------

    /** Executes a physical action. Returns true if it was dispatched successfully. */
    fun execute(action: Action): Boolean = when (action) {
        is Action.Tap -> clickTarget(action.target, long = false)
        is Action.LongPress -> clickTarget(action.target, long = true)
        is Action.Swipe -> swipe(action.direction)
        is Action.Type -> typeInto(action.target, action.text)
        is Action.Key -> globalKey(action.key)
        is Action.OpenApp -> launchApp(action.packageName)
        is Action.Wait, is Action.AskUser, is Action.Complete, is Action.Fail -> false
    }

    private fun clickTarget(target: String, long: Boolean): Boolean {
        val node = resolveNode(target) ?: return false
        return clickNode(node, long)
    }

    private fun clickNode(info: AccessibilityNodeInfo, long: Boolean): Boolean {
        val actionId = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        if (info.performAction(actionId)) return true
        val bounds = Rect()
        info.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            tapAt(bounds.exactCenterX(), bounds.exactCenterY(), if (long) 600L else 60L)
            return true
        }
        return false
    }

    private fun typeInto(target: String, text: String): Boolean {
        val node = resolveNode(target) ?: return false
        if (!node.isEditable) return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun globalKey(key: Action.KeyAction): Boolean = when (key) {
        Action.KeyAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
        Action.KeyAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
        Action.KeyAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        Action.KeyAction.ENTER -> imeEnter()
    }

    /**
     * Best-effort submit: find a clickable control labelled as the enter/search key,
     * first in the app window and then in the soft-keyboard (IME) window, and tap it.
     */
    private fun imeEnter(): Boolean {
        val labels = listOf("search", "go", "enter", "done", "send", "submit", "next")
        fun matches(info: AccessibilityNodeInfo): Boolean {
            if (!info.isClickable) return false
            val text = info.text?.toString()?.trim().orEmpty()
            val desc = info.contentDescription?.toString()?.trim().orEmpty()
            val resId = info.viewIdResourceName?.substringAfterLast('/').orEmpty()
            return labels.any { text.equals(it, ignoreCase = true) || desc.equals(it, ignoreCase = true) } ||
                labels.any { resId.contains(it, ignoreCase = true) }
        }
        for ((info, _) in SnapshotBuilder.walkDfs(currentRoot())) {
            if (matches(info)) return clickNode(info, long = false)
        }
        for (window in windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                for ((info, _) in SnapshotBuilder.walkDfs(window.root)) {
                    if (matches(info)) return clickNode(info, long = false)
                }
            }
        }
        return false
    }

    private fun launchApp(packageName: String): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    private fun swipe(direction: Action.SwipeDirection): Boolean {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        val (sx, sy, ex, ey) = when (direction) {
            Action.SwipeDirection.UP -> floatArrayOf(w / 2, h * 0.78f, w / 2, h * 0.28f)
            Action.SwipeDirection.DOWN -> floatArrayOf(w / 2, h * 0.28f, w / 2, h * 0.78f)
            Action.SwipeDirection.LEFT -> floatArrayOf(w * 0.82f, h / 2, w * 0.18f, h / 2)
            Action.SwipeDirection.RIGHT -> floatArrayOf(w * 0.18f, h / 2, w * 0.82f, h / 2)
        }
        gesture(sx, sy, ex, ey, 400L)
        return true
    }

    private fun tapAt(x: Float, y: Float, durationMs: Long) = gesture(x, y, x, y, durationMs)

    private fun gesture(sx: Float, sy: Float, ex: Float, ey: Float, durationMs: Long) {
        val path = Path().apply { moveTo(sx, sy); lineTo(ex, ey) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, mainHandler)
    }

    // ---- Floating Stop control -------------------------------------------

    fun showOverlay(status: String) {
        if (overlay == null) overlay = TaskPilotOverlay(this)
        overlay?.show(status)
    }

    fun updateOverlay(status: String) {
        overlay?.update(status)
    }

    fun hideOverlay() {
        overlay?.hide()
        overlay = null
    }
}
