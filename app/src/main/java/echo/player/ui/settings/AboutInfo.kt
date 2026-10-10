package echo.player.ui.settings

/** What the About and Privacy cards say. Plain data so the wording is easy to review and test. */
object AboutInfo {
    const val APP_NAME = "ECHO"
    const val CREDIT = "ECHO is a product of JARVEX (jarvex.in). Developed and maintained by JARVEX."
    const val COMPANY = "JARVEX is a digital services company from India: websites and hosting, web and business applications, " +
        "AI automation and ongoing support. Build Smarter. Grow Faster."
    const val WEBSITE_LABEL = "jarvex.in"
    const val WEBSITE_URL = "https://jarvex.in"
    const val EMAIL = "info@jarvex.in"
    const val PHONE_LABEL = "+91 99495 01828"
    const val PHONE_URI = "tel:+919949501828"

    val privacy: List<String> = listOf(
        "Your music stays on your phone. ECHO reads the music you allow it to and never uploads it.",
        "No account, no ads and no tracking. ECHO has no analytics and JARVEX collects nothing about you from this app.",
        "Your favorites, playlists, recent plays and settings are stored only on this phone. Uninstalling ECHO removes them.",
        "Permissions: music access to build your library, notifications for the player controls, and internet only for optional online features. Online connections use HTTPS.",
    )
    /** The same promises as [privacy], worded for the computer versions (Windows and Linux). */
    val desktopPrivacy: List<String> = listOf(
        "Your music stays on your computer. ECHO reads the folders you choose and never uploads your music.",
        "No account, no ads and no tracking. ECHO has no analytics and JARVEX collects nothing about you from this app.",
        "Your favorites, playlists and settings are stored only on this computer, in ECHO's own folder.",
        "ECHO only uses the internet for optional online features, over HTTPS.",
    )
    const val PRIVACY_CONTACT = "Questions about privacy? Write to info@jarvex.in."

    fun versionLine(versionName: String, versionCode: Int) = "Version $versionName (build $versionCode)"
}
