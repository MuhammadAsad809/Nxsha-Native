package com.nxsha.nativeapp.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MediaItem(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("overview") val overview: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("media_type") val mediaType: String = "movie"
) : Serializable {
    val displayTitle: String
        get() = title ?: name ?: "Untitled"

    val displayDate: String
        get() = releaseDate ?: firstAirDate ?: ""

    val posterUrl: String
        get() = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else ""

    val backdropUrl: String
        get() = if (backdropPath != null) "https://image.tmdb.org/t/p/w1280$backdropPath" else ""
}

data class Season(
    @SerializedName("id") val id: String,
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("name") val name: String,
    @SerializedName("episode_count") val episodeCount: Int = 0
) : Serializable

data class Episode(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("episode_number") val episodeNumber: Int,
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("overview") val overview: String = "",
    @SerializedName("still_path") val stillPath: String? = null
) : Serializable {
    val stillUrl: String
        get() = if (stillPath != null) "https://image.tmdb.org/t/p/w500$stillPath" else ""
}

data class Server(
    val name: String,
    val providerId: String,
    val sources: List<StreamSource>
) : Serializable

data class StreamSource(
    val serverName: String,
    val quality: String, // "360p", "480p", "720p", "1080p", "4K", "Auto"
    val url: String,
    val isM3u8: Boolean = false,
    val headers: Map<String, String> = emptyMap()
) : Serializable

data class SubtitleTrack(
    val language: String,
    val label: String,
    val url: String
) : Serializable
