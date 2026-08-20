package com.example.kokorotts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kokorotts.data.TtsViewModel
import com.example.kokorotts.ui.screens.MainTtsScreen
import com.example.kokorotts.ui.theme.KokoroTTSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KokoroTTSTheme {
                val viewModel: TtsViewModel = viewModel()
                MainTtsScreen(viewModel = viewModel)
            }
        }
    }
}
