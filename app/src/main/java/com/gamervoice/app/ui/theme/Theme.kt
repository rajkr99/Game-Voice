package com.gamervoice.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Speaking = Color(0xFF3DDC84)

private val Dark = darkColorScheme(
    primary = Color(0xFF3DDC84),
    onPrimary = Color(0xFF00210E),
    secondary = Color(0xFF8AB4F8),
    background = Color(0xFF0E1116),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF1F2630),
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
    error = Color(0xFFFF6B6B),
)

/** Dark only, flat colours, no animations: cheap to render while a game runs. */
@Composable
fun GamerVoiceTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Dark, content = content)
