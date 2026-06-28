package com.stalker.player.ui.screens

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stalker.player.R
import com.stalker.player.data.model.*
import com.stalker.player.viewmodel.MainViewModel
import kotlinx.coroutines.launch

val Bg = Color(0xFF11161D)
val CardBg = Color(0xFF1A212B)
val Cyan = Color(0xFF7FA6C9)
val Green = Color(0xFF7FA287)
val Purple = Color(0xFF8A86A8)
val Pink = Color(0xFFAA7F95)
val White = Color(0xFFE7EDF4)
val Gray = Color(0xFF8C99AA)
val Red = Color(0xFFD06C62)
val Orange = Color(0xFFC19363)

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF2A3442),
    focusedTextColor = White, unfocusedTextColor = White, cursorColor = Cyan
)

private data class LanguageOption(
    val code: String,
    val flag: String,
    val nativeName: String
)

private val languageOptions = listOf(
    LanguageOption("it", "\uD83C\uDDEE\uD83C\uDDF9", "Italiano"),
    LanguageOption("en", "\uD83C\uDDEC\uD83C\uDDE7", "English"),
    LanguageOption("fr", "\uD83C\uDDEB\uD83C\uDDF7", "Français"),
    LanguageOption("de", "\uD83C\uDDE9\uD83C\uDDEA", "Deutsch"),
    LanguageOption("es", "\uD83C\uDDEA\uD83C\uDDF8", "Español"),
    LanguageOption("ru", "\uD83C\uDDF7\uD83C\uDDFA", "Русский")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel, onPlay: () -> Unit) {
    var macHostname by remember { mutableStateOf("") }
    var macAddress by remember { mutableStateOf("") }
    var xtreamHostname by remember { mutableStateOf("") }
    var xtreamUsername by remember { mutableStateOf("") }
    var xtreamPassword by remember { mutableStateOf("") }
    var m3uSource by remember { mutableStateOf("") }
    var pwVis by remember { mutableStateOf(false) }
    var selTab by remember { mutableIntStateOf(0) }
    var showProfMgr by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingDeleteProfile by remember { mutableStateOf<Profile?>(null) }
    var pname by remember { mutableStateOf("") }
    var searchQ by remember { mutableStateOf("") }
    var connectPending by remember { mutableStateOf(false) }
    val tabs = listOf(Strings["live"], Strings["movies"], Strings["series"], Strings["info"])
    val tabKeys = listOf("Live", "Movies", "Series", "Info")
    val context = LocalContext.current
    val activity = context as? Activity
    val rootView = LocalView.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideLayout = configuration.screenWidthDp >= 840
    val compactHeight = configuration.screenHeightDp < 620
    val formMaxWidth = if (isWideLayout) 820.dp else 640.dp
    val dialogMaxContentHeight = (configuration.screenHeightDp * if (isLandscape) 0.56f else 0.68f).dp
    val compactUiScale = when {
        isLandscape && configuration.screenHeightDp < 390 -> 0.82f
        isLandscape && configuration.screenHeightDp < 460 -> 0.88f
        isLandscape && configuration.screenHeightDp < 540 -> 0.93f
        else -> 1f
    }

    val connected by viewModel.connected.collectAsState()
    val error by viewModel.error.collectAsState()
    val portalType by viewModel.portalType.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val loadedProfile by viewModel.loadedProfile.collectAsState()
    val currentLang by viewModel.appLanguage.collectAsState()
    val currentView by viewModel.currentView.collectAsState()
    val navStack by viewModel.navStackFlow.collectAsState()
    val selectedMovie by viewModel.selectedMovie.collectAsState()
    val selectedSeries by viewModel.selectedSeries.collectAsState()
    val playerActive by viewModel.playerActive.collectAsState()
    val currentHostname by viewModel.currentHostname.collectAsState()
    val currentMac by viewModel.currentMac.collectAsState()
    val currentUsername by viewModel.currentUsername.collectAsState()
    val currentPassword by viewModel.currentPassword.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val remoteMessage by viewModel.remoteImportMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isX = portalType == "xtream"
    val isM3u = portalType == "m3u"
    val hostname = when (portalType) {
        "xtream" -> xtreamHostname
        "m3u" -> m3uSource
        else -> macHostname
    }
    val mac = macAddress
    val username = xtreamUsername
    val password = xtreamPassword
    val loginLoading = !connected && isLoading
    val showConnectStatus = connectPending || loginLoading || progress > 0
    val scope = rememberCoroutineScope()
    var lastBackAt by remember { mutableLongStateOf(0L) }
    val m3uPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            viewModel.setM3uSource(it)
            m3uSource = it.toString()
        }
    }

    LaunchedEffect(loadedProfile) {
        loadedProfile?.let { p ->
            when (p.type) {
                "xtream" -> {
                    xtreamHostname = p.url
                    xtreamUsername = p.username
                    xtreamPassword = p.password
                }
                "m3u" -> m3uSource = p.url
                else -> {
                    macHostname = p.url
                    macAddress = p.mac
                }
            }
            viewModel.setPortalType(p.type)
            viewModel.consumeLoadedProfile()
        }
    }

    LaunchedEffect(portalType) {
        when (portalType) {
            "xtream" -> {
                macHostname = ""
                macAddress = ""
                m3uSource = ""
            }
            "m3u" -> {
                macHostname = ""
                macAddress = ""
                xtreamHostname = ""
                xtreamUsername = ""
                xtreamPassword = ""
                pwVis = false
            }
            else -> {
                xtreamHostname = ""
                xtreamUsername = ""
                xtreamPassword = ""
                m3uSource = ""
                pwVis = false
            }
        }
    }

    LaunchedEffect(selTab) {
        viewModel.setActiveTab(tabKeys[selTab])
        searchQ = ""
    }

    LaunchedEffect(searchQ, selTab, connected) {
        if (connected && selTab < 3) {
            viewModel.updateSearch(tabKeys[selTab], searchQ)
        } else {
            viewModel.clearSearch()
        }
    }

    LaunchedEffect(loginLoading, connected, error, progress) {
        if (loginLoading || progress > 0 || connected || !error.isNullOrBlank()) {
            connectPending = false
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

    DisposableEffect(rootView) {
        rootView.keepScreenOn = true
        onDispose { rootView.keepScreenOn = false }
    }

    val shouldInterceptBack = connected || (!playerActive && (selectedMovie != null || selectedSeries != null)) || navStack.isNotEmpty() || currentView != "categories"
    BackHandler(enabled = shouldInterceptBack) {
        when {
            selectedMovie != null -> viewModel.clearDetail()
            selectedSeries != null -> viewModel.handleSeriesBack()
            navStack.isNotEmpty() || currentView != "categories" -> viewModel.goBack()
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2000L) {
                    activity?.finish()
                } else {
                    lastBackAt = now
                    Toast.makeText(context, Strings["exitPrompt"], Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_brand_mark),
                        contentDescription = Strings["tvApp"],
                        tint = Color.Unspecified,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Strings["tvApp"], color = White, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                            if (connected) {
                                Spacer(Modifier.width(8.dp))
                                Surface(color = when (portalType) { "xtream" -> Pink; "m3u" -> Orange; else -> Cyan }.copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
                                    Text(Strings.portalLabel(portalType), color = when (portalType) { "xtream" -> Pink; "m3u" -> Orange; else -> Cyan }, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            },
            actions = {
                if (connected) {
                    TextButton(onClick = { viewModel.disconnect() }, enabled = !playerActive) {
                        Icon(Icons.Default.Close, null, tint = Red, modifier = Modifier.size(16.dp))
                        Text(Strings["disconnect"], color = Red, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, null, tint = Gray)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )

        if (error != null && error!!.isNotBlank()) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), color = Red.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(error!!, color = Red, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(18.dp)) {
                        Icon(Icons.Default.Close, null, tint = Red, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        if (!connected) {
            val canConnect = when {
                showConnectStatus -> false
                isX -> hostname.isNotBlank() && username.isNotBlank() && password.isNotBlank()
                isM3u -> hostname.isNotBlank()
                else -> hostname.isNotBlank() && mac.isNotBlank()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = formMaxWidth)
                        .scale(compactUiScale)
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(Strings["portalType"], color = Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("mac" to Strings["stbMac"], "xtream" to Strings["xtream"], "m3u" to Strings["m3u"]).forEach { (t, l) ->
                                FilterChip(
                                    selected = portalType == t,
                                    onClick = { viewModel.setPortalType(t) },
                                    label = { Text(l, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Cyan.copy(alpha = 0.12f),
                                        selectedLabelColor = Cyan,
                                        containerColor = Bg,
                                        labelColor = Gray
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            hostname,
                            {
                                when (portalType) {
                                    "xtream" -> xtreamHostname = it
                                    "m3u" -> m3uSource = it
                                    else -> macHostname = it
                                }
                            },
                            label = { Text(if (isM3u) Strings["m3uSource"] else Strings["hostname"], color = Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        Spacer(Modifier.height(8.dp))
                        if (isX) {
                            OutlinedTextField(username, { xtreamUsername = it }, label = { Text(Strings["username"], color = Gray) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                password,
                                { xtreamPassword = it },
                                label = { Text(Strings["password"], color = Gray) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (pwVis) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = { IconButton(onClick = { pwVis = !pwVis }) { Icon(if (pwVis) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = Gray) } },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                colors = fieldColors()
                            )
                        } else if (isM3u) {
                            OutlinedButton(
                                onClick = { m3uPicker.launch(arrayOf("*/*", "audio/x-mpegurl", "application/x-mpegURL")) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Orange.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(Strings["chooseM3uFile"])
                            }
                        } else {
                            OutlinedTextField(mac, { macAddress = it }, label = { Text(Strings["macAddress"], color = Gray) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), colors = fieldColors())
                        }
                        Spacer(Modifier.height(16.dp))

                        if (isLandscape && !isWideLayout) {
                            Button(
                                onClick = {
                                    connectPending = true
                                    scope.launch {
                                        when {
                                            isX -> viewModel.connect(hostname, username, password)
                                            isM3u -> viewModel.connect(hostname, "")
                                            else -> viewModel.connect(hostname, mac)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canConnect,
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Bg)
                            ) {
                                if (!showConnectStatus) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(if (showConnectStatus) Strings["connecting"] else Strings["connect"], fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (showConnectStatus) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelConnect(); connectPending = false },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Orange.copy(alpha = 0.4f))
                                ) {
                                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(Strings["cancel"])
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showSave = true },
                                    enabled = hostname.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = 0.2f))
                                ) {
                                    Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(Strings["save"])
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        connectPending = true
                                        scope.launch {
                                            when {
                                                isX -> viewModel.connect(hostname, username, password)
                                                isM3u -> viewModel.connect(hostname, "")
                                                else -> viewModel.connect(hostname, mac)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = canConnect,
                                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Bg)
                                ) {
                                    if (!showConnectStatus) {
                                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(if (showConnectStatus) Strings["connecting"] else Strings["connect"], fontWeight = FontWeight.Bold)
                                }
                                if (showConnectStatus) {
                                    OutlinedButton(onClick = { viewModel.cancelConnect(); connectPending = false }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange), border = androidx.compose.foundation.BorderStroke(1.dp, Orange.copy(alpha = 0.4f))) {
                                        Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(Strings["cancel"])
                                    }
                                } else {
                                    OutlinedButton(onClick = { showSave = true }, enabled = hostname.isNotBlank(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray), border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = 0.2f))) {
                                        Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(Strings["save"])
                                    }
                                }
                            }
                        }

                        if (showConnectStatus) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Cyan.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        when {
                                            connectPending -> Strings["startingConnection"]
                                            progress >= 80 -> Strings["loadingCategories"]
                                            else -> Strings["handshake"]
                                        },
                                        color = White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    if (progress > 0) {
                                        LinearProgressIndicator(
                                            progress = { (progress.coerceIn(0, 100)) / 100f },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Cyan,
                                            trackColor = Bg
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Cyan,
                                            trackColor = Bg
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        if (progress > 0) "$progress%" else Strings["pleaseWait"],
                                        color = Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(if (compactHeight) 8.dp else 10.dp))
                OutlinedButton(
                    onClick = { showProfMgr = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = formMaxWidth)
                        .scale(compactUiScale)
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Gray),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.ManageAccounts, null, tint = Cyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Strings["profiles"], color = Cyan)
                }
            }
        }

        if (connected) {
            val tabIconSize = if (compactUiScale < 1f) 14.dp else 16.dp
            val tabFontSize = if (compactUiScale < 1f) 11.sp else 12.sp
            OutlinedTextField(searchQ, { searchQ = it }, placeholder = { Text(Strings["search"], color = Gray) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = fieldColors(), leadingIcon = { Icon(Icons.Default.Search, null, tint = Gray) }, trailingIcon = {
                when {
                    searchLoading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Cyan, strokeWidth = 2.dp)
                    searchQ.isNotBlank() -> IconButton(onClick = { searchQ = "" }) { Icon(Icons.Default.Clear, null, tint = Gray) }
                }
            }, shape = RoundedCornerShape(18.dp))
            TabRow(selectedTabIndex = selTab, containerColor = Bg, contentColor = Cyan, divider = {}) {
                val tabIcons = listOf(Icons.Default.LiveTv, Icons.Default.Movie, Icons.Default.Tv, Icons.Default.Info)
                tabs.forEachIndexed { i, t ->
                    Tab(selected = selTab == i, onClick = { selTab = i; viewModel.setActiveTab(t) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = tabIcons[i], contentDescription = null, modifier = Modifier.size(tabIconSize), tint = if (selTab == i) Cyan else Gray)
                                Spacer(Modifier.width(4.dp))
                                Text(t, fontSize = tabFontSize, color = if (selTab == i) Cyan else Gray)
                            }
                        })
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                when (selTab) {
                    0 -> ChannelListView(viewModel, "Live", searchQ, onPlay)
                    1 -> GridView(viewModel, "Movies", searchQ, onPlay, isSeries = false)
                    2 -> GridView(viewModel, "Series", searchQ, onPlay, isSeries = true)
                    3 -> InfoTab(viewModel)
                }
            }
        }
    }

    // ── Dialogs ──
    if (showSave) {
        AlertDialog(onDismissRequest = { showSave = false }, containerColor = CardBg, title = { Text(Strings["saveProfile"], color = Cyan, fontWeight = FontWeight.Bold) },
            text = { Column { OutlinedTextField(pname, { pname = it }, label = { Text(Strings["name"], color = Gray) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors()); Spacer(Modifier.height(4.dp)); Text("${Strings["type"]}: ${Strings.portalLabel(portalType)}", color = Gray, fontSize = 11.sp) } },
            confirmButton = { TextButton(onClick = { viewModel.saveProfile(pname.ifBlank { "P${profiles.size + 1}" }, hostname, mac, username, password, portalType); pname = ""; showSave = false }) { Text(Strings["save"], color = Cyan) } },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text(Strings["cancel"], color = Gray) } })
    }
    if (showProfMgr) {
        AlertDialog(onDismissRequest = { showProfMgr = false }, containerColor = CardBg, title = { Row { Icon(Icons.Default.ManageAccounts, null, tint = Cyan); Spacer(Modifier.width(8.dp)); Text(Strings["profiles"], color = Cyan, fontWeight = FontWeight.Bold) } },
            text = {
                if (profiles.isEmpty()) Text(Strings["noProfiles"], color = Gray)
                else LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(profiles.size) { i ->
                        val p = profiles[i]
                        Card(colors = CardDefaults.cardColors(containerColor = Bg), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(p.name, color = White, fontSize = 13.sp)
                                        Spacer(Modifier.width(6.dp))
                                        val profileType = p.type.lowercase()
                                        val badgeColor = when (profileType) {
                                            "xtream" -> Pink
                                            "m3u" -> Orange
                                            else -> Cyan
                                        }
                                        Surface(color = badgeColor.copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
                                            Text(
                                                Strings.portalLabel(profileType),
                                                color = badgeColor,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(p.url, color = Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { viewModel.loadProfile(i); showProfMgr = false }) { Icon(Icons.Default.PlayArrow, Strings["load"], tint = Green) }
                                IconButton(onClick = { pendingDeleteProfile = p }) { Icon(Icons.Default.Delete, Strings["delete"], tint = Red) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProfMgr = false }) { Text(Strings["close"], color = Cyan) } })
    }
    pendingDeleteProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDeleteProfile = null },
            containerColor = CardBg,
            title = { Text(Strings["delete"], color = Red, fontWeight = FontWeight.Bold) },
            text = { Text(Strings.fmt("confirmDeleteProfileNamed", profile.name), color = White) },
            confirmButton = {
                TextButton(onClick = {
                    val index = profiles.indexOfFirst { it.name == profile.name }
                    if (index >= 0) viewModel.deleteProfile(index)
                    pendingDeleteProfile = null
                }) { Text(Strings["delete"], color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProfile = null }) { Text(Strings["cancel"], color = Gray) }
            }
        )
    }

    // Settings dialog
    if (showSettings) {
        AlertDialog(onDismissRequest = { showSettings = false }, containerColor = CardBg,
            title = { Row { Icon(Icons.Default.Settings, null, tint = Cyan); Spacer(Modifier.width(8.dp)); Text(Strings["settings"], color = Cyan, fontWeight = FontWeight.Bold) } },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = dialogMaxContentHeight)
                        .scale(compactUiScale)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(Strings["language"], color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        languageOptions.forEach { option ->
                            LanguageRow(
                                option = option,
                                selected = currentLang == option.code,
                                onClick = { viewModel.setLanguage(option.code); showSettings = false }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Cyan.copy(alpha = 0.1f))
                    Spacer(Modifier.height(12.dp))
                    Text(Strings["developer"], color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text("Ak1r4Yuk1", color = Cyan, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(Strings["credits"], color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(Strings["developedWith"], color = Gray, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text(Strings["close"], color = Cyan) } }
        )
    }

    // Movie detail
    val movie by viewModel.selectedMovie.collectAsState()
    if (!playerActive) movie?.let { m ->
        AlertDialog(onDismissRequest = { viewModel.clearDetail() }, containerColor = CardBg, title = { Text(m.name, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = dialogMaxContentHeight)
                        .scale(compactUiScale)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(Modifier.fillMaxWidth().height(if (isLandscape) 140.dp else 200.dp), contentAlignment = Alignment.Center) {
                        if (m.screenshotUri.isNotBlank()) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(m.screenshotUri).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (m.ratingImdb.isNotBlank()) Text("\u2B50 IMDb: ${m.ratingImdb}", color = Cyan, fontSize = 13.sp)
                    if (m.year.isNotBlank()) Text("${Strings["year"]}: ${m.year}", color = White, fontSize = 13.sp)
                    if (m.genresStr.isNotBlank()) Text(m.genresStr, color = Gray, fontSize = 12.sp)
                    val movieDuration = formatDurationLabel(m)
                    if (movieDuration.isNotBlank()) Text("Duration: $movieDuration", color = White, fontSize = 12.sp)
                    if (m.age.isNotBlank()) Text("Age: ${m.age}", color = White, fontSize = 12.sp)
                    if (m.director.isNotBlank()) Text("${Strings["director"]}: ${m.director}", color = White, fontSize = 12.sp)
                    if (m.actors.isNotBlank()) Text("Cast: ${m.actors}", color = Gray, fontSize = 12.sp)
                    if (m.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isLandscape) 68.dp else 96.dp),
                            color = Bg,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp)
                            ) {
                                Text(m.description, color = Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { viewModel.startPlayback(m, onPlay) }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(Strings["play"]) } },
            dismissButton = { TextButton(onClick = { viewModel.clearDetail() }) { Text(Strings["close"], color = Gray) } })
    }

    // Series detail
    val series by viewModel.selectedSeries.collectAsState()
    if (!playerActive) series?.let { s ->
        val cv by viewModel.currentView.collectAsState()
        val seas by viewModel.seasons.collectAsState()
        val eps by viewModel.episodes.collectAsState()
        AlertDialog(onDismissRequest = { if (cv == "episodes") viewModel.goBack() else viewModel.clearDetail() }, containerColor = CardBg, title = { Text(s.name, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = dialogMaxContentHeight)
                        .scale(compactUiScale)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(Modifier.fillMaxWidth().height(if (isLandscape) 128.dp else 180.dp), contentAlignment = Alignment.Center) {
                        if (s.screenshotUri.isNotBlank()) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(s.screenshotUri).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (s.ratingImdb.isNotBlank()) Text("\u2B50 IMDb: ${s.ratingImdb}", color = Cyan, fontSize = 13.sp)
                    if (s.year.isNotBlank()) Text("${Strings["year"]}: ${s.year}", color = White, fontSize = 13.sp)
                    if (s.genresStr.isNotBlank()) Text(s.genresStr, color = Gray, fontSize = 12.sp)
                    val seriesDuration = formatDurationLabel(s)
                    if (seriesDuration.isNotBlank()) Text("Duration: $seriesDuration", color = White, fontSize = 12.sp)
                    if (s.age.isNotBlank()) Text("Age: ${s.age}", color = White, fontSize = 12.sp)
                    if (s.director.isNotBlank()) Text("${Strings["director"]}: ${s.director}", color = White, fontSize = 12.sp)
                    if (s.actors.isNotBlank()) Text("Cast: ${s.actors}", color = Gray, fontSize = 12.sp)
                    if (s.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isLandscape) 64.dp else 88.dp),
                            color = Bg,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp)
                            ) {
                                Text(s.description, color = Gray, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp)); HorizontalDivider(color = Cyan.copy(alpha = 0.1f)); Spacer(Modifier.height(6.dp))
                    when (cv) {
                        "seasons" -> {
                            Text(Strings["seasons"], color = Purple, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.height(4.dp))
                            LazyColumn(Modifier.heightIn(max = 200.dp)) {
                                items(seas) { s2 -> Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { viewModel.onSeasonClick(s2) }, colors = CardDefaults.cardColors(containerColor = Bg), shape = RoundedCornerShape(8.dp)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, tint = Purple, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("${Strings["seasonShort"]}${s2.seasonNumber} \u2014 ${s2.name}", color = White, fontSize = 13.sp) } } }
                            }
                        }
                        "episodes" -> {
                            Text(Strings["episodes"], color = Green, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.height(4.dp))
                            LazyColumn(Modifier.heightIn(max = 200.dp)) {
                                items(eps) { ep -> Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { viewModel.startPlayback(ep, onPlay) }, colors = CardDefaults.cardColors(containerColor = Bg), shape = RoundedCornerShape(8.dp)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayArrow, null, tint = Green, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("${Strings["episodeShort"]}${ep.episodeNumber} \u2014 ${ep.name}", color = White, fontSize = 13.sp) } } }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { if (cv == "episodes") viewModel.goBack() else viewModel.clearDetail() }) { Text(if (cv == "episodes") Strings["back"] else Strings["close"], color = Gray) } },
            dismissButton = {})
    }

}

// ─── Channel list view (Live tab) ───
@Composable
fun ChannelListView(viewModel: MainViewModel, tab: String, query: String, onPlay: () -> Unit) {
    val configuration = LocalConfiguration.current
    val cv by viewModel.currentView.collectAsState()
    val ns by viewModel.navStackFlow.collectAsState()
    val chs by viewModel.channels.collectAsState()
    val cats by viewModel.categories.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()
    // Stati di scroll distinti per la lista categorie e per la lista canali, mantenuti
    // tra le navigazioni avanti/indietro.
    val channelsGridState = rememberLazyGridState()
    val categoriesGridState = rememberLazyGridState()
    val channelsListState = rememberLazyListState()
    val categoriesListState = rememberLazyListState()
    val activeGridState = if (cv == "channels") channelsGridState else categoriesGridState
    val activeListState = if (cv == "channels") channelsListState else categoriesListState

    Column(Modifier.fillMaxSize()) {
        if (ns.isNotEmpty()) {
            TextButton(onClick = { viewModel.goBack() }, modifier = Modifier.padding(horizontal = 6.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Cyan, modifier = Modifier.size(16.dp))
                Text(Strings["back"], color = Cyan, fontSize = 12.sp)
            }
        }
        if (isLoading && cv == "channels") {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Cyan, strokeWidth = 3.dp)
                Spacer(Modifier.height(8.dp))
                Text(Strings["loading"], color = Gray, fontSize = 13.sp)
            }
        }
        val items = when (cv) {
            "channels" -> chs
            else -> (cats[tab] ?: emptyList()).map { cat -> Channel(id = cat.categoryId, name = cat.name, itemType = "category", categoryType = cat.categoryType, screenshotUri = cat.screenshotUri) }
        }
        val filtered = if (query.isBlank()) items else searchResults
        val useTvGrid = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && configuration.screenWidthDp >= 960
        if (query.isNotBlank() && filtered.isEmpty() && !searchLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Strings["noResults"], color = Gray, fontSize = 14.sp)
            }
            return
        }
        if (useTvGrid) {
            LazyVerticalGrid(
                state = activeGridState,
                columns = GridCells.Adaptive(minSize = if (cv == "channels") 300.dp else 260.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered) { item ->
                    ChannelRowCard(item = item, onClick = {
                        scope.launch {
                            if (item.itemType == "category") viewModel.onCategoryClick(Category(item.name, item.categoryType, item.id))
                            else viewModel.startPlayback(item, onPlay)
                        }
                    })
                }
            }
        } else {
            LazyColumn(state = activeListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(filtered) { item ->
                    ChannelRowCard(
                        item = item,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        onClick = {
                            scope.launch {
                                if (item.itemType == "category") viewModel.onCategoryClick(Category(item.name, item.categoryType, item.id))
                                else viewModel.startPlayback(item, onPlay)
                            }
                        }
                    )
                }
            }
        }
    }

}

@Composable
private fun ChannelRowCard(item: Channel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ChanIcon(item.screenshotUri, item.itemType)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            }
            if (item.itemType == "category") {
                Surface(color = Cyan.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text(Strings.categoryTypeLabel(item.categoryType), color = Cyan, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
        }
    }
}

// ─── Grid view (Movies/Series tabs) ───
@Composable
fun GridView(viewModel: MainViewModel, tab: String, query: String, onPlay: () -> Unit, isSeries: Boolean) {
    val configuration = LocalConfiguration.current
    val cv by viewModel.currentView.collectAsState()
    val ns by viewModel.navStackFlow.collectAsState()
    val chs by viewModel.channels.collectAsState()
    val cats by viewModel.categories.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val scope = rememberCoroutineScope()
    val minGridCardWidth = if (configuration.screenWidthDp >= 840) 200.dp else if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 168.dp else 148.dp
    // Stati di scroll mantenuti a livello di GridView cosi' la posizione sopravvive
    // quando si apre il dettaglio (film/serie): aprendo una serie la view passa a
    // "seasons" e il ramo poster esce dalla composizione, ma lo stato resta qui.
    val posterGridState = rememberLazyGridState()
    val categoryListState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        if (ns.isNotEmpty()) {
            TextButton(onClick = { viewModel.goBack() }, modifier = Modifier.padding(horizontal = 6.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Cyan, modifier = Modifier.size(16.dp))
                Text(Strings["back"], color = Cyan, fontSize = 12.sp)
            }
        }
        val items = when (cv) {
            "channels" -> chs
            else -> (cats[tab] ?: emptyList()).map { cat -> Channel(id = cat.categoryId, name = cat.name, itemType = "category", screenshotUri = cat.screenshotUri) }
        }
        val filtered = if (query.isBlank()) items else searchResults
        if (query.isNotBlank() && filtered.isEmpty() && !searchLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Strings["noResults"], color = Gray, fontSize = 14.sp)
            }
            return
        }
        val showPosterResults = query.isNotBlank() || cv == "channels"
        if (showPosterResults) {
            LazyVerticalGrid(state = posterGridState, columns = GridCells.Adaptive(minSize = minGridCardWidth), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { item ->
                    Card(Modifier.fillMaxWidth().clickable { scope.launch { if (isSeries) viewModel.onSeriesClick(item) else viewModel.onVodClick(item) } }, colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                        Column {
                            Box(Modifier.fillMaxWidth().aspectRatio(0.7f), contentAlignment = Alignment.Center) {
                                if (item.screenshotUri.isNotBlank()) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(item.screenshotUri).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Surface(Modifier.fillMaxSize(), color = Bg) { Box(contentAlignment = Alignment.Center) { Icon(if (isSeries) Icons.Default.Tv else Icons.Default.Movie, null, tint = Gray, modifier = Modifier.size(48.dp)) } }
                            }
                            Column(Modifier.padding(8.dp)) {
                                Text(item.name, color = White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                                if (item.year.isNotBlank()) Text(item.year, color = Gray, fontSize = 11.sp)
                                if (item.ratingImdb.isNotBlank()) Text("\u2B50 ${item.ratingImdb}", color = Cyan, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(state = categoryListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(filtered) { item ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp).clickable { scope.launch { viewModel.onCategoryClick(Category(item.name, if (isSeries) "Series" else "VOD", item.id)) } }, colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChanIcon(item.screenshotUri, "category")
                            Spacer(Modifier.width(10.dp)); Text(item.name, color = White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Surface(color = (if (isSeries) Purple else Green).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) { Text(if (isSeries) Strings["seriesBadge"] else Strings["vodBadge"], color = if (isSeries) Purple else Green, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChanIcon(uri: String, itemType: String) {
    Surface(Modifier.size(40.dp), shape = RoundedCornerShape(8.dp), color = Bg) {
        if (uri.isNotBlank()) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) { Icon(when (itemType) { "category" -> Icons.Default.Folder; "channel" -> Icons.Default.LiveTv; "vod" -> Icons.Default.Movie; "series" -> Icons.Default.Tv; "season" -> Icons.Default.Folder; "episode" -> Icons.Default.PlayArrow; else -> Icons.Default.Folder }, null, tint = when (itemType) { "channel" -> Cyan; "vod" -> Green; "series" -> Purple; "episode" -> Green; else -> Cyan }, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
fun InfoTab(viewModel: MainViewModel) {
    val info by viewModel.accountInfo.collectAsState()
    val pt by viewModel.portalType.collectAsState()
    val currentHostname by viewModel.currentHostname.collectAsState()
    val currentMac by viewModel.currentMac.collectAsState()
    val currentUsername by viewModel.currentUsername.collectAsState()
    val currentPassword by viewModel.currentPassword.collectAsState()
    val serverValue = info.serverUrl.ifBlank { currentHostname }
    val usernameValue = info.username.ifBlank { currentUsername }
    val passwordValue = info.password.ifBlank { currentPassword }
    val macValue = info.mac.ifBlank { currentMac }
    val expiresValue = (if (info.expireBillingDate.isNotBlank()) info.expireBillingDate else info.expireDate).ifBlank { Strings["notAvailable"] }
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = Cyan, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(Strings["account"], color = Cyan, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    Spacer(Modifier.height(8.dp)); HorizontalDivider(color = Cyan.copy(alpha = 0.1f)); Spacer(Modifier.height(8.dp))
                    InfoRow(Strings["type"], Strings.portalLabel(pt))
                    InfoRow(Strings["name"], info.name)
                    InfoRow(Strings["server"], serverValue)
                    if (pt == "xtream") {
                        InfoRow(Strings["username"], usernameValue)
                        InfoRow(Strings["password"], passwordValue)
                    } else if (pt != "m3u") {
                        InfoRow(Strings["macAddress"], macValue)
                    }
                    if (info.isStalker || pt == "stalker") { InfoRow(Strings["maxOnline"], info.maxOnline.toString()); InfoRow(Strings["parental"], info.parentalPassword.ifBlank { Strings["notAvailable"] }) }
                    InfoRow(Strings["expires"], expiresValue, allowPlaceholder = true)
                }
            }
        }
    }
}

private fun formatDurationLabel(item: Channel): String {
    if (item.durationText.isNotBlank()) return item.durationText
    if (item.time <= 0) return ""
    val totalMinutes = item.time / 60
    if (totalMinutes <= 0) return "${item.time}s"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun LanguageRow(option: LanguageOption, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) Cyan.copy(alpha = 0.45f) else Color(0xFF2A3442)
    val background = if (selected) Cyan.copy(alpha = 0.12f) else Bg

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(option.flag, fontSize = 20.sp)
            Spacer(Modifier.height(2.dp))
            Text(option.nativeName, fontSize = 12.sp, color = if (selected) White else Gray)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            option.code.uppercase(),
            color = if (selected) Cyan else Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, allowPlaceholder: Boolean = false) {
    if (value.isNotBlank() && value != "0" && (allowPlaceholder || value != Strings["notAvailable"])) {
        Row(Modifier.padding(vertical = 4.dp)) {
            Text("$label:  ", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(value, color = White, fontSize = 14.sp)
        }
    }
}
