package com.thereal.taskpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.thereal.taskpilot.ui.TaskPilotApp
import com.thereal.taskpilot.ui.theme.TaskPilotTheme

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
