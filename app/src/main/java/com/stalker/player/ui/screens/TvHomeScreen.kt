package com.stalker.player.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import com.stalker.player.R
import com.stalker.player.data.model.Category
import com.stalker.player.data.model.Channel
import com.stalker.player.data.model.Profile
import com.stalker.player.data.model.Strings
import com.stalker.player.viewmodel.MainViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

private data class TvLanguageOption(
    val code: String,
    val flag: String,
    val nativeName: String
)

private val tvLanguageOptions = listOf(
    TvLanguageOption("it", "\uD83C\uDDEE\uD83C\uDDF9", "Italiano"),
    TvLanguageOption("en", "\uD83C\uDDEC\uD83C\uDDE7", "English"),
    TvLanguageOption("fr", "\uD83C\uDDEB\uD83C\uDDF7", "Français"),
    TvLanguageOption("de", "\uD83C\uDDE9\uD83C\uDDEA", "Deutsch"),
    TvLanguageOption("es", "\uD83C\uDDEA\uD83C\uDDF8", "Español"),
    TvLanguageOption("ru", "\uD83C\uDDF7\uD83C\uDDFA", "Русский")
)

@Composable
fun TvHomeScreen(viewModel: MainViewModel, onPlay: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val connected by viewModel.connected.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val portalType by viewModel.portalType.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val remoteUrl by viewModel.remoteImportUrl.collectAsState()
    val remoteCode by viewModel.remoteImportCode.collectAsState()
    val error by viewModel.error.collectAsState()
    val remoteMessage by viewModel.remoteImportMessage.collectAsState()
    val currentLang by viewModel.appLanguage.collectAsState()
    val movie by viewModel.selectedMovie.collectAsState()
    val series by viewModel.selectedSeries.collectAsState()
    val playerActive by viewModel.playerActive.collectAsState()
    val currentView by viewModel.currentView.collectAsState()
    val navStack by viewModel.navStackFlow.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQ by remember { mutableStateOf("") }
    var lastBackAt by remember { mutableLongStateOf(0L) }
    var showSettings by remember { mutableStateOf(false) }
    val tabKeys = listOf("Live", "Movies", "Series", "Info")
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(connected) {
        if (connected) {
            selectedTab = 0
            viewModel.setActiveTab(tabKeys[0])
            searchQ = ""
        }
    }
    LaunchedEffect(selectedTab, connected, searchQ) {
        if (connected && selectedTab < 3) {
            viewModel.updateSearch(tabKeys[selectedTab], searchQ)
        } else {
            viewModel.clearSearch()
        }
    }
    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            kotlinx.coroutines.delay(3_000)
            if (viewModel.error.value == error) {
                viewModel.clearError()
            }
        }
    }
    LaunchedEffect(remoteMessage) {
        if (remoteMessage.isNotBlank()) {
            kotlinx.coroutines.delay(3_000)
            if (viewModel.remoteImportMessage.value == remoteMessage) {
                viewModel.clearRemoteImportMessage()
            }
        }
    }
    val handleBack: () -> Unit = {
        when {
            movie != null -> viewModel.clearDetail()
            series != null -> viewModel.handleSeriesBack()
            navStack.isNotEmpty() || currentView != "categories" -> viewModel.goBack()
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2000L) {
                    activity?.finish()
                } else {
                    lastBackAt = now
                    android.widget.Toast.makeText(context, Strings["exitPrompt"], android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    BackHandler(enabled = !playerActive) { handleBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .onPreviewKeyEvent { event ->
                if (!playerActive && (event.key == Key.Back || event.key == Key.Escape)) {
                    if (event.type == KeyEventType.KeyUp) handleBack()
                    true
                } else {
                    false
                }
            }
    ) {
        if (!playerActive && movie != null) {
            TvMovieDetailDialog(item = movie!!, onPlay = { viewModel.startPlayback(movie!!, onPlay) }, onClose = { viewModel.clearDetail() })
        } else if (!playerActive && series != null) {
            TvSeriesDetailDialog(viewModel = viewModel, item = series!!, onPlay = onPlay, onClose = { viewModel.clearDetail() })
        } else Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TvSidebar(
                connected = connected,
                profiles = profiles,
                remoteUrl = remoteUrl,
                selectedTab = selectedTab,
                onSettings = { showSettings = true },
                onTab = {
                    selectedTab = it
                    searchQ = ""
                    viewModel.setActiveTab(tabKeys[it])
                },
                onProfile = { index -> viewModel.connectProfile(index) },
                onDisconnect = { viewModel.disconnect() }
            )

            Column(Modifier.weight(1f).fillMaxHeight()) {
                TvStatus(error = error, remoteMessage = remoteMessage, onClearError = { viewModel.clearError() }, onClearMessage = { viewModel.clearRemoteImportMessage() })
                Spacer(Modifier.height(12.dp))
                if (!connected) {
                    TvSetupPanel(remoteUrl = remoteUrl, remoteCode = remoteCode)
                } else {
                    if (selectedTab < 3 && currentView != "seasons" && currentView != "episodes") {
                        OutlinedTextField(
                            value = searchQ,
                            onValueChange = { searchQ = it },
                            placeholder = { Text(Strings["search"], color = Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                            colors = fieldColors(),
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Gray) },
                            trailingIcon = {
                                when {
                                    searchLoading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Cyan, strokeWidth = 2.dp)
                                    searchQ.isNotBlank() -> IconButton(onClick = { searchQ = "" }) { Icon(Icons.Default.Clear, null, tint = Gray) }
                                }
                            },
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    when (selectedTab) {
                        0 -> TvLiveGrid(viewModel = viewModel, query = searchQ, autoFocusResults = searchQ.isBlank(), onPlay = onPlay)
                        1 -> TvPosterGrid(viewModel = viewModel, query = searchQ, isSeries = false, autoFocusResults = searchQ.isBlank(), onPlay = onPlay)
                        2 -> TvPosterGrid(viewModel = viewModel, query = searchQ, isSeries = true, autoFocusResults = searchQ.isBlank(), onPlay = onPlay)
                        else -> InfoTab(viewModel)
                    }
                }
            }
        }

        if (isLoading) {
            TvLoadingOverlay(
                progress = progress,
                message = if (connected) Strings["loadingInProgress"] else Strings["loginInProgress"]
            )
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = Cyan)
                    Spacer(Modifier.width(8.dp))
                    Text(Strings["settings"], color = Cyan, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(Strings["language"], color = White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    tvLanguageOptions.forEach { option ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                viewModel.setLanguage(option.code)
                                showSettings = false
                            },
                            color = if (currentLang == option.code) Cyan.copy(alpha = 0.18f) else Bg,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (currentLang == option.code) Cyan else Cyan.copy(alpha = 0.12f))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(option.flag, fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(option.nativeName, color = White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                if (currentLang == option.code) Icon(Icons.Default.Check, null, tint = Cyan)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text(Strings["close"], color = Cyan) }
            }
        )
    }
}

@Composable
private fun TvSidebar(
    connected: Boolean,
    profiles: List<Profile>,
    remoteUrl: String,
    selectedTab: Int,
    onSettings: () -> Unit,
    onTab: (Int) -> Unit,
    onProfile: (Int) -> Unit,
    onDisconnect: () -> Unit
) {
    val firstProfileFocusRequester = remember { FocusRequester() }
    LaunchedEffect(connected, profiles.size) {
        if (!connected && profiles.isNotEmpty()) {
            firstProfileFocusRequester.requestFocus()
        }
    }
    Column(Modifier.width(330.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_brand_mark), null, tint = Color.Unspecified, modifier = Modifier.size(54.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(Strings["tvApp"], color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(Strings["tvSubtitle"], color = Cyan, fontSize = 13.sp)
            }
        }

        if (connected) {
            listOf(
                Icons.Default.LiveTv to Strings["live"],
                Icons.Default.Movie to Strings["movies"],
                Icons.Default.Tv to Strings["series"],
                Icons.Default.Info to Strings["info"]
            ).forEachIndexed { index, item ->
                val selected = selectedTab == index
                TvMenuButton(text = item.second, icon = item.first, selected = selected, onClick = { onTab(index) })
            }
            TvMenuButton(text = Strings["settings"], icon = Icons.Default.Settings, selected = false, onClick = onSettings)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red), border = BorderStroke(1.dp, Red.copy(alpha = 0.35f))) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(Strings["disconnect"])
            }
        } else {
            Text(Strings["profiles"], color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                if (profiles.isEmpty()) item { Text(Strings["noProfiles"], color = Gray, fontSize = 14.sp) }
                items(profiles.size) { index ->
                    val profile = profiles[index]
                    val modifier = if (index == 0) Modifier.focusRequester(firstProfileFocusRequester) else Modifier
                    Card(modifier.fillMaxWidth().clickable { onProfile(index) }, colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(profile.name, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Strings.portalLabel(profile.type), color = Cyan, fontSize = 12.sp)
                            Text(profile.url, color = Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan), border = BorderStroke(1.dp, Cyan.copy(alpha = 0.22f))) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(Strings["settings"])
            }
        }
    }
}

