package com.nova.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.assistant.data.NovaSettings
import com.nova.assistant.data.SecureKeyStore
import com.nova.assistant.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPrivacy: () -> Unit, onOpenMemory: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val keyStore = remember { SecureKeyStore(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settingsFlow.collectAsState(initial = NovaSettings())

    var nameField by remember(settings.assistantName) { mutableStateOf(settings.assistantName) }
    var apiKeyField by remember { mutableStateOf(keyStore.getApiKey(settings.aiProviderId).orEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SectionLabel("Assistant Identity")
            OutlinedTextField(
                value = nameField,
                onValueChange = { nameField = it },
                label = { Text("Assistant name") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { scope.launch { repo.setAssistantName(nameField) } }) {
                Text("Save name")
            }

            SectionLabel("Wake Word")
            SwitchRow("Enable wake word", settings.wakeWordEnabled) {
                scope.launch { repo.setWakeEnabled(it) }
            }
            Text("Sensitivity: ${(settings.wakeSensitivity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = settings.wakeSensitivity,
                onValueChange = { v -> scope.launch { repo.setWakeSensitivity(v) } }
            )
            SwitchRow("Background listening", settings.backgroundListening) {
                scope.launch { repo.setBackgroundListening(it) }
            }
            SwitchRow("Battery-saving listening mode", settings.batterySavingMode) {
                scope.launch { repo.setBatterySavingMode(it) }
            }
            Text(
                "Note: Android limits how long apps can listen in the background. Wake-word " +
                    "detection runs as a foreground service with a persistent notification, " +
                    "as required by the OS.",
                style = MaterialTheme.typography.bodySmall
            )

            SectionLabel("Language & Personality")
            LanguageSelector(settings.language) { lang -> scope.launch { repo.setLanguage(lang) } }
            PersonalitySelector(settings.personality) { p -> scope.launch { repo.setPersonality(p) } }

            SectionLabel("AI Brain")
            SwitchRow("Offline mode only", settings.offlineModeOnly) {
                scope.launch { repo.setOfflineOnly(it) }
            }
            SwitchRow("Allow cloud AI for general questions", settings.cloudAiEnabled) {
                scope.launch { repo.setCloudAiEnabled(it) }
            }
            OutlinedTextField(
                value = apiKeyField,
                onValueChange = { apiKeyField = it },
                label = { Text("Anthropic API key") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { keyStore.setApiKey("anthropic", apiKeyField) }) {
                Text("Save API key (encrypted on-device)")
            }

            SectionLabel("Memory & Privacy")
            SwitchRow("Enable personal memory", settings.memoryEnabled) {
                scope.launch { repo.setMemoryEnabled(it) }
            }
            OutlinedButton(onClick = onOpenMemory) { Text("View / manage memory") }
            OutlinedButton(onClick = onOpenPrivacy) { Text("Privacy settings") }

            SectionLabel("About")
            Text(
                "Nova Assistant — a customizable, privacy-respecting Android voice assistant. " +
                    "Some features require Accessibility or Notification Access, which are always " +
                    "opt-in and explained before you grant them.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable private fun SectionLabel(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LanguageSelector(current: String, onSelect: (String) -> Unit) {
    val options = listOf("en-US" to "English", "si-LK" to "Sinhala", "mixed" to "Sinhala-English Mixed")
    Column {
        Text("Language", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (code, label) ->
                FilterChip(selected = current == code, onClick = { onSelect(code) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun PersonalitySelector(current: String, onSelect: (String) -> Unit) {
    val options = listOf("Friendly", "Professional", "Funny", "Short answers", "Detailed", "Robotic", "Natural")
    Column {
        Text("Personality", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.take(4).forEach { p ->
                FilterChip(selected = current == p, onClick = { onSelect(p) }, label = { Text(p) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.drop(4).forEach { p ->
                FilterChip(selected = current == p, onClick = { onSelect(p) }, label = { Text(p) })
            }
        }
    }
}

