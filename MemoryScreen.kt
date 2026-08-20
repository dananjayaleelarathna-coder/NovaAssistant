package com.nova.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.assistant.data.MemoryFact
import com.nova.assistant.data.MemoryManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val memoryManager = remember { MemoryManager(context) }
    val scope = rememberCoroutineScope()
    val facts by memoryManager.facts().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        if (facts.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Nothing remembered yet. Try saying \"Remember that I like Sinhala replies.\"")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
                items(facts) { fact: MemoryFact ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column {
                            Text(fact.key, style = MaterialTheme.typography.labelMedium)
                            Text(fact.value, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { scope.launch { memoryManager.forget(fact.key) } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Forget")
                        }
                    }
                    Divider()
                }
            }
        }
    }
}
