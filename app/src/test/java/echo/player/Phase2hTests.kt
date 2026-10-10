package echo.player

import echo.player.ui.SwipeDecision
import echo.player.ui.SwipeDirection
import echo.player.ui.welcome.WelcomeContent
import echo.player.visualizer.VisualizerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SwipeDecisionTests {
    private val threshold = 100f

    @Test
    fun aLongDragTowardsALeftOrRightEdgeIsASwipe() {
        assertEquals(SwipeDirection.LEFT, SwipeDecision.of(-150f, threshold))
        assertEquals(SwipeDirection.RIGHT, SwipeDecision.of(150f, threshold))
        assertEquals(SwipeDirection.LEFT, SwipeDecision.of(-100f, threshold))
        assertEquals(SwipeDirection.RIGHT, SwipeDecision.of(100f, threshold))
    }

    @Test
    fun aShortOrWobblyDragIsNotASwipe() {
        assertEquals(SwipeDirection.NONE, SwipeDecision.of(0f, threshold))
        assertEquals(SwipeDirection.NONE, SwipeDecision.of(60f, threshold))
        assertEquals(SwipeDirection.NONE, SwipeDecision.of(-99f, threshold))
    }
}

class DisplaySwipeModeTests {
    @Test
    fun previousUndoesNextForEveryDisplay() {
        for (m in VisualizerMode.entries) assertEquals(m, m.next().previous())
        for (m in VisualizerMode.entries) assertEquals(m, m.previous().next())
    }

    @Test
    fun theCycleWrapsInBothDirections() {
        val first = VisualizerMode.entries.first()
        val last = VisualizerMode.entries.last()
        assertEquals(last, first.previous())
        assertEquals(first, last.next())
    }

    @Test
    fun swipingThroughEveryDisplayVisitsEachOnce() {
        val seen = generateSequence(VisualizerMode.entries.first()) { it.next() }.take(VisualizerMode.entries.size).toSet()
        assertEquals(VisualizerMode.entries.toSet(), seen)
    }
}

class WelcomeContentTests {
    @Test
    fun everyHintHasATitleAndAReadableBody() {
        assertTrue(WelcomeContent.hints.size in 3..8)
        for (h in WelcomeContent.hints) {
            assertTrue(h.title.isNotBlank())
            assertTrue(h.body.length in 30..260)
        }
        assertEquals(WelcomeContent.hints.size, WelcomeContent.hints.map { it.title }.toSet().size)
    }

    @Test
    fun theHintsDescribeTheGesturesThatReallyExist() {
        val text = WelcomeContent.hints.joinToString(" ") { it.title + " " + it.body }.lowercase()
        assertTrue(text.contains("swipe") && text.contains("playlist") && text.contains("widget"))
        assertTrue("states the song swipe direction", text.contains("right for the next") && text.contains("left for the previous"))
    }
}

class NowPlayingGesturePolicyTests {
    private fun src(vararg parts: String) = File("src/main/java/echo/player/" + parts.joinToString("/")).readText()

    @Test
    fun swipingTheSongNameSkipsRightForNextAndLeftForPrevious() {
        val np = src("ui", "nowplaying", "NowPlayingScreen.kt")
        assertTrue(np.contains("onHorizontalSwipe(onLeft = onSwipePrevious, onRight = onSwipeNext)"))
        assertTrue(np.contains("onSwipeNext = controls::next, onSwipePrevious = controls::previous"))
        assertTrue(np.contains("onHorizontalSwipe(onLeft = controls::previous, onRight = controls::next)"))
    }

    @Test
    fun swipingTheDisplayChangesTheVisualization() {
        val np = src("ui", "nowplaying", "NowPlayingScreen.kt")
        assertTrue(np.contains("onLeft = { onModeChange(mode.next()) }, onRight = { onModeChange(mode.previous()) }"))
    }

    @Test
    fun theWelcomeShowsOnceAndCanBeBroughtBack() {
        val vm = src("ui", "EchoViewModel.kt")
        assertTrue(vm.contains("showWelcome by mutableStateOf(!settings.welcomeSeen)"))
        assertTrue(vm.contains("settings.welcomeSeen = true"))
        val app = src("MainActivity.kt")
        assertEquals("shown both before and after the permission screen", 2, Regex("WelcomeHints\\(onDone").findAll(app).count())
        assertTrue(src("ui", "settings", "SettingsScreen.kt").contains("Show welcome hints"))
        assertFalse(src("ui", "welcome", "WelcomeHints.kt").contains("http"))
    }
}

class AboutInfoTests {
    @Test
    fun theCreditAndContactDetailsAreJarvex() {
        assertTrue(echo.player.ui.settings.AboutInfo.CREDIT.contains("JARVEX"))
        assertEquals("info@jarvex.in", echo.player.ui.settings.AboutInfo.EMAIL)
        assertEquals("https://jarvex.in", echo.player.ui.settings.AboutInfo.WEBSITE_URL)
    }
}

class AboutDetailsTests {
    @Test
    fun companyAndContactDetailsAreComplete() {
        val a = echo.player.ui.settings.AboutInfo
        assertTrue(a.COMPANY.contains("JARVEX"))
        assertEquals("tel:+919949501828", a.PHONE_URI)
        assertEquals("Version 1.0 (build 1)", a.versionLine("1.0", 1))
    }

    @Test
    fun privacyTextMakesOnlyClaimsTheAppKeeps() {
        val text = echo.player.ui.settings.AboutInfo.privacy.joinToString(" ").lowercase()
        assertTrue(text.contains("never uploads") && text.contains("no analytics") && text.contains("only on this phone"))
        // the manifest must not request anything the privacy text does not mention
        val manifest = File("src/main/AndroidManifest.xml").readText()
        for (perm in Regex("uses-permission android:name=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }) {
            assertTrue(perm, perm.substringAfterLast('.') in setOf(
                "INTERNET", "READ_MEDIA_AUDIO", "READ_EXTERNAL_STORAGE", "POST_NOTIFICATIONS",
                "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MEDIA_PLAYBACK", "WAKE_LOCK",
            ))
        }
    }
}
