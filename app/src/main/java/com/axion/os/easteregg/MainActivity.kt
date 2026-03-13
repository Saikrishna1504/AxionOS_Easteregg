package com.axion.os.easteregg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.axion.os.easteregg.ui.MainContainer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force dark system bars regardless of system theme
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        
        setContent {
            // Force Dark Color Scheme for strict monochrome dark aesthetic
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color.Black,
                surface = Color.Black,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                MainContainer()
            }
        }
    }
}
