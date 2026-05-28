package com.stalker.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stalker.player.ui.navigation.AppNavigation
import com.stalker.player.ui.theme.DarkBg
import com.stalker.player.ui.theme.StalkerTheme
import com.stalker.player.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF11161D.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF11161D.toInt())
        )
        setContent {
            StalkerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    val viewModel: MainViewModel = viewModel()
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
