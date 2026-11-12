package com.eric.personalledger.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = LedgerAccent,
    secondary = LedgerSecondary,
    tertiary = LedgerTertiary,
    primaryContainer = LedgerContainer,
    secondaryContainer = LedgerContainer,
    tertiaryContainer = LedgerContainer,
    surface = LedgerSurface,
    background = LedgerBackground,
    surfaceVariant = LedgerSurfaceVariant,
    onPrimary = LedgerBackground,
    onPrimaryContainer = LedgerInk,
    onSecondary = LedgerInk,
    onSecondaryContainer = LedgerInk,
    onTertiary = LedgerInk,
    onTertiaryContainer = LedgerInk,
    onSurface = LedgerInk,
    onSurfaceVariant = LedgerInk,
    onBackground = LedgerInk,
    outline = LedgerOutline,
    outlineVariant = LedgerOutlineVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = LedgerAccent,
    secondary = LedgerSecondary,
    tertiary = LedgerTertiary,
    primaryContainer = LedgerContainer,
    secondaryContainer = LedgerContainer,
    tertiaryContainer = LedgerContainer,
    surface = LedgerSurface,
    background = LedgerBackground,
    surfaceVariant = LedgerSurfaceVariant,
    onPrimary = LedgerBackground,
    onPrimaryContainer = LedgerInk,
    onSecondary = LedgerInk,
    onSecondaryContainer = LedgerInk,
    onTertiary = LedgerBackground,
    onTertiaryContainer = LedgerInk,
    onBackground = LedgerInk,
    onSurface = LedgerInk,
    onSurfaceVariant = LedgerInk,
    outline = LedgerOutline,
    outlineVariant = LedgerOutlineVariant,
)

@Composable
fun PersonalLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
