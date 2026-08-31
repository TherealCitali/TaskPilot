package dev.citali.taskpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import dev.citali.taskpilot.ui.TaskPilotApp
import dev.citali.taskpilot.ui.theme.TaskPilotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            TaskPilotTheme {
                TaskPilotApp()
            }
        }
    }
}
