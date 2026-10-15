package com.gamervoice.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamervoice.app.util.SystemSettings
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun JoinScreen(vm: CallViewModel, rejoinRoom: String?, onSettings: () -> Unit, onHelp: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val joining by vm.joining.collectAsStateWithLifecycle()
    val error by vm.joinError.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf("") }
    var room by rememberSaveable { mutableStateOf("") }
    var micDenied by remember { mutableStateOf(false) }

    // Prefill from DataStore once.
    LaunchedEffect(settings.lastName, settings.lastRoom) {
        if (name.isEmpty()) name = settings.lastName
        if (room.isEmpty()) room = rejoinRoom ?: settings.lastRoom
    }

    val perms = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true) { micDenied = false; vm.join(name, room) }
        else micDenied = true
    }

    Scaffold { pad ->
        Column(
            Modifier.padding(pad).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("GamerVoice", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onHelp) { Icon(Icons.Default.HelpOutline, "Keep call alive help") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
            }
            Text("Light voice chat that won't lag your game.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))

            OutlinedTextField(name, { name = it.take(32) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                room, { room = it.uppercase().take(32) }, label = { Text("Room code") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(room)) }, enabled = room.isNotBlank()) { Icon(Icons.Default.ContentCopy, "Copy") }
                        IconButton(onClick = { SystemSettings.shareRoomCode(ctx, room) }, enabled = room.isNotBlank()) { Icon(Icons.Default.Share, "Share") }
                    }
                },
            )
            OutlinedButton(onClick = { room = SystemSettings.randomCode() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Casino, null); Spacer(Modifier.width(8.dp)); Text("Generate random code")
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (micDenied) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Microphone access is needed so friends can hear you. Please allow it in app settings.")
                        TextButton(onClick = { SystemSettings.openAppSettings(ctx) }) { Text("Open app settings") }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = { launcher.launch(perms) },
                enabled = !joining,
                modifier = Modifier.fillMaxWidth().height(60.dp),
            ) {
                if (joining) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Join", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
