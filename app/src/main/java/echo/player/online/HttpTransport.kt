package echo.player.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val code: Int, val body: String)

/** The one thing a source needs from the network. Faked in tests; the real one is [UrlConnectionTransport]. */
interface HttpTransport {
    /** Throws [IOException] for network failures (UnknownHost, Connect, Timeout...). Never logs the URL. */
    suspend fun get(url: String): HttpResponse
}

/** Plain HttpURLConnection: HTTPS only, default certificate validation, bounded time and size. */
class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
    private val maxBytes: Int = 2_000_000,
) : HttpTransport {
    override suspend fun get(url: String): HttpResponse = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) throw IOException("Only https is allowed")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) throw IOException("Response too large")
                }
                out.toString(Charsets.UTF_8.name())
            }.orEmpty()
            HttpResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }
}
