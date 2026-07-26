package com.yishenghuang.sealrec.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SealColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Slate,
    onSecondary = Color.White,
    tertiary = TealBright,
    background = Foam,
    onBackground = Ink,
    surface = Mist,
    onSurface = Ink,
    error = Crimson,
    onError = Color.White,
)

@Composable
fun SealRecTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SealColorScheme,
        typography = Typography,
        content = content,
    )
}
