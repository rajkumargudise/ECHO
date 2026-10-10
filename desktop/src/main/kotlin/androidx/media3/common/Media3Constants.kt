package androidx.media3.common

/** Stand-ins for the few Media3 constants the shared playback model refers to. The desktop engine does not use Media3. */
object Player {
    const val REPEAT_MODE_OFF = 0
    const val REPEAT_MODE_ONE = 1
    const val REPEAT_MODE_ALL = 2
}

object PlaybackException {
    const val ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001
    const val ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002
    const val ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE = 2003
    const val ERROR_CODE_IO_BAD_HTTP_STATUS = 2004
    const val ERROR_CODE_IO_FILE_NOT_FOUND = 2005
    const val ERROR_CODE_IO_NO_PERMISSION = 2006
    const val ERROR_CODE_PARSING_CONTAINER_MALFORMED = 3001
    const val ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED = 3003
    const val ERROR_CODE_DECODER_INIT_FAILED = 4001
    const val ERROR_CODE_DECODER_QUERY_FAILED = 4002
    const val ERROR_CODE_DECODING_FAILED = 4003
    const val ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 4005
}
