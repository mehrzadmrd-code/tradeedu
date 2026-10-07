package com.tradeedu

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.mode = getSharedPreferences("app", 0).getInt("theme", 2)
        setContent {
            val dark = when (ThemeStore.mode) { 0 -> false; 1 -> true; else -> isSystemInDarkTheme() }
            MaterialTheme(colorScheme = if (dark) TgScheme else TgLight) {
                val bar = if (ThemeStore.tab == 1) Color.Black else MaterialTheme.colorScheme.surface
                val lightBars = !dark && ThemeStore.tab != 1
                val view = LocalView.current
                SideEffect {
                    val w = (view.context as Activity).window
                    w.statusBarColor = bar.toArgb(); w.navigationBarColor = bar.toArgb()
                    WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = lightBars; isAppearanceLightNavigationBars = lightBars }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App() }
                }
            }
        }
    }
}
