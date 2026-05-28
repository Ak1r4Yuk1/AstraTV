package com.stalker.player.data.api

import com.google.gson.JsonParser
import com.stalker.player.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class XtreamClient {

    companion object {
        private const val MAX_PARALLEL_REQUESTS = 24
        private const val MAX_PARALLEL_REQUESTS_PER_HOST = 12
    }

    private val networkExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val dispatcher = okhttp3.Dispatcher(networkExecutor).apply {
        maxRequests = MAX_PARALLEL_REQUESTS
        maxRequestsPerHost = MAX_PARALLEL_REQUESTS_PER_HOST
    }

    // USER-AGENT "IPTVSmartersPro" PER BYPASSARE BLOCCHI ANTI-BROWSER
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "IPTVSmartersPro")
                    .header("Accept", "*/*")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private suspend fun httpGetWithRetry(url: String, maxRetries: Int = 3): String = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                if (response.code in listOf(429, 500, 502, 503, 504)) {
                    val wait = minOf(1000L * (1L shl (attempt - 1)), 16000L)
                    android.util.Log.w("XtreamClient", "Attempt $attempt/$maxRetries: HTTP ${response.code} for $url, retrying in ${wait}ms")
                    response.close()
                    Thread.sleep(wait)
                    continue
                }
                if (body.isNullOrBlank()) {
                    response.close()
                    throw IOException("Empty response from $url (HTTP ${response.code})")
                }
                if (!response.isSuccessful) {
                    val snippet = if (body.length > 200) body.take(200) + "..." else body
                    response.close()
                    throw IOException("HTTP ${response.code} from $url: $snippet")
                }
                response.close()
                return@withContext body
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) {
                    val wait = minOf(1000L * (1L shl (attempt - 1)), 16000L)
                    Thread.sleep(wait)
                }
            }
        }
        throw lastException ?: IOException("All $maxRetries attempts failed for $url")
    }

    private fun buildUrl(
        serverUrl: String,
        username: String,
        password: String,
        vararg params: Pair<String, String>
    ): String {
        val base = serverUrl.trimEnd('/')
        val sb = StringBuilder("$base/player_api.php?username=$username&password=$password")
        for ((key, value) in params) {
            sb.append("&$key=$value")
        }
        return sb.toString()
    }

    private fun com.google.gson.JsonObject.string(key: String): String {
        val el = get(key) ?: return ""
        if (el.isJsonNull) return ""
        return runCatching { el.asString }.getOrDefault("")
    }

    private fun com.google.gson.JsonObject.int(key: String): Int? {
        val el = get(key) ?: return null
        if (el.isJsonNull) return null
        return runCatching { el.asInt }.getOrNull()
            ?: runCatching { el.asString.toIntOrNull() }.getOrNull()
    }

    private fun com.google.gson.JsonObject.obj(key: String): com.google.gson.JsonObject? {
        val el = get(key) ?: return null
        if (el.isJsonNull || !el.isJsonObject) return null
        return el.asJsonObject
    }

    private fun com.google.gson.JsonObject.arr(key: String): com.google.gson.JsonArray? {
        val el = get(key) ?: return null
        if (el.isJsonNull || !el.isJsonArray) return null
        return el.asJsonArray
    }

    private fun com.google.gson.JsonElement?.objOrNull(): com.google.gson.JsonObject? {
        if (this == null || isJsonNull || !isJsonObject) return null
        return asJsonObject
    }

    private fun com.google.gson.JsonElement?.arrOrNull(): com.google.gson.JsonArray? {
        if (this == null || isJsonNull || !isJsonArray) return null
        return asJsonArray
    }

    // ── Authentication Tollerante e Diagnostica ───────────────────
    suspend fun authenticate(
        url: String,
        username: String,
        password: String
    ): Result<Pair<String, String>> {
        return try {
            val builtUrl = buildUrl(url, username, password)
            val body = httpGetWithRetry(builtUrl)
            val bodyTrimmed = body.trim()
            
            // 1. Controllo risposte nulle o vuote immediate
            if (bodyTrimmed.equals("null", ignoreCase = true) || bodyTrimmed.isEmpty()) {
                return Result.failure(IOException("Il server ha restituito una risposta vuota o 'null'."))
            }

            // 2. Parsing dell'elemento base
            val jsonElement = try {
                JsonParser.parseString(bodyTrimmed)
            } catch (e: Exception) {
                return Result.failure(IOException("Errore parsing stringa (Non è un JSON valido): ${e.message}"))
            }

            // 3. Se il server risponde con un Array anziché un Object (capita in certi vecchi pannelli XC)
            if (jsonElement.isJsonArray) {
                val array = jsonElement.asJsonArray
                if (array.size() == 0) {
                    return Result.failure(IOException("Il server ha restituito un array JSON vuoto (Credenziali errate?)."))
                }
                // Se contiene un oggetto interno, proviamo a usare quello
                val firstEl = array.get(0)
                if (firstEl != null && firstEl.isJsonObject) {
                    val obj = firstEl.asJsonObject
                    val userInfo = obj.get("user_info")?.toString() ?: "{}"
                    val serverInfo = obj.get("server_info")?.toString() ?: "{}"
                    return Result.success(Pair(userInfo, serverInfo))
                }
                return Result.failure(IOException("Risposta Array JSON non supportata."))
            }

            if (!jsonElement.isJsonObject) {
                return Result.failure(IOException("Risposta imprevista dal server (Formato sconosciuto)."))
            }

            val json = jsonElement.asJsonObject
            
            // 4. Estrazione difensiva avanzata
            val userInfoEl = json.get("user_info")
            val serverInfoEl = json.get("server_info")
            
            // Se mancano entrambi i nodi chiave ma il JSON è valido, potrebbe essere un errore di auth inserito nel JSON stesso
            if ((userInfoEl == null || userInfoEl.isJsonNull) && (serverInfoEl == null || serverInfoEl.isJsonNull)) {
                val statusEl = json.get("status")?.runCatching { asString }?.getOrNull()
                val messageEl = json.get("message")?.runCatching { asString }?.getOrNull()
                if (statusEl != null || messageEl != null) {
                    return Result.failure(IOException("Server rifiuta l'accesso: $messageEl (Status: $statusEl)"))
                }
                // Tentativo estremo: se non ci sono i nodi ma ci sono dati sparsi, passiamo l'intero JSON come userInfo
                return Result.success(Pair(json.toString(), "{}"))
            }
            
            val userInfo = if (userInfoEl != null && !userInfoEl.isJsonNull) userInfoEl.toString() else "{}"
            val serverInfo = if (serverInfoEl != null && !serverInfoEl.isJsonNull) serverInfoEl.toString() else "{}"
            
            Result.success(Pair(userInfo, serverInfo))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Categories ────────────────────────────────────────────────

    suspend fun getLiveCategories(
        url: String,
        username: String,
        password: String
    ): List<Category> = withContext(Dispatchers.IO) {
        val builtUrl = buildUrl(url, username, password, "action" to "get_live_categories")
        val body = httpGetWithRetry(builtUrl)
        parseCategories(body, "IPTV")
    }

    suspend fun getVodCategories(
        url: String,
        username: String,
        password: String
    ): List<Category> = withContext(Dispatchers.IO) {
        val builtUrl = buildUrl(url, username, password, "action" to "get_vod_categories")
        val body = httpGetWithRetry(builtUrl)
        parseCategories(body, "VOD")
    }

    suspend fun getSeriesCategories(
        url: String,
        username: String,
        password: String
    ): List<Category> = withContext(Dispatchers.IO) {
        val builtUrl = buildUrl(url, username, password, "action" to "get_series_categories")
        val body = httpGetWithRetry(builtUrl)
        parseCategories(body, "Series")
    }

    private fun parseCategories(jsonStr: String, categoryType: String): List<Category> {
        if (jsonStr.trim().equals("null", ignoreCase = true)) return emptyList()
        val arr = try {
            JsonParser.parseString(jsonStr).asJsonArray
        } catch (e: Exception) {
            throw IOException("Invalid categories response", e)
        }
        return arr.map { el ->
            val o = el.asJsonObject
            Category(
                name = o.string("category_name"),
                categoryType = categoryType,
                categoryId = o.string("category_id")
            )
        }
    }

    // ── Streams ───────────────────────────────────────────────────

    suspend fun getLiveStreams(
        url: String,
        username: String,
        password: String,
        categoryId: String
    ): List<Channel> = withContext(Dispatchers.IO) {
        val builtUrl = buildUrl(
            url, username, password,
            "action" to "get_live_streams",
            "category_id" to categoryId
        )
        val body = httpGetWithRetry(builtUrl)
        parseChannels(body, "channel")
    }

    suspend fun getVodStreams(
        url: String,
        username: String,
        password: String,
        categoryId: String
    ): List<Channel> = withContext(Dispatchers.IO) {
        val builtUrl = buildUrl(
            url, username, password,
            "action" to "get_vod_streams",
            "category_id" to categoryId
        )
        val body = httpGetWithRetry(builtUrl)
        parseChannels(body, "vod")
    }

    suspend fun getSeries(
        url: String,
        username: String,
        password: String,
        categoryId: String
    ): List<Channel> = withContext(Dispatchers.IO) {
        val builtUrl = buildUrl(
            url, username, password,
            "action" to "get_series",
            "category_id" to categoryId
        )
        val body = httpGetWithRetry(builtUrl)
        parseChannels(body, "series")
    }

    suspend fun getSeriesDetails(
        url: String,
        username: String,
        password: String,
        seriesId: String
    ): Channel = withContext(Dispatchers.IO) {
        if (seriesId.isBlank()) return@withContext Channel()

        val candidateUrls = linkedSetOf(
            buildUrl(
                url, username, password,
                "action" to "get_series_info",
                "series_id" to seriesId
            ),
            buildUrl(
                url, username, password,
                "action" to "get_series_info",
                "series" to seriesId
            )
        )

        var best = Channel()
        var bestScore = -1
        var lastError: Exception? = null

        for (candidateUrl in candidateUrls) {
            val parsed = try {
                val body = httpGetWithRetry(candidateUrl)
                parseSeriesDetails(body, seriesId)
            } catch (e: Exception) {
                lastError = e
                continue
            }

            val score =
                (if (parsed.description.isNotBlank()) 4 else 0) +
                (if (parsed.year.isNotBlank()) 2 else 0) +
                (if (parsed.screenshotUri.isNotBlank()) 2 else 0) +
                (if (parsed.ratingImdb.isNotBlank()) 1 else 0) +
                (if (parsed.actors.isNotBlank()) 1 else 0)
            if (score > bestScore) {
                bestScore = score
                best = parsed
            }
        }

        if (bestScore >= 0) return@withContext best
        throw lastError ?: IOException("Unable to load series details for id $seriesId")
    }

    suspend fun getVodInfo(
        url: String,
        username: String,
        password: String,
        vodId: String
    ): Channel = withContext(Dispatchers.IO) {
        if (vodId.isBlank()) return@withContext Channel()

        val candidateUrls = linkedSetOf(
            buildUrl(
                url, username, password,
                "action" to "get_vod_info",
                "vod_id" to vodId
            ),
            buildUrl(
                url, username, password,
                "action" to "get_vod_info",
                "movie_id" to vodId
            )
        )

        var best = Channel()
        var bestScore = -1
        var lastError: Exception? = null

        for (candidateUrl in candidateUrls) {
            val parsed = try {
                val body = httpGetWithRetry(candidateUrl)
                parseVodInfo(body, vodId)
            } catch (e: Exception) {
                lastError = e
                continue
            }

            val score =
                (if (parsed.description.isNotBlank()) 4 else 0) +
                (if (parsed.year.isNotBlank()) 2 else 0) +
                (if (parsed.screenshotUri.isNotBlank()) 2 else 0) +
                (if (parsed.ratingImdb.isNotBlank()) 1 else 0) +
                (if (parsed.actors.isNotBlank()) 1 else 0)
            if (score > bestScore) {
                bestScore = score
                best = parsed
            }
        }

        if (bestScore >= 0) return@withContext best
        throw lastError ?: IOException("Unable to load vod info for id $vodId")
    }

    private fun parseChannels(jsonStr: String, itemType: String): List<Channel> {
        if (jsonStr.trim().equals("null", ignoreCase = true)) return emptyList()
        val arr = try {
            JsonParser.parseString(jsonStr).asJsonArray
        } catch (e: Exception) {
            throw IOException("Invalid channels response", e)
        }
        return arr.map { el ->
            val o = el.asJsonObject
            val isSeries = o.string("series_id").isNotEmpty()
            Channel(
                id = o.string("stream_id").ifBlank { o.string("series_id") },
                name = o.string("name"),
                itemType = itemType,
                screenshotUri = o.string("stream_icon").ifBlank { o.string("cover") },
                description = o.string("plot").ifBlank { o.string("description") },
                year = o.string("year"),
                releaseDate = o.string("releaseDate").ifBlank { o.string("releasedate") },
                director = o.string("director"),
                actors = o.string("cast").ifBlank { o.string("actors") },
                ratingImdb = o.string("rating").ifBlank { o.string("rating_imdb") },
                time = o.int("duration_secs")
                    ?: o.int("duration")
                    ?: o.int("time")
                    ?: 0,
                durationText = o.string("duration").ifBlank { o.string("episode_run_time") },
                age = o.string("age"),
                country = o.string("country"),
                genresStr = o.string("genre"),
                trailerUrl = o.string("youtube_trailer"),
                tmdbId = o.string("tmdb").ifBlank { o.string("tmdb_id") },
                backdropPath = parseBackdrop(o),
                added = o.string("added"),
                isSeries = isSeries,
                posterPath = o.string("stream_icon").ifBlank { o.string("cover") },
                movieId = o.string("series_id"),
                seriesId = o.string("series_id"),
                episodeId = o.string("id"),
                streamUrl = o.string("direct_source"),
                streamFormat = o.string("container_extension")
            )
        }
    }

    // ── Series Info ───────────────────────────────────────────────

    suspend fun getSeriesInfo(
        url: String,
        username: String,
        password: String,
        seriesId: String
    ): Pair<List<Channel>, List<Channel>> = withContext(Dispatchers.IO) {
        if (seriesId.isBlank()) return@withContext Pair(emptyList(), emptyList())

        val candidateUrls = linkedSetOf(
            buildUrl(
                url, username, password,
                "action" to "get_series_info",
                "series_id" to seriesId
            ),
            buildUrl(
                url, username, password,
                "action" to "get_series_info",
                "series" to seriesId
            )
        )

        var bestResult = Pair(emptyList<Channel>(), emptyList<Channel>())
        var bestScore = -1
        var lastError: Exception? = null

        for (candidateUrl in candidateUrls) {
            val parsed = try {
                val body = httpGetWithRetry(candidateUrl)
                parseSeriesInfo(body, seriesId)
            } catch (e: Exception) {
                lastError = e
                continue
            }
            val score = (parsed.second.size * 10) + parsed.first.size
            if (score > bestScore) {
                bestScore = score
                bestResult = parsed
            }
        }

        if (bestScore >= 0) return@withContext bestResult
        throw lastError ?: IOException("Unable to load series info for series $seriesId")
    }

    private fun parseSeriesInfo(
        jsonStr: String,
        seriesId: String
    ): Pair<List<Channel>, List<Channel>> {
        if (jsonStr.trim().equals("null", ignoreCase = true)) return Pair(emptyList(), emptyList())
        val root = try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: Exception) {
            throw IOException("Invalid series info response", e)
        }

        val seasons = mutableListOf<Channel>()
        val episodes = mutableListOf<Channel>()

        fun addSeason(seasonNumber: Int, seasonIdRaw: String, seasonNameRaw: String, cover: String) {
            val normalizedSeasonId = seasonIdRaw.ifBlank { if (seasonNumber > 0) seasonNumber.toString() else "" }
            if (normalizedSeasonId.isBlank() && seasonNumber <= 0) return
            seasons.add(
                Channel(
                    id = normalizedSeasonId.ifBlank { seasonNumber.toString() },
                    name = seasonNameRaw.ifBlank { if (seasonNumber > 0) "Season $seasonNumber" else "Season" },
                    itemType = "season",
                    seasonId = normalizedSeasonId.ifBlank { seasonNumber.toString() },
                    seasonNumber = seasonNumber,
                    screenshotUri = cover,
                    posterPath = cover,
                    movieId = seriesId,
                    seriesId = seriesId
                )
            )
        }

        root.arr("seasons")?.forEach { el ->
            val seasonObj = el.objOrNull() ?: return@forEach
            val seasonNumber = seasonObj.int("season_number")
                ?: seasonObj.int("season")
                ?: seasonObj.int("season_num")
                ?: seasonObj.string("id").toIntOrNull()
                ?: 0
            val seasonId = seasonObj.string("id")
            val seasonName = seasonObj.string("name")
            val cover = seasonObj.string("cover")
            addSeason(seasonNumber, seasonId, seasonName, cover)

            seasonObj.arr("episodes")?.forEach { episodeEl ->
                val episodeObj = episodeEl.objOrNull() ?: return@forEach
                episodes.add(
                    toEpisodeChannel(
                        episodeObj = episodeObj,
                        seriesId = seriesId,
                        defaultSeasonNumber = seasonNumber,
                        defaultSeasonId = seasonId.ifBlank { seasonNumber.toString() }
                    )
                )
            }
        }

        root.obj("seasons")?.entrySet()?.forEach { entry ->
            val seasonFromKey = entry.key.toIntOrNull() ?: 0
            val seasonContainer = entry.value.objOrNull()
            val seasonArr = entry.value.arrOrNull() ?: seasonContainer?.arr("episodes") ?: return@forEach
            if (seasonArr.size() == 0) return@forEach

            val seasonName = seasonContainer?.string("name").orEmpty().ifBlank { "Season $seasonFromKey" }
            val cover = seasonContainer?.string("cover").orEmpty()
            addSeason(seasonFromKey, seasonContainer?.string("id").orEmpty(), seasonName, cover)

            seasonArr.forEach { ep ->
                val episodeObj = ep.objOrNull() ?: return@forEach
                episodes.add(
                    toEpisodeChannel(
                        episodeObj = episodeObj,
                        seriesId = seriesId,
                        defaultSeasonNumber = seasonFromKey,
                        defaultSeasonId = seasonFromKey.toString()
                    )
                )
            }
        }

        root.arr("episodes")?.forEach { el ->
            val episodeObj = el.objOrNull() ?: return@forEach
            val seasonNumber = episodeObj.int("season")
                ?: episodeObj.int("season_number")
                ?: 0
            addSeason(
                seasonNumber = seasonNumber,
                seasonIdRaw = episodeObj.string("season_id"),
                seasonNameRaw = "",
                cover = ""
            )
            episodes.add(
                toEpisodeChannel(
                    episodeObj = episodeObj,
                    seriesId = seriesId,
                    defaultSeasonNumber = seasonNumber,
                    defaultSeasonId = seasonNumber.toString()
                )
            )
        }

        root.obj("episodes")?.entrySet()?.forEach { entry ->
            val seasonFromKey = entry.key.toIntOrNull() ?: 0
            val arr = entry.value.arrOrNull() ?: return@forEach
            addSeason(seasonFromKey, seasonFromKey.toString(), "", "")
            arr.forEach { ep ->
                val episodeObj = ep.objOrNull() ?: return@forEach
                episodes.add(
                    toEpisodeChannel(
                        episodeObj = episodeObj,
                        seriesId = seriesId,
                        defaultSeasonNumber = seasonFromKey,
                        defaultSeasonId = seasonFromKey.toString()
                    )
                )
            }
        }

        val dedupedSeasons = seasons
            .filter { it.seasonNumber > 0 || it.seasonId.isNotBlank() || it.id.isNotBlank() }
            .distinctBy { season ->
                when {
                    season.seasonNumber > 0 -> "n:${season.seasonNumber}"
                    season.seasonId.isNotBlank() -> "sid:${season.seasonId}"
                    else -> "id:${season.id}"
                }
            }
            .sortedWith(compareBy<Channel> { if (it.seasonNumber > 0) it.seasonNumber else Int.MAX_VALUE }.thenBy { it.name.lowercase() })

        val dedupedEpisodes = episodes
            .map { episode ->
                val normalizedSeasonNumber = if (episode.seasonNumber > 0) {
                    episode.seasonNumber
                } else {
                    episode.seasonId.toIntOrNull() ?: 0
                }
                val normalizedSeasonId = episode.seasonId.ifBlank {
                    if (normalizedSeasonNumber > 0) normalizedSeasonNumber.toString() else ""
                }
                episode.copy(
                    seasonNumber = normalizedSeasonNumber,
                    seasonId = normalizedSeasonId,
                    movieId = seriesId,
                    seriesId = seriesId
                )
            }
            .filter { ep ->
                ep.id.isNotBlank() || ep.episodeNumber > 0 || ep.name.isNotBlank() || ep.streamUrl.isNotBlank()
            }
            .distinctBy { ep ->
                val fallbackEpisodeNumber = if (ep.episodeNumber > 0) ep.episodeNumber.toString() else "x"
                val normalizedName = ep.name.trim().lowercase()
                when {
                    ep.id.isNotBlank() && ep.seasonNumber > 0 -> "sid:${ep.seasonNumber}:${ep.id}"
                    ep.id.isNotBlank() && ep.seasonId.isNotBlank() -> "sid:${ep.seasonId}:${ep.id}"
                    ep.id.isNotBlank() -> "id:${ep.id}:${normalizedName}"
                    else -> "f:${ep.seasonNumber}:${ep.seasonId}:$fallbackEpisodeNumber:$normalizedName"
                }
            }
            .sortedWith(compareBy<Channel> { it.seasonNumber }.thenBy { it.episodeNumber }.thenBy { it.name.lowercase() })

        return Pair(dedupedSeasons, dedupedEpisodes)
    }

    private fun toEpisodeChannel(
        episodeObj: com.google.gson.JsonObject,
        seriesId: String,
        defaultSeasonNumber: Int,
        defaultSeasonId: String
    ): Channel {
        val infoObj = episodeObj.obj("info")
        val seasonNumber = episodeObj.int("season")
            ?: episodeObj.int("season_number")
            ?: episodeObj.int("season_num")
            ?: defaultSeasonNumber
        val seasonId = episodeObj.string("season_id").ifBlank {
            if (seasonNumber > 0) seasonNumber.toString() else defaultSeasonId
        }
        return Channel(
            id = episodeObj.string("id").ifBlank { episodeObj.string("episode_id") },
            name = episodeObj.string("title").ifBlank { episodeObj.string("name") },
            itemType = "episode",
            seasonId = seasonId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeObj.int("episode_num")
                ?: episodeObj.int("episodeNum")
                ?: episodeObj.int("episode_number")
                ?: 0,
            time = episodeObj.int("duration_secs")
                ?: episodeObj.int("duration")
                ?: 0,
            screenshotUri = infoObj?.string("tmdb_image").orEmpty().ifBlank { episodeObj.string("cover") },
            posterPath = infoObj?.string("tmdb_image").orEmpty().ifBlank { episodeObj.string("cover") },
            movieId = seriesId,
            seriesId = seriesId,
            added = episodeObj.string("added"),
            description = episodeObj.string("plot").ifBlank { episodeObj.string("description") },
            streamUrl = episodeObj.string("direct_source").ifBlank { infoObj?.string("direct_source").orEmpty() },
            streamFormat = episodeObj.string("container_extension").ifBlank { infoObj?.string("container_extension").orEmpty() }
        )
    }

    private fun parseVodInfo(jsonStr: String, vodId: String): Channel {
        if (jsonStr.trim().equals("null", ignoreCase = true)) return Channel(id = vodId, itemType = "vod")
        val root = try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: Exception) {
            throw IOException("Invalid vod info response", e)
        }

        val info = root.obj("info")
        val movieData = root.obj("movie_data")

        val name = info?.string("name").orEmpty().ifBlank { movieData?.string("name").orEmpty() }
        val description = info?.string("plot").orEmpty()
            .ifBlank { info?.string("description").orEmpty() }
            .ifBlank { movieData?.string("plot").orEmpty() }
            .ifBlank { movieData?.string("description").orEmpty() }
        val year = info?.string("year").orEmpty()
            .ifBlank { movieData?.string("year").orEmpty() }
            .ifBlank { extractYear(info?.string("releasedate").orEmpty()) }
            .ifBlank { extractYear(info?.string("releaseDate").orEmpty()) }
            .ifBlank { extractYear(movieData?.string("releasedate").orEmpty()) }
        val cover = info?.string("movie_image").orEmpty()
            .ifBlank { info?.string("cover_big").orEmpty() }
            .ifBlank { info?.string("cover").orEmpty() }
            .ifBlank { movieData?.string("stream_icon").orEmpty() }
            .ifBlank { movieData?.string("cover").orEmpty() }
        val releaseDate = info?.string("releasedate").orEmpty()
            .ifBlank { info?.string("releaseDate").orEmpty() }
            .ifBlank { movieData?.string("releasedate").orEmpty() }
            .ifBlank { movieData?.string("releaseDate").orEmpty() }

        return Channel(
            id = movieData?.string("stream_id").orEmpty().ifBlank { vodId },
            name = name,
            itemType = "vod",
            screenshotUri = cover,
            posterPath = cover,
            description = description,
            year = year,
            releaseDate = releaseDate,
            director = info?.string("director").orEmpty().ifBlank { movieData?.string("director").orEmpty() },
            actors = info?.string("cast").orEmpty()
                .ifBlank { info?.string("actors").orEmpty() }
                .ifBlank { movieData?.string("cast").orEmpty() },
            ratingImdb = info?.string("rating").orEmpty()
                .ifBlank { info?.string("rating_5based").orEmpty() }
                .ifBlank { movieData?.string("rating").orEmpty() },
            time = info?.int("duration_secs")
                ?: info?.int("duration")
                ?: movieData?.int("duration_secs")
                ?: movieData?.int("duration")
                ?: 0,
            durationText = info?.string("duration").orEmpty()
                .ifBlank { info?.string("runtime").orEmpty() }
                .ifBlank { info?.string("episode_run_time").orEmpty() },
            age = info?.string("age").orEmpty(),
            country = info?.string("country").orEmpty(),
            genresStr = info?.string("genre").orEmpty()
                .ifBlank { movieData?.string("genre").orEmpty() },
            trailerUrl = info?.string("youtube_trailer").orEmpty(),
            tmdbId = info?.string("tmdb_id").orEmpty()
                .ifBlank { movieData?.string("tmdb_id").orEmpty() },
            backdropPath = parseBackdrop(info).ifBlank { parseBackdrop(movieData) },
            added = movieData?.string("added").orEmpty(),
            streamUrl = movieData?.string("direct_source").orEmpty(),
            streamFormat = movieData?.string("container_extension").orEmpty()
        )
    }

    private fun parseSeriesDetails(jsonStr: String, seriesId: String): Channel {
        if (jsonStr.trim().equals("null", ignoreCase = true)) return Channel(id = seriesId, itemType = "series")
        val root = try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: Exception) {
            throw IOException("Invalid series details response", e)
        }

        val info = root.obj("info")
        val movieData = root.obj("movie_data")

        val cover = info?.string("cover").orEmpty()
            .ifBlank { info?.string("cover_big").orEmpty() }
            .ifBlank { movieData?.string("cover").orEmpty() }
        val description = info?.string("plot").orEmpty()
            .ifBlank { info?.string("description").orEmpty() }
            .ifBlank { movieData?.string("plot").orEmpty() }
            .ifBlank { movieData?.string("description").orEmpty() }
        val year = info?.string("year").orEmpty()
            .ifBlank { extractYear(info?.string("releaseDate").orEmpty()) }
            .ifBlank { extractYear(info?.string("releasedate").orEmpty()) }
        val releaseDate = info?.string("releaseDate").orEmpty()
            .ifBlank { info?.string("releasedate").orEmpty() }

        return Channel(
            id = movieData?.string("series_id").orEmpty().ifBlank { seriesId },
            name = info?.string("name").orEmpty().ifBlank { movieData?.string("name").orEmpty() },
            itemType = "series",
            screenshotUri = cover,
            posterPath = cover,
            description = description,
            year = year,
            releaseDate = releaseDate,
            director = info?.string("director").orEmpty(),
            actors = info?.string("cast").orEmpty().ifBlank { info?.string("actors").orEmpty() },
            ratingImdb = info?.string("rating").orEmpty().ifBlank { info?.string("rating_5based").orEmpty() },
            time = info?.int("episode_run_time")
                ?: info?.int("duration")
                ?: 0,
            durationText = info?.string("episode_run_time").orEmpty()
                .ifBlank { info?.string("duration").orEmpty() },
            age = info?.string("age").orEmpty(),
            country = info?.string("country").orEmpty(),
            genresStr = info?.string("genre").orEmpty(),
            trailerUrl = info?.string("youtube_trailer").orEmpty(),
            tmdbId = info?.string("tmdb_id").orEmpty()
                .ifBlank { movieData?.string("tmdb_id").orEmpty() },
            backdropPath = parseBackdrop(info).ifBlank { parseBackdrop(movieData) },
            added = movieData?.string("last_modified").orEmpty(),
            movieId = movieData?.string("series_id").orEmpty().ifBlank { seriesId },
            seriesId = movieData?.string("series_id").orEmpty().ifBlank { seriesId }
        )
    }

    private fun parseBackdrop(obj: com.google.gson.JsonObject?): String {
        if (obj == null) return ""
        val backdropElement = obj.get("backdrop_path") ?: return ""
        if (backdropElement.isJsonNull) return ""
        return when {
            backdropElement.isJsonArray -> {
                backdropElement.asJsonArray.firstOrNull()?.let { el ->
                    runCatching { el.asString }.getOrDefault("")
                }.orEmpty()
            }
            else -> runCatching { backdropElement.asString }.getOrDefault("")
        }
    }

    private fun extractYear(raw: String): String {
        if (raw.isBlank()) return ""
        val match = Regex("(19|20)\\d{2}").find(raw) ?: return ""
        return match.value
    }

    // ── EPG ───────────────────────────────────────────────────────

    suspend fun getEpg(
        url: String,
        username: String,
        password: String,
        streamId: String,
        limit: Int
    ): List<EpgItem> = withContext(Dispatchers.IO) {
        val builtUrl = buildUrl(
            url, username, password,
            "action" to "get_short_epg",
            "stream_id" to streamId,
            "limit" to limit.toString()
        )
        val body = httpGetWithRetry(builtUrl)
        parseEpg(body)
    }

    private fun parseEpg(jsonStr: String): List<EpgItem> {
        if (jsonStr.trim().equals("null", ignoreCase = true)) return emptyList()
        val root = try {
            JsonParser.parseString(jsonStr).asJsonObject
        } catch (e: Exception) {
            throw IOException("Invalid EPG response", e)
        }
        val listings = root.arr("epg_listings") ?: return emptyList()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        return listings.map { el ->
            val o = el.asJsonObject
            val startStr = o.string("start")
            val stopStr = o.string("stop")
            val startTs = try {
                (dateFormat.parse(startStr)?.time ?: 0L) / 1000L
            } catch (_: Exception) {
                0L
            }
            val endTs = try {
                (dateFormat.parse(stopStr)?.time ?: 0L) / 1000L
            } catch (_: Exception) {
                0L
            }
            val durationMin = if (startTs > 0 && endTs > startTs) {
                ((endTs - startTs) / 60000).toInt()
            } else {
                o.int("duration_secs")?.div(60)
                    ?: o.int("duration")
                    ?: 0
            }

            EpgItem(
                name = o.string("title"),
                startTs = startTs,
                endTs = endTs,
                descr = o.string("description").ifBlank { o.string("descr") },
                category = o.string("category"),
                durationMin = durationMin
            )
        }
    }

    // ── Stream URLs ───────────────────────────────────────────────

    fun getLiveStreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String
    ): String {
        val base = serverUrl.trimEnd('/')
        return "$base/live/$username/$password/$streamId.ts"
    }

    fun getVodStreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        extension: String = "mp4"
    ): String {
        val base = serverUrl.trimEnd('/')
        return "$base/movie/$username/$password/$streamId.${extension.ifBlank { "mp4" }}"
    }

    fun getSeriesStreamUrl(
        serverUrl: String,
        username: String,
        password: String,
        episodeId: String,
        extension: String = "mp4"
    ): String {
        val base = serverUrl.trimEnd('/')
        return "$base/series/$username/$password/$episodeId.${extension.ifBlank { "mp4" }}"
    }
}
