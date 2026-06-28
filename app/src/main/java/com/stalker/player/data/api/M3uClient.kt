package com.stalker.player.data.api

import android.content.Context
import android.net.Uri
import com.stalker.player.data.model.Category
import com.stalker.player.data.model.Channel
import com.stalker.player.data.model.Strings
import com.stalker.player.data.model.XtreamCredential
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

class M3uClient(private val context: Context) {
    companion object {
        private const val MAX_CHANNELS_SOFT_LIMIT = 400_000

        // UA usato per la riproduzione/risoluzione degli stream. Alcuni relinker
        // (es. RAI) rispondono 403 allo UA "IPTVSmartersPro" ma accettano uno UA
        // da browser/player. Usato sia qui che in ExoPlayer per coerenza del token.
        const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:151.0) Gecko/20100101 Firefox/151.0"
    }

    // USER-AGENT "IPTVSmartersPro" PER ENTRARE NELLE WHITELIST DEI SERVER (solo se la
    // richiesta non specifica gia' un proprio User-Agent, vedi resolveFinalUrl).
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder().header("Accept", "*/*")
            if (original.header("User-Agent") == null) {
                builder.header("User-Agent", "IPTVSmartersPro")
            }
            chain.proceed(builder.build())
        }
        .build()

    data class M3uPlaylist(
        val categories: List<Category>,
        val channelsByCategory: Map<String, List<Channel>>
    )

    // Aborta tutte le richieste HTTP in volo (usato per annullare una connessione).
    fun cancelAll() = http.dispatcher.cancelAll()

    suspend fun load(source: String): M3uPlaylist {
        return when {
            source.startsWith("content://") -> {
                val uri = Uri.parse(source)
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException(Strings["openM3uError"])
                input.bufferedReader().use { reader -> parseStreaming(reader) }
            }
            source.startsWith("http://") || source.startsWith("https://") -> {
                val request = Request.Builder().url(source).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("${Strings["loadingM3uError"]} (HTTP ${response.code})")
                    }
                    val body = response.body ?: throw IOException(Strings["loadingM3uError"])
                    body.charStream().buffered().use { reader -> parseStreaming(reader) }
                }
            }
            else -> {
                context.openFileInput(source).bufferedReader().use { reader -> parseStreaming(reader) }
            }
        }
    }

    /**
     * Alcuni link (es. RAI relinker) non sono lo stream vero ma un redirect 302 verso
     * l'URL reale, che spesso e' un .m3u8 (HLS) con token. ExoPlayer deduce il tipo di
     * contenuto dall'URL ORIGINALE (qui un .htm con content-type text/html) e quindi
     * non riconosce l'HLS. Risolviamo noi il redirect e restituiamo l'URL finale, cosi'
     * il player lo tratta correttamente. In caso di errore si torna all'URL originale.
     */
    suspend fun resolveFinalUrl(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return url
        val path = Uri.parse(url).path?.lowercase().orEmpty()
        val looksLikeMedia = listOf(".m3u8", ".m3u", ".ts", ".mp4", ".mkv", ".mpd", ".avi", ".mov", ".webm")
            .any { path.endsWith(it) }
        // Se l'URL ha gia' un'estensione media nota non serve risolvere nulla.
        if (looksLikeMedia) return url
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", STREAM_USER_AGENT)
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (_: Exception) {
            url
        }
    }

    fun extractXtreamCredential(source: String): XtreamCredential? {
        if (!source.startsWith("http://") && !source.startsWith("https://")) return null

        val uri = Uri.parse(source)
        val username = uri.getQueryParameter("username").orEmpty()
        val password = uri.getQueryParameter("password").orEmpty()
        if (username.isBlank() || password.isBlank()) return null

        val pathSegments = uri.pathSegments ?: emptyList()
        if (pathSegments.isEmpty()) return null

        val lastSegment = pathSegments.lastOrNull().orEmpty()
        if (lastSegment != "get.php" && lastSegment != "player_api.php") return null

        val basePath = pathSegments.dropLast(1).joinToString("/")
        val authority = uri.authority ?: return null
        val serverUrl = buildString {
            append(uri.scheme ?: return null)
            append("://")
            append(authority)
            if (basePath.isNotBlank()) {
                append("/")
                append(basePath)
            }
        }

        return XtreamCredential(
            serverUrl = serverUrl,
            username = username,
            password = password
        )
    }

    private fun parseStreaming(reader: BufferedReader): M3uPlaylist {
        val channelsByCategory = linkedMapOf<String, MutableList<Channel>>()
        val categoryTypes = mutableMapOf<String, String>() 
        var pendingMeta: Map<String, String> = emptyMap()
        var pendingName = ""
        var parsedChannels = 0
        var seenNonEmptyLine = false
        var seenPlaylistHeader = false
        var seenHtmlEvidence = false
        var seenNullPayload = false

        fun addChannel(url: String) {
            if (url.isBlank()) return
            val group = pendingMeta["group-title"].orEmpty().ifBlank { Strings["allChannels"] }
            val logo = pendingMeta["tvg-logo"].orEmpty()
            val id = pendingMeta["tvg-id"].orEmpty().ifBlank { pendingName.ifBlank { url } }
            val name = pendingName.ifBlank { pendingMeta["tvg-name"].orEmpty().ifBlank { id } }

            val urlLower = url.lowercase()
            val groupLower = group.lowercase()

            var detectedCatType = "IPTV"
            var detectedItemType = "channel"

            when {
                urlLower.contains("/series/") -> {
                    detectedCatType = "Series"
                    detectedItemType = "series"
                }
                urlLower.contains("/movie/") || 
                urlLower.endsWith(".mp4") || urlLower.endsWith(".mkv") || urlLower.endsWith(".avi") || 
                groupLower.contains("vod") || groupLower.contains("film") || groupLower.contains("movie") -> {
                    detectedCatType = "VOD"
                    detectedItemType = "movie"
                }
            }

            if (!categoryTypes.containsKey(group) || detectedCatType != "IPTV") {
                categoryTypes[group] = detectedCatType
            }

            channelsByCategory.getOrPut(group) { mutableListOf() }.add(
                Channel(
                    id = id,
                    name = name,
                    itemType = detectedItemType,
                    categoryType = detectedCatType,
                    screenshotUri = logo,
                    streamUrl = url
                )
            )
            parsedChannels++
            if (parsedChannels >= MAX_CHANNELS_SOFT_LIMIT) {
                throw IOException("Playlist troppo grande (${parsedChannels} canali). Limite sicurezza: $MAX_CHANNELS_SOFT_LIMIT")
            }
        }

        reader.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isNotEmpty()) {
                seenNonEmptyLine = true
                if (!seenPlaylistHeader && line.equals("#EXTM3U", ignoreCase = true)) {
                    seenPlaylistHeader = true
                }
                if (line.equals("null", ignoreCase = true)) {
                    seenNullPayload = true
                }
                val lower = line.lowercase()
                if (lower.contains("<!doctype html>") || lower.contains("<html") || lower.contains("<body")) {
                    seenHtmlEvidence = true
                }
            }
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingMeta = parseExtInf(line)
                    pendingName = line.substringAfter(",", "").trim()
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    addChannel(line)
                    pendingMeta = emptyMap()
                    pendingName = ""
                }
            }
        }

        if (!seenNonEmptyLine || seenNullPayload) {
            throw IOException("Il server ha rifiutato l'accesso (Risposta: null o vuota). Controlla i dati o la scadenza.")
        }
        if (seenHtmlEvidence && parsedChannels == 0) {
            throw IOException("Il server ha bloccato la richiesta (Risposta HTML ricevuta).")
        }
        if (!seenPlaylistHeader && parsedChannels == 0) {
            throw IOException("Formato playlist non valido o playlist vuota.")
        }
        
        val categories = channelsByCategory.keys.map { name ->
            Category(
                name = name, 
                categoryType = categoryTypes[name] ?: "IPTV", 
                categoryId = name
            )
        }
        
        return M3uPlaylist(categories = categories, channelsByCategory = channelsByCategory)
    }

    private fun parseExtInf(line: String): Map<String, String> {
        val attrs = linkedMapOf<String, String>()
        val regex = Regex("""([A-Za-z0-9\-_]+)="([^"]*)"""")
        regex.findAll(line).forEach { match ->
            attrs[match.groupValues[1]] = match.groupValues[2]
        }
        return attrs
    }
}
