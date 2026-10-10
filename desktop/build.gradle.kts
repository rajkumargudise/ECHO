import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin { jvmToolchain(17) }

// The desktop app shares the platform-free parts of the Android app: design system, domain, visualizer, online layer
// and the screens that need nothing from Android. Everything else is written for the desktop under echo/player/desktop.
sourceSets {
    main {
        kotlin {
            srcDir("../app/src/main/java")
            exclude(
                "echo/player/online/OnlineHistory.kt",
                "echo/player/ui/EchoViewModel.kt",
                "echo/player/ui/library/LibraryScreen.kt",
                "echo/player/ui/nowplaying/NowPlayingScreen.kt",
                "echo/player/ui/settings/AboutSection.kt",
                "echo/player/ui/settings/SettingsScreen.kt",
                "echo/player/ui/welcome/**",
                "echo/player/ui/discover/**",
                "echo/player/ui/folders/**",
                "echo/player/ui/library/DetailScreens.kt",
                "echo/player/ui/library/PermissionScreen.kt",
                "echo/player/ui/ArtworkImage.kt",
            )
            include(
                "android/**",
                "androidx/media3/**",
                "echo/player/desktop/**",
                "echo/player/design/**",
                "echo/player/companion/**",
                "echo/player/domain/**",
                "echo/player/visualizer/**",
                "echo/player/online/**",
                "echo/player/ui/**",
                "echo/player/playback/NowPlayingModel.kt",
                "echo/player/playback/PlaybackControls.kt",
                "echo/player/playback/PlaybackState.kt",
                "echo/player/playback/PlaybackLogic.kt",
                "echo/player/playback/SleepTimer.kt",
                "echo/player/widget/WidgetState.kt",
            )
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.json:json:20240303")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    implementation("org.jflac:jflac-codec:1.5.2")
    implementation("com.tianscar.javasound:jaad:0.9.4") // AAC and M4A
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
}

compose.desktop {
    application {
        mainClass = "echo.player.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)
            modules("java.instrument", "jdk.unsupported")
            packageName = "ECHO"
            packageVersion = "1.0.0"
            vendor = "JARVEX"
            description = "ECHO music player by JARVEX (jarvex.in)"
            windows {
                menuGroup = "JARVEX"
                shortcut = true
                dirChooser = true
                iconFile.set(project.file("packaging/echo.ico"))
                upgradeUuid = "6f0c7a42-3f1d-4a55-9a7e-5b0e1c9d2e11"
            }
            linux {
                menuGroup = "Audio"
                debMaintainer = "info@jarvex.in"
                appCategory = "AudioVideo"
                shortcut = true
                iconFile.set(project.file("packaging/echo.png"))
            }
        }
    }
}
