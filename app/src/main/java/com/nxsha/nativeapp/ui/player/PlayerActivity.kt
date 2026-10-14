package com.nxsha.nativeapp.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.nxsha.nativeapp.NxshaApplication
import com.nxsha.nativeapp.data.local.WatchHistoryEntity
import com.nxsha.nativeapp.data.model.MediaItem
import com.nxsha.nativeapp.data.model.Server
import com.nxsha.nativeapp.data.model.StreamSource
import com.nxsha.nativeapp.data.model.SubtitleTrack
import com.nxsha.nativeapp.data.resolver.StreamResolver
import com.nxsha.nativeapp.databinding.ActivityPlayerBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity(), PlayerGestureDetector.GestureListener {

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null

    private lateinit var mediaItem: MediaItem
    private var seasonNumber: Int = 0
    private var episodeNumber: Int = 0
    private var episodeTitle: String? = null

    private val streamResolver = StreamResolver()
    private var availableServers: List<Server> = emptyList()
    private var currentServerIndex = 0
    private var currentSourceIndex = 0

    private var availableSubtitles: List<SubtitleTrack> = emptyList()
    private var selectedSubtitle: SubtitleTrack? = null

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private var progressTrackingJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                exoPlayer?.pause()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Retrieve media arguments
        mediaItem = (intent.getSerializableExtra("EXTRA_MEDIA_ITEM") as? MediaItem) ?: run {
            Toast.makeText(this, "Missing media details", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        seasonNumber = intent.getIntExtra("EXTRA_SEASON_NUMBER", 0)
        episodeNumber = intent.getIntExtra("EXTRA_EPISODE_NUMBER", 0)
        episodeTitle = intent.getStringExtra("EXTRA_EPISODE_TITLE")

        setupImmersiveFullscreen()
        setupUI()
        setupGestures()
        initializePlayer()

        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        loadMediaStreams()
    }

    private fun setupImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupUI() {
        binding.tvPlayerTitle.text = if (episodeTitle != null) {
            "${mediaItem.displayTitle} - S${seasonNumber}E${episodeNumber}: $episodeTitle"
        } else {
            mediaItem.displayTitle
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPip.setOnClickListener { enterPipMode() }
        binding.btnQuality.setOnClickListener { showQualityDialog() }
        binding.btnServer.setOnClickListener { showServerDialog() }
        binding.btnSubtitles.setOnClickListener { showSubtitleDialog() }
    }

    private fun setupGestures() {
        val gestureDetector = PlayerGestureDetector(this, this)
        binding.playerView.setOnTouchListener(gestureDetector)
    }

    private fun initializePlayer() {
        requestAudioFocus()

        val player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        binding.playerView.player = player
        exoPlayer = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> binding.progressBarLoading.visibility = View.VISIBLE
                    Player.STATE_READY -> {
                        binding.progressBarLoading.visibility = View.GONE
                        startProgressTracker()
                    }
                    Player.STATE_ENDED -> stopProgressTracker()
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                binding.progressBarLoading.visibility = View.GONE
                // Auto-failover matching observed reverse-engineered behavior:
                // "RCTImageSource error detected, switching sources..."
                handlePlaybackFailure()
            }
        })
    }

    private fun loadMediaStreams() {
        binding.progressBarLoading.visibility = View.VISIBLE
        binding.tvServerName.text = "Resolving streams..."

        lifecycleScope.launch {
            availableServers = streamResolver.resolveMediaStreams(
                mediaId = mediaItem.id,
                title = mediaItem.displayTitle,
                season = if (seasonNumber > 0) seasonNumber else null,
                episode = if (episodeNumber > 0) episodeNumber else null
            )

            availableSubtitles = streamResolver.resolveSubtitles(mediaItem.id)

            if (availableServers.isEmpty()) {
                binding.progressBarLoading.visibility = View.GONE
                Toast.makeText(this@PlayerActivity, "No sources found", Toast.LENGTH_LONG).show()
                return@launch
            }

            currentServerIndex = 0
            currentSourceIndex = 0
            playCurrentSource()
        }
    }

    private fun playCurrentSource() {
        val server = availableServers.getOrNull(currentServerIndex) ?: return
        val source = server.sources.getOrNull(currentSourceIndex) ?: return

        binding.tvServerName.text = "${server.name} (${source.quality})"

        // Build data source with custom User-Agent and Referer headers
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(source.headers["User-Agent"] ?: "Mozilla/5.0 (Linux; Android 10; Mobile)")
            .setDefaultRequestProperties(source.headers)
            .setAllowCrossProtocolRedirects(true)

        val uri = Uri.parse(source.url)
        val mediaSource: MediaSource = if (source.isM3u8 || source.url.contains(".m3u8")) {
            HlsMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(ExoMediaItem.fromUri(uri))
        } else {
            ProgressiveMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(ExoMediaItem.fromUri(uri))
        }

        lifecycleScope.launch {
            // Check local Room DB for resume position
            val historyDao = NxshaApplication.instance.database.watchHistoryDao()
            val saved = historyDao.getHistoryItem(buildHistoryKey())
            val resumeMs = saved?.positionMs ?: 0L

            exoPlayer?.setMediaSource(mediaSource)
            exoPlayer?.prepare()
            if (resumeMs > 5000 && resumeMs < (saved?.durationMs ?: 0L) - 10000) {
                exoPlayer?.seekTo(resumeMs)
                Toast.makeText(this@PlayerActivity, "Resumed from ${formatMs(resumeMs)}", Toast.LENGTH_SHORT).show()
            }
            exoPlayer?.playWhenReady = true
        }
    }

    private fun handlePlaybackFailure() {
        val server = availableServers.getOrNull(currentServerIndex)
        if (server != null && currentSourceIndex + 1 < server.sources.size) {
            currentSourceIndex++
            Toast.makeText(this, "Switched to alternative video source", Toast.LENGTH_SHORT).show()
            playCurrentSource()
        } else if (currentServerIndex + 1 < availableServers.size) {
            currentServerIndex++
            currentSourceIndex = 0
            Toast.makeText(this, "Server failed. Switching to next server...", Toast.LENGTH_SHORT).show()
            playCurrentSource()
        } else {
            Toast.makeText(this, "All sources failed for this title", Toast.LENGTH_LONG).show()
        }
    }

    // --- GestureListener Implementation ---

    override fun onDoubleTapSeek(isForward: Boolean) {
        val player = exoPlayer ?: return
        val offset = if (isForward) 10000L else -10000L
        val target = (player.currentPosition + offset).coerceIn(0, player.duration)
        player.seekTo(target)
        showHudToast(if (isForward) "+10s" else "-10s")
    }

    override fun onSingleTap() {
        if (binding.controlsLayout.visibility == View.VISIBLE) {
            binding.controlsLayout.visibility = View.GONE
        } else {
            binding.controlsLayout.visibility = View.VISIBLE
        }
    }

    override fun onSpeedBoost(active: Boolean) {
        // Reverse-engineered 2.0x hold speed with speedometer HUD
        exoPlayer?.setPlaybackSpeed(if (active) 2.0f else 1.0f)
        if (active) {
            binding.hudToast.text = "2.0x Speed"
            binding.hudToast.visibility = View.VISIBLE
        } else {
            binding.hudToast.visibility = View.GONE
        }
    }

    override fun onBrightnessChanged(percent: Int) {
        showHudToast("Brightness: $percent%")
    }

    override fun onVolumeChanged(percent: Int) {
        showHudToast("Volume: $percent%")
    }

    private fun showHudToast(text: String) {
        binding.hudToast.text = text
        binding.hudToast.visibility = View.VISIBLE
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ binding.hudToast.visibility = View.GONE }, 1200)
    }

    // --- Quality & Server Pickers ---

    private fun showServerDialog() {
        val serverNames = availableServers.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Server")
            .setSingleChoiceItems(serverNames, currentServerIndex) { dialog, which ->
                currentServerIndex = which
                currentSourceIndex = 0
                dialog.dismiss()
                playCurrentSource()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQualityDialog() {
        val server = availableServers.getOrNull(currentServerIndex) ?: return
        val qualities = server.sources.map { it.quality }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Quality")
            .setSingleChoiceItems(qualities, currentSourceIndex) { dialog, which ->
                currentSourceIndex = which
                dialog.dismiss()
                playCurrentSource()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSubtitleDialog() {
        val subOptions = mutableListOf("Off")
        subOptions.addAll(availableSubtitles.map { "${it.label} (${it.language})" })

        val currentIdx = if (selectedSubtitle == null) 0 else availableSubtitles.indexOf(selectedSubtitle) + 1
        AlertDialog.Builder(this)
            .setTitle("Select Subtitles")
            .setSingleChoiceItems(subOptions.toTypedArray(), currentIdx) { dialog, which ->
                if (which == 0) {
                    selectedSubtitle = null
                    Toast.makeText(this, "Subtitles disabled", Toast.LENGTH_SHORT).show()
                } else {
                    selectedSubtitle = availableSubtitles[which - 1]
                    Toast.makeText(this, "Loaded: ${selectedSubtitle?.label}", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Audio Focus Management ---

    private fun requestAudioFocus() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        exoPlayer?.pause()
                    }
                }
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    // --- Progress Tracking (Local SQLite Room) ---

    private fun startProgressTracker() {
        stopProgressTracker()
        progressTrackingJob = lifecycleScope.launch {
            while (isActive) {
                val player = exoPlayer
                if (player != null && player.isPlaying) {
                    saveWatchProgress(player.currentPosition, player.duration)
                }
                delay(5000) // save every 5 seconds
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    private fun saveWatchProgress(positionMs: Long, durationMs: Long) {
        if (positionMs <= 0 || durationMs <= 0) return
        lifecycleScope.launch {
            val dao = NxshaApplication.instance.database.watchHistoryDao()
            dao.saveProgress(
                WatchHistoryEntity(
                    id = buildHistoryKey(),
                    title = mediaItem.displayTitle,
                    posterUrl = mediaItem.posterUrl,
                    mediaType = mediaItem.mediaType,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    episodeTitle = episodeTitle,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            )
        }
    }

    private fun buildHistoryKey(): String {
        return if (seasonNumber > 0) "${mediaItem.id}_S${seasonNumber}E${episodeNumber}" else mediaItem.id
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            Toast.makeText(this, "Picture-in-Picture requires Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && exoPlayer?.isPlaying == true) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            binding.controlsLayout.visibility = View.GONE
            binding.hudToast.visibility = View.GONE
            binding.playerView.useController = false
        } else {
            binding.playerView.useController = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(noisyReceiver)
        abandonAudioFocus()
        exoPlayer?.let {
            saveWatchProgress(it.currentPosition, it.duration)
            it.release()
        }
        exoPlayer = null
    }
}
