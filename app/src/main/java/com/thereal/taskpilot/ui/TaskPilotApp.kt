@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.thereal.taskpilot.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.thereal.taskpilot.accessibility.TaskPilotAccessibilityService

private enum class Destination(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Filled.Home),
    HISTORY("History", Icons.Filled.History, Icons.Filled.History),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Filled.Settings)
}

private data class PlanStep(
    val title: String,
    val detail: String,
    val highRisk: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskPilotApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var command by rememberSaveable { mutableStateOf("") }
    var showPlan by rememberSaveable { mutableStateOf(false) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var serviceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var showQuestion by rememberSaveable { mutableStateOf(false) }
    var questionAnswer by rememberSaveable { mutableStateOf("") }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showPlan) {
        PlanPreviewScreen(
            command = command,
            onBack = { showPlan = false },
            onEdit = { showPlan = false },
            onApprove = {
                showPlan = false
                isRunning = true
                selectedTab = 0
            }
        )
        return
    }

    val destination = Destination.values()[selectedTab.coerceIn(0, Destination.values().lastIndex)]
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "TaskPilot · careful automation",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Destination.values().forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) item.selectedIcon else item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (isRunning) {
                ExtendedFloatingActionButton(
                    onClick = { isRunning = false },
                    icon = { Icon(Icons.Filled.Stop, contentDescription = null) },
                    text = { Text("Stop task") },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (destination) {
            Destination.HOME -> HomeScreen(
                modifier = Modifier.padding(paddingValues),
                command = command,
                onCommandChange = { command = it },
                onCreatePlan = { showPlan = true },
                onOpenAccessibility = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                serviceEnabled = serviceEnabled,
                isRunning = isRunning,
                onAskQuestion = { showQuestion = true }
            )

            Destination.HISTORY -> HistoryScreen(Modifier.padding(paddingValues))
            Destination.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(paddingValues),
                onOpenAccessibility = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        }
    }

    if (showQuestion) {
        AlertDialog(
            onDismissRequest = { showQuestion = false },
            title = { Text("TaskPilot needs an answer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "The live question overlay will appear here when the agent needs clarification.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = questionAnswer,
                        onValueChange = { questionAnswer = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your answer") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showQuestion = false }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showQuestion = false }) { Text("Cancel task") }
            }
        )
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    command: String,
    onCommandChange: (String) -> Unit,
    onCreatePlan: () -> Unit,
    onOpenAccessibility: () -> Unit,
    serviceEnabled: Boolean,
    isRunning: Boolean,
    onAskQuestion: () -> Unit
) {
    val examples = listOf(
        "Open YouTube and search for Minecraft tutorials.",
        "Open Chrome and search Kotlin Coroutines guide.",
        "Compose a WhatsApp message to John, but do not send it."
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            WelcomeHeader()
        }
        item {
            ServiceCard(
                enabled = serviceEnabled,
                onOpenAccessibility = onOpenAccessibility
            )
        }
        item {
            CommandCard(
                command = command,
                onCommandChange = onCommandChange,
                onCreatePlan = onCreatePlan,
                enabled = serviceEnabled
            )
        }
        if (isRunning) {
            item {
                RunningTaskCard(onAskQuestion = onAskQuestion)
            }
        }
        item {
            SectionHeading(
                title = "Try a task",
                subtitle = "Start with a clear destination and outcome."
            )
        }
        items(examples) { example ->
            ExampleRow(
                text = example,
                onClick = { onCommandChange(example) }
            )
        }
        item {
            PrivacyCallout()
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
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
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
private fun RunningTaskCard(onAskQuestion: () -> Unit) {
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
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Task is running", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Observe → think → act → observe",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Filled.Pause, contentDescription = "Paused between actions")
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onAskQuestion) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Open task question")
            }
        }
    }
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

@Composable
private fun PlanPreviewScreen(
    command: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onApprove: () -> Unit
) {
    val steps = remember(command) { planFor(command) }
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
                        Text(command, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                SectionHeading("Proposed steps", "One validated action will run at a time.")
            }
            items(steps) { step ->
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

@Composable
private fun HistoryScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
            Text("No tasks yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Approved tasks and redacted chat history will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier = Modifier, onOpenAccessibility: () -> Unit) {
    var endpoint by rememberSaveable { mutableStateOf("https://api.example.com/v1") }
    var model by rememberSaveable { mutableStateOf("your-model") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var saved by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                        placeholder = { Text("Stored securely on device") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Button(onClick = { saved = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Security, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (saved) "Settings saved locally" else "Save provider settings")
                    }
                    Text(
                        "The secure Keystore-backed storage layer is part of the next implementation step. Never commit this key.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                onClick = { }
            )
        }
        item {
            SettingsActionCard(
                icon = Icons.Outlined.Code,
                title = "Debug / Developer options",
                subtitle = "Diagnostics will expose redacted tree summaries and action validation results.",
                action = "Open",
                onClick = { }
            )
        }
        item {
            SettingsActionCard(
                icon = Icons.Outlined.Info,
                title = "About and diagnostics",
                subtitle = "TaskPilot 0.1.0 · native Android scaffold",
                action = "View",
                onClick = { }
            )
        }
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

private fun planFor(command: String): List<PlanStep> {
    val normalized = command.lowercase()
    return when {
        "youtube" in normalized -> listOf(
            PlanStep("Open YouTube", "Launch the installed YouTube application."),
            PlanStep("Find search", "Locate the accessible search control."),
            PlanStep("Search", "Enter the requested query in the search field."),
            PlanStep("Verify results", "Confirm that the search results are visible.")
        )
        "chrome" in normalized -> listOf(
            PlanStep("Open Chrome", "Launch the installed Chrome application."),
            PlanStep("Focus the address bar", "Locate the accessible address or search field."),
            PlanStep("Search", "Enter the requested query and submit it."),
            PlanStep("Verify page", "Confirm that the search results page loaded.")
        )
        "whatsapp" in normalized -> listOf(
            PlanStep("Open WhatsApp", "Launch WhatsApp and locate the requested contact."),
            PlanStep("Open the conversation", "Find the conversation with the named person."),
            PlanStep("Draft the message", "Type the requested ordinary text into the message field."),
            PlanStep("Do not send", "Leave the message as a draft; no send action will be attempted.")
        )
        "gallery" in normalized || "screenshot" in normalized -> listOf(
            PlanStep("Open Gallery", "Launch the available Gallery application."),
            PlanStep("Find screenshots", "Identify screenshots matching the requested age filter."),
            PlanStep("Review matches", "Show the matched items and verify the deletion scope."),
            PlanStep("Delete matched items", "Delete only after an additional confirmation.", highRisk = true)
        )
        "battery saver" in normalized -> listOf(
            PlanStep("Open Settings", "Launch Android Settings."),
            PlanStep("Find Battery Saver", "Locate the Battery Saver setting."),
            PlanStep("Enable Battery Saver", "Change the setting after the approved plan.")
        )
        else -> listOf(
            PlanStep("Identify the destination", "Determine which installed app or Android surface matches the request."),
            PlanStep("Observe the current UI", "Build a redacted accessibility-tree snapshot."),
            PlanStep("Complete the requested task", "Continue one validated action at a time."),
            PlanStep("Verify completion", "Confirm the requested outcome or ask a follow-up question.")
        )
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val expected = ComponentName(context, TaskPilotAccessibilityService::class.java).flattenToString()
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}
