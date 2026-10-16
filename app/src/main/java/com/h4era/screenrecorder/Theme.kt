package com.h4era.screenrecorder

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF070B14)
val CardBg = Color(0xFF111A2E)
val Cyan = Color(0xFF00E5FF)
val Blue = Color(0xFF2979FF)
val Muted = Color(0xFF8A94A6)

@Composable
fun H4Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            onPrimary = Color.Black,
            secondary = Blue,
            background = Navy,
            onBackground = Color.White,
            surface = CardBg,
            onSurface = Color.White
        ),
        content = content
    )
}
