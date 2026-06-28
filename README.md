<p align="center">
  <img src="app-logo.png" alt="AstraTV" width="120" />
</p>

<h1 align="center">AstraTV</h1>

<p align="center">
  <strong>IPTV Streaming Player for Android</strong><br>
  Live TV, Movies & Series on phones, tablets, and Android TV
</p>

---

## Features

- **Three IPTV protocols** — MAC/STB (Stalker/Ministra), Xtream Codes, and M3U/M3U8 playlists
- **Dual UI** — touch-optimized for phones/tablets and D-pad optimized for Android TV (Leanback)
- **Built-in video player** — ExoPlayer with HLS support, fullscreen mode, and seek controls
- **Live search** — debounced instant search across all channels and VOD content
- **Profile management** — save, load, and delete multiple connection profiles
- **Remote import** — embedded HTTP server with QR code to import playlists from your phone or PC
- **Multi-language** — Italian, English, French, German, Spanish, Russian
- **Account info** — server name, expiration date, connection limits at a glance
- **Dark theme** — Material3 dark color scheme throughout

## Tech Stack

| Category | Technology |
|---|---|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material3 |
| Video Player | AndroidX Media3 / ExoPlayer 1.5.1 |
| HTTP | OkHttp 4.12 |
| JSON | Gson 2.11 |
| Image Loading | Coil Compose 2.7 |
| Navigation | Navigation Compose 2.8 |
| Persistence | DataStore Preferences + SQLite |
| QR Code | ZXing Core 3.5 |

**Min SDK:** 26 (Android 8.0) • **Target SDK:** 36

## Build

```bash
git clone https://github.com/Ak1r4Yuk1/AstraTV.git
cd AstraTV
./gradlew assembleDebug
```

The signed release APK is built with:

```bash
./gradlew assembleRelease
```

Place your keystore at `keystore/release.jks` and configure signing properties in `local.properties`:

```properties
RELEASE_STORE_FILE=keystore/release.jks
RELEASE_STORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=release
RELEASE_KEY_PASSWORD=your_password
```

## Architecture

```
View (Compose) → ViewModel (StateFlow) → Repository → API Client → IPTV Server
```

The app follows **MVVM** with a single centralized ViewModel managing all state via `MutableStateFlow`. The `StalkerRepository` abstracts away the IPTV protocol, making the UI indifferent to whether the source is Stalker, Xtream, or M3U.

## License

This project is private. All rights reserved.
