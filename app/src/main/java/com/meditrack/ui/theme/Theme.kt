package com.meditrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E7E6F),
    onPrimary = Color.White,
    secondary = Color(0xFF355C7D),
    tertiary = Color(0xFF8A5A44),
    background = Color(0xFFF7FAFC),
    surface = Color.White,
    error = Color(0xFFB42318)
)

@Composable
fun MediTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
