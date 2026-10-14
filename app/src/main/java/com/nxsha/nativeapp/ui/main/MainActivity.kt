package com.nxsha.nativeapp.ui.main

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nxsha.nativeapp.NxshaApplication
import com.nxsha.nativeapp.R
import com.nxsha.nativeapp.data.local.WatchHistoryEntity
import com.nxsha.nativeapp.data.local.WatchlistEntity
import com.nxsha.nativeapp.data.model.Episode
import com.nxsha.nativeapp.data.model.MediaItem
import com.nxsha.nativeapp.data.model.Season
import com.nxsha.nativeapp.data.repository.CatalogRepository
import com.nxsha.nativeapp.databinding.ActivityMainBinding
import com.nxsha.nativeapp.ui.player.PlayerActivity
import com.nxsha.nativeapp.ui.watchlist.WatchlistFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), WatchlistFragment.WatchlistHost {

    private lateinit var binding: ActivityMainBinding
    private val repository = CatalogRepository()
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSearch()
        setupSwipeRefresh()
        setupBottomNavigation()
        loadLocalHistory()
        loadCatalogFeeds()
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    binding.fragmentContainer.visibility = View.GONE
                    binding.swipeRefresh.visibility = View.VISIBLE
                    true
                }
                R.id.nav_watchlist -> {
                    binding.swipeRefresh.visibility = View.GONE
                    binding.fragmentContainer.visibility = View.VISIBLE

                    val fragment = supportFragmentManager.findFragmentByTag("WATCHLIST_FRAGMENT")
                        ?: WatchlistFragment()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, fragment, "WATCHLIST_FRAGMENT")
                        .commit()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim() ?: ""
                searchJob?.cancel()
                if (q.length >= 2) {
                    searchJob = lifecycleScope.launch {
                        delay(400) // debounce
                        performSearch(q)
                    }
                } else if (q.isEmpty()) {
                    binding.rvSearchResults.visibility = View.GONE
                    binding.layoutFeeds.visibility = View.VISIBLE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadCatalogFeeds()
        }
    }

    private fun loadLocalHistory() {
        lifecycleScope.launch {
            val dao = NxshaApplication.instance.database.watchHistoryDao()
            dao.getAllHistory().collect { historyList ->
                if (historyList.isNotEmpty()) {
                    binding.layoutContinueWatching.visibility = View.VISIBLE
                    setupHistoryRecycler(historyList)
                } else {
                    binding.layoutContinueWatching.visibility = View.GONE
                }
            }
        }
    }

    private fun setupHistoryRecycler(items: List<WatchHistoryEntity>) {
        binding.rvContinueWatching.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvContinueWatching.adapter = object : RecyclerView.Adapter<HistoryViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_continue_watching, parent, false)
                return HistoryViewHolder(view)
            }
            override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
                val item = items[position]
                holder.tvTitle.text = item.title
                holder.tvSubtitle.text = if (item.episodeTitle != null) "S${item.seasonNumber}E${item.episodeNumber}" else "${item.progressPercent}%"
                holder.progressBar.progress = item.progressPercent
                Glide.with(this@MainActivity).load(item.posterUrl).into(holder.ivPoster)

                holder.itemView.setOnClickListener {
                    val mediaItem = MediaItem(
                        id = item.id.split("_")[0],
                        title = item.title,
                        posterPath = item.posterUrl.replace("https://image.tmdb.org/t/p/w500", ""),
                        mediaType = item.mediaType
                    )
                    launchPlayer(mediaItem, item.seasonNumber, item.episodeNumber, item.episodeTitle)
                }
            }
            override fun getItemCount(): Int = items.size
        }
    }

    private fun loadCatalogFeeds() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val trendingMovies = repository.getTrendingMovies()
                val trendingTv = repository.getTrendingTvShows()
                val popularAnime = repository.getPopularAnime()

                setupHorizontalRecycler(binding.rvTrendingMovies, trendingMovies)
                setupHorizontalRecycler(binding.rvTrendingTv, trendingTv)
                setupHorizontalRecycler(binding.rvPopularAnime, popularAnime)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed to load catalog feeds", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            binding.layoutFeeds.visibility = View.GONE
            binding.rvSearchResults.visibility = View.VISIBLE
            val results = repository.search(query)
            setupGridRecycler(binding.rvSearchResults, results)
        }
    }

    private fun setupHorizontalRecycler(rv: RecyclerView, items: List<MediaItem>) {
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = MediaAdapter(items) { mediaItem ->
            showMediaDetails(mediaItem)
        }
    }

    private fun setupGridRecycler(rv: RecyclerView, items: List<MediaItem>) {
        rv.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3)
        rv.adapter = MediaAdapter(items) { mediaItem ->
            showMediaDetails(mediaItem)
        }
    }

    private fun showMediaDetails(item: MediaItem) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_media_details, null)
        dialog.setContentView(view)

        val ivPoster = view.findViewById<ImageView>(R.id.ivDetailPoster)
        val tvTitle = view.findViewById<TextView>(R.id.tvDetailTitle)
        val tvRating = view.findViewById<TextView>(R.id.tvDetailRating)
        val tvOverview = view.findViewById<TextView>(R.id.tvDetailOverview)
        val btnPlay = view.findViewById<Button>(R.id.btnDetailPlay)
        val btnWatchlist = view.findViewById<Button>(R.id.btnDetailWatchlist)
        val rvEpisodes = view.findViewById<RecyclerView>(R.id.rvDetailEpisodes)
        val tvEpisodesHeader = view.findViewById<TextView>(R.id.tvEpisodesHeader)

        tvTitle.text = item.displayTitle
        tvRating.text = "★ %.1f | %s".format(item.voteAverage, item.displayDate)
        tvOverview.text = item.overview
        Glide.with(this).load(item.posterUrl).into(ivPoster)

        // Setup Watchlist toggle button
        val watchlistDao = NxshaApplication.instance.database.watchlistDao()
        lifecycleScope.launch {
            val isSaved = watchlistDao.isBookmarked(item.id)
            btnWatchlist.text = if (isSaved) "✓ In Watchlist" else "+ Watchlist"

            btnWatchlist.setOnClickListener {
                lifecycleScope.launch {
                    val currentlySaved = watchlistDao.isBookmarked(item.id)
                    if (currentlySaved) {
                        watchlistDao.removeFromWatchlist(item.id)
                        btnWatchlist.text = "+ Watchlist"
                        Toast.makeText(this@MainActivity, "Removed from Watchlist", Toast.LENGTH_SHORT).show()
                    } else {
                        watchlistDao.addToWatchlist(
                            WatchlistEntity(
                                id = item.id,
                                title = item.displayTitle,
                                posterUrl = item.posterUrl,
                                mediaType = item.mediaType
                            )
                        )
                        btnWatchlist.text = "✓ In Watchlist"
                        Toast.makeText(this@MainActivity, "Added to Watchlist", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        if (item.mediaType == "tv") {
            tvEpisodesHeader.visibility = View.VISIBLE
            rvEpisodes.visibility = View.VISIBLE
            lifecycleScope.launch {
                val seasons = repository.getTvSeasons(item.id)
                if (seasons.isNotEmpty()) {
                    val firstSeason = seasons[0]
                    val episodes = repository.getSeasonEpisodes(item.id, firstSeason.seasonNumber)
                    rvEpisodes.layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
                    rvEpisodes.adapter = EpisodeAdapter(episodes) { ep ->
                        dialog.dismiss()
                        launchPlayer(item, ep.seasonNumber, ep.episodeNumber, ep.name)
                    }
                }
            }
        } else {
            tvEpisodesHeader.visibility = View.GONE
            rvEpisodes.visibility = View.GONE
        }

        btnPlay.setOnClickListener {
            dialog.dismiss()
            launchPlayer(item, 0, 0, null)
        }

        dialog.show()
    }

    override fun openMediaDetails(
        entityId: String,
        mediaType: String,
        title: String,
        posterUrl: String
    ) {
        val mediaItem = MediaItem(
            id = entityId,
            title = if (mediaType == "movie") title else null,
            name = if (mediaType == "tv") title else null,
            posterPath = posterUrl.replace("https://image.tmdb.org/t/p/w500", "").replace("https://image.tmdb.org/t/p/w342", ""),
            mediaType = mediaType
        )
        showMediaDetails(mediaItem)
    }

    private fun launchPlayer(item: MediaItem, season: Int, episode: Int, episodeTitle: String?) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("EXTRA_MEDIA_ITEM", item)
            putExtra("EXTRA_SEASON_NUMBER", season)
            putExtra("EXTRA_EPISODE_NUMBER", episode)
            putExtra("EXTRA_EPISODE_TITLE", episodeTitle)
        }
        startActivity(intent)
    }

    class MediaAdapter(
        private val items: List<MediaItem>,
        private val onClick: (MediaItem) -> Unit
    ) : RecyclerView.Adapter<MediaViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_media_card, parent, false)
            return MediaViewHolder(v)
        }
        override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.displayTitle
            holder.tvRating.text = "★ %.1f".format(item.voteAverage)
            Glide.with(holder.itemView.context).load(item.posterUrl).into(holder.ivPoster)
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount(): Int = items.size
    }

    class EpisodeAdapter(
        private val episodes: List<Episode>,
        private val onClick: (Episode) -> Unit
    ) : RecyclerView.Adapter<EpisodeViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_episode_card, parent, false)
            return EpisodeViewHolder(v)
        }
        override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
            val ep = episodes[position]
            holder.tvTitle.text = "Ep ${ep.episodeNumber}: ${ep.name}"
            Glide.with(holder.itemView.context).load(ep.stillUrl).into(holder.ivStill)
            holder.itemView.setOnClickListener { onClick(ep) }
        }
        override fun getItemCount(): Int = episodes.size
    }

    class MediaViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val ivPoster: ImageView = v.findViewById(R.id.ivPoster)
        val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        val tvRating: TextView = v.findViewById(R.id.tvRating)
    }

    class EpisodeViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val ivStill: ImageView = v.findViewById(R.id.ivEpisodeStill)
        val tvTitle: TextView = v.findViewById(R.id.tvEpisodeTitle)
    }

    class HistoryViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val ivPoster: ImageView = v.findViewById(R.id.ivHistoryPoster)
        val tvTitle: TextView = v.findViewById(R.id.tvHistoryTitle)
        val tvSubtitle: TextView = v.findViewById(R.id.tvHistorySubtitle)
        val progressBar: android.widget.ProgressBar = v.findViewById(R.id.pbHistoryProgress)
    }
}
