package com.stalker.player.data.api

import com.google.gson.JsonParser
import com.stalker.player.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.io.Reader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StalkerApiClient {

    companion object {
        const val UA = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
        private const val MAX_RETRIES = 3
        private const val BACKOFF_MS = 800L
        private const val MAX_PARALLEL_REQUESTS = 24
        private const val MAX_PARALLEL_REQUESTS_PER_HOST = 12
        private const val MAX_CHANNEL_PAGE_CONCURRENCY = 4
    }

    var lang: String = "it"

    private val networkExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val dispatcher = Dispatcher(networkExecutor).apply {
        maxRequests = MAX_PARALLEL_REQUESTS
        maxRequestsPerHost = MAX_PARALLEL_REQUESTS_PER_HOST
    }

    private val http = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            private val store = mutableMapOf<String, MutableList<Cookie>>()
            override fun loadForRequest(url: HttpUrl) = store[url.host] ?: emptyList()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { store[url.host] = cookies.toMutableList() }
        })
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", UA).build()) }
        .build()

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun genSerial(mac: String) = md5(mac).take(13).uppercase()
    private fun genDeviceId(mac: String) = sha256(mac).uppercase()
    private fun genSignature(mac: String, serial: String, d1: String, d2: String) = sha256("$mac$serial$d1$d2").uppercase()
    private fun randHex(n: Int = 40) = (1..n).map { "0123456789abcdef".random() }.joinToString("")
    private fun safeInt(el: com.google.gson.JsonElement?) = el?.asString?.toIntOrNull() ?: try { el?.asInt } catch (_: Exception) { null } ?: 0
    private fun safeLong(el: com.google.gson.JsonElement?) = el?.asString?.toLongOrNull() ?: try { el?.asLong } catch (_: Exception) { null } ?: 0L

    private fun path(type: String) = if (type == "stalker") "stalker_portal/server/load.php" else "portal.php"

    private fun cookieString(mac: String, token: String?): String {
        val parts = mutableListOf("mac=${URLEncoder.encode(mac, "UTF-8")}", "stb_lang=$lang", "timezone=${URLEncoder.encode("Europe/Paris", "UTF-8")}")
        if (!token.isNullOrEmpty()) parts.add("token=$token")
        return parts.joinToString("; ")
    }

    private fun buildReq(base: String, pType: String, mac: String, token: String?, params: Map<String, String>): Request {
        val b = base.trimEnd('/')
        val q = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        return Request.Builder().url("$b/${path(pType)}?$q")
            .header("Cookie", cookieString(mac, token))
            .header("User-Agent", UA)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "gzip, deflate")
            .header("Connection", "keep-alive")
            .apply {
                if (!token.isNullOrEmpty()) header("Authorization", "Bearer $token")
                if (pType == "stalker") {
                    header("Referer", "$b/stalker_portal/c/index.html")
                    header("X-User-Agent", "Model: MAG250; Link: WiFi")
                }
            }.build()
    }

    private fun readBodySafely(response: Response, maxChars: Int? = null): String {
        val body = response.body ?: return ""
        val contentLength = body.contentLength()
        if (maxChars != null && contentLength > maxChars) {
            throw IOException("Response too large from ${response.request.url}: $contentLength bytes")
        }
        if (maxChars == null) return body.string()

        val builder = StringBuilder(minOf(maxChars, 64 * 1024))
        val buffer = CharArray(8192)
        val reader: Reader = body.charStream()
        while (true) {
            val read = reader.read(buffer)
            if (read == -1) break
            if (builder.length + read > maxChars) {
                throw IOException("Response too large from ${response.request.url}: exceeded $maxChars chars")
            }
            builder.append(buffer, 0, read)
        }
        return builder.toString()
    }

    private suspend fun get(req: Request, retries: Int = MAX_RETRIES, maxChars: Int? = null): String = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (attempt in 1..retries) {
            try {
                val r = http.newCall(req).execute()
                val body = try {
                    readBodySafely(r, maxChars)
                } finally {
                    r.close()
                }
                if (r.code in listOf(429, 500, 502, 503, 504)) {
                    val wait = minOf(BACKOFF_MS * (1L shl (attempt - 1)), 16000L)
                    android.util.Log.w("StalkerClient", "Attempt $attempt/$retries: HTTP ${r.code} for ${req.url}, retrying in ${wait}ms")
                    delay(wait)
                    continue
                }
                if (body.isBlank()) throw IOException("Empty response from ${req.url} (${r.code})")
                if (!r.isSuccessful) throw IOException("HTTP ${r.code} from ${req.url}: ${body.take(200)}")
                return@withContext body
            } catch (e: IOException) {
                lastException = e
                if (attempt < retries) {
                    val wait = minOf(BACKOFF_MS * (1L shl (attempt - 1)), 16000L)
                    android.util.Log.w("StalkerClient", "Attempt $attempt/$retries: ${e.message}, retrying in ${wait}ms")
                    delay(wait)
                }
            }
        }
        throw lastException ?: IOException("All $retries attempts failed for ${req.url}")
    }

    private fun safeParse(json: String) = try { JsonParser.parseString(json).asJsonObject } catch (_: Exception) { null }

    private fun looksLikeSeriesCategory(name: String): Boolean {
        val n = name.lowercase()
        return listOf("tv", "series", "show", "serie", "season", "saison", "temporada").any { it in n }
    }

    private suspend fun fetchRawCategories(
        base: String,
        pType: String,
        mac: String,
        token: String,
        requestType: String
    ): List<Category> {
        val action = if (requestType == "itv") "get_genres" else "get_categories"
        val body = get(buildReq(base, pType, mac, token, mapOf("type" to requestType, "action" to action, "JsHttpRequest" to "1-xml")))
        val js = safeParse(body)?.get("js") ?: return emptyList()
        val arr = if (js.isJsonArray) js.asJsonArray else js.asJsonObject?.getAsJsonArray("data") ?: return emptyList()
        return arr.mapNotNull {
            val o = it.asJsonObject
            val name = o.get("title")?.asString ?: o.get("name")?.asString ?: o.get("category_name")?.asString.orEmpty()
            val id = o.get("id")?.asString ?: o.get("category_id")?.asString.orEmpty()
            if (name.isBlank() || id.isBlank()) null else Category(name = name, categoryType = "RAW", categoryId = id)
        }
    }

    private fun imgUrl(base: String, portalType: String, o: com.google.gson.JsonObject): String {
        for (k in listOf("screenshot_uri", "pic", "cover", "poster", "logo", "icon", "image")) {
            val v = o.get(k)?.asString; if (!v.isNullOrBlank()) return resolveUrl(base, v)
        }
        return ""
    }

    private fun resolveUrl(base: String, maybeUrl: String): String {
        if (maybeUrl.isBlank()) return ""
        if (maybeUrl.startsWith("http://") || maybeUrl.startsWith("https://")) return maybeUrl
        val b = base.trimEnd('/')
        return "$b/${maybeUrl.trimStart('/')}"
    }

    // ── Handshake (both MAC and Stalker) ──────────────────────
    suspend fun handshake(url: String, mac: String, portalType: String): String = withContext(Dispatchers.IO) {
        val base = url.trimEnd('/')
        if (portalType == "stalker") {
            val req1 = buildReq(base, "stalker", mac, null, mapOf("type" to "stb", "action" to "handshake", "token" to "", "JsHttpRequest" to "1-xml"))
            try {
                val body = get(req1)
                val tok = safeParse(body)?.getAsJsonObject("js")?.get("token")?.asString
                if (!tok.isNullOrBlank()) return@withContext tok
            } catch (e: IOException) {
                val genToken = randHex(32)
                val prehash = sha256(genToken)
                val req2 = buildReq(base, "stalker", mac, null, mapOf("type" to "stb", "action" to "handshake", "token" to genToken, "prehash" to prehash, "JsHttpRequest" to "1-xml"))
                val body2 = get(req2)
                val tok2 = safeParse(body2)?.getAsJsonObject("js")?.get("token")?.asString
                if (!tok2.isNullOrBlank()) return@withContext tok2
            }
            throw IOException("No token in handshake")
        } else {
            val req = buildReq(base, "mac", mac, null, mapOf("type" to "stb", "action" to "handshake", "JsHttpRequest" to "1-xml"))
            val body = get(req)
            safeParse(body)?.getAsJsonObject("js")?.get("token")?.asString ?: throw IOException("No token in handshake")
        }
    }

    // ── getProfile (Stalker only - required after handshake) ──
    suspend fun getProfileInfo(url: String, mac: String, token: String, portalType: String): AccountInfo = withContext(Dispatchers.IO) {
        val base = url.trimEnd('/')
        val serial = genSerial(mac)
        val deviceId = genDeviceId(mac)
        val signature = genSignature(mac, serial, deviceId, deviceId)
        val randomVal = randHex()
        val metrics = """{"mac":"$mac","sn":"$serial","type":"STB","model":"MAG250","uid":"","random":"$randomVal"}"""
        val params = mutableMapOf(
            "type" to "stb", "action" to "get_profile",
            "hd" to "1",
            "ver" to "ImageDescription: 0.2.18-r23-250; ImageDate: Thu Sep 13 11:31:16 EEST 2018; PORTAL version: 5.6.2; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c",
            "num_banks" to "2", "sn" to serial, "stb_type" to "MAG250",
            "client_type" to "STB", "image_version" to "218", "video_out" to "hdmi",
            "device_id" to deviceId, "device_id2" to deviceId,
            "signature" to signature, "auth_second_step" to "1",
            "hw_version" to "1.7-BD-00", "not_valid_token" to "0",
            "metrics" to metrics,
            "hw_version_2" to sha256(mac),
            "timestamp" to (System.currentTimeMillis() / 1000).toString(),
            "api_signature" to "262", "prehash" to "", "JsHttpRequest" to "1-xml"
        )
        val req = buildReq(base, if (portalType == "stalker") "stalker" else "mac", mac, token, params)
        val body = get(req, 2)
        val js = safeParse(body)?.getAsJsonObject("js") ?: return@withContext AccountInfo()
        AccountInfo(
            name = js.get("fname")?.asString.orEmpty(),
            serverUrl = url.trimEnd('/'),
            mac = js.get("mac_address")?.asString ?: js.get("device_mac")?.asString ?: js.get("mac")?.asString ?: mac,
            maxOnline = safeInt(js.get("max_online")),
            parentalPassword = js.get("parent_password")?.asString.orEmpty(),
            expireDate = js.get("expire_date")?.asString
                ?: js.get("exp_date")?.asString
                ?: js.get("phone")?.asString
                ?: js.get("end_date")?.asString
                ?: js.get("expire_billing_date")?.asString
                .orEmpty(),
            expireBillingDate = js.get("expire_billing_date")?.asString.orEmpty(),
            isStalker = portalType == "stalker"
        )
    }

    // ── Categories ───────────────────────────────────────────
    suspend fun getCategories(token: String, url: String, mac: String, type: String): List<Category> = withContext(Dispatchers.IO) {
        val base = url.trimEnd('/')
        val pType = if (url.contains("/stalker_portal/")) "stalker" else "mac"
        val raw = when (type) {
            "itv" -> fetchRawCategories(base, pType, mac, token, "itv")
            "vod" -> {
                val vodCats = fetchRawCategories(base, pType, mac, token, "vod")
                if (pType == "mac") vodCats.filterNot { looksLikeSeriesCategory(it.name) } else vodCats
            }
            "series" -> {
                val nativeSeries = runCatching { fetchRawCategories(base, pType, mac, token, "series") }.getOrDefault(emptyList())
                if (pType != "mac") {
                    nativeSeries
                } else {
                    val vodCats = runCatching { fetchRawCategories(base, pType, mac, token, "vod") }.getOrDefault(emptyList())
                    val merged = linkedMapOf<String, Category>()
                    nativeSeries.forEach { merged[it.categoryId] = it }
                    vodCats.filter { looksLikeSeriesCategory(it.name) }.forEach { merged.putIfAbsent(it.categoryId, it) }
                    merged.values.toList()
                }
            }
            else -> emptyList()
        }
        val filtered = when (type) {
            "series" -> raw
            "vod" -> raw
            else -> raw
        }
        val catType = when (type) { "itv" -> "IPTV"; "vod" -> "VOD"; "series" -> "Series"; else -> "IPTV" }
        filtered
            .map { it.copy(categoryType = catType) }
    }

    // ── Channels (paginated, parallel) ───────────────────────
    suspend fun getChannels(token: String, url: String, mac: String, category: Category, portalType: String): List<Channel> = coroutineScope {
        getChannels(token, url, mac, category, portalType, null)
    }

    suspend fun getChannels(
        token: String,
        url: String,
        mac: String,
        category: Category,
        portalType: String,
        onPartialResult: ((List<Channel>) -> Unit)?
    ): List<Channel> = coroutineScope {
        val base = url.trimEnd('/')
        val isMacLike = portalType != "stalker"
        val pType = if (portalType == "stalker") "stalker" else "mac"
        val startPage = if (portalType == "stalker") 1 else 0
        val strategies = when (category.categoryType) {
            "IPTV" -> listOf(Triple("itv", "genre", false))
            "VOD" -> listOf(Triple("vod", "category", false))
            "Series" -> if (isMacLike) listOf(
                Triple("series", "category", false),
                Triple("vod", "category", true)
            ) else listOf(Triple("series", "category", false))
            else -> listOf(Triple("itv", "genre", false))
        }

        fun isCategoryMarker(item: Channel): Boolean {
            val normalized = item.name.trim()
            return item.itemType == "channel"
                && item.cmd.isBlank()
                && Regex("^##.+##$").matches(normalized)
        }

        fun sanitizeItems(items: List<Channel>, filterSeries: Boolean): List<Channel> {
            val filteredByType = if (filterSeries) {
                items.filter { it.isSeries || it.seriesId.isNotBlank() }
            } else {
                items
            }
            return filteredByType.filterNot(::isCategoryMarker)
        }

        val pageConcurrency = when (category.categoryType) {
            "IPTV" -> 8
            "VOD" -> 4
            "Series" -> 3
            else -> 4
        }

        suspend fun fetchStrategy(apiType: String, keyName: String, filterSeries: Boolean): List<Channel> = coroutineScope {
            val firstBody = get(buildReq(base, pType, mac, token, mapOf("type" to apiType, "action" to "get_ordered_list", keyName to category.categoryId, "p" to startPage.toString(), "JsHttpRequest" to "1-xml")))
            val (parsedFirstItems, total) = parseChannels(firstBody, apiType, base, portalType)
            val firstItems = sanitizeItems(parsedFirstItems, filterSeries)
            val pages = linkedMapOf<Int, List<Channel>>()
            val deduped = LinkedHashMap<String, Channel>()

            fun rebuildOrderedResults(): List<Channel> {
                deduped.clear()
                pages.toSortedMap().values.flatten().forEach { item ->
                    val key = item.id.ifBlank { item.movieId.ifBlank { item.name } }
                    if (key.isNotBlank() && !deduped.containsKey(key)) {
                        deduped[key] = item
                    }
                }
                return deduped.values.toList()
            }

            pages[startPage] = firstItems
            onPartialResult?.invoke(rebuildOrderedResults())
            if (firstItems.isEmpty() || parsedFirstItems.isEmpty() || parsedFirstItems.size >= total) return@coroutineScope deduped.values.toList()

            val totalPages = (total + parsedFirstItems.size - 1) / parsedFirstItems.size
            val semaphore = Semaphore(pageConcurrency)
            val remaining = ((startPage + 1) until totalPages).map { page ->
                async {
                    semaphore.withPermit {
                        try {
                            val b = get(buildReq(base, pType, mac, token, mapOf("type" to apiType, "action" to "get_ordered_list", keyName to category.categoryId, "p" to page.toString(), "JsHttpRequest" to "1-xml")))
                            parseChannels(b, apiType, base, portalType).first.let { pageItems ->
                                val filtered = sanitizeItems(pageItems, filterSeries)
                                if (filtered.isNotEmpty()) {
                                    synchronized(pages) {
                                        pages[page] = filtered
                                        onPartialResult?.invoke(rebuildOrderedResults())
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("StalkerClient", "Skipping page $page for $apiType category ${category.categoryId}: ${e.message}")
                        }
                    }
                }
            }
            remaining.awaitAll()
            rebuildOrderedResults()
        }

        var lastNonEmpty: List<Channel> = emptyList()
        for ((apiType, keyName, filterSeries) in strategies) {
            val result = runCatching { fetchStrategy(apiType, keyName, filterSeries) }.getOrDefault(emptyList())
            if (result.isNotEmpty()) return@coroutineScope result
            lastNonEmpty = result
        }
        lastNonEmpty
    }

    suspend fun search(
        token: String,
        url: String,
        mac: String,
        apiType: String,
        query: String,
        portalType: String
    ): List<Channel> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return@withContext emptyList()

        val base = url.trimEnd('/')
        val pType = if (portalType == "stalker") "stalker" else "mac"
        val startPage = if (portalType == "stalker") 1 else 0
        val deduped = linkedMapOf<String, Channel>()
        var page = startPage
        var totalItems = Int.MAX_VALUE

        while (deduped.size < totalItems) {
            val body = get(
                buildReq(
                    base,
                    pType,
                    mac,
                    token,
                    mapOf(
                        "type" to apiType,
                        "action" to "get_ordered_list",
                        "search" to normalizedQuery,
                        "p" to page.toString(),
                        "JsHttpRequest" to "1-xml"
                    )
                )
            )
            val (items, total) = parseChannels(body, apiType, base, portalType)
            if (items.isEmpty()) break
            totalItems = total
            items.forEach { item ->
                val key = item.id.ifBlank { item.seriesId.ifBlank { item.movieId.ifBlank { item.name } } }
                if (key.isNotBlank()) deduped.putIfAbsent(key, item)
            }
            if (items.size >= totalItems) break
            page += 1
        }

        deduped.values.toList()
    }

    private fun parseChannels(body: String, apiType: String, base: String = "", portalType: String = ""): Pair<List<Channel>, Int> {
        val root = safeParse(body) ?: return emptyList<Channel>() to 0
        val js = root.getAsJsonObject("js") ?: return emptyList<Channel>() to 0
        val arr = js.getAsJsonArray("data") ?: return emptyList<Channel>() to 0
        val total = js.get("total_items")?.asString?.toIntOrNull() ?: arr.size()
        val itemType = when (apiType) { "series" -> "series"; "vod" -> "vod"; else -> "channel" }
        return arr.map { el ->
            val o = el.asJsonObject
            val rawVideoId = o.get("video_id")?.asString.orEmpty()
            val rawMovieId = o.get("movie_id")?.asString.orEmpty()
            val resolvedMovieId = when (apiType) {
                "series" -> rawVideoId.ifBlank { rawMovieId }
                "vod" -> rawVideoId.ifBlank { rawMovieId.ifBlank { o.get("id")?.asString.orEmpty() } }
                else -> rawMovieId.ifBlank { o.get("id")?.asString.orEmpty() }
            }
            Channel(
                id = o.get("id")?.asString.orEmpty(), name = o.get("name")?.asString ?: o.get("title")?.asString.orEmpty(),
                cmd = o.get("cmd")?.asString.orEmpty(), itemType = itemType,
                movieId = resolvedMovieId,
                screenshotUri = resolveUrl(base, o.get("screenshot_uri")?.asString ?: o.get("pic")?.asString ?: o.get("cover")?.asString ?: o.get("poster")?.asString ?: o.get("logo")?.asString.orEmpty()),
                description = o.get("description")?.asString ?: o.get("descr")?.asString.orEmpty(),
                year = o.get("year")?.asString.orEmpty(), director = o.get("director")?.asString.orEmpty(),
                actors = o.get("actors")?.asString.orEmpty(), ratingImdb = o.get("rating_imdb")?.asString.orEmpty(),
                time = safeInt(o.get("time")).let { if (it == 0) safeInt(o.get("duration")) else it },
                genresStr = o.get("genres_str")?.asString.orEmpty(), added = o.get("added")?.asString.orEmpty(),
                isSeries = o.get("is_series")?.asBoolean ?: (o.get("is_series")?.asString == "1"),
                seasonNumber = safeInt(o.get("season_number")), seasonId = o.get("season_id")?.asString.orEmpty(),
                episodeNumber = safeInt(o.get("episode_number")).let { if (it == 0) safeInt(o.get("series_number")) else it },
                episodeId = o.get("episode_id")?.asString.orEmpty(), seriesId = o.get("series_id")?.asString.orEmpty().ifBlank { rawVideoId }
            )
        } to total
    }

    private fun parseOrderedData(body: String): Pair<List<com.google.gson.JsonObject>, Int> {
        val root = safeParse(body) ?: return emptyList<com.google.gson.JsonObject>() to 0
        val js = root.getAsJsonObject("js") ?: return emptyList<com.google.gson.JsonObject>() to 0
        val arr = js.getAsJsonArray("data") ?: return emptyList<com.google.gson.JsonObject>() to 0
        val total = js.get("total_items")?.asString?.toIntOrNull() ?: arr.size()
        return arr.mapNotNull { runCatching { it.asJsonObject }.getOrNull() } to total
    }

    private fun isTruthy(value: com.google.gson.JsonElement?): Boolean {
        val raw = value?.asString?.lowercase() ?: return false
        return raw == "1" || raw == "true" || raw == "yes"
    }

    private fun parseSeriesNumbers(value: com.google.gson.JsonElement?): List<Int> {
        if (value == null || value.isJsonNull) return emptyList()
        if (value.isJsonArray) {
            return value.asJsonArray.mapNotNull { el ->
                el?.asString?.toIntOrNull() ?: runCatching { el?.asInt }.getOrNull()
            }.distinct().sorted()
        }
        val raw = value.asString.orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",", ";", " ")
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
            .sorted()
    }

    // ── Seasons ──────────────────────────────────────────────
    suspend fun getSeasons(token: String, url: String, mac: String, seriesIds: List<String>, portalType: String): List<Channel> = withContext(Dispatchers.IO) {
        val base = url.trimEnd('/')
        val pType = if (portalType == "stalker") "stalker" else "mac"
        val startPage = if (portalType == "stalker") 1 else 0
        val requestTypes = listOf("series", "vod")
        for (seriesId in seriesIds.filter { it.isNotBlank() }.distinct()) {
            for (requestType in requestTypes) {
                val deduped = linkedMapOf<String, Channel>()
                var page = startPage
                var totalItems = Int.MAX_VALUE
                var fetchedItems = 0

                while (fetchedItems < totalItems) {
                    val body = get(buildReq(base, pType, mac, token, mapOf(
                        "type" to requestType,
                        "action" to "get_ordered_list",
                        "movie_id" to seriesId,
                        "season_id" to "0",
                        "episode_id" to "0",
                        "p" to page.toString(),
                        "JsHttpRequest" to "1-xml"
                    )))
                    val (items, total) = parseOrderedData(body)
                    if (items.isEmpty()) break
                    totalItems = total
                    fetchedItems += items.size
                    items.forEach { item ->
                        val rawSeasonId = item.get("season_id")?.asString ?: item.get("id")?.asString.orEmpty()
                        val itemName = item.get("name")?.asString.orEmpty()
                        val seasonNumbers = parseSeriesNumbers(item.get("series"))
                        val seasonFromField = safeInt(item.get("season_number"))
                        val seasonFromName = Regex("(?i)(season|stagione|saison|temporada)\\s*(\\d{1,2})")
                            .find(itemName)
                            ?.groupValues
                            ?.getOrNull(2)
                            ?.toIntOrNull()
                            ?: 0
                        val seasonNumber = sequenceOf(
                            seasonFromField.takeIf { it > 0 },
                            extractSeasonNumber(rawSeasonId).takeIf { it > 0 },
                            seasonFromName.takeIf { it > 0 }
                        ).filterNotNull().firstOrNull() ?: 0
                        val resolvedSeasonId = rawSeasonId.ifBlank {
                            if (seasonNumber > 0) "season$seasonNumber" else ""
                        }
                        if (resolvedSeasonId.isBlank()) return@forEach
                        val isSeason = isTruthy(item.get("is_season")) || looksLikeSeasonId(resolvedSeasonId) || seasonNumbers.isNotEmpty() || seasonNumber > 0
                        if (!isSeason) return@forEach
                        val rawMovieId = item.get("video_id")?.asString ?: item.get("movie_id")?.asString.orEmpty()
                        val movieId = rawMovieId.takeUnless { it.isBlank() || it == resolvedSeasonId } ?: seriesId
                        val seasonName = item.get("name")?.asString
                            ?: if (seasonNumber > 0) "Season $seasonNumber" else resolvedSeasonId
                        deduped.putIfAbsent(
                            resolvedSeasonId,
                            Channel(
                                id = resolvedSeasonId,
                                name = seasonName,
                                cmd = item.get("cmd")?.asString.orEmpty(),
                                itemType = "season",
                                movieId = movieId,
                                seasonId = resolvedSeasonId,
                                seasonNumber = seasonNumber,
                                seriesId = seriesId,
                                screenshotUri = resolveUrl(base, item.get("screenshot_uri")?.asString ?: item.get("pic")?.asString ?: item.get("cover")?.asString ?: item.get("poster")?.asString.orEmpty()),
                                posterPath = resolveUrl(base, item.get("cover")?.asString ?: item.get("poster")?.asString.orEmpty()),
                                seriesNumbers = seasonNumbers
                            )
                        )
                    }
                    if (fetchedItems >= totalItems) break
                    page += 1
                }

                if (deduped.isNotEmpty()) {
                    return@withContext deduped.values.toList()
                }
            }
        }
        emptyList()
    }

    private fun extractSeasonNumber(rawSeasonId: String): Int {
        return Regex("[Ss]eason\\s*[-_]?\\s*(\\d+)").find(rawSeasonId)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("season(\\d+)", RegexOption.IGNORE_CASE).find(rawSeasonId)?.groupValues?.get(1)?.toIntOrNull()
            ?: rawSeasonId.split(":").getOrNull(1)?.toIntOrNull()
            ?: rawSeasonId.toIntOrNull()
            ?: 0
    }

    private fun looksLikeSeasonId(rawSeasonId: String): Boolean {
        if (rawSeasonId.isBlank()) return false
        if (Regex("^season\\d+$", RegexOption.IGNORE_CASE).matches(rawSeasonId)) return true
        if (Regex("^\\d+:\\d+$").matches(rawSeasonId)) return true
        return false
    }

    // ── Episodes ─────────────────────────────────────────────
    suspend fun getEpisodes(token: String?, url: String, mac: String, seriesIds: List<String>, seasonId: String, seasonNumber: Int, portalType: String): List<Channel> = withContext(Dispatchers.IO) {
        val base = url.trimEnd('/')
        val resolvedSeasonId = seasonId.ifBlank { seasonNumber.toString() }
        val pType = if (portalType == "stalker") "stalker" else "mac"
        val startPage = if (portalType == "stalker") 1 else 0
        val requestTypes = listOf("vod", "series")
        for (seriesId in seriesIds.filter { it.isNotBlank() }.distinct()) {
            for (requestType in requestTypes) {
                val deduped = linkedMapOf<String, Channel>()
                var page = startPage
                var totalItems = Int.MAX_VALUE
                var fetchedItems = 0

                while (fetchedItems < totalItems) {
                    val body = get(buildReq(base, pType, mac, token, mapOf(
                        "type" to requestType,
                        "action" to "get_ordered_list",
                        "movie_id" to seriesId,
                        "season_id" to resolvedSeasonId,
                        "episode_id" to "0",
                        "p" to page.toString(),
                        "JsHttpRequest" to "1-xml"
                    )))
                    val (items, total) = parseOrderedData(body)
                    if (items.isEmpty()) break
                    totalItems = total
                    fetchedItems += items.size
                    items.forEach { item ->
                        val episodeId = item.get("id")?.asString.orEmpty()
                        if (episodeId.isBlank()) return@forEach
                        deduped.putIfAbsent(
                            episodeId,
                            Channel(
                                id = episodeId,
                                name = item.get("name")?.asString ?: item.get("title")?.asString.orEmpty(),
                                cmd = item.get("cmd")?.asString.orEmpty(),
                                itemType = "episode",
                                movieId = seriesId,
                                seasonId = resolvedSeasonId,
                                episodeId = episodeId,
                                episodeNumber = safeInt(item.get("series_number")).let { if (it == 0) safeInt(item.get("episode_number")) else it },
                                seasonNumber = seasonNumber,
                                seriesId = seriesId,
                                screenshotUri = resolveUrl(base, item.get("screenshot_uri")?.asString ?: item.get("pic")?.asString ?: item.get("cover")?.asString ?: item.get("poster")?.asString.orEmpty()),
                                description = item.get("description")?.asString ?: item.get("descr")?.asString.orEmpty(),
                                added = item.get("added")?.asString.orEmpty(),
                                time = safeInt(item.get("time")).let { if (it == 0) safeInt(item.get("duration")) else it }
                            )
                        )
                    }
                    if (fetchedItems >= totalItems) break
                    page += 1
                }

                if (deduped.isNotEmpty()) {
                    return@withContext deduped.values.toList()
                }
            }
        }
        emptyList()
    }

    // ── Stream URL (replicates stalker.py exactly) ────────────
    suspend fun getStreamUrl(token: String, url: String, mac: String, item: Channel, portalType: String): String = withContext(Dispatchers.IO) {
        if (portalType == "stalker") return@withContext stalkerStreamUrl(url, mac, item)

        val base = url.trimEnd('/')
        val cmd = item.cmd

        if (item.itemType == "episode") {
            if (cmd.isNotBlank()) {
                val body = get(buildReq(base, "mac", mac, token, mapOf("type" to "vod", "action" to "create_link", "cmd" to cmd, "series" to (item.episodeNumber.toString()), "JsHttpRequest" to "1-xml")))
                val js = safeParse(body)?.getAsJsonObject("js") ?: throw IOException("Invalid create_link response")
                val raw = js.get("cmd")?.asString ?: js.get("url")?.asString ?: throw IOException("No stream URL")
                return@withContext raw.removePrefix("ffmpeg ").trim()
            }
            val movieId = item.movieId.ifBlank { item.seriesId }.ifBlank { item.id.split(":").firstOrNull() ?: "" }
            if (movieId.isNotBlank()) {
                try {
                    val season = item.seasonNumber.toString()
                    val epId = item.episodeId.ifBlank { item.id }
                    val listBody = get(buildReq(base, "mac", mac, token, mapOf("type" to "vod", "action" to "get_ordered_list", "movie_id" to movieId, "season_id" to season, "episode_id" to epId, "JsHttpRequest" to "1-xml")))
                    val listRoot = safeParse(listBody)?.getAsJsonObject("js")
                    val listData = listRoot?.getAsJsonArray("data")
                    if (listData != null && listData.size() > 0) {
                        val epInfo = listData[0].asJsonObject
                        val streamId = epInfo.get("id")?.asString ?: movieId
                        val streamCmd = epInfo.get("cmd")?.asString?.takeIf { it.isNotBlank() }
                        val cmdToUse = streamCmd ?: "/media/file_$streamId.mpg"
                        val createParams = mutableMapOf("type" to "vod", "action" to "create_link", "cmd" to cmdToUse, "JsHttpRequest" to "1-xml")
                        if (item.episodeNumber > 0) createParams["series"] = item.episodeNumber.toString()
                        val createBody = get(buildReq(base, "mac", mac, token, createParams))
                        val createJs = safeParse(createBody)?.getAsJsonObject("js") ?: throw IOException("Invalid create_link response")
                        val rawUrl = createJs.get("cmd")?.asString ?: createJs.get("url")?.asString ?: throw IOException("No stream URL")
                        return@withContext rawUrl.removePrefix("ffmpeg ").trim()
                    }
                } catch (_: Exception) { }
                try {
                    val listBody = get(buildReq(base, "mac", mac, token, mapOf("type" to "vod", "action" to "get_ordered_list", "movie_id" to movieId, "JsHttpRequest" to "1-xml")))
                    val listRoot = safeParse(listBody)?.getAsJsonObject("js")
                    val listData = listRoot?.getAsJsonArray("data")
                    if (listData != null && listData.size() > 0) {
                        val streamId = listData[0].asJsonObject.get("id")?.asString ?: movieId
                        val createBody = get(buildReq(base, "mac", mac, token, mapOf("type" to "vod", "action" to "create_link", "cmd" to "/media/file_$streamId.mpg", "JsHttpRequest" to "1-xml")))
                        val createJs = safeParse(createBody)?.getAsJsonObject("js") ?: throw IOException("Invalid create_link response")
                        val rawUrl = createJs.get("cmd")?.asString ?: createJs.get("url")?.asString ?: throw IOException("No stream URL")
                        return@withContext rawUrl.removePrefix("ffmpeg ").trim()
                    }
                } catch (_: Exception) { }
            }
            throw IOException("No command or movie_id for episode: ${item.name}")
        }

        if (item.itemType == "channel") {
            if (cmd.isBlank()) throw IOException("No command for channel: ${item.name}")
            val needsLink = cmd.contains("/ch/") && cmd.endsWith("_")
            if (needsLink) {
                val body = get(buildReq(base, "mac", mac, token, mapOf("type" to "itv", "action" to "create_link", "cmd" to cmd, "JsHttpRequest" to "1-xml")))
                val js = safeParse(body)?.getAsJsonObject("js") ?: throw IOException("Invalid create_link response")
                return@withContext (js.get("cmd")?.asString ?: js.get("url")?.asString)?.removePrefix("ffmpeg ")?.trim() ?: throw IOException("No stream URL")
            }
            return@withContext cmd.removePrefix("ffmpeg ").trim()
        }

        if (item.itemType == "vod") {
            if (cmd.isNotBlank()) {
                val body = get(buildReq(base, "mac", mac, token, mapOf("type" to "vod", "action" to "create_link", "cmd" to cmd, "JsHttpRequest" to "1-xml")))
                val js = safeParse(body)?.getAsJsonObject("js") ?: throw IOException("Invalid create_link response")
                val raw = (js.get("cmd")?.asString ?: js.get("url")?.asString)?.trim() ?: throw IOException("No stream URL")
                val cleaned = raw.split(" ").drop(1).joinToString(" ").ifBlank { raw.removePrefix("ffmpeg ").trim() }
                return@withContext cleaned
            }
            val movieId = item.movieId.ifBlank { item.id }
            if (movieId.isNotBlank()) {
                try {
                    val listBody = get(buildReq(base, "mac", mac, token, mapOf("type" to "vod", "action" to "get_ordered_list", "movie_id" to movieId, "JsHttpRequest" to "1-xml")))
                    val listRoot = safeParse(listBody)?.getAsJsonObject("js")
                    val listData = listRoot?.getAsJsonArray("data")
                    val streamId = if (listData != null && listData.size() > 0) listData[0].asJsonObject.get("id")?.asString ?: movieId else movieId
                    val createBody = get(buildReq(base, "mac", mac, token, mapOf("type" to "vod", "action" to "create_link", "cmd" to "/media/file_$streamId.mpg", "JsHttpRequest" to "1-xml")))
                    val createJs = safeParse(createBody)?.getAsJsonObject("js") ?: throw IOException("Invalid create_link response")
                    val rawUrl = createJs.get("cmd")?.asString ?: createJs.get("url")?.asString ?: throw IOException("No stream URL")
                    return@withContext rawUrl.removePrefix("ffmpeg ").trim()
                } catch (_: Exception) { }
            }
            throw IOException("No command for VOD item: ${item.name}")
        }

        throw IOException("Unknown item type: ${item.itemType}")
    }

    private suspend fun stalkerStreamUrl(url: String, mac: String, item: Channel): String = withContext(Dispatchers.IO) {
        val base = url.trimEnd('/')
        if (item.itemType == "episode") {
            val movieId = item.movieId.ifBlank { item.seriesId }
            val seasonId = item.seasonId.ifBlank { item.seasonNumber.toString() }
            val episodeId = item.episodeId.ifBlank { item.id }
            val listBody = get(buildReq(base, "stalker", mac, null, mapOf("action" to "get_ordered_list", "type" to "vod", "movie_id" to movieId, "season_id" to seasonId, "episode_id" to episodeId, "JsHttpRequest" to "1-xml")))
            val listJs = safeParse(listBody)?.getAsJsonObject("js") ?: throw IOException("Invalid ordered list response")
            val data = listJs.getAsJsonArray("data")
            val streamId = if (data != null && data.size() > 0) data[0].asJsonObject.get("id")?.asString ?: episodeId else episodeId
            val streamCmd = "/media/file_$streamId.mpg"
            val createBody = get(buildReq(base, "stalker", mac, null, mapOf("action" to "create_link", "type" to "vod", "cmd" to streamCmd, "JsHttpRequest" to "1-xml")))
            val createJs = safeParse(createBody)?.getAsJsonObject("js") ?: throw IOException("Invalid create_link response")
            val result = (createJs.get("cmd")?.asString ?: createJs.get("url")?.asString)?.removePrefix("ffmpeg ")?.trim() ?: throw IOException("No stream URL")
            return@withContext if (result.startsWith("http")) result else "${base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore("/")}/vod4/${result.trimStart('/')}"
        }
        if (item.itemType == "vod") {
            if (item.cmd.isNotBlank()) {
                val body = get(buildReq(base, "stalker", mac, null, mapOf("type" to "vod", "action" to "create_link", "cmd" to item.cmd, "JsHttpRequest" to "1-xml")))
                val js = safeParse(body)?.getAsJsonObject("js") ?: throw IOException("Invalid response")
                val streamUrl = js.get("cmd")?.asString ?: js.get("url")?.asString ?: throw IOException("No stream URL")
                val cleaned = streamUrl.removePrefix("ffmpeg ").trim()
                return@withContext if (cleaned.startsWith("http")) cleaned else "${base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore("/")}/vod4/${cleaned.trimStart('/')}"
            }
            val movieId = item.movieId.ifBlank { item.id }
            if (movieId.isNotBlank()) {
                val listBody = get(buildReq(base, "stalker", mac, null, mapOf("type" to "vod", "action" to "get_ordered_list", "movie_id" to movieId, "JsHttpRequest" to "1-xml")))
                val listRoot = safeParse(listBody)?.getAsJsonObject("js") ?: throw IOException("Invalid ordered list response")
                val listData = listRoot.getAsJsonArray("data") ?: throw IOException("No data in ordered list")
                val streamId = if (listData.size() > 0) listData[0].asJsonObject.get("id")?.asString ?: movieId else movieId
                val streamCmd = "/media/file_$streamId.mpg"
                val createBody = get(buildReq(base, "stalker", mac, null, mapOf("action" to "create_link", "type" to "vod", "cmd" to streamCmd, "JsHttpRequest" to "1-xml")))
                val createJs = safeParse(createBody)?.getAsJsonObject("js") ?: throw IOException("Invalid create_link response")
                val result = (createJs.get("cmd")?.asString ?: createJs.get("url")?.asString)?.removePrefix("ffmpeg ")?.trim() ?: throw IOException("No stream URL")
                return@withContext if (result.startsWith("http")) result else "${base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore("/")}/vod4/${result.trimStart('/')}"
            }
            throw IOException("No command or movie_id for VOD: ${item.name}")
        }
        if (item.cmd.isNotBlank()) {
            val body = get(buildReq(base, "stalker", mac, null, mapOf("type" to "itv", "action" to "create_link", "cmd" to item.cmd, "JsHttpRequest" to "1-xml")))
            val js = safeParse(body)?.getAsJsonObject("js") ?: throw IOException("Invalid response")
            val streamUrl = js.get("cmd")?.asString ?: js.get("url")?.asString ?: throw IOException("No stream URL")
            val cleaned = streamUrl.removePrefix("ffmpeg ").trim()
            return@withContext if (cleaned.startsWith("http")) cleaned else "${base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore("/")}/vod4/${cleaned.trimStart('/')}"
        }
        throw IOException("No command for item: ${item.name}")
    }

    // ── Account Info ─────────────────────────────────────────
    suspend fun getAccountInfo(token: String, url: String, mac: String, portalType: String): AccountInfo = withContext(Dispatchers.IO) {
        val pType = if (portalType == "stalker") "stalker" else "mac"
        val body = get(buildReq(url.trimEnd('/'), pType, mac, token, mapOf("type" to "account_info", "action" to "get_main_info", "JsHttpRequest" to "1-xml")))
        val js = safeParse(body)?.getAsJsonObject("js") ?: return@withContext AccountInfo()
        AccountInfo(
            name = js.get("fname")?.asString.orEmpty(),
            serverUrl = url.trimEnd('/'),
            mac = js.get("mac")?.asString ?: js.get("device_mac")?.asString ?: js.get("mac_address")?.asString ?: mac,
            maxOnline = safeInt(js.get("max_online")), parentalPassword = js.get("parent_password")?.asString.orEmpty(),
            expireDate = js.get("expire_billing_date")?.asString
                ?: js.get("expire_date")?.asString
                ?: js.get("exp_date")?.asString
                ?: js.get("phone")?.asString
                ?: js.get("end_date")?.asString
                .orEmpty(),
            expireBillingDate = js.get("expire_billing_date")?.asString.orEmpty(), isStalker = portalType == "stalker"
        )
    }

}
