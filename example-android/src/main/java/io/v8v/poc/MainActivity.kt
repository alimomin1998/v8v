@file:Suppress("ktlint:standard:function-naming")

package io.v8v.poc

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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
                    VoiceAgentScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAgentScreen(vm: MainViewModel = viewModel()) {
    val todos by vm.todos.collectAsState()
    val agentState by vm.agentState.collectAsState()
    val lastTranscript by vm.lastTranscript.collectAsState()
    val lastError by vm.lastError.collectAsState()
    val debugLog by vm.debugLog.collectAsState()
    val audioLevel by vm.audioLevel.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) vm.startListening()
    }

    val isListening = agentState == AgentState.LISTENING ||
        agentState == AgentState.PROCESSING

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("V8V Example") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
        floatingActionButton = {
            MicFab(isListening, audioLevel) {
                when {
                    isListening -> vm.stopListening()
                    hasPermission -> vm.startListening()
                    else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            // Library badge
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Using published library from Maven Central",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "io.github.alimomin1998:core-android:0.3.0",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            StatusCard(agentState, lastTranscript)

            AnimatedVisibility(visible = lastError.isNotBlank()) {
                Text(
                    text = "Error: $lastError",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            SettingsCard(vm)
            Spacer(Modifier.height(12.dp))
            CommandsCard()
            Spacer(Modifier.height(12.dp))

            Text(
                "Task List (LOCAL)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            if (todos.isEmpty()) {
                Text(
                    "No tasks yet. Say \"Add project status update\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                todos.forEachIndexed { i, todo ->
                    TodoItem(todo, "LOCAL") { vm.removeTodo(i) }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            WebhookUrlField(vm)
            Spacer(Modifier.height(12.dp))
            DebugPanel(debugLog)
            Spacer(Modifier.height(80.dp))
        }
    }
}

// ── Mic FAB ─────────────────────────────────────────────────────────

@Composable
private fun MicFab(isListening: Boolean, audioLevel: Float, onClick: () -> Unit) {
    val fabColor by animateColorAsState(
        if (isListening) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
        label = "fab",
    )
    val level by animateFloatAsState(
        if (isListening) audioLevel else 0f,
        animationSpec = tween(100), label = "level",
    )
    val ringScale = 1f + level * 0.8f
    val ringAlpha = if (isListening) 0.15f + level * 0.25f else 0f

    Box(contentAlignment = Alignment.Center) {
        if (isListening) {
            Box(
                Modifier
                    .size(56.dp)
                    .scale(ringScale)
                    .graphicsLayer { alpha = ringAlpha }
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                        CircleShape,
                    ),
            )
        }
        FloatingActionButton(onClick = onClick, containerColor = fabColor) {
            Icon(
                painterResource(
                    if (isListening) R.drawable.ic_mic_off else R.drawable.ic_mic,
                ),
                contentDescription = if (isListening) "Stop" else "Start",
            )
        }
    }
}

// ── Settings ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCard(vm: MainViewModel) {
    val language by vm.language.collectAsState()
    val continuous by vm.continuous.collectAsState()
    val fuzzy by vm.fuzzyThreshold.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            val langs = listOf("en" to "English", "hi" to "Hindi", "es" to "Spanish")
            ExposedDropdownMenuBox(expanded, { expanded = it }) {
                OutlinedTextField(
                    value = langs.firstOrNull { it.first == language }?.second ?: language,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Language") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    langs.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { vm.setLanguage(code); expanded = false },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Continuous listening", style = MaterialTheme.typography.bodySmall)
                Switch(continuous, { vm.setContinuous(it) })
            }
            Spacer(Modifier.height(4.dp))
            Text("Fuzzy threshold: ${"%.1f".format(fuzzy)}", style = MaterialTheme.typography.bodySmall)
            Slider(fuzzy, { vm.setFuzzyThreshold(it) }, valueRange = 0f..1f, steps = 9)
        }
    }
}

// ── Commands Card ───────────────────────────────────────────────────

@Composable
private fun CommandsCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Voice Commands", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            CmdRow("LOCAL", "\"Add project status update\"")
            CmdRow("MCP", "\"Create task schedule design review\"")
            CmdRow("REMOTE", "\"Notify team project kickoff is at 3 PM\"")
        }
    }
}

@Composable
private fun CmdRow(scope: String, example: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        ScopeBadge(scope)
        Spacer(Modifier.width(8.dp))
        Text(example, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
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
    Surface(shape = MaterialTheme.shapes.extraSmall, color = color.copy(alpha = 0.15f)) {
        Text(
            scope,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Status Card ─────────────────────────────────────────────────────

@Composable
private fun StatusCard(state: AgentState, transcript: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                AgentState.LISTENING -> MaterialTheme.colorScheme.primaryContainer
                AgentState.PROCESSING -> MaterialTheme.colorScheme.tertiaryContainer
                AgentState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                when (state) {
                    AgentState.LISTENING -> "Listening..."
                    AgentState.PROCESSING -> "Processing..."
                    AgentState.IDLE -> "Tap the mic to start"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (transcript.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Last heard: \"$transcript\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Todo Item ───────────────────────────────────────────────────────

@Composable
private fun TodoItem(text: String, scope: String, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ScopeBadge(scope)
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── Webhook URL ─────────────────────────────────────────────────────

@Composable
private fun WebhookUrlField(vm: MainViewModel) {
    val url by vm.webhookUrl.collectAsState()
    var text by remember { mutableStateOf(url) }
    OutlinedTextField(
        text, { text = it; vm.setWebhookUrl(it) },
        label = { Text("n8n Webhook URL (REMOTE)") },
        placeholder = { Text("https://n8n.example.com/webhook/voice") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall,
    )
}

// ── Debug Panel ─────────────────────────────────────────────────────

@Composable
private fun DebugPanel(lines: List<String>) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                Arrangement.SpaceBetween, Alignment.CenterVertically,
            ) {
                Text("Debug Log (${lines.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(if (open) "Hide" else "Show", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 4.dp)) {
                    if (lines.isEmpty()) {
                        Text("No logs yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        lines.forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
