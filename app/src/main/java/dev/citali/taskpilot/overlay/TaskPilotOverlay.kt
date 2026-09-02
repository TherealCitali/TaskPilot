package dev.citali.taskpilot.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.citali.taskpilot.agent.AgentEngine
import dev.citali.taskpilot.ui.theme.TaskPilotTheme

/**
 * Floating Stop control shown while a task is running.
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY, which is available to an enabled
 * AccessibilityService without the "display over other apps" permission.
 */
class TaskPilotOverlay(private val service: AccessibilityService) {

    private val windowManager: WindowManager =
        service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager

    private var view: ComposeView? = null
    private val statusState = mutableStateOf("Running")

    fun show(statusText: String) {
        statusState.value = statusText
        if (view != null) return
        val composeView = ComposeView(service).apply {
            setContent {
                TaskPilotTheme {
                    OverlayContent(statusState = statusState, onStop = { AgentEngine.stop() })
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 24.dpToPx()
        }
        runCatching { windowManager.addView(composeView, params) }.onSuccess {
            view = composeView
        }.onFailure {
            // Overlay is best-effort; the in-app live log still shows the Stop control.
        }
    }

    fun update(statusText: String) {
        statusState.value = statusText
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    private fun Int.dpToPx(): Int =
        (this * service.resources.displayMetrics.density).toInt()
}

@Composable
private fun OverlayContent(statusState: State<String>, onStop: () -> Unit) {
    val status by statusState
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
        shape = RoundedCornerShape(50),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f), RoundedCornerShape(50))
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "TaskPilot · $status",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                onClick = onStop,
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                shape = RoundedCornerShape(50),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Text("Stop", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
