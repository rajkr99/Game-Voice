package com.gamervoice.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamervoice.app.data.NoiseLevel
import com.gamervoice.app.util.SystemSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: CallViewModel, onBack: () -> Unit, onHelp: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val call by vm.call.collectAsStateWithLifecycle()
    var server by remember(s.serverUrl) { mutableStateOf(s.serverUrl) }
    var showAdvanced by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Toggle("Push-to-talk", "Hold the button to talk", call.pushToTalk || s.pushToTalk) { vm.setPushToTalk(it) }
            Text("Noise cancellation")
            Text(
                "Echo cancel + noise filter + auto gain. Strong also hides keyboard/tap noise. Applies on next join",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Off" to NoiseLevel.OFF, "Standard" to NoiseLevel.STANDARD, "Strong" to NoiseLevel.STRONG)
                    .forEachIndexed { i, (label, level) ->
                        SegmentedButton(
                            selected = s.noiseLevel == level,
                            onClick = { vm.setNoiseLevel(level) },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                        ) { Text(label) }
                    }
            }
            Toggle("Clear voice", "Higher quality (32 kbps), uses more data. Applies on next join", s.clearVoice) { vm.setClearVoice(it) }
            Toggle("Data saver", "Lower quality, ~12 kbps. Applies on next join", s.dataSaver) { vm.setDataSaver(it) }

            Text(
                "Tip: use headphones. On the phone speaker the mic can pick up game sound and teammates will hear it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val exempt = SystemSettings.isIgnoringBatteryOptimizations(ctx)
            Text(if (exempt) "Battery optimisation: off ✓" else "Battery optimisation is on — calls may drop in background")
            if (!exempt) Button(onClick = { SystemSettings.requestIgnoreBatteryOptimizations(ctx) }) { Text("Disable battery optimisation") }
            TextButton(onClick = onHelp) { Text("Keep call alive tips for your phone") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "Hide advanced" else "Advanced") }
            if (showAdvanced) {
                OutlinedTextField(server, { server = it }, label = { Text("Token server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.setServerUrl(server) }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        }
        Switch(checked, onChange)
    }
}
