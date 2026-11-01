package com.georecon.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Spy {
    val Black = Color(0xFF05080A)
    val Gunmetal = Color(0xFF2A3036)
    val Cyan = Color(0xFF3FE0FF)
    val CyanDim = Color(0xFF1A6F80)
    val Amber = Color(0xFFFFB238)
}

@Composable
fun SpyTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Spy.Cyan, background = Spy.Black, surface = Spy.Gunmetal,
        onPrimary = Spy.Black, onBackground = Spy.Cyan, onSurface = Spy.Cyan,
    ),
    content = content,
)
