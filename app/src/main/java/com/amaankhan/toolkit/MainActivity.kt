package com.amaankhan.toolkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amaankhan.toolkit.ui.ToolkitApp
import com.amaankhan.toolkit.ui.ToolkitTheme
import com.amaankhan.toolkit.ui.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: ToolkitViewModel = viewModel()
            val preferences by model.preferences.collectAsStateWithLifecycle()
            val dark = isDarkTheme(preferences.theme)
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            ToolkitTheme(preferences.theme, preferences.palette) { ToolkitApp(model, preferences) }
        }
    }
}
