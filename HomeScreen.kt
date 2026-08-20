package com.nova.assistant.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.assistant.ui.AssistantStatus
import com.nova.assistant.ui.ChatMessage
import com.nova.assistant.ui.ConversationViewModel
import com.nova.assistant.ui.components.AnimatedOrb

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, vm: ConversationViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var typedText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.assistantName, fontWeight = FontWeight.SemiBold) },
                actions = {
                    AssistiveStatusChip(state.offlineMode)
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedOrb(status = state.status)
                if (state.status == AssistantStatus.LISTENING && state.partialTranscript.isNotBlank()) {
                    Text(
                        text = state.partialTranscript,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = statusLabel(state.status),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
            )

            QuickActionsRow(vm)

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages) { message -> ChatBubble(message) }
            }

            state.pendingConfirmation?.let {
                ConfirmationBar(
                    onYes = { vm.confirmPendingAction(true) },
                    onNo = { vm.confirmPendingAction(false) }
                )
            }

            InputBar(
                typedText = typedText,
                onTypedTextChange = { typedText = it },
                isListening = state.status == AssistantStatus.LISTENING,
                onMicClick = {
                    if (state.status == AssistantStatus.LISTENING) vm.stopListening() else vm.startListening()
                },
                onSend = {
                    vm.submitTypedText(typedText)
                    typedText = ""
                }
            )
        }
    }
}

@Composable
private fun AssistiveStatusChip(offline: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text(if (offline) "Offline Mode" else "AI Online") }
    )
}

private fun statusLabel(status: AssistantStatus): String = when (status) {
    AssistantStatus.IDLE -> "Tap the mic or say the wake word"
    AssistantStatus.LISTENING -> "Listening…"
    AssistantStatus.THINKING -> "Thinking…"
    AssistantStatus.SPEAKING -> "Speaking…"
}

@Composable
private fun QuickActionsRow(vm: ConversationViewModel) {
    val actions = listOf(
        "Torch" to "Turn on the flashlight",
        "Wi-Fi" to "Turn on wifi",
        "Bluetooth" to "Turn on bluetooth",
        "Camera" to "Open camera",
        "Music" to "Play music",
        "Alarm" to "Set an alarm for 7 AM",
        "Battery" to "What is my battery percentage?"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { (label, command) ->
            AssistChip(onClick = { vm.submitTypedText(command) }, label = { Text(label) })
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(text = message.text, modifier = Modifier.padding(12.dp))
        }
        message.statusLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun ConfirmationBar(onYes: () -> Unit, onNo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(onClick = onNo) { Text("NO") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onYes) { Text("YES") }
    }
}

@Composable
private fun InputBar(
    typedText: String,
    onTypedTextChange: (String) -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = typedText,
            onValueChange = onTypedTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a command…") },
            singleLine = true
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onSend, enabled = typedText.isNotBlank()) {
            Icon(Icons.Default.Send, contentDescription = "Send")
        }
        FilledIconButton(onClick = onMicClick) {
            Icon(
                if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Microphone"
            )
        }
    }
}
