package com.nova.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.nova.assistant.ui.NovaApp
import com.nova.assistant.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {

    // Only the permissions actually needed for the features present in this build.
    // Contacts/Call/SMS/Location are requested lazily, right when a matching command is
    // first used — not all at launch — per the "request only what's needed" requirement.
    private val corePermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results handled reactively by the UI reading checkSelfPermission */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val missing = corePermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        setContent {
            NovaTheme {
                NovaApp()
            }
        }
    }
}
