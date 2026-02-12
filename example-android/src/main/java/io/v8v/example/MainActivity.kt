package io.v8v.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.v8v.core.AgentState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    VoiceTodoScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTodoScreen(viewModel: MainViewModel = viewModel()) {
    val todos by viewModel.todos.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    val lastTranscript by viewModel.lastTranscript.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val debugLog by viewModel.debugLog.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.startListening()
    }

    val isListening = agentState == AgentState.LISTENING ||
        agentState == AgentState.PROCESSING

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Agent Demo") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
        floatingActionButton = {
            MicFab(
                isListening = isListening,
                audioLevel = audioLevel,
                onClick = {
                    when {
                        isListening -> viewModel.stopListening()
                        hasPermission -> viewModel.startListening()
                        else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // -- Status --
            StatusCard(agentState, lastTranscript)

            AnimatedVisibility(visible = lastError.isNotBlank()) {
                Text(
                    text = "Error: $lastError",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // -- Settings --
            SettingsCard(viewModel)

            Spacer(modifier = Modifier.height(12.dp))

            // -- Voice commands guide --
            CommandsCard()

            Spacer(modifier = Modifier.height(12.dp))

            // -- Todo list (LOCAL scope) --
            Text(
                text = "Todos (LOCAL)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (todos.isEmpty()) {
                Text(
                    text = "No todos yet. Say \"Add milk\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                todos.forEachIndexed { index, todo ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        TodoItem(
                            text = todo,
                            scope = "LOCAL",
                            onDelete = { viewModel.removeTodo(index) },
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // -- Webhook URL config (REMOTE scope) --
            WebhookUrlField(viewModel)

            Spacer(modifier = Modifier.height(12.dp))

            // -- Debug panel (collapsible) --
            CollapsibleDebugPanel(debugLog)

            Spacer(modifier = Modifier.height(80.dp)) // FAB clearance
        }
    }
}

// ---- Mic FAB with pulse animation ----

@Composable
private fun MicFab(
    isListening: Boolean,
    audioLevel: Float,
    onClick: () -> Unit,
) {
    val fabColor by animateColorAsState(
        targetValue = if (isListening) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
        label = "fab_color",
    )

    // Smooth the audio level for visual appeal
    val animatedLevel by animateFloatAsState(
        targetValue = if (isListening) audioLevel else 0f,
        animationSpec = tween(durationMillis = 100),
        label = "audio_level",
    )

    // Pulse ring: scale 1.0 → 1.8 based on audio level
    val ringScale = 1f + animatedLevel * 0.8f
    val ringAlpha = if (isListening) (0.15f + animatedLevel * 0.25f) else 0f

    Box(contentAlignment = Alignment.Center) {
        // Pulsing ring behind the FAB
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(ringScale)
                    .graphicsLayer { alpha = ringAlpha }
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                        shape = CircleShape,
                    ),
            )
        }

        FloatingActionButton(
            onClick = onClick,
            containerColor = fabColor,
        ) {
            Icon(
                painter = painterResource(
                    if (isListening) R.drawable.ic_mic_off else R.drawable.ic_mic,
                ),
                contentDescription = if (isListening) "Stop" else "Start",
            )
        }
    }
}

// ---- Settings Card ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCard(viewModel: MainViewModel) {
    val language by viewModel.language.collectAsState()
    val continuous by viewModel.continuous.collectAsState()
    val fuzzyThreshold by viewModel.fuzzyThreshold.collectAsState()

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Language dropdown
            val languages = listOf("en" to "English", "hi" to "Hindi", "es" to "Spanish")
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = languages.firstOrNull { it.first == language }?.second ?: language,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Language") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(type = MenuAnchorType.PrimaryNotEditable),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    languages.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.setLanguage(code)
                                expanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Continuous listening toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Continuous listening", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = continuous,
                    onCheckedChange = { viewModel.setContinuous(it) },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fuzzy threshold slider
            Text(
                text = "Fuzzy match threshold: ${"%.1f".format(fuzzyThreshold)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = fuzzyThreshold,
                onValueChange = { viewModel.setFuzzyThreshold(it) },
                valueRange = 0f..1f,
                steps = 9,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Exact only", style = MaterialTheme.typography.labelSmall)
                Text("Very fuzzy", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ---- Commands Card ----

@Composable
private fun CommandsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Voice Commands",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            CommandRow(scope = "LOCAL", example = "\"Add milk\"")
            CommandRow(scope = "MCP", example = "\"Create task buy groceries\"")
            CommandRow(scope = "REMOTE", example = "\"Notify meeting at 3pm\"")
        }
    }
}

@Composable
private fun CommandRow(scope: String, example: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScopeBadge(scope)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = example,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun ScopeBadge(scope: String) {
    val color = when (scope) {
        "LOCAL" -> MaterialTheme.colorScheme.primary
        "MCP" -> MaterialTheme.colorScheme.tertiary
        "REMOTE" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = scope,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---- Webhook URL ----

@Composable
private fun WebhookUrlField(viewModel: MainViewModel) {
    val currentUrl by viewModel.webhookUrl.collectAsState()
    var text by remember { mutableStateOf(currentUrl) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            viewModel.setWebhookUrl(it)
        },
        label = { Text("n8n Webhook URL (REMOTE)") },
        placeholder = { Text("https://n8n.example.com/webhook/voice") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall,
    )
}

// ---- Status Card ----

@Composable
private fun StatusCard(agentState: AgentState, lastTranscript: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (agentState) {
                AgentState.LISTENING -> MaterialTheme.colorScheme.primaryContainer
                AgentState.PROCESSING -> MaterialTheme.colorScheme.tertiaryContainer
                AgentState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (agentState) {
                    AgentState.LISTENING -> "Listening..."
                    AgentState.PROCESSING -> "Processing..."
                    AgentState.IDLE -> "Tap the mic to start"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (lastTranscript.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last heard: \"$lastTranscript\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Todo Item with scope badge ----

@Composable
private fun TodoItem(text: String, scope: String, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                ScopeBadge(scope)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = text, style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ---- Collapsible Debug Panel ----

@Composable
private fun CollapsibleDebugPanel(lines: List<String>) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Debug Log (${lines.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isExpanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    if (lines.isEmpty()) {
                        Text(
                            "No logs yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        lines.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
