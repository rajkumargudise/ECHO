# ECHO

ECHO is a music player for Indian listeners. It plays the music already on your phone or computer, with a clean library, playlists, favorites, live visualizations and a small robot companion. It runs on **Android**, **Windows** and **Linux**.

**ECHO is a product of JARVEX ([jarvex.in](https://jarvex.in)). Developed and maintained by JARVEX.**

## Features
- Library by songs, artists, albums and folders (tiles or list), with an A–Z index and search that understands Indian scripts
- Favorites and playlists, with a `+` to add the playing song to a playlist
- Now Playing with several live visualizations drawn from the real audio
- A first-run welcome with short hints
- Private by design: your music stays on your device, no account, no ads, no analytics
- Android only: swipe between library tabs, swipe the Now Playing display and song name, home-screen widgets
- Desktop only: keyboard shortcuts (Space play/pause, Ctrl+Right next, Ctrl+Left previous, Esc back, media keys)

## Desktop (Windows and Linux)
The desktop app plays MP3, FLAC, OGG, WAV and AAC/M4A, reads tags and cover art, and keeps its data in `%APPDATA%\ECHO` on Windows or `~/.local/share/echo` on Linux.

Run it from source (needs JDK 17):

```
./gradlew :desktop:run
```

Build the packages:

| What | Command | Notes |
| --- | --- | --- |
| Portable app (own Java included) | `./gradlew :desktop:createDistributable` | Windows and Linux, output in `desktop/build/compose/binaries/main/app/` |
| Windows installer | `./gradlew :desktop:packageMsi :desktop:packageExe` | Windows only; needs the WiX Toolset |
| Linux packages | `./gradlew :desktop:packageDeb :desktop:packageRpm` | Linux only; needs `fakeroot` and `rpm` |

Packages can only be built on the system they are for. The **ECHO desktop** workflow in GitHub Actions builds both sets: open the Actions tab and choose Run workflow, or push a tag such as `v1.0.0`.

## Android
Requires JDK 17 and the Android SDK (compileSdk 34, minSdk 24). Create `local.properties` with your SDK path (`sdk.dir=...`), then:

```
./gradlew testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## Contact
- Website: [jarvex.in](https://jarvex.in)
- Email: info@jarvex.in

Copyright (c) JARVEX. All rights reserved.
