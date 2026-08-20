package com.nova.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.assistant.data.MemoryManager
import com.nova.assistant.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val memoryManager = remember { MemoryManager(context) }
    val scope = rememberCoroutineScope()

    fun granted(perm: String) = context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Access Status", style = MaterialTheme.typography.titleMedium)
            PermissionRow("Microphone", granted(Manifest.permission.RECORD_AUDIO))
            PermissionRow("Camera", granted(Manifest.permission.CAMERA))
            PermissionRow("Contacts", granted(Manifest.permission.READ_CONTACTS))
            PermissionRow("Phone / Calls", granted(Manifest.permission.CALL_PHONE))
            PermissionRow("Location", granted(Manifest.permission.ACCESS_FINE_LOCATION))
            PermissionRow("Bluetooth", granted(Manifest.permission.BLUETOOTH_CONNECT))

            Divider()
            Text("Cloud & Memory", style = MaterialTheme.typography.titleMedium)
            Text("Cloud AI status: used only for general questions and information requests. Raw microphone audio is never uploaded — only recognized text is sent, and only after on-device speech recognition.")
            Text("Local memory status: stored only on this device unless you clear it below.")

            Divider()
            Text("Data Controls", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { scope.launch { memoryManager.clearAll() } }) {
                Text("Clear all remembered facts")
            }
            Button(onClick = { scope.launch { memoryManager.clearConversationHistory() } }) {
                Text("Delete conversation history")
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, isGranted: Boolean) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Icon(
            if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFB00020)
        )
        Spacer(Modifier.width(8.dp))
        Text(label)
        Spacer(Modifier.weight(1f))
        Text(if (isGranted) "Granted" else "Not granted", style = MaterialTheme.typography.bodySmall)
    }
}
