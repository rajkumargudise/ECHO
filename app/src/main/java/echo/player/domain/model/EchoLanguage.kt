package echo.player.domain.model

/**
 * The languages ECHO understands, as ECHO's own model. Provider language values are normalized into it; anything the
 * provider does not state, or that does not map cleanly, is [UNKNOWN] rather than a guess.
 */
enum class EchoLanguage(val label: String, val isIndian: Boolean = true) {
    TELUGU("Telugu"),
    HINDI("Hindi"),
    TAMIL("Tamil"),
    MALAYALAM("Malayalam"),
    KANNADA("Kannada"),
    BENGALI("Bengali"),
    MARATHI("Marathi"),
    PUNJABI("Punjabi"),
    GUJARATI("Gujarati"),
    ODIA("Odia"),
    ASSAMESE("Assamese"),
    URDU("Urdu"),
    ENGLISH("English", isIndian = false),

    /** An Indian language outside the thirteen above (Sanskrit, Bhojpuri, Konkani...). */
    OTHER_INDIAN("Other Indian"),
    UNKNOWN("Unknown", isIndian = false),
    ;

    companion object {
        private val aliases: Map<String, EchoLanguage> = buildMap {
            fun add(language: EchoLanguage, vararg names: String) = names.forEach { put(it, language) }
            add(TELUGU, "telugu", "te", "tel")
            add(HINDI, "hindi", "hi", "hin")
            add(TAMIL, "tamil", "ta", "tam")
            add(MALAYALAM, "malayalam", "ml", "mal")
            add(KANNADA, "kannada", "kn", "kan")
            add(BENGALI, "bengali", "bangla", "bn", "ben")
            add(MARATHI, "marathi", "mr", "mar")
            add(PUNJABI, "punjabi", "panjabi", "pa", "pan")
            add(GUJARATI, "gujarati", "gu", "guj")
            add(ODIA, "odia", "oriya", "or", "ori")
            add(ASSAMESE, "assamese", "as", "asm")
            add(URDU, "urdu", "ur", "urd")
            add(ENGLISH, "english", "en", "eng")
            add(
                OTHER_INDIAN,
                "sanskrit", "sa", "san", "bhojpuri", "bho", "rajasthani", "raj", "konkani", "kok", "kashmiri", "ks", "kas",
                "sindhi", "sd", "snd", "maithili", "mai", "dogri", "doi", "santali", "sat", "manipuri", "mni", "meitei", "tulu", "tcy",
            )
        }

        /** Normalizes a provider's language value ("te", "Telugu", "hi-IN", "hin"...). Null, blank or unmapped is UNKNOWN. */
        fun fromProvider(raw: String?): EchoLanguage {
            val key = raw?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')?.takeIf { it.isNotEmpty() } ?: return UNKNOWN
            return aliases[key] ?: UNKNOWN
        }

        fun fromName(name: String?): EchoLanguage = entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/** Language filter chips built only from the languages that really appear in the results on screen. */
object LanguageFacets {
    /** Distinct known languages in [tracks], most frequent first. Empty when no track states a language. */
    fun of(tracks: List<RemoteTrack>): List<EchoLanguage> =
        tracks.map { it.language }.filter { it != EchoLanguage.UNKNOWN }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    fun filter(tracks: List<RemoteTrack>, language: EchoLanguage?): List<RemoteTrack> =
        if (language == null) tracks else tracks.filter { it.language == language }
}
