package echo.player.ui.discover

import echo.player.online.SourceError

/** Short, truthful wording for why an online request failed. No URLs, no credentials. */
fun SourceError.userMessage(): String = when (this) {
    SourceError.NotConfigured -> "This build has no online music source set up."
    SourceError.Offline -> "You're offline."
    SourceError.Timeout -> "The source took too long to answer. Try again."
    is SourceError.Http -> "The source returned an error (HTTP $code). Try again later."
    SourceError.Malformed -> "The source sent a response ECHO couldn't read."
    SourceError.NotAuthorized -> "The source didn't accept this app's access."
    SourceError.TrackUnavailable -> "This track is no longer available."
    SourceError.Unknown -> "Something went wrong. Try again."
}
