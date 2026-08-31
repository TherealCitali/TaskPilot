package dev.citali.taskpilot.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent

/**
 * Entry point for observing accessible Android UI.
 *
 * The observe-think-act orchestration and action validator will be added behind this
 * service. This first scaffold deliberately does not inspect or log text from events.
 */
class TaskPilotAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TODO: Convert the current window into a redacted UI-tree snapshot.
        // Do not log raw event text: it may contain personal or sensitive data.
    }

    override fun onInterrupt() {
        // TODO: Cancel the active task and release any overlay controls.
    }
}
