package com.yishenghuang.sealrec.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightSealColorScheme = lightColorScheme(
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

private val DarkSealColorScheme = darkColorScheme(
    primary = TealBright,
    onPrimary = Ink,
    secondary = Mist,
    onSecondary = Ink,
    tertiary = Teal,
    background = Ink,
    onBackground = Mist,
    surface = Slate,
    onSurface = Mist,
    error = Crimson,
    onError = Color.White,
)

@Composable
fun SealRecTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkSealColorScheme else LightSealColorScheme,
        typography = Typography,
        content = content,
    )
}
