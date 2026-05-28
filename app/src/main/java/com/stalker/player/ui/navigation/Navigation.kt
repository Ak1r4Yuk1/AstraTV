package com.stalker.player.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.stalker.player.ui.screens.HomeScreen
import com.stalker.player.ui.screens.PlayerScreen
import com.stalker.player.viewmodel.MainViewModel

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val playerActive = viewModel.playerActive.collectAsState().value
    val streamUrl = viewModel.currentStreamUrl.collectAsState().value

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(viewModel = viewModel, onPlay = {})

        if (playerActive) {
            PlayerScreen(
                streamUrl = streamUrl,
                onBack = { viewModel.clearPlaybackState() }
            )
        }
    }
}
