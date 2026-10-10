pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ECHO"

// The Android app needs the Android SDK. The desktop app does not, so on a machine without the SDK (a Linux
// build server, for example) only the desktop module is loaded. Pass -PdesktopOnly to skip Android on purpose.
val sdkFromLocalProperties: String? = file("local.properties").takeIf { it.isFile }
    ?.readLines()?.firstOrNull { it.startsWith("sdk.dir=") }
    ?.substringAfter("=")?.replace("\\:", ":")?.replace("\\\\", "\\")
val hasAndroidSdk = listOfNotNull(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"), sdkFromLocalProperties)
    .any { file(it).isDirectory }
if (hasAndroidSdk && !providers.gradleProperty("desktopOnly").isPresent) include(":app")
include(":desktop")
