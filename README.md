# Nxsha Native

> **Open-source, 100% client-side streaming companion app for Android.**  
> No backend. No server. No ads. All data stays on your device.

---

## What It Does

| Feature | How It Works |
|---|---|
| Browse trending movies & TV | TMDB public API (free key) |
| Search titles | TMDB `/search/multi` |
| Watch episodes | FebBox 2-step stream resolver |
| Subtitles | TMDB `/movie/{id}/videos` + FebBox subtitle tracks |
| Watch history & progress | Room SQLite (local, never synced) |
| Bookmarks / Watchlist | Room SQLite (local, never synced) |
| Video playback | Media3 ExoPlayer (HLS + progressive MP4) |
| Gesture controls | Custom `GestureDetector` (volume / brightness / seek / speed) |

---

## Architecture

```
nxsha-native/
├── app/src/main/
│   ├── java/com/nxsha/nativeapp/
│   │   ├── NxshaApplication.kt          # App singleton, Glide init
│   │   ├── data/
│   │   │   ├── model/MediaModels.kt     # MediaItem, Season, Episode, StreamSource…
│   │   │   ├── local/
│   │   │   │   └── WatchHistoryDatabase.kt  # Room DB: watch history + watchlist DAOs
│   │   │   ├── resolver/StreamResolver.kt   # FebBox client-side stream resolver
│   │   │   └── repository/CatalogRepository.kt  # TMDB API: catalog + search
│   │   └── ui/
│   │       ├── main/
│   │       │   ├── MainActivity.kt      # Home + search + detail bottom sheet
│   │       │   └── MediaCardAdapter.kt  # Horizontal carousel adapter
│   │       ├── player/
│   │       │   ├── PlayerActivity.kt    # ExoPlayer, HLS, multi-server failover
│   │       │   └── PlayerGestureDetector.kt  # Swipe/hold gesture engine
│   │       └── watchlist/
│   │           ├── WatchlistFragment.kt
│   │           └── WatchlistAdapter.kt
│   └── res/
│       ├── layout/                      # All XML layouts
│       └── values/                      # themes, colors, strings, dimens
└── build.gradle.kts                     # Gradle config (KSP, Media3, Room, OkHttp, Glide)
```

---

## Dependencies

| Library | Purpose |
|---|---|
| `androidx.media3:media3-exoplayer-hls` | HLS stream playback |
| `androidx.media3:media3-ui` | `PlayerView` controls |
| `androidx.room` | Local SQLite (watch history, watchlist) |
| `com.squareup.okhttp3` | HTTP client for TMDB + FebBox |
| `com.github.bumptech.glide` | Image loading / poster caching |
| `com.google.android.material` | Material 3 UI components |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Async streams |

---

## How to Build

### Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- Android SDK `compileSdk 34`, `minSdk 24`

### Steps

1. Clone or copy this folder to your machine.
2. Open Android Studio → **File → Open** → select `nxsha-native/`.
3. Let Gradle sync (it downloads all dependencies automatically).
4. Plug in a device or start an emulator (API 24+).
5. Click **▶ Run**.

### Release Build

```bash
./gradlew assembleRelease
```

Sign with your own keystore. The `proguard-rules.pro` file already keeps Room, Gson, OkHttp, and Media3 classes.

---

## Stream Resolution Flow

```
User taps "Watch"
      │
      ▼
CatalogRepository.getEpisodeServers(mediaId, season, episode)
      │  (calls TMDB for episode metadata)
      ▼
StreamResolver.resolve(shareKey)
      │
      ├─ Step 1: GET febbox.com/file/file_share_list?share_key={key}
      │          → Extract file list with fid values
      │
      └─ Step 2: GET febbox.com/console/video_quality_list?fid={fid}
                 → Returns quality tiers with direct stream URLs
                         │
                         ▼
                  PlayerActivity
                  ExoPlayer.setMediaSource(HlsMediaSource / ProgressiveMediaSource)
```

If FebBox returns no sources, `StreamResolver` falls back to the bundled public HLS test stream so the player always has something to play.

---

## Configuration

### TMDB API Key

Located in [`CatalogRepository.kt`](app/src/main/java/com/nxsha/nativeapp/data/repository/CatalogRepository.kt):

```kotlin
private const val TMDB_API_KEY = "38b975e52514faec09968840c9584d4d"
```

Replace with your own free key from [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api).

### FebBox Share Key

To resolve real streams, you need a valid FebBox `share_key`. This can be:
- Passed programmatically via `StreamResolver.resolve(shareKey)`
- Stored in `SharedPreferences` and entered in a future Settings screen

### Adding a Custom Stream Provider

Implement `StreamResolver.resolve(shareKey: String)` or extend the function to call your own provider API. The function returns `List<StreamSource>` — any list of URLs the player can consume.

---

## Legal & Ethics

- This app does **not** host, store, or redistribute any copyrighted content.
- It is a **client** that queries publicly accessible APIs (TMDB, FebBox) using the same calls a standard web browser would make.
- The original Nxsha app was reverse-engineered for study and interoperability purposes only under fair use / permitted reverse engineering provisions.
- **You are responsible** for ensuring your use of FebBox complies with FebBox's terms of service.
- TMDB data is used under the [TMDB Terms of Use](https://www.themoviedb.org/documentation/api/terms-of-use).

---

## License

```
Apache License 2.0

Copyright (c) 2024 Nxsha Native Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

---

*Built with ❤️ using Kotlin, Media3 ExoPlayer, and TMDB.*
