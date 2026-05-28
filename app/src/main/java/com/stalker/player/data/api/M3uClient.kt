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
    }

    // USER-AGENT "IPTVSmartersPro" PER ENTRARE NELLE WHITELIST DEI SERVER
    private val http = OkHttpClient.Builder()
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

    data class M3uPlaylist(
        val categories: List<Category>,
        val channelsByCategory: Map<String, List<Channel>>
    )

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
