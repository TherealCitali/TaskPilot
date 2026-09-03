@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.citali.taskpilot.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.citali.taskpilot.R
import dev.citali.taskpilot.accessibility.TaskPilotAccessibilityService
import dev.citali.taskpilot.agent.AgentEngine
import dev.citali.taskpilot.agent.CommandPlanner
import dev.citali.taskpilot.agent.Plan
import dev.citali.taskpilot.agent.PlanStep
import dev.citali.taskpilot.data.HistoryEntry
import dev.citali.taskpilot.data.HistoryStore
import dev.citali.taskpilot.data.SecureStore
import dev.citali.taskpilot.data.SettingsStore
import dev.citali.taskpilot.data.TaskPilotSettings
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class Destination(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Filled.Home),
    HISTORY("History", Icons.Filled.History),
    SETTINGS("Settings", Icons.Filled.Settings)
}

private enum class SettingsPage(
    val title: String,
) {
    ROOT("Settings"),
    SAFETY("Safety and permissions"),
    DEVELOPER("Developer options"),
    ABOUT("About and diagnostics")
}

@Composable
fun TaskPilotApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var settingsPageIndex by rememberSaveable { mutableIntStateOf(0) }
    var command by rememberSaveable { mutableStateOf("") }
    var pendingPlan by remember { mutableStateOf<Plan?>(null) }
    var serviceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var aiAvailable by remember { mutableStateOf(SecureStore.hasApiKey(context)) }

    val engineState by AgentEngine.state.collectAsState()
    val settings by SettingsStore.settings(context).collectAsState(initial = TaskPilotSettings())
    val history by HistoryStore.entries(context).collectAsState(initial = emptyList())

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = isAccessibilityServiceEnabled(context)
                aiAvailable = SecureStore.hasApiKey(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Engine-driven question (high-risk confirmation or open question).
    val question = engineState.question
    if (question != null) {
        QuestionDialog(
            question = question,
            onApprove = { text -> AgentEngine.answer(true, text) },
            onDecline = { AgentEngine.answer(false) },
        )
    }

    pendingPlan?.let { plan ->
        PlanPreviewScreen(
            plan = plan,
            onBack = { pendingPlan = null },
            onEdit = { pendingPlan = null },
            onApprove = {
                pendingPlan = null
                AgentEngine.start(context, plan)
                selectedTab = 0
            }
        )
        return
    }

    val destination = Destination.values()[selectedTab.coerceIn(0, Destination.values().lastIndex)]
    val settingsPage = SettingsPage.values()[settingsPageIndex.coerceIn(0, SettingsPage.values().lastIndex)]
    val isRunning = engineState.phase in setOf(
        AgentEngine.Phase.RUNNING,
        AgentEngine.Phase.WAITING_FOR_USER,
        AgentEngine.Phase.PAUSED,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (destination == Destination.SETTINGS) settingsPage.title else destination.label,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (destination == Destination.SETTINGS && settingsPageIndex != SettingsPage.ROOT.ordinal) {
                            Text(
                                "TaskPilot · careful automation",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (destination == Destination.SETTINGS && settingsPageIndex != SettingsPage.ROOT.ordinal) {
                        IconButton(onClick = { settingsPageIndex = 0 }) {
                            Text("‹", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Destination.values().forEachIndexed { index, dest ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index; settingsPageIndex = 0 },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (destination) {
                Destination.HOME -> HomeScreen(
                    aiAvailable = aiAvailable,
                    onOpenSettings = { selectedTab = Destination.SETTINGS.ordinal; settingsPageIndex = 0 },
                    command = command,
                    onCommandChange = { command = it },
                    onCreatePlan = {
                        val text = command.trim()
                        if (text.isNotBlank()) {
                            aiAvailable = SecureStore.hasApiKey(context)
                            pendingPlan = CommandPlanner.parse(text, aiAvailable)
                        }
                    },
                    onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    serviceEnabled = serviceEnabled,
                    engineState = engineState,
                    onPause = { AgentEngine.pause() },
                    onResume = { AgentEngine.resume() },
                    onStop = { AgentEngine.stop() },
                    onDismissOutcome = { AgentEngine.reset() },
                )
                Destination.HISTORY -> HistoryScreen(entries = history)
                Destination.SETTINGS -> when (settingsPage) {
                    SettingsPage.ROOT -> SettingsScreen(
                        settings = settings,
                        onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onOpenSafety = { settingsPageIndex = SettingsPage.SAFETY.ordinal },
                        onOpenDeveloper = { settingsPageIndex = SettingsPage.DEVELOPER.ordinal },
                        onOpenAbout = { settingsPageIndex = SettingsPage.ABOUT.ordinal },
                    )
                    SettingsPage.SAFETY -> SafetySettingsScreen(settings = settings)
                    SettingsPage.DEVELOPER -> DeveloperSettingsScreen(
                        settings = settings,
                        serviceEnabled = serviceEnabled,
                    )
                    SettingsPage.ABOUT -> AboutDiagnosticsScreen(serviceEnabled = serviceEnabled)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

@Composable
private fun HomeScreen(
    aiAvailable: Boolean,
    onOpenSettings: () -> Unit,
    command: String,
    onCommandChange: (String) -> Unit,
    onCreatePlan: () -> Unit,
    onOpenAccessibility: () -> Unit,
    serviceEnabled: Boolean,
    engineState: AgentEngine.EngineState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDismissOutcome: () -> Unit,
) {
    val examples = listOf(
        "Open YouTube and search for Minecraft tutorials.",
        "Open Chrome and search Kotlin Coroutines guide.",
        "Compose a WhatsApp message to John saying I'll be there in 10 minutes, but do not send it."
    )
    val isRunning = engineState.phase in setOf(
        AgentEngine.Phase.RUNNING,
        AgentEngine.Phase.WAITING_FOR_USER,
        AgentEngine.Phase.PAUSED,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { WelcomeHeader() }
        item {
            ServiceCard(
                enabled = serviceEnabled,
                onOpenAccessibility = onOpenAccessibility
            )
        }
        item { AiProviderCard(aiAvailable = aiAvailable, onOpenSettings = onOpenSettings) }
        item {
            CommandCard(
                command = command,
                onCommandChange = onCommandChange,
                onCreatePlan = onCreatePlan,
                enabled = serviceEnabled && !isRunning
            )
        }
        // The log must survive a finished run. Previously it was gated on
        // isRunning, so COMPLETED/FAILED/BLOCKED wiped the card the instant the
        // task ended and the screen looked like nothing had happened at all.
        val hasOutcome = engineState.phase in setOf(
            AgentEngine.Phase.COMPLETED,
            AgentEngine.Phase.FAILED,
            AgentEngine.Phase.BLOCKED,
            AgentEngine.Phase.STOPPED,
        )
        if (isRunning) {
            item {
                RunningTaskCard(
                    engineState = engineState,
                    onPause = onPause,
                    onResume = onResume,
                    onStop = onStop,
                )
            }
        } else if (hasOutcome) {
            item { TaskOutcomeCard(engineState = engineState, onDismiss = onDismissOutcome) }
        }
        if (isRunning || hasOutcome) {
            item { LiveLogCard(engineState) }
        }
        item {
            SectionHeading(
                title = "Try a task",
                subtitle = "Start with a clear destination and outcome."
            )
        }
        items(examples) { example ->
            ExampleRow(text = example, onClick = { onCommandChange(example) })
        }
        item { PrivacyCallout() }
    }
}

/**
 * Makes the online/offline distinction explicit. Without a provider TaskPilot can
 * only run its few built-in routines, which previously looked like "the app only
 * understands the examples".
 */
@Composable
private fun AiProviderCard(aiAvailable: Boolean, onOpenSettings: () -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (aiAvailable) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
            }
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (aiAvailable) Icons.Rounded.AutoAwesome else Icons.Outlined.Info,
                contentDescription = null
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (aiAvailable) "AI provider connected" else "No AI provider",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (aiAvailable) {
                        "TaskPilot can follow your own instructions."
                    } else {
                        "Only the built-in example routines will run. Add a key to use your own commands."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!aiAvailable) {
                TextButton(onClick = onOpenSettings) { Text("Add key") }
            }
        }
    }
}

@Composable
private fun WelcomeHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.taskpilot_icon),
                    contentDescription = "TaskPilot icon",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Column {
                Text(
                    text = "Good to see you",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Give your next task a destination.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServiceCard(enabled: Boolean, onOpenAccessibility: () -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            }
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccessibilityNew,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onSecondary
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (enabled) "Accessibility is ready" else "Connect Accessibility Service",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (enabled) "TaskPilot can observe the current UI."
                    else "Allow TaskPilot to observe and act only after you approve a plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!enabled) {
                FilledTonalButton(onClick = onOpenAccessibility) { Text("Set up") }
            } else {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Connected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CommandCard(
    command: String,
    onCommandChange: (String) -> Unit,
    onCreatePlan: () -> Unit,
    enabled: Boolean
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "What should I do?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "TaskPilot will build a plan before acting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            OutlinedTextField(
                value = command,
                onValueChange = onCommandChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                placeholder = { Text("Open an app, find something, or complete a task…") },
                shape = MaterialTheme.shapes.medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Plan first · act once at a time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onCreatePlan,
                    enabled = enabled && command.isNotBlank()
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Create plan")
                }
            }
        }
    }
}

@Composable
private fun RunningTaskCard(
    engineState: AgentEngine.EngineState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val paused = engineState.phase == AgentEngine.Phase.PAUSED
    val waiting = engineState.phase == AgentEngine.Phase.WAITING_FOR_USER
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (paused) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (paused) "Paused" else if (waiting) "Waiting for your decision" else "Task is running",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        engineState.statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = if (paused) onResume else onPause,
                    enabled = !waiting
                ) {
                    Icon(
                        imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(if (paused) "Resume" else "Pause")
                }
                Button(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Stop")
                }
            }
        }
    }
}

/**
 * Terminal-state summary. A finished run has to stay on screen with its reason,
 * otherwise a task that stopped immediately is indistinguishable from one that
 * never started.
 */
@Composable
private fun TaskOutcomeCard(engineState: AgentEngine.EngineState, onDismiss: () -> Unit) {
    val failed = engineState.phase in setOf(
        AgentEngine.Phase.FAILED,
        AgentEngine.Phase.BLOCKED,
    )
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (failed) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            }
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (failed) Icons.Outlined.WarningAmber else Icons.Filled.CheckCircle,
                    contentDescription = null
                )
                Text(
                    when (engineState.phase) {
                        AgentEngine.Phase.COMPLETED -> "Task complete"
                        AgentEngine.Phase.FAILED -> "Task failed"
                        AgentEngine.Phase.BLOCKED -> "Task blocked"
                        else -> "Task stopped"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(engineState.statusText, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun LiveLogCard(engineState: AgentEngine.EngineState) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text("Live log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            val entries = engineState.log.takeLast(14)
            if (entries.isEmpty()) {
                Text(
                    "Actions will appear here as they run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                entries.forEach { entry ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            time(entry.timeMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            entry.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = logColor(entry.level),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun logColor(level: AgentEngine.LogLevel): Color = when (level) {
    AgentEngine.LogLevel.ERROR -> MaterialTheme.colorScheme.error
    AgentEngine.LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    AgentEngine.LogLevel.ACTION -> MaterialTheme.colorScheme.primary
    AgentEngine.LogLevel.MODEL -> MaterialTheme.colorScheme.secondary
    AgentEngine.LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExampleRow(text: String, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Use example")
        }
    }
}

@Composable
private fun PrivacyCallout() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Private by design", fontWeight = FontWeight.SemiBold)
                Text(
                    "Likely sensitive UI values are redacted before the accessibility tree is shared with the configured AI endpoint.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Plan preview
// ---------------------------------------------------------------------------

@Composable
private fun PlanPreviewScreen(
    plan: Plan,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onApprove: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Plan preview", fontWeight = FontWeight.SemiBold)
                        Text(
                            "No actions have run",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit command")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "You approve once. TaskPilot will ask again before high-risk actions.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Approve plan & run")
                    }
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your command", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(plan.command, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                SectionHeading("Proposed steps", "One validated action will run at a time.")
            }
            items(plan.steps) { step ->
                PlanStepRow(step)
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            "TaskPilot will pause instead of guessing if the screen, target, or requested action is unclear.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanStepRow(step: PlanStep) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (step.highRisk) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (step.highRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (step.highRisk) Icons.Outlined.WarningAmber else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (step.highRisk) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(step.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (step.highRisk) {
                        Text(
                            "CONFIRM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(step.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------

@Composable
private fun HistoryScreen(entries: List<HistoryEntry>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                Text("No tasks yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Approved tasks and their redacted outcomes will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeading("Recent tasks", "Newest first.")
                TextButton(onClick = { scope.launch { HistoryStore.clear(context) } }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Clear")
                }
            }
        }
        items(entries) { entry ->
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            entry.command,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        StatusChip(entry.status)
                    }
                    Text(
                        time(entry.completedAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (container, content) = when (status) {
        "completed" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "failed", "blocked" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

@Composable
private fun SettingsScreen(
    settings: TaskPilotSettings,
    onOpenAccessibility: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var endpoint by remember(settings.endpointUrl) { mutableStateOf(settings.endpointUrl) }
    var model by remember(settings.model) { mutableStateOf(settings.model) }
    var apiKey by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(SecureStore.hasApiKey(context)) }
    var saved by remember { mutableStateOf(false) }
    var keyError by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeading("Provider connection", "Use an OpenAI-compatible endpoint you control or trust.")
        }
        item {
            ElevatedCard(shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it; saved = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API base URL") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it; saved = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Model") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; saved = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API key") },
                        placeholder = { Text(if (hasKey) "Stored (Keystore-encrypted). Enter a new key to replace it." else "Stored securely on device") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                SettingsStore.setProvider(context, endpoint, model)
                                if (apiKey.isNotBlank()) {
                                    val ok = SecureStore.saveApiKey(context, apiKey.trim())
                                    keyError = !ok
                                    if (ok) {
                                        apiKey = ""
                                        hasKey = true
                                    }
                                } else {
                                    keyError = false
                                }
                                // Re-read rather than assuming: hasApiKey now
                                // verifies the key can actually be decrypted.
                                hasKey = SecureStore.hasApiKey(context)
                                saved = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (saved && !keyError) "Settings saved" else "Save provider settings")
                    }
                    Text(
                        when {
                            keyError -> "The key could not be stored securely on this device. Try again, or check that a screen lock is set."
                            hasKey -> "API key is saved on this device, encrypted with a Keystore-backed key. It persists until you replace or clear it."
                            else -> "No API key saved yet. TaskPilot needs an AI provider to handle your own commands."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (keyError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasKey) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    SecureStore.clearApiKey(context)
                                    hasKey = false
                                    saved = false
                                    keyError = false
                                }
                            }
                        ) {
                            Text("Remove stored key")
                        }
                    }
                }
            }
        }
        item {
            SettingsActionCard(
                icon = Icons.Outlined.AccessibilityNew,
                title = "Accessibility permission",
                subtitle = "Allow TaskPilot to observe and operate accessible UI.",
                action = "Open settings",
                onClick = onOpenAccessibility
            )
        }
        item {
            SettingsActionCard(
                icon = Icons.Filled.Tune,
                title = "Safety and permissions",
                subtitle = "Manage risk confirmations, sensitive-field handling, and allowed actions.",
                action = "Configure",
                onClick = onOpenSafety
            )
        }
        item {
            SettingsActionCard(
                icon = Icons.Outlined.Code,
                title = "Debug / Developer options",
                subtitle = "Open redacted tree summaries, overlay status, and action validation results.",
                action = "Open",
                onClick = onOpenDeveloper
            )
        }
        item {
            SettingsActionCard(
                icon = Icons.Outlined.Info,
                title = "About and diagnostics",
                subtitle = "TaskPilot 0.2.0 · agent connected",
                action = "View",
                onClick = onOpenAbout
            )
        }
    }
}

@Composable
private fun SafetySettingsScreen(settings: TaskPilotSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeading(
                "Safety defaults",
                "TaskPilot pauses instead of guessing when a task could cause harm or confusion."
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.WarningAmber,
                title = "Confirm high-risk actions",
                subtitle = "Ask again before deletes, sends, purchases, permissions, and critical settings.",
                checked = settings.highRiskConfirmations,
                onCheckedChange = { scope.launch { SettingsStore.setHighRiskConfirmations(context, it) } }
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Lock,
                title = "Redact sensitive values",
                subtitle = "Mask passwords, PINs, tokens, payment data, and likely personal identifiers before AI transmission.",
                checked = settings.redactSensitiveValues,
                onCheckedChange = { scope.launch { SettingsStore.setRedactSensitiveValues(context, it) } }
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.HelpOutline,
                title = "Pause on ambiguity",
                subtitle = "Stop and ask a question when the target, UI state, or intended action is not clear.",
                checked = settings.pauseOnAmbiguity,
                onCheckedChange = { scope.launch { SettingsStore.setPauseOnAmbiguity(context, it) } }
            )
        }
        item {
            ElevatedCard(shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Always protected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Passwords, OTPs, banking credentials, card details, UPI PINs, recovery codes, authentication tokens, and government IDs are never handled automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Text(
                        "A user decision can allow a one-time manual interaction, but the value remains outside AI context and persistent history.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DeveloperSettingsScreen(
    settings: TaskPilotSettings,
    serviceEnabled: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeading(
                "Diagnostics",
                "Developer tools stay redacted and are intended for testing the agent safely."
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Code,
                title = "Show redacted tree summary",
                subtitle = "Display node counts and package names in the live log.",
                checked = settings.showTreeSummary,
                onCheckedChange = { scope.launch { SettingsStore.setShowTreeSummary(context, it) } }
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Filled.Security,
                title = "Show action validation",
                subtitle = "Explain why an action was accepted, paused, or rejected by the safety layer.",
                checked = settings.showValidation,
                onCheckedChange = { scope.launch { SettingsStore.setShowValidation(context, it) } }
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "Show floating Stop control",
                subtitle = "Keep the overlay visible while a task is active.",
                checked = settings.showOverlay,
                onCheckedChange = { scope.launch { SettingsStore.setShowOverlay(context, it) } }
            )
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        if (serviceEnabled) Icons.Filled.CheckCircle else Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = if (serviceEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        if (serviceEnabled) "Accessibility service is connected; redacted snapshots are available."
                        else "Accessibility service is not connected — enable it to observe UI.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Raw accessibility text, screenshots, API keys, and sensitive task values are never written to diagnostics.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutDiagnosticsScreen(serviceEnabled: Boolean) {
    val context = LocalContext.current
    var diagnosticsRun by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TaskPilot", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Careful automation for Android", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Version 0.2.0 · agent connected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item {
            ElevatedCard(shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    DetailInfoRow("Application ID", "dev.citali.taskpilot")
                    HorizontalDivider()
                    DetailInfoRow("Android support", "Android 10+ · API 29")
                    HorizontalDivider()
                    DetailInfoRow("Build output", "4 signed ABI APKs")
                    HorizontalDivider()
                    DetailInfoRow("Execution model", "Observe · Think · Validate · Act")
                }
            }
        }
        item {
            ElevatedCard(shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Column {
                            Text("Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Check local app configuration and service readiness.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(onClick = { diagnosticsRun = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (diagnosticsRun) "Run diagnostics again" else "Run diagnostics")
                    }
                    if (diagnosticsRun) {
                        HorizontalDivider()
                        CheckRow("Accessibility service", serviceEnabled)
                        CheckRow("API key stored (Keystore)", SecureStore.hasApiKey(context))
                        CheckRow("Overlay available", serviceEnabled)
                        CheckRow("History store", true)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            if (ok) "OK" else "Not ready",
            style = MaterialTheme.typography.labelMedium,
            color = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun SettingsToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ElevatedCard(shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.1f))
    }
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit
) {
    ElevatedCard(onClick = onClick, shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------------------------------------------------------------------------
// Question dialog (engine-driven)
// ---------------------------------------------------------------------------

@Composable
private fun QuestionDialog(
    question: AgentEngine.Question,
    onApprove: (String?) -> Unit,
    onDecline: () -> Unit,
) {
    var answerText by remember(question) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Text(if (question.highRisk) "Confirmation required" else "TaskPilot needs an answer")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = question.text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!question.highRisk) {
                    OutlinedTextField(
                        value = answerText,
                        onValueChange = { answerText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your answer") },
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApprove(answerText.takeIf { it.isNotBlank() }) }) {
                Text(if (question.highRisk) "Approve" else "Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(if (question.highRisk) "Decline" else "Cancel task") }
        }
    )
}

// ---------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val expected = ComponentName(context, TaskPilotAccessibilityService::class.java).flattenToString()
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}

private fun time(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
