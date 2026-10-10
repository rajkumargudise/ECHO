# ECHO

ECHO is a music player for Android, built for Indian listeners. It plays the music already on your phone, with a clean library, playlists, favorites, live visualizations and home-screen widgets.

**ECHO is a product of JARVEX ([jarvex.in](https://jarvex.in)). Developed and maintained by JARVEX.**

## Features
- Library by songs, artists, albums and folders (tiles or list), with swipe between tabs
- Favorites and playlists, with a `+` to add the playing song to a playlist
- Now Playing with several visualizations: swipe the display to change it, swipe the song name for next or previous
- Home-screen widgets with progress, elapsed time and a favorite heart
- A first-run welcome with short hints
- Private by design: your music stays on your phone, no account, no ads, no analytics

## Build
Requires JDK 17 and the Android SDK (compileSdk 34, minSdk 24).

```
./gradlew testDebugUnitTest assembleDebug
```

Create `local.properties` with your SDK path (`sdk.dir=...`). The APK is written to `app/build/outputs/apk/debug/`.

## Contact
- Website: [jarvex.in](https://jarvex.in)
- Email: info@jarvex.in

Copyright (c) JARVEX. All rights reserved.