@Composable
private fun TvMenuButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = if (selected) Cyan else CardBg,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) Bg else Cyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, color = if (selected) Bg else White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TvStatus(error: String?, remoteMessage: String, onClearError: () -> Unit, onClearMessage: () -> Unit) {
    val text = error?.takeIf { it.isNotBlank() } ?: remoteMessage
    if (text.isBlank()) return
    val isError = !error.isNullOrBlank()
    Surface(Modifier.fillMaxWidth(), color = (if (isError) Red else Green).copy(alpha = 0.13f), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isError) Icons.Default.Error else Icons.Default.CheckCircle, null, tint = if (isError) Red else Green)
            Spacer(Modifier.width(10.dp))
            Text(text, color = White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = if (isError) onClearError else onClearMessage) { Icon(Icons.Default.Close, null, tint = Gray) }
        }
    }
}

@Composable
private fun TvLoadingOverlay(progress: Int, message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 360.dp, max = 520.dp),
            color = CardBg,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.18f))
        ) {
            Column(
                Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = Cyan, strokeWidth = 4.dp)
                Text(message, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(Strings["pleaseWaitLong"], color = Gray, fontSize = 14.sp)
                if (progress > 0) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Cyan,
                        trackColor = Bg
                    )
                    Text("$progress%", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TvSetupPanel(remoteUrl: String, remoteCode: String) {
    Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(28.dp)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(Strings["addListsFromBrowser"], color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            QrCodeImage(remoteUrl, Modifier.size(190.dp))
            Spacer(Modifier.height(14.dp))
            Text(Strings["urlLabel"], color = Gray, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Surface(color = Bg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Cyan.copy(alpha = 0.28f))) {
                Text(remoteUrl.ifBlank { Strings["serverStarting"] }, color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 560.dp).padding(horizontal = 16.dp, vertical = 10.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(Strings["codeLabel"], color = Gray, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Surface(color = Cyan, shape = RoundedCornerShape(16.dp)) {
                Text(remoteCode, color = Bg, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun TvTypeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), color = if (selected) Cyan.copy(alpha = 0.18f) else Bg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (selected) Cyan else Color(0xFF2A3442))) {
        Text(text, color = if (selected) Cyan else Gray, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun TvTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, password: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label, color = Gray) }, singleLine = true, modifier = modifier.fillMaxWidth(), colors = fieldColors(), textStyle = LocalTextStyle.current.copy(fontSize = 17.sp), visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}

@Composable
private fun QrCodeImage(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { createQrBitmap(text.ifBlank { "AstraTV" }, 512) }
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(24.dp)) {
        Image(bitmap.asImageBitmap(), null, modifier = Modifier.fillMaxSize().padding(10.dp))
    }
}

private fun createQrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}

@Composable
private fun TvLiveGrid(viewModel: MainViewModel, query: String, autoFocusResults: Boolean, onPlay: () -> Unit) {
    val cv by viewModel.currentView.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val scope = rememberCoroutineScope()
    val items = if (query.isNotBlank()) {
        searchResults
    } else if (cv == "channels") {
        channels
    } else {
        (categories["Live"] ?: emptyList()).map { Channel(id = it.categoryId, name = it.name, itemType = "category", categoryType = it.categoryType) }
    }
    if (query.isBlank() && cv != "channels") {
        TvCategoryList(items = items, requestInitialFocus = true) { item ->
            scope.launch { viewModel.onCategoryClick(Category(item.name, item.categoryType, item.id)) }
        }
        return
    }
    if (query.isNotBlank() && items.isEmpty() && !searchLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Strings["noResults"], color = Gray, fontSize = 16.sp)
        }
        return
    }
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(items.size, cv, autoFocusResults, query) {
        if (!autoFocusResults || query.isNotBlank()) return@LaunchedEffect
        if (items.isNotEmpty()) firstItemFocusRequester.requestFocus()
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(250.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items) { item ->
            val modifier = if (item == items.firstOrNull()) Modifier.focusRequester(firstItemFocusRequester) else Modifier
            Card(modifier.height(78.dp).clickable { scope.launch { if (item.itemType == "category") viewModel.onCategoryClick(Category(item.name, item.categoryType, item.id)) else viewModel.startPlayback(item, onPlay) } }, colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChanIcon(item.screenshotUri, item.itemType)
                    Spacer(Modifier.width(12.dp))
                    Text(item.name, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TvPosterGrid(viewModel: MainViewModel, query: String, isSeries: Boolean, autoFocusResults: Boolean, onPlay: () -> Unit) {
    val cv by viewModel.currentView.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val scope = rememberCoroutineScope()
    val tab = if (isSeries) "Series" else "Movies"
    val items = if (query.isNotBlank()) {
        searchResults
    } else if (cv == "channels") {
        channels
    } else {
        (categories[tab] ?: emptyList()).map { Channel(id = it.categoryId, name = it.name, itemType = "category", categoryType = it.categoryType) }
    }
    if (query.isBlank() && cv != "channels") {
        TvCategoryList(items = items, requestInitialFocus = true) { item ->
            scope.launch { viewModel.onCategoryClick(Category(item.name, if (isSeries) "Series" else "VOD", item.id)) }
        }
        return
    }
    if (query.isNotBlank() && items.isEmpty() && !searchLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Strings["noResults"], color = Gray, fontSize = 16.sp)
        }
        return
    }
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(items.size, cv, isSeries, autoFocusResults, query) {
        if (!autoFocusResults || query.isNotBlank()) return@LaunchedEffect
        if (items.isNotEmpty()) firstItemFocusRequester.requestFocus()
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(165.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items) { item ->
            val modifier = if (item == items.firstOrNull()) Modifier.focusRequester(firstItemFocusRequester) else Modifier
            Card(modifier.clickable { scope.launch { if (item.itemType == "category") viewModel.onCategoryClick(Category(item.name, if (isSeries) "Series" else "VOD", item.id)) else if (isSeries) viewModel.onSeriesClick(item) else viewModel.onVodClick(item) } }, colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp)) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.72f), contentAlignment = Alignment.Center) {
                        if (item.screenshotUri.isNotBlank()) AsyncImage(ImageRequest.Builder(LocalContext.current).data(item.screenshotUri).crossfade(true).build(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(if (isSeries) Icons.Default.Tv else Icons.Default.Movie, null, tint = Gray, modifier = Modifier.size(46.dp))
                    }
                    Text(item.name, color = White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
                }
            }
        }
    }
}

@Composable
private fun TvCategoryList(items: List<Channel>, requestInitialFocus: Boolean = false, onClick: (Channel) -> Unit) {
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(items.size, requestInitialFocus) {
        if (requestInitialFocus && items.isNotEmpty()) firstItemFocusRequester.requestFocus()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { item ->
            val modifier = if (item == items.firstOrNull()) Modifier.focusRequester(firstItemFocusRequester) else Modifier
            Surface(
                modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onClick(item) },
                color = CardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Cyan.copy(alpha = 0.08f))
            ) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = Cyan, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(item.name, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = Gray, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun TvMovieDetailDialog(item: Channel, onPlay: () -> Unit, onClose: () -> Unit) {
    BackHandler(enabled = true) { onClose() }
    val backFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocusRequester.requestFocus() }

    Row(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(Modifier.width(250.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PosterBox(item, Modifier.width(170.dp).aspectRatio(0.68f))
            Surface(Modifier.fillMaxWidth(), color = CardBg, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Cyan.copy(alpha = 0.08f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvMetaChips(item, compact = true)
                    HorizontalDivider(color = Cyan.copy(alpha = 0.10f))
                    TvFact(Strings["year"], item.year)
                    TvFact(Strings["duration"], tvFormatDurationLabel(item))
                    TvFact(Strings["ageShort"], item.age)
                    TvFact("IMDb", item.ratingImdb)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose, modifier = Modifier.focusRequester(backFocusRequester)) {
                    Icon(Icons.Default.ArrowBack, null, tint = Cyan)
                    Spacer(Modifier.width(6.dp))
                    Text(Strings["back"], color = Cyan, fontSize = 15.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(item.name, color = White, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Surface(Modifier.fillMaxWidth().heightIn(min = 112.dp, max = 180.dp), color = CardBg, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Cyan.copy(alpha = 0.08f))) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Text(Strings["plot"], color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.description.ifBlank { Strings["noDescription"] },
                        color = White.copy(alpha = 0.86f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onPlay, modifier = Modifier.width(188.dp).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg)) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(Strings["play"], fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TvSeriesDetailDialog(viewModel: MainViewModel, item: Channel, onPlay: () -> Unit, onClose: () -> Unit) {
    val currentView by viewModel.currentView.collectAsState()
    val seasons by viewModel.seasons.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val backFocusRequester = remember { FocusRequester() }
    val onBack = remember(viewModel, currentView, onClose) {
        {
            if (currentView == "episodes") viewModel.goBack() else onClose()
        }
    }
    BackHandler(enabled = true) { onBack() }
    LaunchedEffect(currentView) { backFocusRequester.requestFocus() }

    Row(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(Modifier.width(250.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PosterBox(item, Modifier.width(160.dp).aspectRatio(0.68f))
            Surface(Modifier.fillMaxWidth(), color = CardBg, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Cyan.copy(alpha = 0.08f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvMetaChips(item, compact = true)
                    HorizontalDivider(color = Cyan.copy(alpha = 0.10f))
                    TvFact(Strings["year"], item.year)
                    TvFact(Strings["duration"], tvFormatDurationLabel(item))
                    TvFact(Strings["ageShort"], item.age)
                    TvFact("IMDb", item.ratingImdb)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, modifier = Modifier.focusRequester(backFocusRequester)) {
                    Icon(Icons.Default.ArrowBack, null, tint = Cyan)
                    Spacer(Modifier.width(6.dp))
                    Text(Strings["back"], color = Cyan, fontSize = 15.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(item.name, color = White, fontSize = 23.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (currentView == "episodes") {
                    TextButton(onClick = { viewModel.goBack() }) { Text(Strings["seasons"], color = Cyan) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(Modifier.fillMaxWidth().heightIn(min = 104.dp, max = 136.dp), color = CardBg, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Cyan.copy(alpha = 0.08f))) {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(Strings["plot"], color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (item.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(item.description, color = White.copy(alpha = 0.82f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(Modifier.fillMaxWidth().weight(1f), color = CardBg, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Cyan.copy(alpha = 0.08f))) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(if (currentView == "episodes") Strings["episodes"] else Strings["seasons"], color = if (currentView == "episodes") Green else Purple, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (currentView == "episodes") {
                        TvEpisodeList(items = episodes, emptyText = Strings["noEpisodesAvailable"], requestInitialFocus = true, modifier = Modifier.weight(1f)) { episode -> viewModel.startPlayback(episode, onPlay) }
                    } else {
                        TvEpisodeList(items = seasons, emptyText = Strings["noSeasonsAvailable"], requestInitialFocus = true, modifier = Modifier.weight(1f)) { season -> viewModel.onSeasonClick(season) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvEpisodeList(items: List<Channel>, emptyText: String, requestInitialFocus: Boolean = false, modifier: Modifier = Modifier, onClick: (Channel) -> Unit) {
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(items.size, requestInitialFocus) {
        if (requestInitialFocus && items.isNotEmpty()) firstItemFocusRequester.requestFocus()
    }
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(emptyText, color = Gray, fontSize = 15.sp) }
    } else {
        LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(items) { row ->
                val modifier = if (row == items.firstOrNull()) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                TvEpisodeRow(row, modifier) { onClick(row) }
            }
        }
    }
}

@Composable
private fun TvMetaChips(item: Channel, compact: Boolean = false) {
    val chips = buildList {
        if (item.ratingImdb.isNotBlank()) add("IMDb ${item.ratingImdb}")
        if (item.year.isNotBlank()) add(item.year)
        tvFormatDurationLabel(item).takeIf { it.isNotBlank() }?.let { add(it) }
        if (item.age.isNotBlank()) add(item.age)
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)) {
        if (chips.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.take(4).forEach { chip ->
                    Surface(color = Cyan.copy(alpha = 0.13f), shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, Cyan.copy(alpha = 0.25f))) {
                        Text(chip, color = Cyan, fontSize = if (compact) 10.sp else 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 4.dp else 5.dp))
                    }
                }
            }
        }
        if (item.genresStr.isNotBlank()) Text(item.genresStr, color = White, fontSize = if (compact) 12.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.director.isNotBlank()) Text("${Strings["directedBy"]}: ${item.director}", color = Gray, fontSize = if (compact) 11.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.actors.isNotBlank()) Text("${Strings["cast"]}: ${item.actors}", color = Gray, fontSize = if (compact) 11.sp else 12.sp, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvFact(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PosterBox(item: Channel, modifier: Modifier = Modifier) {
    Surface(modifier, color = Bg, shape = RoundedCornerShape(20.dp)) {
        if (item.screenshotUri.isNotBlank()) {
            AsyncImage(ImageRequest.Builder(LocalContext.current).data(item.screenshotUri).crossfade(true).build(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Movie, null, tint = Gray, modifier = Modifier.size(64.dp)) }
        }
    }
}

@Composable
private fun TvMeta(item: Channel) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (item.ratingImdb.isNotBlank()) Text("IMDb ${item.ratingImdb}", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (item.year.isNotBlank()) Text(item.year, color = White, fontSize = 14.sp)
        if (item.genresStr.isNotBlank()) Text(item.genresStr, color = Gray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val duration = tvFormatDurationLabel(item)
        if (duration.isNotBlank()) Text(duration, color = Gray, fontSize = 13.sp)
        if (item.actors.isNotBlank()) Text("${Strings["cast"]}: ${item.actors}", color = Gray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun tvFormatDurationLabel(item: Channel): String {
    if (item.durationText.isNotBlank()) return item.durationText
    if (item.time <= 0) return ""
    val totalMinutes = item.time / 60
    if (totalMinutes <= 0) return "${item.time}s"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun TvEpisodeRow(item: Channel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), color = Bg, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Cyan.copy(alpha = 0.12f))) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (item.itemType == "season") Icons.Default.Folder else Icons.Default.PlayArrow, null, tint = if (item.itemType == "season") Purple else Green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(item.name.ifBlank { if (item.itemType == "season") Strings.seasonName(item.seasonNumber) else Strings.episodeName(item.episodeNumber) }, color = White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}
