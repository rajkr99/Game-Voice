package com.gamervoice.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamervoice.app.util.SystemSettings

private val tips = linkedMapOf(
    "xiaomi" to "Xiaomi / Redmi / POCO: Settings → Apps → GamerVoice → Battery saver → No restrictions. Also enable Autostart, and lock the app in Recents (swipe down on its card).",
    "oppo" to "Oppo: Settings → Battery → App battery management → GamerVoice → allow background activity. Lock it in Recents.",
    "realme" to "Realme: Settings → Battery → App battery management → GamerVoice → allow background & auto launch.",
    "vivo" to "Vivo / iQOO: Settings → Battery → Background power consumption → GamerVoice → Allow. Enable Autostart in i Manager.",
    "samsung" to "Samsung: Settings → Battery → Background usage limits → remove GamerVoice from Sleeping apps, then add it to Never sleeping apps.",
    "oneplus" to "OnePlus: Settings → Battery → Battery optimisation → GamerVoice → Don't optimise.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepAliveHelpScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val brand = SystemSettings.brand
    Scaffold(topBar = {
        TopAppBar(title = { Text("Keep call alive") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Some phones close background apps to save battery. That can cut your voice chat while you game.")
            Button(onClick = { SystemSettings.requestIgnoreBatteryOptimizations(ctx) }) { Text("Disable battery optimisation") }
            OutlinedButton(onClick = { SystemSettings.openAppSettings(ctx) }) { Text("Open app settings") }
            tips.forEach { (key, text) ->
                val mine = brand.contains(key)
                Card {
                    Column(Modifier.padding(12.dp)) {
                        if (mine) Text("Your phone", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(text)
                    }
                }
            }
            Text("Also: turn off any \"Game turbo / Game space\" option that blocks background audio, and keep the call notification visible.")
        }
    }
}
