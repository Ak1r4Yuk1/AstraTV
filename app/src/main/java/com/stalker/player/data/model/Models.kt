package com.stalker.player.data.model

data class Profile(
    val name: String,
    val url: String,
    val mac: String = "",
    val username: String = "",
    val password: String = "",
    val type: String = "mac"  // "mac", "stalker", "xtream"
)

data class Category(
    val name: String,
    val categoryType: String,
    val categoryId: String,
    val screenshotUri: String = ""
)

data class Channel(
    val id: String = "",
    val name: String = "",
    val cmd: String = "",
    val itemType: String = "channel", // "channel", "vod", "series", "season", "episode", "category"
    val categoryType: String = "",    // "IPTV", "VOD", "Series" - only for category items
    val movieId: String = "",
    val seasonId: String = "",
    val episodeId: String = "",
    val episodeNumber: Int = 0,
    val seasonNumber: Int = 0,
    val seriesId: String = "",
    val screenshotUri: String = "",
    val description: String = "",
    val year: String = "",
    val releaseDate: String = "",
    val director: String = "",
    val actors: String = "",
    val ratingImdb: String = "",
    val time: Int = 0,
    val durationText: String = "",
    val age: String = "",
    val country: String = "",
    val genresStr: String = "",
    val trailerUrl: String = "",
    val tmdbId: String = "",
    val backdropPath: String = "",
    val added: String = "",
    val isSeries: Boolean = false,
    val posterPath: String = "",
    val parentPoster: String = "",
    val streamUrl: String = "",
    val streamFormat: String = "",
    val seriesNumbers: List<Int> = emptyList()
)

data class EpgItem(
    val name: String = "",
    val startTs: Long = 0,
    val endTs: Long = 0,
    val descr: String = "",
    val category: String = "",
    val durationMin: Int = 0
)

data class AccountInfo(
    val name: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val mac: String = "",
    val maxOnline: Int = 0,
    val parentalPassword: String = "",
    val expireDate: String = "",
    val expireBillingDate: String = "",
    val isStalker: Boolean = false
)

data class PlaylistData(
    val categories: Map<String, List<Category>> = emptyMap(),
    val accountInfo: AccountInfo = AccountInfo()
)

data class NavState(
    val category: Category? = null,
    val view: String = "categories", // "categories", "channels", "seasons", "episodes"
    val channels: List<Channel> = emptyList(),
    val scrollPosition: Int = 0
)

data class XtreamCredential(
    val serverUrl: String,
    val username: String,
    val password: String
)
