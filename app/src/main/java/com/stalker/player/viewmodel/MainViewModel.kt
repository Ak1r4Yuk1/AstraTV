package com.stalker.player.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stalker.player.data.api.StalkerApiClient
import com.stalker.player.data.api.M3uClient
import com.stalker.player.data.api.RemoteImportServer
import com.stalker.player.data.api.XtreamClient
import com.stalker.player.data.repository.StalkerRepository
import com.stalker.player.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.random.Random

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val client = StalkerApiClient()
    private val xtreamClient = XtreamClient()
    private val m3uClient = M3uClient(application)
    private val repository = StalkerRepository(application, client, xtreamClient, m3uClient)
    private val settingsManager = SettingsManager(application)
    private val remoteAccessCode = Random.nextInt(100_000, 1_000_000).toString()
    private val remoteImportServer = RemoteImportServer(
        context = application,
        accessCode = remoteAccessCode,
        profilesProvider = { profiles.value },
        onProfileImported = { profile -> viewModelScope.launch { saveImportedProfile(profile) } },
        onProfileDeleted = { name -> viewModelScope.launch { deleteProfileByName(name) } }
    )

    val connected = MutableStateFlow(false)
    val isLoading get() = repository.isLoading
    val progress get() = repository.progress
    val error get() = repository.error
    val categories get() = repository.categories
    val accountInfo get() = repository.accountInfo
    val channels get() = repository.channels
    val seasons get() = repository.seasons
    val episodes get() = repository.episodes
    val searchResults get() = repository.searchResults
    val searchLoading get() = repository.searchLoading

    val portalType = MutableStateFlow("mac")
    val profiles = MutableStateFlow<List<Profile>>(emptyList())
    val currentView = MutableStateFlow("categories")
    private val navStack = mutableListOf<NavState>()
    val navStackFlow = MutableStateFlow<List<NavState>>(emptyList())

    val selectedSeries = MutableStateFlow<Channel?>(null)
    val selectedMovie = MutableStateFlow<Channel?>(null)
    val appLanguage = MutableStateFlow(Strings.lang.value)
    val playerActive = MutableStateFlow(false)
    val currentStreamUrl = MutableStateFlow("")
    val streamResolving = MutableStateFlow(false)
    val currentHostname = MutableStateFlow("")
    val currentMac = MutableStateFlow("")
    val currentUsername = MutableStateFlow("")
    val currentPassword = MutableStateFlow("")
    val remoteImportUrl = MutableStateFlow("")
    val remoteImportCode = MutableStateFlow(remoteAccessCode)
    val remoteImportMessage = MutableStateFlow("")

    private var currentCategory: Category? = null
    private var activeTab = "Live"
    private var searchJob: Job? = null

    init {
        loadProfiles()
        remoteImportServer.start()
        remoteImportUrl.value = remoteImportServer.localUrl()
        viewModelScope.launch {
            settingsManager.language.collect { lang ->
                appLanguage.value = lang
                Strings.lang.value = lang
                client.lang = lang
                remoteImportUrl.value = remoteImportServer.localUrl()
            }
        }
    }

    override fun onCleared() {
        remoteImportServer.stop()
        super.onCleared()
    }

    fun setActiveTab(tab: String) {
        if (activeTab != tab) {
            activeTab = tab
            currentView.value = "categories"
            navStack.clear()
            navStackFlow.value = emptyList()
            clearSearch()
        }
    }

    fun goBack() {
        if (navStack.isNotEmpty()) {
            val prev = navStack.removeAt(navStack.lastIndex)
            currentView.value = prev.view
            navStackFlow.value = navStack.toList()
            currentCategory = prev.category
        }
    }

    fun disconnect() {
        if (playerActive.value) return
        connected.value = false
        currentView.value = "categories"
        navStack.clear(); navStackFlow.value = emptyList()
        selectedSeries.value = null; selectedMovie.value = null
    }

    fun clearError() { repository.setError("") }

    fun updateSearch(tab: String, query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            repository.clearSearch()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            repository.search(tab, query)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        repository.clearSearch()
    }
    
    fun detectPortalType(url: String) = when {
        url.contains("player_api") -> "xtream"
        url.endsWith(".m3u", true) || url.endsWith(".m3u8", true) || url.startsWith("content://") -> "m3u"
        else -> "mac"
    }
    
    fun setPortalType(type: String) { portalType.value = type }

    fun setM3uSource(uri: Uri) {
        portalType.value = "m3u"
        currentHostname.value = uri.toString()
        currentMac.value = ""
        currentUsername.value = ""
        currentPassword.value = ""
        _loadedProfile.value = Profile(name = "M3U", url = uri.toString(), type = "m3u")
    }

    fun connect(hostname: String, macOrUser: String, password: String = "") {
        repository.clearError()

        // Prendiamo il tipo ESPLICITO che l'utente ha selezionato nella UI (es. tramite tab o menu)
        val selectedType = portalType.value 

        // Assegnazioni rigide dei campi in base al tipo SELEZIONATO, senza autodetect
        currentHostname.value = if (selectedType == "m3u") hostname else normalizeUrl(hostname)
        currentMac.value = if (selectedType == "mac") macOrUser else ""
        currentUsername.value = if (selectedType == "xtream") macOrUser else ""
        currentPassword.value = if (selectedType == "xtream") password else ""

        viewModelScope.launch {
            try {
                withTimeout(10_000L) {
                    val finalUrl = currentHostname.value

                    // Configura SOLO ed esclusivamente il client scelto
                    when (selectedType) {
                        "xtream" -> repository.configureXtream(finalUrl, currentUsername.value, currentPassword.value)
                        "m3u" -> repository.configureM3u(finalUrl) 
                        else -> repository.configure(finalUrl, currentMac.value, selectedType)
                    }

                    // Esegue il caricamento. Se fallisce, restituisce l'errore del client specifico
                    val loadResult = repository.loadPlaylist()

                    loadResult
                        .onSuccess { 
                            connected.value = true
                            viewModelScope.launch {
                                repository.warmupSearchIndex()
                            }
                        }
                        .onFailure { e -> 
                            if (e !is CancellationException) repository.setError(e.message ?: e.toString()) 
                        }
                }
            } catch (_: TimeoutCancellationException) {
                connected.value = false
                repository.setError(Strings["connectionTimeout"])
            } catch (e: CancellationException) { 
            } catch (e: Exception) { 
                repository.setError(e.message ?: e.toString()) 
            }
        }
    }

    fun onCategoryClick(cat: Category) {
        currentCategory = cat
        navStack.add(NavState(category = cat, view = currentView.value))
        currentView.value = "channels"
        navStackFlow.value = navStack.toList()
        viewModelScope.launch {
            try {
                repository.loadChannels(cat).onFailure { e ->
                    if (e !is CancellationException) { repository.setError(e.message ?: Strings["genericError"]) }
                }
            } catch (e: CancellationException) { } catch (e: Exception) { repository.setError(e.message ?: Strings["genericError"]) }
        }
    }

    fun onVodClick(item: Channel) {
        selectedMovie.value = item
        viewModelScope.launch {
            try {
                repository.loadVodDetails(item)
                    .onSuccess { enriched -> selectedMovie.value = enriched }
                    .onFailure { e ->
                        if (e !is CancellationException) {
                            repository.setError(e.message ?: Strings["genericError"])
                        }
                    }
            } catch (e: CancellationException) {
            } catch (e: Exception) {
                repository.setError(e.message ?: Strings["genericError"])
            }
        }
    }

    fun clearDetail() {
        selectedMovie.value = null
        if (selectedSeries.value != null) {
            selectedSeries.value = null
            while (navStack.isNotEmpty() && currentView.value != "channels") {
                val prev = navStack.removeAt(navStack.lastIndex)
                currentView.value = prev.view
                currentCategory = prev.category
            }
            navStackFlow.value = navStack.toList()
        }
    }

    fun handleSeriesBack() {
        if (currentView.value == "episodes") goBack() else closeSeriesDetail()
    }

    fun closeSeriesDetail() {
        selectedSeries.value = null
        while (navStack.isNotEmpty() && (currentView.value == "seasons" || currentView.value == "episodes")) {
            val prev = navStack.removeAt(navStack.lastIndex)
            currentView.value = prev.view
            currentCategory = prev.category
        }
        navStackFlow.value = navStack.toList()
    }

    fun setPlayerActive(active: Boolean) {
        playerActive.value = active
    }

    fun clearPlaybackState() {
        streamResolving.value = false
        currentStreamUrl.value = ""
        playerActive.value = false
    }

    fun playChannel(item: Channel, onPlay: (String) -> Unit) {
        viewModelScope.launch {
            try { repository.getStreamUrl(item).onSuccess { onPlay(it) }.onFailure { e -> if (e !is CancellationException) repository.setError(e.message ?: Strings["streamError"]) } }
            catch (e: CancellationException) { } catch (e: Exception) { repository.setError(e.message ?: Strings["genericError"]) }
        }
    }

    fun startPlayback(item: Channel, onNavigate: () -> Unit) {
        if (streamResolving.value) return
        repository.clearError()
        streamResolving.value = true
        currentStreamUrl.value = ""
        playerActive.value = true
        onNavigate()

        viewModelScope.launch {
            try {
                repository.getStreamUrl(item)
                    .onSuccess {
                        currentStreamUrl.value = it
                        streamResolving.value = false
                    }
                    .onFailure { e ->
                        if (e !is CancellationException) {
                            repository.setError(e.message ?: Strings["streamError"])
                        }
                        streamResolving.value = false
                    }
            } catch (e: CancellationException) {
                streamResolving.value = false
            } catch (e: Exception) {
                repository.setError(e.message ?: Strings["genericError"])
                streamResolving.value = false
            }
        }
    }

    fun onSeriesClick(item: Channel) {
        selectedSeries.value = item
        val seriesIds = linkedSetOf(item.movieId, item.id, item.seriesId).filter { it.isNotBlank() }
        navStack.add(NavState(category = currentCategory, view = currentView.value))
        currentView.value = "seasons"
        navStackFlow.value = navStack.toList()
        viewModelScope.launch {
            try {
                val detailsDeferred = async {
                    repository.loadSeriesDetails(item)
                }

                repository.loadSeasons(seriesIds, item.cmd).onFailure { e ->
                    if (e !is CancellationException) repository.setError(e.message ?: Strings["genericError"])
                }

                detailsDeferred.await()
                    .onSuccess { enriched -> selectedSeries.value = enriched }
                    .onFailure { e ->
                        if (e !is CancellationException) repository.setError(e.message ?: Strings["genericError"])
                    }
            }
            catch (e: CancellationException) { } catch (e: Exception) { repository.setError(e.message ?: Strings["genericError"]) }
        }
    }

    fun onSeasonClick(season: Channel) {
        navStack.add(NavState(category = currentCategory, view = currentView.value))
        currentView.value = "episodes"
        navStackFlow.value = navStack.toList()
        viewModelScope.launch {
            try { repository.loadEpisodes(season).onFailure { e -> if (e !is CancellationException) repository.setError(e.message ?: Strings["genericError"]) } }
            catch (e: CancellationException) { } catch (e: Exception) { repository.setError(e.message ?: Strings["genericError"]) }
        }
    }

    fun setLanguage(lang: String) {
        appLanguage.value = lang
        Strings.lang.value = lang
        client.lang = lang
        viewModelScope.launch { settingsManager.saveLanguage(lang) }
    }

    // ── Profiles ──
    fun saveProfile(name: String, url: String, mac: String, username: String, password: String, type: String) {
        val list = profiles.value.toMutableList()
        val idx = list.indexOfFirst { it.name == name }
        val p = Profile(name=name, url=url, mac=mac, username=username, password=password, type=type)
        if (idx >= 0) list[idx] = p else list.add(p)
        profiles.value = list; persistProfiles()
    }
    fun deleteProfile(index: Int) {
        val list = profiles.value.toMutableList()
        if (index in list.indices) { list.removeAt(index); profiles.value = list; persistProfiles() }
    }
    fun loadProfile(index: Int) {
        profiles.value.getOrNull(index)?.let { p ->
            portalType.value = p.type
            _loadedProfile.value = p
        }
    }
    fun connectProfile(index: Int) {
        profiles.value.getOrNull(index)?.let { p ->
            portalType.value = p.type
            _loadedProfile.value = p
            connect(p.url, if (p.type == "xtream") p.username else p.mac, p.password)
        }
    }
    private val _loadedProfile = MutableStateFlow<Profile?>(null)
    val loadedProfile: StateFlow<Profile?> = _loadedProfile
    fun consumeLoadedProfile() { _loadedProfile.value = null }
    private fun loadProfiles() {
        viewModelScope.launch {
            settingsManager.profilesJson().collect { json ->
                if (json != "[]" && json.isNotBlank()) try {
                    profiles.value = com.google.gson.Gson().fromJson(json, Array<Profile>::class.java).toList()
                } catch (_: Exception) { }
            }
        }
    }
    private fun persistProfiles() {
        viewModelScope.launch { settingsManager.saveProfilesJson(com.google.gson.Gson().toJson(profiles.value)) }
    }

    private fun saveImportedProfile(profile: Profile) {
        val list = profiles.value.toMutableList()
        val idx = list.indexOfFirst { it.name == profile.name }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        profiles.value = list
        persistProfiles()
        _loadedProfile.value = profile
        remoteImportMessage.value = Strings.fmt("profileImported", profile.name)
    }

    fun clearRemoteImportMessage() { remoteImportMessage.value = "" }

    private fun deleteProfileByName(name: String) {
        val list = profiles.value.toMutableList()
        val removed = list.removeAll { it.name == name }
        if (removed) {
            profiles.value = list
            persistProfiles()
            remoteImportMessage.value = Strings.fmt("profileDeleted", name)
        }
    }
    
    private fun normalizeUrl(input: String): String {
        if (input.startsWith("content://")) return input
        
        if (portalType.value == "m3u" || detectPortalType(input) == "m3u") {
            return if (!input.startsWith("http")) "http://$input" else input
        }
        
        var url = input.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        
        return try {
            val protocolIndex = url.indexOf("://")
            val firstSlashAfterProtocol = url.indexOf("/", protocolIndex + 3)
            
            if (firstSlashAfterProtocol != -1) {
                url.substring(0, firstSlashAfterProtocol)
            } else {
                url
            }
        } catch (_: Exception) { 
            url 
        }
    }
}
