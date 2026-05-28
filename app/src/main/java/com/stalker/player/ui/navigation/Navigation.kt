package com.stalker.player.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.stalker.player.ui.screens.HomeScreen
import com.stalker.player.ui.screens.PlayerScreen
import com.stalker.player.ui.screens.TvHomeScreen
import com.stalker.player.viewmodel.MainViewModel

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val playerActive = viewModel.playerActive.collectAsState().value
    val streamUrl = viewModel.currentStreamUrl.collectAsState().value
    val configuration = LocalConfiguration.current
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

    Box(modifier = Modifier.fillMaxSize()) {
        if (isTv) {
            TvHomeScreen(viewModel = viewModel, onPlay = {})
        } else {
            HomeScreen(viewModel = viewModel, onPlay = {})
        }

        if (playerActive) {
            PlayerScreen(
                streamUrl = streamUrl,
                onBack = { viewModel.clearPlaybackState() }
            )
        }
    }
}
