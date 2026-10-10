package android.net

import java.io.File

/**
 * A minimal stand-in for Android's Uri so the shared domain model compiles unchanged on the desktop.
 * It only keeps the text of the address; the desktop engine plays files by path.
 */
class Uri private constructor(private val text: String) {
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is Uri && other.text == text
    override fun hashCode(): Int = text.hashCode()

    companion object {
        val EMPTY = Uri("")
        fun parse(value: String): Uri = Uri(value)
        fun fromFile(file: File): Uri = Uri(file.toURI().toString())
    }
}
