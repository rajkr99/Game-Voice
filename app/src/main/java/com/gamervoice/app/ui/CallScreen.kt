package com.gamervoice.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamervoice.app.service.ConnState
import com.gamervoice.app.service.Person
import com.gamervoice.app.ui.theme.Speaking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(vm: CallViewModel, onSettings: () -> Unit) {
    val s by vm.call.collectAsStateWithLifecycle()
    var outputMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Room ${s.room}")
                        val status = when (s.conn) {
                            ConnState.Connecting -> "Connecting…"
                            ConnState.Reconnecting -> "Reconnecting…"
                            ConnState.Connected -> "Connected"
                            else -> "Disconnected"
                        }
                        Text("$status • ${s.people.size}/10", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { vm.refreshOutputs(); outputMenu = true }) { Icon(Icons.Default.VolumeUp, "Audio output") }
                        DropdownMenu(outputMenu, { outputMenu = false }) {
                            s.outputs.forEach { o ->
                                DropdownMenuItem(
                                    text = { Text(if (o == s.selectedOutput) "✓ $o" else o) },
                                    onClick = { vm.selectOutput(o); outputMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.people, key = { it.id }) { p -> PersonRow(p) { v -> vm.setVolume(p.id, v) } }
            }

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = vm::leave,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(72.dp),
                ) { Icon(Icons.Default.CallEnd, "Leave") }

                val bg = if (s.micOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                Box(
                    Modifier.weight(1f).height(72.dp).background(bg, CircleShape)
                        .pointerInput(s.pushToTalk) {
                            detectTapGestures(
                                onPress = {
                                    if (s.pushToTalk) {
                                        vm.setMic(true); tryAwaitRelease(); vm.setMic(false)
                                    }
                                },
                                onTap = { if (!s.pushToTalk) vm.toggleMic() },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (s.micOn) Icons.Default.Mic else Icons.Default.MicOff, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                s.pushToTalk && s.micOn -> "Talking…"
                                s.pushToTalk -> "Hold to talk"
                                s.micOn -> "Mute"
                                else -> "Unmute"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(p: Person, onVolume: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp)
                        .border(3.dp, if (p.speaking) Speaking else MaterialTheme.colorScheme.surface, CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(p.name.take(1).uppercase()) }
                Spacer(Modifier.width(12.dp))
                Text(if (p.isLocal) "${p.name} (you)" else p.name, Modifier.weight(1f))
                if (p.muted) Icon(Icons.Default.MicOff, "Muted", tint = MaterialTheme.colorScheme.error)
                if (!p.isLocal) IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.Tune, "Volume")
                }
            }
            if (expanded) Slider(value = p.volume, onValueChange = onVolume, valueRange = 0f..2f)
        }
    }
}
