package com.stuchess.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.stuchess.app.game.GameViewModel
import com.stuchess.app.ui.AppPalette
import com.stuchess.app.ui.GameScreen

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AppPalette.AccentPrimary,
                    background = AppPalette.BgPrimary,
                    surface = AppPalette.SurfacePrimary,
                    onPrimary = AppPalette.AccentOnPrimary,
                    onBackground = AppPalette.TextPrimary,
                    onSurface = AppPalette.TextPrimary
                )
            ) {
                GameScreen(viewModel = gameViewModel)
            }
        }
    }
}
