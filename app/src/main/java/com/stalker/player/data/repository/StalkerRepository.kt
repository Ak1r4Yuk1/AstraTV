package com.stalker.player.data.repository

import android.content.Context
import com.google.gson.JsonParser
import com.stalker.player.data.api.StalkerApiClient
import com.stalker.player.data.api.XtreamClient
import com.stalker.player.data.api.M3uClient
import com.stalker.player.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class StalkerRepository(
    context: Context,
    private val client: StalkerApiClient,
    private val xtreamClient: XtreamClient,
    private val m3uClient: M3uClient
) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _categories = MutableStateFlow<Map<String, List<Category>>>(emptyMap())
    val categories: StateFlow<Map<String, List<Category>>> = _categories
    private val _accountInfo = MutableStateFlow(AccountInfo())
    val accountInfo: StateFlow<AccountInfo> = _accountInfo
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels
    private val _seasons = MutableStateFlow<List<Channel>>(emptyList())
    val seasons: StateFlow<List<Channel>> = _seasons
    private val _episodes = MutableStateFlow<List<Channel>>(emptyList())
    val episodes: StateFlow<List<Channel>> = _episodes
    private val _searchResults = MutableStateFlow<List<Channel>>(emptyList())
    val searchResults: StateFlow<List<Channel>> = _searchResults
    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading

    private var portalType = "mac"
    private var portalUrl = ""
    private var portalMac = ""
    private var xtreamUser = ""
    private var xtreamPass = ""
    private var m3uSource = ""
    private var token: String? = null
    private val cachedXtreamEpisodesBySeriesId = mutableMapOf<String, List<Channel>>()
    private val cachedM3uXtreamEpisodesBySeriesId = mutableMapOf<String, List<Channel>>()
    private var m3uXtreamCredential: XtreamCredential? = null
    
    @Volatile private var channelLoadVersion = 0
    @Volatile private var seasonLoadVersion = 0
    @Volatile private var episodeLoadVersion = 0
    
    private val channelCache = mutableMapOf<String, List<Channel>>()
    private val seasonCache = mutableMapOf<String, List<Channel>>()
    private val episodeCache = mutableMapOf<String, List<Channel>>()
    private val m3uChannelsByCategory = mutableMapOf<String, List<Channel>>()
    private val m3uSeriesEpisodesBySeriesId = mutableMapOf<String, List<Channel>>()
    private val vodInfoCache = mutableMapOf<String, Channel>()
    private val seriesInfoCache = mutableMapOf<String, Channel>()
    private val searchIndexStore = SearchIndexStore(context.applicationContext)

    private val _profiles = mutableListOf<Profile>()

    fun getProfiles() = _profiles.toList()
    fun saveProfiles(p: List<Profile>) { _profiles.clear(); _profiles.addAll(p) }
    
    // Configura i parametri azzerando rigorosamente gli altri per evitare travasi di dati
    fun configure(url: String, mac: String, type: String) { 
        portalUrl = url; portalMac = mac; portalType = type; xtreamUser = ""; xtreamPass = ""; m3uSource = ""; token = null; clearCaches() 
    }
    fun configureXtream(url: String, u: String, p: String) { 
        portalUrl = url; portalMac = ""; portalType = "xtream"; xtreamUser = u; xtreamPass = p; m3uSource = ""; token = null; clearCaches() 
    }
    fun configureM3u(source: String) { 
        portalUrl = source; portalMac = ""; portalType = "m3u"; m3uSource = source; xtreamUser = ""; xtreamPass = ""; token = null; clearCaches() 
    }
    fun getToken(): String? = token

    suspend fun loadPlaylist(): Result<Unit> = withContext(Dispatchers.IO) {
        _isLoading.value = true; _progress.value = 0; _error.value = null
        try {
            when (portalType) {
                "xtream" -> loadXtream()
                "m3u" -> loadM3u()
                else -> loadStb()
            }
            _progress.value = 100; _progress.value = 0; _isLoading.value = false
            Result.success(Unit)
        } catch (e: CancellationException) {
            // Connessione annullata dall'utente: nessun messaggio di errore, solo reset.
            _isLoading.value = false; _progress.value = 0
            throw e
        } catch (e: Exception) {
            // Se l'annullo ha abortito le richieste HTTP, la coroutine è già cancellata:
            // ensureActive() rilancia CancellationException così non mostriamo un errore.
            ensureActive()
            _error.value = e.message ?: e.toString(); _isLoading.value = false; _progress.value = 0
            Result.failure(e)
        }
    }

    // Annulla una connessione in corso: aborta le richieste HTTP in volo (così il
    // login si ferma SUBITO, senza attendere i timeout) e resetta lo stato.
    fun cancelLoading() {
        client.cancelAll()
        xtreamClient.cancelAll()
        m3uClient.cancelAll()
        _isLoading.value = false
        _progress.value = 0
        _error.value = null
    }

    private suspend fun loadStb() {
        token = client.handshake(portalUrl, portalMac, portalType); _progress.value = 15
        val profileInfo = runCatching {
            client.getProfileInfo(portalUrl, portalMac, token!!, portalType)
        }.getOrDefault(AccountInfo(serverUrl = portalUrl, mac = portalMac, isStalker = portalType == "stalker"))
        _progress.value = 25
        val results = coroutineScope {
            listOf(
                async { client.getCategories(token!!, portalUrl, portalMac, "itv") },
                async { client.getCategories(token!!, portalUrl, portalMac, "vod") },
                async { client.getCategories(token!!, portalUrl, portalMac, "series") }
            ).awaitAll()
        }; _progress.value = 80
        _categories.value = mapOf("Live" to results[0], "Movies" to results[1], "Series" to results[2])
        try {
            val accountInfo = client.getAccountInfo(token!!, portalUrl, portalMac, portalType)
            _accountInfo.value = mergeAccountInfo(accountInfo, profileInfo)
        } catch (_: Exception) {
            _accountInfo.value = mergeAccountInfo(AccountInfo(), profileInfo)
        }
    }

    private suspend fun loadXtream() {
        val (userJson, serverJson) = xtreamClient.authenticate(portalUrl, xtreamUser, xtreamPass).getOrThrow()
        _accountInfo.value = buildXtreamAccountInfo(
            userJson = userJson,
            serverJson = serverJson,
            credential = XtreamCredential(portalUrl, xtreamUser, xtreamPass),
            fallbackServerUrl = portalUrl
        )
        _progress.value = 30
        val results = coroutineScope {
            listOf(
                async { xtreamClient.getLiveCategories(portalUrl, xtreamUser, xtreamPass) },
                async { xtreamClient.getVodCategories(portalUrl, xtreamUser, xtreamPass) },
                async { xtreamClient.getSeriesCategories(portalUrl, xtreamUser, xtreamPass) }
            ).awaitAll()
        }; _progress.value = 90
        _categories.value = mapOf("Live" to results[0], "Movies" to results[1], "Series" to results[2])
    }

    private suspend fun loadM3u() {
        m3uXtreamCredential = null
        cachedM3uXtreamEpisodesBySeriesId.clear()

        val extractedCredential = m3uClient.extractXtreamCredential(m3uSource)
        if (extractedCredential != null) {
            val loadedViaXtream = runCatching {
                loadM3uViaXtream(extractedCredential)
            }.isSuccess
            if (loadedViaXtream) return
        }

        val playlist = m3uClient.load(m3uSource)
        m3uChannelsByCategory.clear()
        m3uChannelsByCategory.putAll(playlist.channelsByCategory)
        val liveCategories = playlist.categories.filter { it.categoryType.equals("IPTV", ignoreCase = true) }
        val movieCategories = playlist.categories.filter { it.categoryType.equals("VOD", ignoreCase = true) }
        val seriesCategories = playlist.categories.filter { it.categoryType.equals("Series", ignoreCase = true) }
        _categories.value = mapOf("Live" to liveCategories, "Movies" to movieCategories, "Series" to seriesCategories)
        _accountInfo.value = AccountInfo(name = Strings["m3uPlaylist"], serverUrl = m3uSource, isStalker = false)
        _progress.value = 90
    }

    private suspend fun loadM3uViaXtream(credential: XtreamCredential) {
        val (userJson, serverJson) = xtreamClient.authenticate(
            credential.serverUrl,
            credential.username,
            credential.password
        ).getOrThrow()
        _accountInfo.value = buildXtreamAccountInfo(
            userJson = userJson,
            serverJson = serverJson,
            credential = credential,
            fallbackServerUrl = credential.serverUrl
        )
        _progress.value = 30
        val results = coroutineScope {
            listOf(
                async { xtreamClient.getLiveCategories(credential.serverUrl, credential.username, credential.password) },
                async { xtreamClient.getVodCategories(credential.serverUrl, credential.username, credential.password) },
                async { xtreamClient.getSeriesCategories(credential.serverUrl, credential.username, credential.password) }
            ).awaitAll()
        }
        m3uXtreamCredential = credential
        m3uChannelsByCategory.clear()
        _categories.value = mapOf("Live" to results[0], "Movies" to results[1], "Series" to results[2])
        _progress.value = 90
    }

    suspend fun warmupSearchIndex() = withContext(Dispatchers.IO) {
        runCatching {
            when (portalType) {
                "xtream" -> refreshXtreamSearchIndex(portalUrl, xtreamUser, xtreamPass)
                "m3u" -> m3uXtreamCredential?.let { credential ->
                    refreshXtreamSearchIndex(credential.serverUrl, credential.username, credential.password)
                }
            }
        }
    }

    suspend fun search(tab: String, query: String): Result<List<Channel>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            _searchLoading.value = false
            _searchResults.value = emptyList()
            return@withContext Result.success(emptyList())
        }

        _searchLoading.value = true
        try {
            val results = when (portalType) {
                "xtream" -> searchXtream(tab, query, XtreamCredential(portalUrl, xtreamUser, xtreamPass))
                "m3u" -> m3uXtreamCredential?.let { credential ->
                    searchXtream(tab, query, credential)
                } ?: searchM3u(tab, query)
                else -> searchStb(tab, query)
            }
            _searchResults.value = filterVisibleItems(results)
            _searchLoading.value = false
            Result.success(results)
        } catch (_: CancellationException) {
            _searchLoading.value = false
            Result.failure(CancellationException())
        } catch (e: Exception) {
            _searchLoading.value = false
            _error.value = e.message ?: e.toString()
            Result.failure(e)
        }
    }

    fun clearSearch() {
        _searchLoading.value = false
        _searchResults.value = emptyList()
    }

    suspend fun loadChannels(cat: Category): Result<List<Channel>> = withContext(Dispatchers.IO) {
        val requestVersion = ++channelLoadVersion
        _isLoading.value = true
        _channels.value = emptyList()
        try {
            val cacheKey = "${portalType}:${cat.categoryType}:${cat.categoryId}"
            channelCache[cacheKey]?.let { cached ->
                if (requestVersion == channelLoadVersion) {
                    _channels.value = cached
                    _isLoading.value = false
                }
                return@withContext Result.success(cached)
            }
            val chs = when (portalType) {
                "xtream" -> when(cat.categoryType) { 
                    "IPTV" -> xtreamClient.getLiveStreams(portalUrl, xtreamUser, xtreamPass, cat.categoryId)
                    "VOD" -> xtreamClient.getVodStreams(portalUrl, xtreamUser, xtreamPass, cat.categoryId)
                    "Series" -> xtreamClient.getSeries(portalUrl, xtreamUser, xtreamPass, cat.categoryId)
                    else -> emptyList() 
                }
                "m3u" -> m3uXtreamCredential?.let { credential ->
                    when (cat.categoryType) {
                        "IPTV" -> xtreamClient.getLiveStreams(credential.serverUrl, credential.username, credential.password, cat.categoryId)
                        "VOD" -> xtreamClient.getVodStreams(credential.serverUrl, credential.username, credential.password, cat.categoryId)
                        "Series" -> xtreamClient.getSeries(credential.serverUrl, credential.username, credential.password, cat.categoryId)
                        else -> emptyList()
                    }
                } ?: if (cat.categoryType.equals("Series", ignoreCase = true)) {
                    buildM3uSeriesIndex(cat)
                } else {
                    m3uChannelsByCategory[cat.categoryId].orEmpty()
                }
                else -> client.getChannels(token!!, portalUrl, portalMac, cat, portalType) { partial ->
                    if (requestVersion == channelLoadVersion) {
                        _channels.value = filterVisibleItems(partial)
                    }
                }
            }
            val visibleChannels = filterVisibleItems(chs)
            channelCache[cacheKey] = visibleChannels
            if (requestVersion == channelLoadVersion) {
                _channels.value = visibleChannels
                _isLoading.value = false
            }
            Result.success(visibleChannels)
        } catch (e: Exception) {
            _error.value = e.message ?: e.toString()
            if (requestVersion == channelLoadVersion) {
                _isLoading.value = false
            }
            Result.failure(e)
        }
    }

    suspend fun loadSeasons(seriesIds: List<String>, seriesCmd: String): Result<List<Channel>> = withContext(Dispatchers.IO) {
        val requestVersion = ++seasonLoadVersion
        _isLoading.value = true
        _seasons.value = emptyList()
        _episodes.value = emptyList()
        try {
            val resolvedSeriesIds = seriesIds.filter { it.isNotBlank() }.distinct()
            val primarySeriesId = resolvedSeriesIds.firstOrNull().orEmpty()
            val cacheKey = "${portalType}:${resolvedSeriesIds.joinToString("|")}"
            seasonCache[cacheKey]?.let { cached ->
                if (requestVersion == seasonLoadVersion) {
                    _seasons.value = cached
                    _isLoading.value = false
                }
                return@withContext Result.success(cached)
            }
            val s = when (portalType) {
                "xtream" -> {
                    val resolved = resolveXtreamSeriesInfo(
                        seriesIds = resolvedSeriesIds,
                        episodeCacheBySeries = cachedXtreamEpisodesBySeriesId
                    ) { seriesId ->
                        xtreamClient.getSeriesInfo(portalUrl, xtreamUser, xtreamPass, seriesId)
                    }
                    buildSeasonsFromXtreamData(
                        seriesId = resolved.resolvedSeriesId,
                        parsedSeasons = resolved.seasons,
                        episodes = resolved.episodes
                    )
                }
                "m3u" -> m3uXtreamCredential?.let { credential ->
                    val resolved = resolveXtreamSeriesInfo(
                        seriesIds = resolvedSeriesIds,
                        episodeCacheBySeries = cachedM3uXtreamEpisodesBySeriesId
                    ) { seriesId ->
                        xtreamClient.getSeriesInfo(
                            credential.serverUrl,
                            credential.username,
                            credential.password,
                            seriesId
                        )
                    }
                    buildSeasonsFromXtreamData(
                        seriesId = resolved.resolvedSeriesId,
                        parsedSeasons = resolved.seasons,
                        episodes = resolved.episodes
                    )
                } ?: run {
                    val seriesKey = primarySeriesId.ifBlank { resolvedSeriesIds.firstOrNull().orEmpty() }
                    val m3uEpisodes = m3uSeriesEpisodesBySeriesId[seriesKey].orEmpty()
                    deriveSeasonsFromEpisodes(m3uEpisodes, seriesKey)
                }
                else -> client.getSeasons(token!!, portalUrl, portalMac, resolvedSeriesIds, portalType)
            }
            seasonCache[cacheKey] = s
            if (requestVersion == seasonLoadVersion) {
                _seasons.value = s
                _isLoading.value = false
            }
            Result.success(s)
        } catch (e: Exception) {
            _error.value = e.message ?: e.toString()
            if (requestVersion == seasonLoadVersion) {
                _isLoading.value = false
            }
            Result.failure(e)
        }
    }

    suspend fun loadEpisodes(season: Channel): Result<List<Channel>> = withContext(Dispatchers.IO) {
        val requestVersion = ++episodeLoadVersion
        _isLoading.value = true
        _episodes.value = emptyList()
        try {
            val seriesIds = linkedSetOf(season.movieId, season.seriesId, season.id).filter { it.isNotBlank() }
            val seasonId = season.seasonId.ifBlank { season.id }
            val seasonNumber = season.seasonNumber
            val resolvedSeriesIds = seriesIds.filter { it.isNotBlank() }.distinct()
            val primarySeriesId = resolvedSeriesIds.firstOrNull().orEmpty()
            val cacheKey = "${portalType}:${resolvedSeriesIds.joinToString("|")}:$seasonId:$seasonNumber"
            
            episodeCache[cacheKey]?.let { cached ->
                if (requestVersion == episodeLoadVersion) {
                    _episodes.value = cached
                    _isLoading.value = false
                }
                return@withContext Result.success(cached)
            }

            val ep = when (portalType) {
                "xtream" -> {
                    loadXtreamEpisodesForSeason(
                        season = season,
                        seriesIds = resolvedSeriesIds,
                        episodeCacheBySeries = cachedXtreamEpisodesBySeriesId
                    ) { seriesId ->
                        xtreamClient.getSeriesInfo(portalUrl, xtreamUser, xtreamPass, seriesId).second
                    }
                }
                "m3u" -> m3uXtreamCredential?.let { credential ->
                    loadXtreamEpisodesForSeason(
                        season = season,
                        seriesIds = resolvedSeriesIds,
                        episodeCacheBySeries = cachedM3uXtreamEpisodesBySeriesId
                    ) { seriesId ->
                        xtreamClient.getSeriesInfo(
                            credential.serverUrl,
                            credential.username,
                            credential.password,
                            seriesId
                        ).second
                    }
                } ?: run {
                    val seriesKey = primarySeriesId.ifBlank { resolvedSeriesIds.firstOrNull().orEmpty() }
                    val allEpisodes = m3uSeriesEpisodesBySeriesId[seriesKey].orEmpty()
                    filterEpisodesForSeason(allEpisodes, season)
                }
                else -> {
                    if (season.seriesNumbers.isNotEmpty()) {
                        val parentSeriesId = season.movieId.ifBlank { season.seriesId }.ifBlank { primarySeriesId }
                        season.seriesNumbers.distinct().sorted().map { episodeNumber ->
                            Channel(
                                id = "$parentSeriesId:$episodeNumber",
                                name = Strings.episodeName(episodeNumber),
                                cmd = season.cmd,
                                itemType = "episode",
                                movieId = parentSeriesId,
                                seasonId = seasonId,
                                episodeNumber = episodeNumber,
                                seasonNumber = seasonNumber,
                                seriesId = parentSeriesId,
                                screenshotUri = season.screenshotUri,
                                parentPoster = season.parentPoster.ifBlank { season.screenshotUri }
                            )
                        }
                    } else {
                        val primary = client.getEpisodes(token, portalUrl, portalMac, resolvedSeriesIds, seasonId, seasonNumber, portalType)
                        if (primary.isNotEmpty() || seasonNumber <= 0 || seasonId == seasonNumber.toString()) {
                            primary
                        } else {
                            client.getEpisodes(token, portalUrl, portalMac, resolvedSeriesIds, seasonNumber.toString(), seasonNumber, portalType)
                        }
                    }
                }
            }
            
            episodeCache[cacheKey] = ep
            if (requestVersion == episodeLoadVersion) {
                _episodes.value = ep
                _isLoading.value = false
            }
            Result.success(ep)
        } catch (e: Exception) {
            _error.value = e.message ?: e.toString()
            if (requestVersion == episodeLoadVersion) {
                _isLoading.value = false
            }
            Result.failure(e)
        }
    }

    suspend fun getStreamUrl(item: Channel): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = when (portalType) {
                "xtream" -> {
                    item.streamUrl.ifBlank {
                        val id = item.id
                        when (item.itemType) {
                            "channel" -> xtreamClient.getLiveStreamUrl(portalUrl, xtreamUser, xtreamPass, id)
                            "episode" -> xtreamClient.getSeriesStreamUrl(portalUrl, xtreamUser, xtreamPass, id, item.streamFormat)
                            else -> xtreamClient.getVodStreamUrl(portalUrl, xtreamUser, xtreamPass, id, item.streamFormat)
                        }
                    }
                }
                "m3u" -> m3uXtreamCredential?.let { credential ->
                    item.streamUrl.ifBlank {
                        val id = item.id
                        when (item.itemType) {
                            "channel" -> xtreamClient.getLiveStreamUrl(credential.serverUrl, credential.username, credential.password, id)
                            "episode" -> xtreamClient.getSeriesStreamUrl(credential.serverUrl, credential.username, credential.password, id, item.streamFormat)
                            else -> xtreamClient.getVodStreamUrl(credential.serverUrl, credential.username, credential.password, id, item.streamFormat)
                        }
                    }
                } ?: item.streamUrl.ifBlank { item.cmd }
                else -> client.getStreamUrl(token!!, portalUrl, portalMac, item, portalType)
            }
            // Risolve eventuali redirect (es. RAI relinker -> .m3u8) cosi' ExoPlayer
            // riconosce il formato reale dello stream.
            Result.success(m3uClient.resolveFinalUrl(url))
        } catch (e: Exception) { 
            _error.value = e.message ?: e.toString()
            Result.failure(e) 
        }
    }

    suspend fun loadVodDetails(item: Channel): Result<Channel> = withContext(Dispatchers.IO) {
        try {
            val vodId = item.id.ifBlank { item.episodeId }.ifBlank { item.movieId }
            if (vodId.isBlank()) return@withContext Result.success(item)
            val cacheKey = "${portalType}:$vodId"
            vodInfoCache[cacheKey]?.let { cached ->
                return@withContext Result.success(mergeVodItem(item, cached))
            }

            val details = when (portalType) {
                "xtream" -> xtreamClient.getVodInfo(portalUrl, xtreamUser, xtreamPass, vodId)
                "m3u" -> m3uXtreamCredential?.let { credential ->
                    xtreamClient.getVodInfo(credential.serverUrl, credential.username, credential.password, vodId)
                } ?: Channel()
                else -> Channel()
            }
            vodInfoCache[cacheKey] = details
            Result.success(mergeVodItem(item, details))
        } catch (_: Exception) {
            Result.success(item)
        }
    }

    suspend fun loadSeriesDetails(item: Channel): Result<Channel> = withContext(Dispatchers.IO) {
        try {
            val seriesIds = linkedSetOf(item.movieId, item.seriesId, item.id).filter { it.isNotBlank() }
            if (seriesIds.isEmpty()) return@withContext Result.success(item)

            var best = item
            var bestScore = scoreDetails(item)

            for (seriesId in seriesIds) {
                val cacheKey = "${portalType}:$seriesId"
                val cached = seriesInfoCache[cacheKey]
                if (cached != null) {
                    val merged = mergeSeriesItem(item, cached)
                    val score = scoreDetails(merged)
                    if (score > bestScore) {
                        best = merged
                        bestScore = score
                    }
                    continue
                }

                val details = when (portalType) {
                    "xtream" -> xtreamClient.getSeriesDetails(portalUrl, xtreamUser, xtreamPass, seriesId)
                    "m3u" -> m3uXtreamCredential?.let { credential ->
                        xtreamClient.getSeriesDetails(credential.serverUrl, credential.username, credential.password, seriesId)
                    } ?: Channel()
                    else -> Channel()
                }
                seriesInfoCache[cacheKey] = details
                val merged = mergeSeriesItem(item, details)
                val score = scoreDetails(merged)
                if (score > bestScore) {
                    best = merged
                    bestScore = score
                }
            }
            Result.success(best)
        } catch (_: Exception) {
            Result.success(item)
        }
    }

    private fun clearCaches() {
        cachedXtreamEpisodesBySeriesId.clear()
        cachedM3uXtreamEpisodesBySeriesId.clear()
        m3uXtreamCredential = null
        channelCache.clear()
        seasonCache.clear()
        episodeCache.clear()
        vodInfoCache.clear()
        seriesInfoCache.clear()
        m3uChannelsByCategory.clear()
        m3uSeriesEpisodesBySeriesId.clear()
        clearSearch()
    }

    private suspend fun refreshXtreamSearchIndex(serverUrl: String, username: String, password: String) {
        val portalKey = buildXtreamPortalKey(serverUrl, username, password)
        val indexed = coroutineScope {
            listOf(
                async { xtreamClient.getAllLiveStreams(serverUrl, username, password) },
                async { xtreamClient.getAllVodStreams(serverUrl, username, password) },
                async { xtreamClient.getAllSeries(serverUrl, username, password) }
            ).awaitAll()
        }
        searchIndexStore.replaceIndex(portalKey, "channel", filterVisibleItems(indexed[0]))
        searchIndexStore.replaceIndex(portalKey, "vod", filterVisibleItems(indexed[1]))
        searchIndexStore.replaceIndex(portalKey, "series", filterVisibleItems(indexed[2]))
    }

    private suspend fun searchStb(tab: String, query: String): List<Channel> {
        val resolvedToken = token ?: throw IOException(Strings["stbSessionUnavailable"])
        val apiType = when (tab) {
            "Movies" -> "vod"
            "Series" -> "series"
            else -> "itv"
        }
        return filterVisibleItems(client.search(resolvedToken, portalUrl, portalMac, apiType, query, portalType))
    }

    private fun searchXtream(tab: String, query: String, credential: XtreamCredential): List<Channel> {
        val itemType = when (tab) {
            "Movies" -> "vod"
            "Series" -> "series"
            else -> "channel"
        }
        return filterVisibleItems(searchIndexStore.search(
            portalKey = buildXtreamPortalKey(credential.serverUrl, credential.username, credential.password),
            itemType = itemType,
            query = query
        ))
    }

    private fun searchM3u(tab: String, query: String): List<Channel> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        val matcher: (Channel) -> Boolean = { item -> item.name.contains(normalized, ignoreCase = true) }
        return when (tab) {
            "Movies" -> m3uChannelsByCategory.values.flatten()
                .filter { it.itemType == "vod" && matcher(it) }
                .filter(::isVisibleItem)
                .sortedBy { it.name.lowercase() }
            "Series" -> {
                val seriesCategories = _categories.value["Series"].orEmpty()
                seriesCategories
                    .flatMap { buildM3uSeriesIndex(it) }
                    .distinctBy { it.id.ifBlank { it.name } }
                    .filter(matcher)
                    .filter(::isVisibleItem)
                    .sortedBy { it.name.lowercase() }
            }
            else -> m3uChannelsByCategory.values.flatten()
                .filter { it.itemType == "channel" && matcher(it) }
                .filter(::isVisibleItem)
                .sortedBy { it.name.lowercase() }
        }
    }

    private fun buildXtreamPortalKey(serverUrl: String, username: String, password: String): String {
        return "${serverUrl.trimEnd('/')}|$username|$password"
    }

    private fun filterVisibleItems(items: List<Channel>): List<Channel> = items.filter(::isVisibleItem)

    private fun isVisibleItem(item: Channel): Boolean {
        val name = item.name
        return !name.contains("=") && name.count { it == '-' } < 2
    }

    private data class ParsedEpisodeToken(
        val seriesTitle: String,
        val seasonNumber: Int,
        val episodeNumber: Int
    )

    private data class XtreamSeriesResolution(
        val resolvedSeriesId: String,
        val seasons: List<Channel>,
        val episodes: List<Channel>
    )

    private fun buildM3uSeriesIndex(category: Category): List<Channel> {
        val source = m3uChannelsByCategory[category.categoryId].orEmpty()
        if (source.isEmpty()) return emptyList()

        val grouped = linkedMapOf<String, MutableList<Channel>>()
        val displayNameBySeries = linkedMapOf<String, String>()

        source.forEachIndexed { index, item ->
            val parsed = parseEpisodeToken(item.name)
            val fallbackTitle = sanitizeSeriesTitle(item.name)
            val seriesTitle = parsed?.seriesTitle?.ifBlank { fallbackTitle } ?: fallbackTitle
            val seriesKey = buildSeriesKey(category.categoryId, seriesTitle, item)
            val seasonNumber = parsed?.seasonNumber ?: 1
            val computedEpisodeNumber = parsed?.episodeNumber ?: (index + 1)

            val normalizedEpisode = item.copy(
                id = item.id.ifBlank { "$seriesKey:$seasonNumber:$computedEpisodeNumber" },
                name = item.name.ifBlank { "$seriesTitle E$computedEpisodeNumber" },
                itemType = "episode",
                categoryType = "Series",
                movieId = seriesKey,
                seriesId = seriesKey,
                seasonId = seasonNumber.toString(),
                seasonNumber = seasonNumber,
                episodeNumber = computedEpisodeNumber,
                parentPoster = item.parentPoster.ifBlank { item.screenshotUri }
            )

            grouped.getOrPut(seriesKey) { mutableListOf() }.add(normalizedEpisode)
            if (displayNameBySeries[seriesKey].isNullOrBlank()) {
                displayNameBySeries[seriesKey] = seriesTitle.ifBlank { item.name }
            }
        }

        val normalizedGroups = grouped.mapValues { (_, episodes) ->
            episodes
                .distinctBy { ep -> ep.id.ifBlank { "${ep.seasonNumber}:${ep.episodeNumber}:${ep.name}" } }
                .sortedWith(compareBy<Channel> { it.seasonNumber }.thenBy { it.episodeNumber }.thenBy { it.name.lowercase() })
        }

        m3uSeriesEpisodesBySeriesId.putAll(normalizedGroups)

        return normalizedGroups.map { (seriesKey, episodes) ->
            val first = episodes.firstOrNull()
            Channel(
                id = seriesKey,
                name = displayNameBySeries[seriesKey].orEmpty().ifBlank { first?.name.orEmpty() },
                itemType = "series",
                categoryType = "Series",
                movieId = seriesKey,
                seriesId = seriesKey,
                screenshotUri = first?.screenshotUri.orEmpty(),
                posterPath = first?.posterPath.orEmpty(),
                parentPoster = first?.parentPoster.orEmpty()
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun deriveSeasonsFromEpisodes(episodes: List<Channel>, seriesId: String): List<Channel> {
        if (episodes.isEmpty()) return emptyList()
        val grouped = episodes.groupBy { if (it.seasonNumber > 0) it.seasonNumber else 1 }
        return grouped.toSortedMap().map { (seasonNumber, seasonEpisodes) ->
            val first = seasonEpisodes.firstOrNull()
            Channel(
                id = "$seriesId:season:$seasonNumber",
                name = "Season $seasonNumber",
                itemType = "season",
                movieId = seriesId,
                seasonId = seasonNumber.toString(),
                seasonNumber = seasonNumber,
                seriesId = seriesId,
                screenshotUri = first?.screenshotUri.orEmpty(),
                posterPath = first?.posterPath.orEmpty(),
                parentPoster = first?.parentPoster.orEmpty()
            )
        }
    }

    private fun buildSeasonsFromXtreamData(
        seriesId: String,
        parsedSeasons: List<Channel>,
        episodes: List<Channel>
    ): List<Channel> {
        if (episodes.isEmpty()) {
            return parsedSeasons
                .sortedWith(compareBy<Channel> { if (it.seasonNumber > 0) it.seasonNumber else Int.MAX_VALUE }.thenBy { it.name.lowercase() })
        }

        val derived = deriveSeasonsFromEpisodes(episodes, seriesId)
        if (derived.isEmpty()) return parsedSeasons

        val seasonMetaByNumber = parsedSeasons
            .filter { it.seasonNumber > 0 }
            .associateBy { it.seasonNumber }

        return derived.map { season ->
            val meta = seasonMetaByNumber[season.seasonNumber]
            if (meta == null) {
                season
            } else {
                season.copy(
                    name = meta.name.ifBlank { season.name },
                    screenshotUri = meta.screenshotUri.ifBlank { season.screenshotUri },
                    posterPath = meta.posterPath.ifBlank { season.posterPath },
                    parentPoster = meta.parentPoster.ifBlank { season.parentPoster }
                )
            }
        }
    }

    private fun filterEpisodesForSeason(allEpisodes: List<Channel>, season: Channel): List<Channel> {
        if (allEpisodes.isEmpty()) return emptyList()

        val seasonNumberFromName = Regex("(?i)(?:season|stagione)\\s*(\\d{1,3})")
            .find(season.name)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
        val targetSeasonNumber = when {
            season.seasonNumber > 0 -> season.seasonNumber
            seasonNumberFromName > 0 -> seasonNumberFromName
            else -> 0
        }

        val byNumber = if (targetSeasonNumber > 0) {
            allEpisodes.filter { it.seasonNumber == targetSeasonNumber }
        } else emptyList()
        if (byNumber.isNotEmpty()) return byNumber

        val seasonIdRaw = season.seasonId.ifBlank { season.id }
        if (seasonIdRaw.isNotBlank()) {
            val bySeasonId = allEpisodes.filter { it.seasonId.equals(seasonIdRaw, ignoreCase = true) }
            if (bySeasonId.isNotEmpty()) return bySeasonId

            val seasonNumFromId = seasonIdRaw.toIntOrNull()
            if (seasonNumFromId != null) {
                val byParsedId = allEpisodes.filter { it.seasonNumber == seasonNumFromId }
                if (byParsedId.isNotEmpty()) return byParsedId
            }
        }

        return if (targetSeasonNumber <= 0 && seasonIdRaw.isBlank()) allEpisodes else emptyList()
    }

    private suspend fun loadXtreamEpisodesForSeason(
        season: Channel,
        seriesIds: List<String>,
        episodeCacheBySeries: MutableMap<String, List<Channel>>,
        fetchEpisodes: suspend (String) -> List<Channel>
    ): List<Channel> {
        val candidates = seriesIds.filter { it.isNotBlank() }.distinct()
        if (candidates.isEmpty()) return emptyList()

        candidates.forEach { seriesId ->
            val cachedEpisodes = episodeCacheBySeries[seriesId].orEmpty()
            val match = filterEpisodesForSeason(cachedEpisodes, season)
            if (match.isNotEmpty()) return match
        }

        for (seriesId in candidates) {
            val fetchedEpisodes = try {
                fetchEpisodes(seriesId)
            } catch (_: Exception) {
                continue
            }
            episodeCacheBySeries[seriesId] = fetchedEpisodes
            val match = filterEpisodesForSeason(fetchedEpisodes, season)
            if (match.isNotEmpty()) return match
        }

        return emptyList()
    }

    private suspend fun resolveXtreamSeriesInfo(
        seriesIds: List<String>,
        episodeCacheBySeries: MutableMap<String, List<Channel>>,
        fetchSeriesInfo: suspend (String) -> Pair<List<Channel>, List<Channel>>
    ): XtreamSeriesResolution {
        val candidates = seriesIds.filter { it.isNotBlank() }.distinct()
        if (candidates.isEmpty()) return XtreamSeriesResolution("", emptyList(), emptyList())

        var best = XtreamSeriesResolution("", emptyList(), emptyList())
        var bestScore = -1

        for (seriesId in candidates) {
            val info = try {
                fetchSeriesInfo(seriesId)
            } catch (_: Exception) {
                continue
            }
            val seasons = info.first
            val episodes = info.second
            episodeCacheBySeries[seriesId] = episodes
            val score = (episodes.size * 10) + seasons.size
            if (score > bestScore) {
                bestScore = score
                best = XtreamSeriesResolution(
                    resolvedSeriesId = seriesId,
                    seasons = seasons,
                    episodes = episodes
                )
            }
        }

        return best
    }

    private fun buildSeriesKey(categoryId: String, seriesTitle: String, item: Channel): String {
        val normalized = seriesTitle
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        val tail = if (normalized.isNotBlank()) normalized else item.id.ifBlank { item.streamUrl.hashCode().toString() }
        return "m3u:$categoryId:$tail"
    }

    private fun sanitizeSeriesTitle(rawName: String): String {
        val cleaned = rawName
            .replace(Regex("(?i)[\\s._-]+s\\d{1,2}[\\s._-]*e\\d{1,3}.*$"), "")
            .replace(Regex("(?i)[\\s._-]+\\d{1,2}x\\d{1,3}.*$"), "")
            .replace(Regex("(?i)[\\s._-]+stagione\\s*\\d{1,2}\\s*episodio\\s*\\d{1,3}.*$"), "")
            .trim()
        return cleaned.ifBlank { rawName.trim() }
    }

    private fun mergeVodItem(base: Channel, details: Channel): Channel {
        return base.copy(
            name = details.name.ifBlank { base.name },
            screenshotUri = details.screenshotUri.ifBlank { base.screenshotUri },
            posterPath = details.posterPath.ifBlank { base.posterPath.ifBlank { base.screenshotUri } },
            description = details.description.ifBlank { base.description },
            year = details.year.ifBlank { base.year },
            releaseDate = details.releaseDate.ifBlank { base.releaseDate },
            director = details.director.ifBlank { base.director },
            actors = details.actors.ifBlank { base.actors },
            ratingImdb = details.ratingImdb.ifBlank { base.ratingImdb },
            time = if (details.time > 0) details.time else base.time,
            durationText = details.durationText.ifBlank { base.durationText },
            age = details.age.ifBlank { base.age },
            country = details.country.ifBlank { base.country },
            genresStr = details.genresStr.ifBlank { base.genresStr },
            trailerUrl = details.trailerUrl.ifBlank { base.trailerUrl },
            tmdbId = details.tmdbId.ifBlank { base.tmdbId },
            backdropPath = details.backdropPath.ifBlank { base.backdropPath },
            added = details.added.ifBlank { base.added },
            streamUrl = details.streamUrl.ifBlank { base.streamUrl },
            streamFormat = details.streamFormat.ifBlank { base.streamFormat }
        )
    }

    private fun mergeSeriesItem(base: Channel, details: Channel): Channel {
        return base.copy(
            name = details.name.ifBlank { base.name },
            screenshotUri = details.screenshotUri.ifBlank { base.screenshotUri },
            posterPath = details.posterPath.ifBlank { base.posterPath.ifBlank { base.screenshotUri } },
            description = details.description.ifBlank { base.description },
            year = details.year.ifBlank { base.year },
            releaseDate = details.releaseDate.ifBlank { base.releaseDate },
            director = details.director.ifBlank { base.director },
            actors = details.actors.ifBlank { base.actors },
            ratingImdb = details.ratingImdb.ifBlank { base.ratingImdb },
            time = if (details.time > 0) details.time else base.time,
            durationText = details.durationText.ifBlank { base.durationText },
            age = details.age.ifBlank { base.age },
            country = details.country.ifBlank { base.country },
            genresStr = details.genresStr.ifBlank { base.genresStr },
            trailerUrl = details.trailerUrl.ifBlank { base.trailerUrl },
            tmdbId = details.tmdbId.ifBlank { base.tmdbId },
            backdropPath = details.backdropPath.ifBlank { base.backdropPath },
            added = details.added.ifBlank { base.added },
            movieId = details.movieId.ifBlank { base.movieId },
            seriesId = details.seriesId.ifBlank { base.seriesId }
        )
    }

    private fun scoreDetails(item: Channel): Int {
        return (if (item.description.isNotBlank()) 4 else 0) +
            (if (item.year.isNotBlank()) 2 else 0) +
            (if (item.screenshotUri.isNotBlank()) 2 else 0) +
            (if (item.ratingImdb.isNotBlank()) 1 else 0) +
            (if (item.actors.isNotBlank()) 1 else 0)
    }

    private fun parseEpisodeToken(rawName: String): ParsedEpisodeToken? {
        val input = rawName
            .replace('_', ' ')
            .replace('.', ' ')
            .trim()

        val patterns = listOf(
            Regex("(?i)^(.*?)[\\s\\[(\\-]+s(\\d{1,2})[\\s._-]*e(\\d{1,3})\\b"),
            Regex("(?i)^(.*?)[\\s\\[(\\-]+(\\d{1,2})x(\\d{1,3})\\b"),
            Regex("(?i)^(.*?)\\s+stagione\\s*(\\d{1,2})\\s*episodio\\s*(\\d{1,3})\\b")
        )

        patterns.forEach { pattern ->
            val match = pattern.find(input) ?: return@forEach
            val title = match.groupValues.getOrNull(1).orEmpty().trim().ifBlank { sanitizeSeriesTitle(rawName) }
            val season = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@forEach
            val episode = match.groupValues.getOrNull(3)?.toIntOrNull() ?: return@forEach
            return ParsedEpisodeToken(title, season, episode)
        }

        return null
    }

    private fun com.google.gson.JsonObject.string(key: String): String {
        val el = get(key) ?: return ""
        if (el.isJsonNull) return ""
        return runCatching { el.asString }.getOrDefault("")
    }

    private fun buildXtreamAccountInfo(
        userJson: String,
        serverJson: String,
        credential: XtreamCredential,
        fallbackServerUrl: String
    ): AccountInfo {
        val user = runCatching { JsonParser.parseString(userJson).asJsonObject }.getOrNull()
        val server = runCatching { JsonParser.parseString(serverJson).asJsonObject }.getOrNull()
        val expiryRaw = user?.string("exp_date").orEmpty()
        val protocol = server?.string("server_protocol").orEmpty()
        val domain = server?.string("url").orEmpty()
        val port = server?.string("port").orEmpty()
        val fallbackServer = if (protocol.isNotBlank() && domain.isNotBlank()) {
            buildString {
                append(protocol).append("://").append(domain)
                if (port.isNotBlank()) append(':').append(port)
            }
        } else {
            fallbackServerUrl
        }
        return AccountInfo(
            name = user?.string("username").orEmpty().ifBlank { credential.username },
            serverUrl = fallbackServer,
            username = credential.username,
            password = credential.password,
            maxOnline = user?.string("max_connections")?.toIntOrNull() ?: 0,
            expireDate = formatXtreamExpiry(expiryRaw),
            expireBillingDate = formatXtreamExpiry(expiryRaw),
            isStalker = false
        )
    }

    private fun formatXtreamExpiry(raw: String): String {
        if (raw.isBlank() || raw == "0") return ""
        val epoch = raw.toLongOrNull() ?: return raw
        return runCatching {
            Instant.ofEpochSecond(epoch)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }.getOrDefault(raw)
    }

    private fun mergeAccountInfo(account: AccountInfo, profile: AccountInfo): AccountInfo {
        return AccountInfo(
            name = account.name.ifBlank { profile.name },
            serverUrl = account.serverUrl.ifBlank { profile.serverUrl.ifBlank { portalUrl } },
            username = account.username.ifBlank { profile.username },
            password = account.password.ifBlank { profile.password },
            mac = account.mac.ifBlank { profile.mac.ifBlank { portalMac } },
            maxOnline = if (account.maxOnline != 0) account.maxOnline else profile.maxOnline,
            parentalPassword = profile.parentalPassword.ifBlank { account.parentalPassword },
            expireDate = firstReal(
                account.expireBillingDate,
                account.expireDate,
                profile.expireBillingDate,
                profile.expireDate
            ),
            expireBillingDate = firstReal(account.expireBillingDate, profile.expireBillingDate),
            isStalker = account.isStalker || profile.isStalker || portalType == "stalker"
        )
    }

    private fun firstReal(vararg values: String): String {
        return values.firstOrNull { value ->
            val normalized = value.trim()
            normalized.isNotEmpty() &&
                normalized != "0" &&
                normalized != "000000000" &&
                normalized != "0000-00-00 00:00:00" &&
                !normalized.equals("none", ignoreCase = true)
        }.orEmpty()
    }

    fun clearError() { _error.value = null }
    fun setError(msg: String) { _error.value = Strings.localizeError(msg) }
}
