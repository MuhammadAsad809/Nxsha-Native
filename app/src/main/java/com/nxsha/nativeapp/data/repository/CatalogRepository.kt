package com.nxsha.nativeapp.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.nxsha.nativeapp.data.model.Episode
import com.nxsha.nativeapp.data.model.MediaItem
import com.nxsha.nativeapp.data.model.Season
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class CatalogRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // TMDB API key for client-side direct queries
    private val apiKey = "8476a7ab80ad76f0936744df0430e67c"
    private val baseUrl = "https://api.themoviedb.org/3"

    suspend fun getTrendingMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/trending/movie/day?api_key=$apiKey&language=en-US"
        fetchMediaList(url, "movie")
    }

    suspend fun getTrendingTvShows(): List<MediaItem> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/trending/tv/day?api_key=$apiKey&language=en-US"
        fetchMediaList(url, "tv")
    }

    suspend fun getPopularAnime(): List<MediaItem> = withContext(Dispatchers.IO) {
        // Animation genre ID: 16, original language Japanese: ja
        val url = "$baseUrl/discover/tv?api_key=$apiKey&with_genres=16&with_original_language=ja&sort_by=popularity.desc"
        fetchMediaList(url, "tv")
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/search/multi?api_key=$apiKey&query=$encoded&include_adult=false"
        fetchMediaList(url, "multi")
    }

    suspend fun getTvSeasons(tvId: String): List<Season> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/tv/$tvId?api_key=$apiKey&language=en-US"
        val seasons = mutableListOf<Season>()
        try {
            val req = Request.Builder().url(url).build()
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext emptyList()
            val json = gson.fromJson(body, JsonObject::class.java)
            val arr = json.getAsJsonArray("seasons")
            arr?.forEach { el ->
                val obj = el.asJsonObject
                val seasonNum = obj.get("season_number")?.asInt ?: 0
                if (seasonNum > 0) { // filter out specials (season 0)
                    seasons.add(
                        Season(
                            id = obj.get("id")?.asString ?: "$seasonNum",
                            seasonNumber = seasonNum,
                            name = obj.get("name")?.asString ?: "Season $seasonNum",
                            episodeCount = obj.get("episode_count")?.asInt ?: 0
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        seasons
    }

    suspend fun getSeasonEpisodes(tvId: String, seasonNumber: Int): List<Episode> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/tv/$tvId/season/$seasonNumber?api_key=$apiKey&language=en-US"
        val episodes = mutableListOf<Episode>()
        try {
            val req = Request.Builder().url(url).build()
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext emptyList()
            val json = gson.fromJson(body, JsonObject::class.java)
            val arr = json.getAsJsonArray("episodes")
            arr?.forEach { el ->
                val obj = el.asJsonObject
                episodes.add(
                    Episode(
                        id = obj.get("id")?.asString ?: "",
                        name = obj.get("name")?.asString ?: "Episode",
                        episodeNumber = obj.get("episode_number")?.asInt ?: 1,
                        seasonNumber = obj.get("season_number")?.asInt ?: seasonNumber,
                        overview = obj.get("overview")?.asString ?: "",
                        stillPath = obj.get("still_path")?.asString
                    )
                )
            }
        } catch (_: Exception) {}
        episodes
    }

    private fun fetchMediaList(url: String, defaultType: String): List<MediaItem> {
        return try {
            val req = Request.Builder().url(url).build()
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return emptyList()
            val json = gson.fromJson(body, JsonObject::class.java)
            val results = json.getAsJsonArray("results") ?: return emptyList()

            val list = mutableListOf<MediaItem>()
            for (el in results) {
                val obj = el.asJsonObject
                val id = obj.get("id")?.asString ?: continue
                val title = obj.get("title")?.asString
                val name = obj.get("name")?.asString
                val overview = obj.get("overview")?.asString ?: ""
                val poster = obj.get("poster_path")?.asString
                val backdrop = obj.get("backdrop_path")?.asString
                val vote = obj.get("vote_average")?.asDouble ?: 0.0
                val releaseDate = obj.get("release_date")?.asString
                val firstAirDate = obj.get("first_air_date")?.asString
                val type = obj.get("media_type")?.asString ?: defaultType

                // Filter out entries without poster or people results
                if (poster != null && type != "person") {
                    list.add(
                        MediaItem(
                            id = id,
                            title = title,
                            name = name,
                            overview = overview,
                            posterPath = poster,
                            backdropPath = backdrop,
                            voteAverage = vote,
                            releaseDate = releaseDate,
                            firstAirDate = firstAirDate,
                            mediaType = if (type == "multi") (if (title != null) "movie" else "tv") else type
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
