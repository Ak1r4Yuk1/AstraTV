package com.stalker.player.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.stalker.player.R
import com.stalker.player.data.api.M3uClient
import com.stalker.player.data.model.Strings
import com.stalker.player.ui.theme.AccentCyan
import com.stalker.player.ui.theme.DarkBg
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(streamUrl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val rootView = LocalView.current
    val configuration = LocalConfiguration.current
    val compactHeight = configuration.screenHeightDp < 480

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isExiting by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    val appContext = context.applicationContext
    val trackSelector = remember { DefaultTrackSelector(appContext) }
    val player = remember {
        // Alcuni server (es. RAI relinker) rispondono con un redirect 302 verso il
        // flusso reale, spesso cambiando protocollo (http -> https) e richiedendo uno
        // User-Agent specifico. ExoPlayer di default NON segue i redirect cross-protocol,
        // quindi va abilitato esplicitamente perche' questi link funzionino.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(M3uClient.STREAM_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        ExoPlayer.Builder(appContext)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(appContext).setDataSourceFactory(httpDataSourceFactory))
            .build()
    }
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Errori di rete/formato (es. 403, stream non supportato): mostriamo un
                // messaggio invece di restare in buffering all'infinito.
                isBuffering = false
                playbackError = Strings["streamError"]
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    fun exitPlayer() {
        if (isExiting) return
        isExiting = true
        player.playWhenReady = false
        onBack()
    }

    BackHandler { exitPlayer() }

    fun applyFullscreen() {
        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            activity.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            activity.window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            activity.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    DisposableEffect(rootView) {
        rootView.keepScreenOn = true
        onDispose { rootView.keepScreenOn = false }
    }

    LaunchedEffect(streamUrl) {
        playbackError = null
        if (streamUrl.isBlank()) {
            isBuffering = true
            player.stop()
            player.clearMediaItems()
            return@LaunchedEffect
        }
        isBuffering = true
        // setMediaItem puo' lanciare in modo sincrono se manca il modulo per il
        // formato richiesto (DASH/SmoothStreaming/RTSP): lo gestiamo senza crashare.
        try {
            player.setMediaItem(MediaItem.fromUri(streamUrl))
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            isBuffering = false
            playbackError = Strings["streamError"]
        }
    }

    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            delay(250)
        }
    }

    LaunchedEffect(isFullscreen) { applyFullscreen() }

    // Sul telecomando della TV non esiste il tocco: il player va guidato col D-pad.
    // Richiediamo il focus cosi' la root Box riceve gli eventi tasto.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        val max = if (duration > 0) duration else Long.MAX_VALUE
        val newPos = (player.currentPosition + deltaMs).coerceIn(0L, max)
        player.seekTo(newPos)
        currentPosition = newPos
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                activity.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                    Key.Spacebar, Key.MediaPlayPause -> {
                        togglePlayPause(); showControls = true; true
                    }
                    Key.MediaPlay -> { player.play(); showControls = true; true }
                    Key.MediaPause -> { player.pause(); showControls = true; true }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        seekBy(-10_000); showControls = true; true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        seekBy(10_000); showControls = true; true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        showControls = true; true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(playbackError ?: "", color = Color.White, fontSize = 14.sp)
                }
            }
        } else if (isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentCyan, strokeWidth = 3.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(Strings["loading"], color = Color.White, fontSize = 13.sp)
                }
            }
        }

        LaunchedEffect(showControls) {
            if (showControls) {
                delay(5000)
                showControls = false
            }
        }

        if (showControls) {
            val topOverlayHeight = if (compactHeight) 56.dp else 80.dp
            val topPaddingVertical = if (compactHeight) 8.dp else 12.dp
            val seekButtonSize = if (compactHeight) 46.dp else 56.dp
            val seekIconSize = if (compactHeight) 24.dp else 32.dp
            val playButtonSize = if (compactHeight) 60.dp else 72.dp
            val playIconSize = if (compactHeight) 34.dp else 40.dp
            val controlsGap = if (compactHeight) 12.dp else 24.dp
            // I canali live non hanno una durata nota (duration <= 0): per questi non ha
            // senso mostrare slider e salti di 10s, quindi si mostra solo play/pausa.
            val isLive = duration <= 0L

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topOverlayHeight)
                        .align(Alignment.TopStart)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = topPaddingVertical),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = ::exitPlayer) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Strings["back"],
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_brand_mark),
                        contentDescription = Strings["tvApp"],
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Strings["tvApp"],
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isLive) {
                        IconButton(
                            onClick = {
                                val newPos = (currentPosition - 10000).coerceAtLeast(0)
                                player.seekTo(newPos)
                                currentPosition = newPos
                            },
                            modifier = Modifier.size(seekButtonSize)
                        ) {
                            Icon(
                                Icons.Filled.Replay10,
                                contentDescription = Strings["rewind10"],
                                tint = Color.White,
                                modifier = Modifier.size(seekIconSize)
                            )
                        }
                        Spacer(Modifier.width(controlsGap))
                    }
                    Surface(
                        modifier = Modifier.size(playButtonSize),
                        shape = CircleShape,
                        color = AccentCyan.copy(alpha = 0.9f)
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) player.pause() else player.play()
                            },
                            modifier = Modifier.size(playButtonSize)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) Strings["pause"] else Strings["play"],
                                tint = DarkBg,
                                modifier = Modifier.size(playIconSize)
                            )
                        }
                    }
                    if (!isLive) {
                        Spacer(Modifier.width(controlsGap))
                        IconButton(
                            onClick = {
                                val newPos = (currentPosition + 10000).coerceAtMost(duration)
                                player.seekTo(newPos)
                                currentPosition = newPos
                            },
                            modifier = Modifier.size(seekButtonSize)
                        ) {
                            Icon(
                                Icons.Filled.Forward10,
                                contentDescription = Strings["forward10"],
                                tint = Color.White,
                                modifier = Modifier.size(seekIconSize)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = if (compactHeight) 6.dp else 8.dp)
                ) {
                    if (!isLive) {
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                            onValueChange = { fraction ->
                                val newPos = (fraction * duration).toLong()
                                player.seekTo(newPos)
                                currentPosition = newPos
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isLive) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    Strings["live"].uppercase(),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = if (compactHeight) 11.sp else 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                "${formatTime(currentPosition)} / ${formatTime(duration)}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = if (compactHeight) 11.sp else 12.sp
                            )
                        }
                        Row {
                            IconButton(onClick = { isFullscreen = !isFullscreen }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = if (isFullscreen) Strings["exitFullscreen"] else Strings["fullscreen"],
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
