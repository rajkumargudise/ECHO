package echo.player.domain

/** The full-screen destinations that sit on top of the library. */
enum class Screen { LIBRARY, NOW_PLAYING, MUSIC_FOLDERS, DISCOVER, SETTINGS }

/** A tiny back stack: the library is always at the bottom, and Back pops one screen. */
data class NavState(val stack: List<Screen> = listOf(Screen.LIBRARY)) {
    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun open(screen: Screen): NavState =
        if (screen == Screen.LIBRARY || screen == current) this else copy(stack = stack + screen)

    fun back(): NavState = if (canGoBack) copy(stack = stack.dropLast(1)) else this
}
