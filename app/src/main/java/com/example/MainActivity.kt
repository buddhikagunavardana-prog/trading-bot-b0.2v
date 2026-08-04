package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.AlphaEngineViewModel
import com.example.ui.screens.MainDashboardScreen
import com.example.ui.theme.CryptoBotTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AlphaEngineViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CryptoBotTheme {
                MainDashboardScreen(viewModel = viewModel)
            }
        }
    }
}
