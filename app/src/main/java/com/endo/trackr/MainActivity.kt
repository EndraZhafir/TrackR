package com.endo.trackr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.endo.trackr.ui.screens.home.HomeScreen
import com.endo.trackr.ui.theme.TrackRTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackRTheme {
                HomeScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}